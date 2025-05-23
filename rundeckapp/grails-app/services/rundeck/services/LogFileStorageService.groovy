/*
 * Copyright 2016 SimplifyOps, Inc. (http://simplifyops.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package rundeck.services

import com.codahale.metrics.Counter
import com.dtolabs.rundeck.app.internal.logging.FSStreamingLogReader
import com.dtolabs.rundeck.app.internal.logging.FSStreamingLogWriter
import com.dtolabs.rundeck.core.logging.internal.RundeckLogFormat
import com.dtolabs.rundeck.app.internal.workflow.PeriodicFileChecker
import com.dtolabs.rundeck.core.dispatcher.DataContextUtils
import com.dtolabs.rundeck.core.execution.ExecutionNotFound
import com.dtolabs.rundeck.core.execution.ExecutionReference
import com.dtolabs.rundeck.core.execution.logstorage.AsyncExecutionFileLoaderService
import com.dtolabs.rundeck.core.execution.logstorage.ExecutionFileLoader
import com.dtolabs.rundeck.core.execution.logstorage.ExecutionFileLoaderService
import com.dtolabs.rundeck.core.execution.logstorage.ExecutionFileState
import com.dtolabs.rundeck.core.logging.*
import com.dtolabs.rundeck.core.plugins.configuration.PropertyResolverFactory
import com.dtolabs.rundeck.core.plugins.configuration.PropertyScope
import com.dtolabs.rundeck.plugins.logging.ExecutionFileStoragePlugin
import com.dtolabs.rundeck.server.plugins.services.ExecutionFileStoragePluginProviderService
import grails.events.EventPublisher
import grails.gorm.transactions.Transactional
import grails.web.mapping.LinkGenerator
import org.hibernate.sql.JoinType
import org.rundeck.app.config.ConfigService
import org.rundeck.app.config.SysConfigProp
import org.rundeck.app.config.SystemConfig
import org.rundeck.app.config.SystemConfigurable
import org.rundeck.app.data.model.v1.logstorage.LogFileStorageRequestData
import org.rundeck.app.data.providers.v1.logstorage.LogFileStorageRequestProvider
import org.rundeck.app.services.ExecutionFile
import org.rundeck.app.services.ExecutionFileProducer
import org.springframework.beans.factory.InitializingBean
import org.springframework.context.ApplicationContext
import org.springframework.context.ApplicationContextAware
import org.springframework.context.ApplicationListener
import org.springframework.context.event.ContextClosedEvent
import org.springframework.core.task.AsyncListenableTaskExecutor
import org.springframework.core.task.TaskExecutor
import org.springframework.scheduling.TaskScheduler
import rundeck.Execution
import rundeck.services.events.ExecutionCompleteEvent
import rundeck.services.execution.ValueHolder
import rundeck.services.execution.ValueWatcher
import rundeck.services.logging.ExecutionFileUtil
import rundeck.services.logging.ExecutionLogReader
import rundeck.services.logging.LogFileLoader
import rundeck.services.logging.MultiFileStorageRequestImpl

import javax.validation.constraints.NotNull
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.*
import java.util.function.Consumer
import java.util.function.Supplier

/**
 * Manage execution file storage retrieve and store requests.
 * "executorService" runs tasks within a hibernate session.
 * "logFileTaskExecutor" runs asynchronous tasks as well as two threads which process retrieve/storage queues
 * "scheduledExecutor" runs delayed tasks for retrying at a later time, OR runs periodic queue processing of resumed tasks
 *     this depends on whether using 'periodic' or 'delayed' strategy, default 'periodic'.
 * "retryIncompleteRequests" queue for resumed incomplete requests
 * "storageRequests" blocking queue for storage requests
 * "retrievalRequests" blocking queue for retrieval requests
 */
class LogFileStorageService
        implements
                InitializingBean,
                ApplicationContextAware,
                EventPublisher,
                ExecutionFileLoaderService,
                AsyncExecutionFileLoaderService,
                SystemConfigurable,
                ApplicationListener<ContextClosedEvent> {

    static final RundeckLogFormat rundeckLogFormat = new RundeckLogFormat()
    ExecutionFileStoragePluginProviderService executionFileStoragePluginProviderService
    PluginService pluginService
    def frameworkService
    AsyncListenableTaskExecutor logFileTaskExecutor
    AsyncListenableTaskExecutor logFileStorageTaskExecutor
    TaskScheduler logFileStorageTaskScheduler
    TaskExecutor logFileStorageDeleteRemoteTask
    def executorService
    def grailsApplication
    LinkGenerator grailsLinkGenerator
    ApplicationContext applicationContext
    def metricService
    ConfigService configurationService
    def jobStateService
    LogFileStorageRequestProvider logFileStorageRequestProvider

    /**
     * Queue of log storage requests ids, for incomplet requests being resumed
     */
    private BlockingQueue<Long> retryIncompleteRequests = new LinkedBlockingQueue<>()
    private Set<Long> retryRequests = new HashSet<>()
    /**
     * Request IDs that were attempted and then failed
     */
    private Set<Long> failedRequests = new HashSet<>()
    private Map<Long,List<String>> failures = new HashMap<>()
    /**
     * Queue of log storage requests, processed by {@link #runStorageRequest(java.util.Map)}
     */
    private BlockingQueue<Map> storageRequests = new LinkedBlockingQueue<Map>()
    /**
     * Queue of log retrieval requests, processed by {@link #runRetrievalRequest(java.util.Map)}
     */
    private BlockingQueue<Map> retrievalRequests = new LinkedBlockingQueue<Map>()
    /**
     * CompleteableFuture listeners (promises) for the retrieval results
     */
    protected ConcurrentHashMap<String, CompletableFuture<RetrieveFileResult>> logFileRetrievalListeners =
            new ConcurrentHashMap<String, CompletableFuture<RetrieveFileResult>>()
    /**
     * Currently running requests
     */
    private Queue<Map> running = new ConcurrentLinkedQueue<Map>()
    /**
     * Map of log retrieval actions
     */
    protected ConcurrentHashMap<String, Map> logFileRetrievalRequests = new ConcurrentHashMap<String, Map>()
    protected ConcurrentHashMap<String, Map> logFileRetrievalResults = new ConcurrentHashMap<String, Map>()

    static final SysConfigProp STORAGE_CONCURRENCY_LIMIT = config {
        key 'rundeck.execution.logs.fileStorage.storageTasks.concurrencyLimit'
        label 'Storage Concurrency Limit'
        description 'Max number of concurrent log file storage tasks'
        datatype 'integer'
        defaultValue '5'
    }

    static final SysConfigProp RETRIEVAL_CONCURRENCY_LIMIT = config {
        key 'rundeck.execution.logs.fileStorage.retrievalTasks.concurrencyLimit'
        label 'Retrieval Concurrency Limit'
        description 'Max number of concurrent log file storage tasks'
        datatype 'integer'
        defaultValue '5'
    }

    static final SysConfigProp RESUME_INCOMPLETE_STRATEGY = config {
        key 'rundeck.logFileStorageService.resumeIncomplete.strategy'
        label 'Resume Incomplete Strategy'
        description 'Strategy for resuming incomplete requests'
        datatype 'String'
        defaultValue 'periodic'
    }

    static final SysConfigProp STORAGE_RETRY_COUNT = config {
        key 'rundeck.execution.logs.fileStorage.storageRetryCount'
        label 'Max Storage Retry Count'
        description 'Max number of times to try storage requests'
        datatype 'integer'
        defaultValue '1'
    }

    static final SysConfigProp STORAGE_RETRY_DELAY = config {
        key 'rundeck.execution.logs.fileStorage.storageRetryDelay'
        label 'Storage Retry Delay'
        description 'Delay between retries, in seconds'
        datatype 'integer'
        defaultValue '60'
    }

    static final SysConfigProp CANCEL_ON_STORAGE_FAILURE = config {
        key 'rundeck.execution.logs.fileStorage.cancelOnStorageFailure'
        label 'Cancel on storage failure'
        description 'Whether to cancel (delete) storage requests after maximum attempts have been reached'
        datatype 'boolean'
        defaultValue 'true'
    }

    static final SysConfigProp FORCE_PARTIAL_CHECKING = config {
        key 'rundeck.execution.logs.fileStorage.forcePartialChecking'
        label 'Force partial logs checking'
        description 'Whether to always check for partial logs even for executions local to the cluster member'
        datatype 'boolean'
        defaultValue 'false'
    }

    static final SysConfigProp RETRIEVAL_RETRY_COUNT = config {
        key 'rundeck.execution.logs.fileStorage.retrievalRetryCount'
        label 'Max Retrieval Retry Count'
        description 'Max number of times to try retrieval requests'
        datatype 'integer'
        defaultValue '3'
    }

    static final SysConfigProp RETRIEVAL_RETRY_DELAY = config {
        key 'rundeck.execution.logs.fileStorage.retrievalRetryDelay'
        label 'Retrieval Retry Delay'
        description 'Delay between retrieval retry attempts, in seconds'
        datatype 'integer'
        defaultValue '60'
    }

    static final SysConfigProp REMOTE_PENDING_DELAY = config {
        key 'rundeck.execution.logs.fileStorage.remotePendingDelay'
        label 'Remote Pending Delay'
        description 'Delay after execution completion before log file is available considered to be in PENDING state, in seconds'
        datatype 'integer'
        defaultValue '120'
    }

    static final SysConfigProp CHECKPOINT_TIME_INTERVAL = config {
        key 'rundeck.execution.logs.fileStorage.checkpoint.time.interval'
        label 'Checkpoint Time Interval'
        description 'Time interval between checkpoint storage requests'
        datatype 'String'
        defaultValue '30s'
    }

    static final SysConfigProp CHECKPOINT_TIME_MINIMUM = config {
        key 'rundeck.execution.logs.fileStorage.checkpoint.time.minimum'
        label 'Checkpoint Time Minimum'
        description 'Minimum time interval to wait between checkpoint storage requests'
        datatype 'String'
        defaultValue '30s'
    }
    static final SysConfigProp CHECKPOINT_FILESIZE_MINIMUM = config {
        key 'rundeck.execution.logs.fileStorage.checkpoint.fileSize.minimum'
        label 'Checkpoint Filesize Minimum'
        description 'Minimum file size before first checkpoint storage requests, in file size string in the form \'###[tgkm][b]\''
        datatype 'String'
        defaultValue '0'
    }

    static final SysConfigProp CHECKPOINT_FILESIZE_INCREMENT = config {
        key 'rundeck.execution.logs.fileStorage.checkpoint.fileSize.increment'
        label 'Checkpoint Filesize Increment'
        description 'Minimum file size change between checkpoint storage requests, in file size string in the form \'###[tgkm][b]\''
        datatype 'String'
        defaultValue '0'
    }

    static final SysConfigProp FILE_STORAGE_PLUGIN = config {
        key 'rundeck.execution.logs.fileStoragePlugin'
        label 'File Storage Plugin'
        description 'Provider name of a LogFileStorage plugin'
        datatype 'String'
    }
    static final SysConfigProp STARTUP_RESUMEMODE = config {
        key 'rundeck.logFileStorageService.startup.resumeMode'
        label 'Log File Storage Startup Resume Incomplete Mode'
        description 'Optional mode for resuming incomplete log file storage at startup: sync, async. If unset it will not be resumed.'
        datatype 'String'
        restart true
    }

    List<SysConfigProp> systemConfigProps = Collections.unmodifiableList([
        STORAGE_CONCURRENCY_LIMIT,
        RETRIEVAL_CONCURRENCY_LIMIT,
        RESUME_INCOMPLETE_STRATEGY,
        STORAGE_RETRY_COUNT,
        STORAGE_RETRY_DELAY,
        CANCEL_ON_STORAGE_FAILURE,
        FORCE_PARTIAL_CHECKING,
        RETRIEVAL_RETRY_COUNT,
        RETRIEVAL_RETRY_DELAY,
        REMOTE_PENDING_DELAY,
        CHECKPOINT_TIME_INTERVAL,
        CHECKPOINT_TIME_MINIMUM,
        CHECKPOINT_FILESIZE_MINIMUM,
        CHECKPOINT_FILESIZE_INCREMENT,
        FILE_STORAGE_PLUGIN,
        STARTUP_RESUMEMODE
    ])


    @Override
    void afterPropertiesSet() throws Exception {
        def pluginName = getConfiguredPluginName()
        if(!pluginName){
            //System.err.println("LogFileStoragePlugin not configured, disabling...")
            return
        }

        logFileStorageTaskExecutor.concurrencyLimit = 1 + configurationService.getInteger(STORAGE_CONCURRENCY_LIMIT, 5)
        logFileTaskExecutor.concurrencyLimit = 1 + configurationService.getInteger(RETRIEVAL_CONCURRENCY_LIMIT, 5)

        log.debug("logFileStorageTaskExecutor concurrency: ${logFileStorageTaskExecutor.concurrencyLimit}")
        log.debug("logFileTaskExecutor concurrency: ${logFileTaskExecutor.concurrencyLimit}")
        logFileStorageTaskExecutor?.execute(new TaskRunner<Map>(storageRequests, { Map task ->

            if (!task.partial) {
                storageQueueCounter?.dec()
            }

            //run within same executor on another thread, may block until a thread is available
            logFileStorageTaskExecutor.execute {
                storageRunningCounter?.inc()
                running << task
                try {
                    runStorageRequest(task)
                } finally {
                    running.remove(task)
                    storageRunningCounter?.dec()
                }
            }
        }))
        logFileTaskExecutor?.execute(new TaskRunner<Map>(retrievalRequests, this.&runRetrievalRequestTask))
        if (getConfiguredResumeStrategy() == 'periodic') {
            long delay = getConfiguredStorageRetryDelay() * 1000
            logFileStorageTaskScheduler.scheduleAtFixedRate(
                {
                    try{
                        dequeueIncompleteLogStorage()
                    }catch (Throwable t){
                        log.error("Error dequeueing incomplete log storage requests", t)
                    }
                }, new Date(System.currentTimeMillis() + delay), delay)
        }
    }

    static SystemConfig.SystemConfigBuilder configDefaults(SystemConfig.SystemConfigBuilder builder) {
        builder.with {
            authRequired 'ops_admin'
            category 'Log File Storage'
            visibility 'Standard'
        }
    }
    static SysConfigProp config(@DelegatesTo(SystemConfig.SystemConfigBuilder) Closure clos){
        def builder = configDefaults(SystemConfig.builder())
        clos.delegate=builder
        clos.call()
        return builder.build()
    }

    void cleanup() {
        logFileStorageTaskScheduler?.shutdown()
    }

    /**
     * Runs the task via the logfileTaskExecutor, uses the completeableFuture result to pass results back to the listener
     * @param task
     * @return
     */
    def runRetrievalRequestTask(Map task){
        def listener = task.ruuid ? logFileRetrievalListeners.remove(task.ruuid) : null
        def future = runRetrievalRequest(task)
        future.handle(
                { result, throwable ->
                    if (result) {
                        listener?.complete(result)
                    } else if (throwable) {
                        listener?.completeExceptionally(throwable)
                    }
                }
        )
    }
    private String getConfiguredResumeStrategy() {
        configurationService?.getString(RESUME_INCOMPLETE_STRATEGY, "periodic")?:'periodic'
    }

    Counter getStorageQueueCounter(){
        metricService?.counter(this.class.name + ".storageRequests","queued")
    }

    Counter getStorageRunningCounter() {
        metricService?.counter(this.class.name + ".storageRequests", "running")
    }

    Counter getStorageTotalCounter(){
        metricService?.counter(this.class.name + ".storageRequests","total")
    }

    Counter getStorageRetryCounter(){
        metricService?.counter(this.class.name + ".storageRequests","retries")
    }

    Counter getStoragePartialCounter() {
        metricService?.counter(this.class.name + ".storageRequests", "partial")
    }
    Counter getStorageSuccessCounter(){
        metricService?.counter(this.class.name + ".storageRequests","succeeded")
    }
    Counter getStorageFailedCounter(){
        metricService?.counter(this.class.name + ".storageRequests","failed")
    }

    List getCurrentRetrievalRequests(){
        return new ArrayList(retrievalRequests)
    }
    List getCurrentStorageRequests(){
        return new ArrayList(storageRequests)
    }
    List getCurrentRequests(){
        return new ArrayList(running)
    }
    Map getCurrentRetrievalResults(){
        return new HashMap<String,Map>(logFileRetrievalResults)
    }

    def Map listLogFileStoragePlugins() {
        return pluginService.listPlugins(ExecutionFileStoragePlugin, executionFileStoragePluginProviderService)
    }
    /**
     * Run a storage request task, and if it fails submit a retry depending on the configured retry count and delay
     * @param task
     */
    void runStorageRequest(Map task){
        if (!task.partial && !task.count) {
            storageTotalCounter?.inc()
        } else if(task.partial){
            storagePartialCounter?.inc()
        } else if(task.count){
            storageRetryCounter?.inc()
        }
        int retry = getConfiguredStorageRetryCount()
        int delay = getConfiguredStorageRetryDelay()

        int count=task.count?:0;
        task.count = ++count

        def success = false

        def filetype=task.filetype
        def requestId = task.requestId
        def partial = task.partial
        def execId = task.execId
        def execUuid = task.executionUuid
        List<String> typelist = filetype != '*' ? (filetype.split(',') as List) : []

        if (partial) {

            def files=[:]
            log.debug("Partial: Storage request [ID#${task.id}]: for types: $typelist")
            Execution.withNewSession {
                Execution execution = execUuid ? Execution.findByUuid(execUuid) : Execution.get(execId)

                files = getExecutionFiles(execution, typelist, true)
                try {
                    def (didsucceed, failuremap) = storeLogFiles(typelist, task.storage, task.id, files, true)
                    success = didsucceed
                    if (success) {
                        log.debug("Partial: Storage request [ID#${task.id}]: succeeded")
                    } else {
                        log.debug("Failure: Partial: Storage request [ID#${task.id}]: ${failuremap}")
                    }
                } catch (IOException | ExecutionFileStorageException e) {
                    success = false
                    log.error("Failure: Partial: Storage request [ID#${task.id}]: ${e.message}", e)
                }
            }
            return
        }

        failedRequests.remove(requestId)
        failures.remove(requestId)
        long retryMax = 30000
        Execution.withNewSession {
            Execution execution = execUuid ? Execution.findByUuid(execUuid) : Execution.get(execId)
            def files = getExecutionFiles(execution, typelist, false)

            try {
                def (didsucceed, failuremap) = storeLogFiles(typelist, task.storage, task.id, files)
                success = didsucceed
                if (!success) {
                    failures.put(requestId, new ArrayList<String>(failuremap.values()))
                }
                if (!success && failuremap && failuremap.size() > 1 || !failuremap[filetype]) {
                    def ftype = failuremap.keySet().findAll { it != null && it != 'null' }.join(',')

                    LogFileStorageRequestData request = logFileStorageRequestProvider.retryLoad(requestId as Long, retryMax)
                    if (!request) {
                        log.error("Storage request [ID#${task.id}]: Error updating: not found for id $requestId")
                        success = false
                    } else if (request.filetype != ftype || request.completed != success) {
                        int retryC = 5
                        boolean saveDone = false
                        Exception saveError
                        while (retryC > 0) {
                            try {
                                logFileStorageRequestProvider.updateFiletypeAndCompleted(execution.uuid, ftype, success)
                                saveDone = true
                                break
                            } catch (Exception e) {
                                saveError = e
                                log.debug("Error: ${e}", e)
                            }
                            retryC--
                        }
                        if (!saveDone) {
                            log.error("Storage request [ID#${task.id}]: Error updating: $saveError", saveError)
                        }
                    }

                }
            } catch (IOException | ExecutionFileStorageException e) {
                success = false
                log.error("Failure: Storage request [ID#${task.id}]: ${e.message}", e)
            }

            if (!success && count < retry) {
                log.debug("Storage request [ID#${task.id}] was not successful, retrying in ${delay} seconds...")

                queueLogStorageRequest(task, delay)
            } else if (!success) {
                getStorageFailedCounter()?.inc()
                if (getConfiguredStorageFailureCancel()) {
                    log.error("Storage request [ID#${task.id}] FAILED ${retry} attempts, cancelling")
                    //if policy, remove the request from db
                    executorService.execute {
                        //use executorService to run within hibernate session
                        LogFileStorageRequestData request = logFileStorageRequestProvider.retryLoad(requestId as Long, retryMax)
                        if (!request) {
                            log.error("Storage request [ID#${task.id}]: Error deleting: not found for id $requestId")
                        } else {
                            logFileStorageRequestProvider.delete(execution.uuid)
                            log.debug("Storage request [ID#${task.id}] cancelled.")
                        }
                    }
                    failures.put(requestId, ["Storage request [ID#${task.id}] FAILED ${retry} attempts, cancelling"])
                    failedRequests.add(requestId)
                } else {
                    log.error("Storage request [ID#${task.id}] FAILED ${retry} attempts, giving up")

                    failures.put(requestId, ["Storage request [ID#${task.id}] FAILED ${retry} attempts, giving up"])
                    failedRequests.add(requestId)
                }
            } else {
                failedRequests.remove(requestId)
                failures.remove(requestId)
                //use executorService to run within hibernate session
                executorService.execute {
                    log.debug("executorService saving storage request status...")
                    LogFileStorageRequestData request = logFileStorageRequestProvider.retryLoad(requestId as Long, retryMax)
                    if (!request) {
                        log.error("Storage request [ID#${task.id}]: Error saving: not found for id $requestId")
                    } else if (request) {
                        log.debug("Loaded LogFileStorageRequest ${requestId} [ID#${task.id}] after retry")
                        logFileStorageRequestProvider.updateCompleted(execution.uuid, success)
                        log.debug("Storage request [ID#${task.id}] complete.")
                    }
                    getStorageSuccessCounter()?.inc()
                }
            }
        }
    }

    /**
     * Run retrieval request task with no retries, executes in the logFileTaskExecutor threadpool
     * @param task
     */
    private CompletableFuture<RetrieveFileResult> runRetrievalRequest(Map task) {
        Supplier<RetrieveFileResult> supplier = { ->
            running << task
            RetrieveFileResult result = retrieveLogFile(
                    task.file,
                    task.filetype,
                    task.storage,
                    task.id,
                    task.partial ? true : false
            )
            def success=result.success
            if (!success) {
                log.error("LogFileStorage: failed retrieval request for ${task.id}")
                def cache = [
                        state: result.error ? LogFileState.ERROR : LogFileState.NOT_FOUND,
                        time: new Date(),
                        count: task.count ? task.count + 1 : 1,
                        errorCode: 'execution.log.storage.retrieval.ERROR',
                        errorData: [task.name, result.error],
                        error: result.error
                ]

                logFileRetrievalResults.put(task.id, task + cache)
            }else{
                logFileRetrievalResults.remove(task.id)
            }
            logFileRetrievalRequests.remove(task.id)
            running.remove(task)
            result
        }
        CompletableFuture.<RetrieveFileResult> supplyAsync(supplier, logFileTaskExecutor)
    }
    /**
     * Return the configured retry count
     * @return
     */
    int getConfiguredStorageRetryCount() {
        def count = configurationService.getInteger(STORAGE_RETRY_COUNT,0)
        count > 0 ? count : 1
    }
    /**
     * Return the configured retry delay in seconds
     * @return
     */
    int getConfiguredStorageRetryDelay() {
        def delay = configurationService.getInteger(STORAGE_RETRY_DELAY,0)
        delay > 0 ? delay : 60
    }
    /**
     * @return whether storage failure should cancel storage request completely
     */
    boolean getConfiguredStorageFailureCancel() {
        configurationService.getBoolean(CANCEL_ON_STORAGE_FAILURE, true)
    }
    /**
     * Return the configured retry count
     * @return
     */
    int getConfiguredRetrievalRetryCount() {
        def count = configurationService.getInteger(RETRIEVAL_RETRY_COUNT,0)
        count > 0 ? count : 3
    }
    /**
     * Return the configured retry delay in seconds
     * @return
     */
    int getConfiguredRetrievalRetryDelay() {
        def delay = configurationService.getInteger(RETRIEVAL_RETRY_DELAY,0)
        delay > 0 ? delay : 60
    }
    /**
     * Return the configured remote pending delay in seconds
     * @return
     */
    int getConfiguredRemotePendingDelay() {
        def delay = configurationService.getInteger(REMOTE_PENDING_DELAY,0)
        delay > 0 ? delay : 120
    }

    def int getLogstoreCheckpointTimeSecondsPeriod() {
        configurationService.getTimeDuration(
                CHECKPOINT_TIME_INTERVAL,
                '30s',
                TimeUnit.SECONDS
        )
    }

    def int getLogstoreCheckpointTimeSecondsMinimum() {
        configurationService.getTimeDuration(
            CHECKPOINT_TIME_MINIMUM,
                '30s',
                TimeUnit.SECONDS
        )
    }

    def long getLogstoreCheckpointFilesizeMinimum() {
        configurationService.getFileSize(CHECKPOINT_FILESIZE_MINIMUM, 0)
    }

    def long getLogstoreCheckpointFilesizeIncrement() {
        configurationService.getFileSize(CHECKPOINT_FILESIZE_INCREMENT, 0)
    }
    /**
     * Return the configured plugin name
     * @return
     */
    String getConfiguredPluginName() {
        configurationService?.getString(FILE_STORAGE_PLUGIN,null)
    }
    /**
     * Create a streaming log writer for the given execution.
     * @param e execution
     * @param defaultMeta default metadata for logging
     * @param resolver @return
     */
    StreamingLogWriter getLogFileWriterForExecution(
            Execution e,
            Map<String, String> defaultMeta,
            ValueWatcher<Long> filesizeWatcher = null
    )
    {
        def filetype = LoggingService.LOG_FILE_FILETYPE
        File file = getFileForExecutionFiletype(e, filetype, false, false)

        if (!file.getParentFile().isDirectory()) {
            try {
                Files.createDirectories(file.getParentFile().toPath());
            } catch (IOException iex) {
                throw new IllegalStateException("Unable to create directories for storage: " + file);
            }
        }
        //stream log events to file, and when closed submit asynch request to store file if needed
        def writer = new FSStreamingLogWriter(new FileOutputStream(file), defaultMeta, rundeckLogFormat)
        if(filesizeWatcher!=null){
            ValueHolder value={->
                writer.bytesWritten
            } as ValueHolder
            filesizeWatcher.watch(value)
        }
        return writer
    }

    /**
     * @param execution
     * @return
     */
    Consumer<Long> createPeriodicCheckpoint(Execution execution) {
        def File logfile = getFileForExecutionFiletype(
                execution,
                LoggingService.LOG_FILE_FILETYPE,
                false,
                false
        )
        if (!pluginEnabledForPartialStorage(execution)) {
            return null
        }
        def execid = execution.id
        def checker = new PeriodicFileChecker(
                periodUnit: TimeUnit.SECONDS,
                period: logstoreCheckpointTimeSecondsPeriod,
                periodThreshold: logstoreCheckpointTimeSecondsMinimum,
                sizeThreshold: logstoreCheckpointFilesizeMinimum,
                sizeIncrement: logstoreCheckpointFilesizeIncrement,
                logfile: logfile,
                //OR means: trigger action if initial size OR time threshold met
                thresholdBehavior: PeriodicFileChecker.Behavior.OR,
                action: { long fileSizeChange, long timediff ->
                    log.debug("Partial log file storage trigger for ${execid}")
                    notify('executionCheckpoint',
                            new ExecutionCompleteEvent(
                                    state: 'partial',
                                    execution: execution,
                                    context: [fileSizeChange: fileSizeChange, timediff: timediff]

                            )
                    )
                }
        )
        log.debug("Partial log file storage enabled for execution ${execid} with checker ${checker}")
        return { long duration ->
            if (checker.triggerCheck()) {
                log.debug("periodic check succeeded after ${duration}")
            }
        }
    }
    /**
     * Submit asynchronous request to store log files for the execution
     * @param e
     */
    boolean pluginEnabledForPartialStorage(Execution e) {
        def plugin = getConfiguredPluginForExecution(e, frameworkService.getFrameworkPropertyResolverFactory(e.project))
        return plugin ? pluginSupportsPartialStorage(plugin) : false
    }
    /**
     * Submit asynchronous request to store log files for the execution
     * @param e
     */
    void submitForPartialStorage(Execution e, long sizeChange, long timeDiff) {
        def plugin = getConfiguredPluginForExecution(e, frameworkService.getFrameworkPropertyResolverFactory(e.project))
        if (null == plugin || !pluginSupportsPartialStorage(plugin)) {
            return
        }
        log.debug("partial storage triggered...[${sizeChange} bytes, after ${timeDiff} ms]")
        //TODO: check if currently running
        if (sizeChange < 1) {
            //todo: configurable threshold
            log.debug("partial storage skipped: ${sizeChange} bytes")
            return
        }
        //TODO: overwrite existing request if present
        def reqid = e.id + ':checkpoint:' + UUID.randomUUID().toString()

        Collection<ExecutionFileProducer> beans = listExecutionFileProducers()
        String filetype = beans.findAll { it.isCheckpointable() }*.executionFileType.join(',')
        storeLogFileAsyncPartial(reqid, plugin, e.id, filetype, e.uuid)
    }

    /**
     * Submit asynchronous request to store log files for the execution
     * @param e
     */
    void submitForStorage(String executionUuid) {
        submitForStorage(Execution.findByUuid(executionUuid))
    }
    /**
     * Submit asynchronous request to store log files for the execution
     * @param e
     */
    void submitForStorage(Execution e) {
        def plugin = getConfiguredPluginForExecution(e, frameworkService.getFrameworkPropertyResolverFactory(e.project))
        if(null==plugin || !pluginSupportsStorage(plugin)){
            return
        }
        //multi storage available
        //avoid multiple storage requests for the same Execution
        def orig = logFileStorageRequestProvider.findByExecutionUuid(e.uuid)
        if(orig){
            return
        }
        LogFileStorageRequestData request = createStorageRequest(e, '*')
        def reqid = request.executionId.toString() + ":" + request.filetype
        storeLogFileAsync(reqid, plugin, request)
    }

    private LogFileStorageRequestData createStorageRequest(Execution e, String filetype) {
        def newData = logFileStorageRequestProvider.build(
                getConfiguredPluginName(),
                filetype,
                false,
                e.uuid,
        )
        return logFileStorageRequestProvider.create(newData)
    }
    public Map getStorageStats() {
        def props=frameworkService.getFrameworkProperties()
        def countMissing = props.getProperty('framework.storage.missing')
        def missing = countMissing=='true'?countMissingLogStorageExecutions():0
        def incompleteRequests = countIncompleteLogStorageRequests()
        def queued = storageQueueCounter.count
        def queuedIncomplete = retryIncompleteRequests.size()
        def queuedRetries = retryRequests.size()
        def queuedRequests = storageRequests.size()
        def failed = storageFailedCounter.count
        def succeeded = storageSuccessCounter.count
        def retries = storageRetryCounter.count
        def total = storageTotalCounter.count
        def partialCount = storagePartialCounter.count
        def running = storageRunningCounter.count

        def incomplete = incompleteRequests

        def data = [
                pluginName     : getConfiguredPluginName(),
                succeededCount : succeeded,
                failedCount    : failed,
                queuedCount    : queued,
                queuedRequestCount    : queuedRequests,
                queuedRetriesCount    : queuedRetries,
                queuedIncompleteCount    : queuedIncomplete,
                totalCount     : total,
                incompleteCount: incomplete,
                missingCount   : missing,
                running        : running,
                partialCount   : partialCount,
                retriesCount   : retries
        ]
        data
    }


    /**
     *
     * @return count of executions with missing log storage requests
     */
    int countMissingLogStorageExecutions(){
        countExecutionsWithoutStorageRequests(frameworkService.serverUUID)
    }

    /**
     * Resume log storage requests for the given serverUUID, or null for unspecified
     * @param serverUUID
     */
    void resumeIncompleteLogStorageAsync(String serverUUID,Long id=null){
        executorService.execute {
            resumeIncompleteLogStorage(serverUUID,id)
        }
    }
    /**
     * resume task, triggered periodically, consumes a single request id from the queue if present
     * and processes it by scheduling storage operation immediately
     * @return
     */
    def dequeueIncompleteLogStorage() {
        def taskId = null
        try {
            taskId = retryIncompleteRequests.poll(30, TimeUnit.SECONDS)
        } catch(InterruptedException iex) {}

        if(!taskId){
            return
        }
        storageQueueCounter?.dec()
        log.debug("dequeueIncompleteLogStorage, processing ${taskId}")
        Long invalidId
        String serverUuid
        Execution.withNewSession {
            LogFileStorageRequestData request = logFileStorageRequestProvider.get(taskId)
            Execution e = Execution.get(request.executionId)
            if (!frameworkService.existsFrameworkProject(e.project)) {
                log.error(
                        "cannot re-queue incomplete log storage request for execution ${e.id}, project does not exist: " + e.project
                )
                invalidId = request.id
                serverUuid = e.serverNodeUUID
                return
            }
            log.debug("re-queueing incomplete log storage request for execution ${e.id}")
            def plugin = getConfiguredPluginForExecution(e, frameworkService.getFrameworkPropertyResolverFactory(e.project))
            if (null != plugin && pluginSupportsStorage(plugin)) {
                //re-queue storage request immediately, pass -1 to skip counter increment
                storeLogFileAsync(e.id.toString() + ":" + request.filetype, plugin, request, -1)
            } else {
                log.error(
                        "cannot re-queue incomplete log storage request for execution ${e.id}, plugin was not available: ${getConfiguredPluginName()}"
                )
            }
            if (invalidId != null) {
                cleanupIncompleteLogStorage(serverUuid, invalidId)
            }
        }
    }
    /**
     * @return set of LogFileStorageRequest IDs that are in the incomplete retry queue
     */
    Set<Long> getQueuedIncompleteRequestIds() {
        Collections.unmodifiableSet new HashSet<Long>(retryIncompleteRequests)
    }
    /**
     * @return set of LogFileStorageRequest Ids that are scheduled for retry
     */
    Set<Long> getQueuedRetryRequestIds() {
        Collections.unmodifiableSet new HashSet<Long>(retryRequests)
    }
    /**
     * @return set of LogFileStorageRequest IDs that are in the storage queue
     */
    Set<Long> getQueuedRequestIds() {
        Collections.unmodifiableSet new HashSet<Long>(storageRequests.findAll{it.requestId}*.requestId)
    }
    /**
     *
     * @return set of LogFileStorageRequest IDs that failed
     */
    Set<Long> getFailedRequestIds(){
        Collections.unmodifiableSet failedRequests
    }
    /**
     *
     * @param id
     * @return List of failure messages for given LogFileStorageRequest ID if available, or null
     */
    List<String> getFailures(Long id){
        Collections.unmodifiableList failures[id]
    }

    /**
     *
     * @return set of Execution IDs of currently running storage/retrieval requests
     */
    Set<String> getRunningExecUuids() {
        def list = new HashSet(running)
        Collections.unmodifiableSet(new HashSet(list.findAll { !it.partial && it.executionUuid }*.executionUuid))
    }
    /**
     * List incomplete requests, add all to a queue processed by periodic task
     * @param serverUUID
     */
    void resumeIncompleteLogStoragePeriodic(String serverUUID, Long id = null) {
        List<LogFileStorageRequestData> incomplete
        if (!id) {
            incomplete = listIncompleteRequests(serverUUID)
        } else {
            def found = logFileStorageRequestProvider.get(id)
            if (found && Execution.get(found.executionId).serverNodeUUID == serverUUID) {
                incomplete = [found]
            }
        }

        incomplete.each { LogFileStorageRequestData request ->
            if(!retryIncompleteRequests.contains(request.id)){
                retryIncompleteRequests.add(request.id as Long)
                storageQueueCounter?.inc()
                failedRequests.remove(request.id)
                failures.remove(request.id)

            }
        }
    }

    /**
     * Resume all incomplete log storage tasks for the given server ID, or null.
     * This uses the configured strategy: 'delayed' or 'periodic' (default)
     * @param serverUUID
     */
    void resumeIncompleteLogStorage(String serverUUID,Long id=null){
        def strategy = getConfiguredResumeStrategy()
        if ('delayed' == strategy) {
            //previous method of processing all tasks now and requeueing at incremental delays
            resumeIncompleteLogStorageDelayed(serverUUID,id)
        } else /*if("periodic".equals(strategy))*/ {
            //requeue all tasks to be processed periodically
            resumeIncompleteLogStoragePeriodic(serverUUID,id)
        }
    }
    void haltIncompleteLogStorage(String serverUUID){
        def strategy = getConfiguredResumeStrategy()
        if ('delayed' == strategy) {
            //previous method of processing all tasks now and requeueing at incremental delays
            throw new IllegalStateException("Cannot halt storage task when strategy is: delayed")
        } else /*if("periodic".equals(strategy))*/ {
            //requeue all tasks to be processed periodically
            int size=retryIncompleteRequests.size()
            retryIncompleteRequests.clear()
            storageQueueCounter.dec(size)
        }
    }
    /**
     * remove incomplete requests from the database
     * @param serverUUID
     * @return
     */
    int cleanupIncompleteLogStorage(String serverUUID, Long id = null) {
        List<LogFileStorageRequestData> incomplete
        if (!id) {
            incomplete = listIncompleteRequests(serverUUID)
        } else {
            def found = logFileStorageRequestProvider.get(id)
            if (found && Execution.get(found.executionId).serverNodeUUID == serverUUID) {
                incomplete = [found]
            }
        }
        incomplete = incomplete.findAll { !retryIncompleteRequests.contains(it.id) }
        incomplete.each { LogFileStorageRequestData request ->
            failedRequests.remove(request.id)
            failures.remove(request.id)
            Execution.get(request.executionId).logFileStorageRequest = null
            logFileStorageRequestProvider.delete(request.executionUuid)
        }
        incomplete.size()
    }

    /**
     * list incomplete requests, schedule each one to be added to queue after an incrementing delay by using the
     * scheduled executor
     * @param serverUUID
     * @return
     */
    int resumeIncompleteLogStorageDelayed(String serverUUID,Long id=null){
        List<LogFileStorageRequestData> incomplete
        if (!id) {
            incomplete = listIncompleteRequests(serverUUID)
        } else {
            def found = logFileStorageRequestProvider.get(id)
            if (found && Execution.get(found.executionId).serverNodeUUID == serverUUID) {
                incomplete = [found]
            }
        }
        log.info("resumeIncompleteLogStorage: found: ${incomplete.size()} incomplete requests for serverUUID: ${serverUUID}")

        if(!incomplete){
            return 0
        }
        //use a slow start to process backlog storage requests
        def delayInc = getConfiguredStorageRetryDelay()
        def delay = delayInc
        def count=0
        List<Long> invalid = []

        incomplete.each{ LogFileStorageRequestData request ->
            Execution e = Execution.get(request.executionId)
                log.debug("re-queueing incomplete log storage request for execution ${e.id} delay ${delay}")
            if (!frameworkService.existsFrameworkProject(e.project)) {
                log.error(
                    "cannot re-queue incomplete log storage request for execution ${e.id}, project does not exist: " + e.project
                )
                invalid << request.id
                return
            }
                def plugin = getConfiguredPluginForExecution(e, frameworkService.getFrameworkPropertyResolverFactory(e.project))
                if(null!=plugin && pluginSupportsStorage(plugin)) {
                    //re-queue storage request
                    storeLogFileAsync(e.id.toString() + ":" + request.filetype, plugin, request, delay)
                    delay += delayInc
                    count++
                }else{
                    log.error("cannot re-queue incomplete log storage request for execution ${e.id}, plugin was not available: ${getConfiguredPluginName()}")
                }
        }
        //cleanup invalid
        invalid.each { reqid ->
            cleanupIncompleteLogStorage(serverUUID, reqid)
        }
        log.info("resumeIncompleteLogStorage: ${count} incomplete requests requeued for serverUUID: ${serverUUID}")
        count
    }


    /**
     *
     * @return count of executions with incomplete log storage requests for this cluster node
     */
    int countIncompleteLogStorageRequests(){
        def serverUUID=frameworkService.serverUUID
        def skipExecUuid = getRunningExecUuids()
        def found2=logFileStorageRequestProvider.countByIncompleteAndClusterNodeNotInExecUuids(serverUUID, skipExecUuid)
        found2
    }
    /**
     *
     * @param serverUUID
     * @return list of incomplete storage requests for this cluster id or null
     */
    def List<LogFileStorageRequestData> listIncompleteRequests(String serverUUID, Map paging =[:]){
        def skipExecUuid = getRunningExecUuids()
        def found2= logFileStorageRequestProvider.listByIncompleteAndClusterNodeNotInExecUuids(serverUUID, skipExecUuid, paging)
        return found2
    }
    int countExecutionsWithoutStorageRequests(String serverUUID){
        def found2=Execution.createCriteria().get{
            createAlias('logFileStorageRequest', 'logid', JoinType.LEFT_OUTER_JOIN)
            isNull( 'logid.id')
            isNotNull('dateCompleted')
            if(null==serverUUID){
                isNull('serverNodeUUID')
            }else{
                eq('serverNodeUUID', serverUUID)
            }
            projections{
                rowCount()
            }
        }
        return found2
    }
    List<Execution> listExecutionsWithoutStorageRequests(String serverUUID,Map paging=[:]){
        Execution.createCriteria().list{
            createAlias('logFileStorageRequest', 'logid', JoinType.LEFT_OUTER_JOIN)
            isNull( 'logid.id')
            isNotNull('dateCompleted')
            if(null==serverUUID){
                isNull('serverNodeUUID')
            }else{
                eq('serverNodeUUID', serverUUID)
            }
            if(paging && paging.max){
                maxResults(paging.max.toInteger())
                firstResult(paging.offset?:0)
            }
        }
    }

    /**
     * Return true if the storage request with the given ID is queued or running
     * @param reqid
     * @return
     */
    boolean isStorageRequestInProgress(reqid) {
        getCurrentStorageRequests().find { it.id == reqid } || getCurrentRequests().find { it.id == reqid }
    }
    /**
     * Return the local file path for a stored file for the execution given the filetype
     * @param execution the execution
     * @param filetype filetype (extension)
     * @param useStoredPath if true, use the original path stored in the execution (support pre 1.6 rundeck), otherwise generate the path dynamically based on the execution/job
     * @return the file
     */
    def File getFileForExecutionFiletype(
            Execution execution,
            String filetype,
            boolean useStoredPath,
            boolean partial = false
    )
    {
        if (useStoredPath && execution.outputfilepath && !execution.isRemoteOutputfilepath()) {
            //use previously stored outputfilepath if present, substitute correct filetype
            String path = execution.outputfilepath.replaceAll(/\.([^\.]+)$/,'')
            return new File(path + '.' + filetype + (partial ? '.part' : ''))
        } else{
            return getFileForLocalPath(generateLocalPathForExecutionFile(execution, filetype, partial))
        }
    }
    /**
     * Return the local file path for a stored file for the execution given the filetype
     * @param execution the execution
     * @param filetype filetype (extension)
     * @param useStoredPath if true, use the original path stored in the execution (support pre 1.6 rundeck), otherwise generate the path dynamically based on the execution/job
     * @return the file
     */
    def File getFileForExecutionFiletype(
            ExecutionReference execution,
            String filetype,
            boolean partial = false
    )
    {
         getFileForLocalPath(generateLocalPathForExecutionFile(execution, filetype, partial))
    }
    /**
     * Generate a relative path for log file of the given execution
     * @param execution
     * @return
     */
    public static String generateLocalPathForExecutionFile(
            Execution execution,
            String extension,
            boolean partial = false
    )
    {
        if (execution.scheduledExecution) {
            return "${execution.project}/job/${execution.scheduledExecution.extid}/logs/${execution.id}." + extension +
                    (partial ? '.part' : '')
        } else {
            return "${execution.project}/run/logs/${execution.id}." + extension +
                    (partial ? '.part' : '')
        }
    }
    /**
     * Generate a relative path for log file of the given execution
     * @param execution
     * @return
     */
    public static String generateLocalPathForExecutionFile(
            ExecutionReference execution,
            String extension,
            boolean partial = false
    )
    {
        if (execution.job) {
            return "$execution.project/job/$execution.job.id/logs/${execution.id}.${extension}${partial ? '.part' : ''}"
        } else {
            return "$execution.project/run/logs/${execution.id}.${extension}${partial ? '.part' : ''}"
        }
    }

    /**
     * Return the local File for the given key
     * @param path
     * @return
     */
    def File getFileForLocalPath(String path) {
        new File(getLocalLogsDir(), path)
    }

    @Override
    File getLocalLogsDir() {
        def props = frameworkService.getFrameworkProperties()
        def dir = props.getProperty('framework.logs.dir')
        if (!dir) {
            throw new IllegalStateException("framework.logs.dir is not set in framework.properties")
        }
        new File(dir, 'rundeck')
    }

    @Override
    File getLocalExecutionFileForType(final ExecutionReference execution, final String filetype) {
        getLocalExecutionFileForType(execution, filetype, false)
    }

    @Override
    File getLocalExecutionFileForType(
            final ExecutionReference execution,
            final String filetype,
            final boolean partial
    ) {
        getFileForLocalPath(
                execution.job
                        ? "$execution.project/job/$execution.job.id/logs/${execution.id}.${filetype}${partial ? '.part' : ''}"
                        : "$execution.project/run/logs/${execution.id}.${filetype}${partial ? '.part' : ''}"
        )
    }

    /**
     * Return a new File log reader for rundeck file format
     * @param file
     * @return
     */
    private static StreamingLogReader getLogReaderForFile(File file) {
        if (!file.exists()) {
            throw new IllegalArgumentException("File does not exist: " + file)
        }
        return new FSStreamingLogReader(file, "UTF-8", rundeckLogFormat);
    }

    /**
     * Determine the state of the log file, based on the local file and previous or current plugin requests
     * @param execution
     * @param plugin
     * @return state of the execution log
     */
    private Map getLogFileState(
            Execution execution,
            String filetype,
            ExecutionFileStoragePlugin plugin,
            boolean checkPartial = false
    )
    {
        def useStoredPath = execution.scheduledExecution == null
        File file = getFileForExecutionFiletype(execution, filetype, useStoredPath, false)
        def key = logFileRetrievalKey(execution,filetype)
        def keyPartial = logFileRetrievalKey(execution, filetype, true)

        //check local file
        LogFileState local = (file!=null && file.exists() )? LogFileState.AVAILABLE : LogFileState.NOT_FOUND
        if(local == LogFileState.AVAILABLE) {
            log.debug("getLogFileState(${execution.id},${filetype},complete) AVAILABLE ${file.length()}b")
            return [state: ExecutionFileState.AVAILABLE]
        }
        boolean supportsPartial = plugin && pluginSupportsPartialRetrieval(plugin) && checkPartial


        LogFileState remote = null

        //consider the state to be PENDING_REMOTE if not found, but we are within a pending grace period
        // after execution completed
        ExecutionFileState remoteNotFound = ExecutionFileState.PENDING_REMOTE
        long pendingDelay = TimeUnit.MILLISECONDS.convert(getConfiguredRemotePendingDelay(), TimeUnit.SECONDS)
        if (execution.dateCompleted != null &&  ((System.currentTimeMillis() - execution.dateCompleted.time) > pendingDelay)) {
            //report NOT_FOUND if not found after execution completed and after the pending grace period
            remoteNotFound = ExecutionFileState.NOT_FOUND
        }
        String errorCode=null
        List errorData=null


        def extra = [:]
        ExecutionFileState state = ExecutionFileState.NOT_FOUND
        //query plugin to see if it is available
        if (null == remote && null != plugin && pluginSupportsRetrieve(plugin)) {
            def errorMessage = null

            def previous = getRetrievalCacheResult(key)
            if (previous != null) {
                //retrieval result is fresh within the cache
                remote = previous.state
                errorCode = previous.errorCode
                errorData = previous.errorData

                log.debug("getLogFileState(${key}): cached state: ${remote}")
            } else {
                try {
                    remote = plugin.isAvailable(filetype) ? LogFileState.AVAILABLE : LogFileState.NOT_FOUND
                } catch (Throwable e) {
                    def pluginName = getConfiguredPluginName()
                    log.error("Log file availability could not be determined ${pluginName}: " + e.message)
                    log.debug("Log file availability could not be determined ${pluginName}: " + e.message, e)
                    errorCode = 'execution.log.storage.state.ERROR'
                    errorMessage = e.message
                    errorData = [pluginName, errorMessage]
                    remote = LogFileState.ERROR

                }
                log.debug("getLogFileState(${key}): plugin state: ${remote}")
                if (!(remote in [LogFileState.AVAILABLE, LogFileState.AVAILABLE_PARTIAL])) {
                    //otherwise cache the result check
                    cacheRetrievalState(key, remote, 0, errorMessage, errorCode, errorData)
                } else {
                    logFileRetrievalResults.remove(key)
                }
            }
            state = ExecutionFileState.forFileStates(LogFileState.NOT_FOUND, remote, remoteNotFound)
            if (remote != LogFileState.AVAILABLE && supportsPartial) {
                //check if partial file is locally available
                File filepart = getFileForExecutionFiletype(execution, filetype, false, true)

                LogFileState localPartialState = (filepart != null && filepart.exists()) ?
                        LogFileState.AVAILABLE_PARTIAL :
                        LogFileState.NOT_FOUND
                LogFileState remotePartialState

                //query plugin to see if it is available in partial data
                def cacheLifeSecs = (int) Math.floor(logstoreCheckpointTimeSecondsPeriod / 2)
                def previousPartial = getRetrievalCacheResult(keyPartial, cacheLifeSecs)
                extra.retryBackoff = (cacheLifeSecs * 1000L)
                if (previousPartial != null) {
                    //retrieval result is fresh within the cache
                    remotePartialState = previousPartial.state
                    errorCode = previousPartial.errorCode
                    errorData = previousPartial.errorData
                    extra.retryBackoff = previousPartial.backoff ?: 0

                    log.debug(
                            "getLogFileState(${keyPartial}): cached state: ${remotePartialState}"
                    )
                } else {
                    try {
                        remotePartialState = plugin.isPartialAvailable(filetype) ?
                                LogFileState.AVAILABLE_PARTIAL :
                                LogFileState.NOT_FOUND
                    } catch (Throwable e) {
                        def pluginName = getConfiguredPluginName()
                        log.error("Log file partial availability could not be determined ${pluginName}: " + e.message)
                        log.debug(
                                "Log file partial availability could not be determined ${pluginName}: " + e.message,
                                e
                        )
                        errorCode = 'execution.log.storage.state.ERROR'
                        errorMessage = e.message
                        errorData = [pluginName, errorMessage]
                        remotePartialState = LogFileState.ERROR
                    }
                    log.debug("getLogFileState(${keyPartial}): plugin state: ${remotePartialState}")
                    if (!(remotePartialState in [LogFileState.AVAILABLE, LogFileState.AVAILABLE_PARTIAL])) {
                        //otherwise cache the result check
                        cacheRetrievalState(keyPartial, remotePartialState, 0, errorMessage, errorCode, errorData)
                    } else {
                        logFileRetrievalResults.remove(keyPartial)
                    }
                }
                extra.remotePartialState = remotePartialState
                state = ExecutionFileState.forFileStates(
                        localPartialState,
                        remotePartialState,
                        state
                )
            }
        }



        log.debug(
                "getLogFileState(${execution.id},${filetype}): tested ${remote}, result: ${state}"
        )
        return [state: state, errorCode: errorCode, errorData: errorData] + extra
    }
    def pluginSupportsRetrieve(Object plugin){
        if(plugin instanceof ExecutionFileStorageOptions){
            return ((ExecutionFileStorageOptions)plugin).retrieveSupported
        }
        true
    }
    def pluginSupportsStorage(Object plugin){
        if(plugin instanceof ExecutionFileStorageOptions){
            return ((ExecutionFileStorageOptions)plugin).storeSupported
        }
        true
    }

    def pluginSupportsPartialStorage(Object plugin) {
        if (!pluginSupportsStorage(plugin)) {
            return false
        }
        if (plugin instanceof ExecutionFileStorageOptions) {
            return ((ExecutionFileStorageOptions) plugin).partialStoreSupported
        }
        false
    }

    def pluginSupportsPartialRetrieval(Object plugin) {
        if (!pluginSupportsRetrieve(plugin)) {
            return false
        }
        if (plugin instanceof ExecutionFileStorageOptions) {
            return ((ExecutionFileStorageOptions) plugin).partialRetrieveSupported
        }
        false
    }
    /**
     * Get a previous retrieval cache result, if it is not expired, or has no more retries
     * @param key
     * @return task result
     */
    Map getRetrievalCacheResult(String key, int cacheLife = -1) {
        def previous = logFileRetrievalResults.get(key)
        if (previous != null && isResultCacheItemFresh(previous, cacheLife)) {
            log.debug("getRetrievalCacheResult, previous result still cached: ${previous}")
            //retry delay is not expired
            def remain = getCacheItemRemainingMS(previous, cacheLife)
            return previous + [backoff: remain]
        } else if (previous != null && !isResultCacheItemAllowedRetry(previous)) {
            //no more retries
            log.debug("getRetrievalCacheResult, reached max retry count of ${previous.count} for ${key}, not retrying")
            return previous
        }else if(previous!=null){
            log.debug("getRetrievalCacheResult, expired cache result: ${previous}")
            logFileRetrievalResults.remove(key)
        }
        return null
    }

    Map cacheRetrievalState(String key, LogFileState state, int count, String error = null, String errorCode=null, List errorData=null) {
        def name= getConfiguredPluginName()
        def cache = [
                id:key,
                name:name,
                state: state,
                time: new Date(),
                count: count,
        ]
        if (error) {
            cache.errorCode = errorCode ?: 'execution.log.storage.retrieval.ERROR'
            cache.errorData = errorData ?: [name, error]
            cache.error = error
        }
        def previous = logFileRetrievalResults.put(key, cache)
        if (null != previous) {
            log.debug("cacheRetrievalState: replacing cached state for ${key}: ${cache}")
            cache.count=previous.count
        }else{
            log.debug("cacheRetrievalState: cached state for ${key}: ${cache}")
        }
        return cache;
    }

    /**
     * Create and initialize the log file storage plugin for this execution, or return null
     * @param execution
     * @param resolver @return
     */
    private ExecutionFileStoragePlugin getConfiguredPluginForExecution(Execution execution, PropertyResolverFactory.Factory factory) {
        def jobcontext = ExecutionService.exportContextForExecution(execution,grailsLinkGenerator)
        def plugin = getConfiguredPlugin(jobcontext, factory)
        plugin
    }

    /**
     * Create and initialize the log file storage plugin for the context, or return null
     * @param context
     * @param resolver @return
     */
    private ExecutionFileStoragePlugin getConfiguredPlugin(Map context, PropertyResolverFactory.Factory factory){
        def pluginName=getConfiguredPluginName()
        if (!pluginName) {
            return null
        }
        def result
        try {
            result= pluginService.configurePlugin(pluginName, executionFileStoragePluginProviderService, factory, PropertyScope.Instance)
        } catch (Throwable e) {
            log.error("Failed to create LogFileStoragePlugin '${pluginName}': ${e.class.name}:" + e.message,e)
            log.debug("Failed to create LogFileStoragePlugin '${pluginName}': ${e.class.name}:" + e.message, e)
        }
        if (result != null && result.instance!=null) {
            def plugin=result.instance
            try {
                plugin.initialize(context)
                return plugin
            } catch (Throwable e) {
                log.error("Failed to initialize LogFileStoragePlugin '${pluginName}': ${e.class.name}: " + e.message,e)
                log.debug("Failed to initialize LogFileStoragePlugin '${pluginName}': ${e.class.name}: " + e.message, e)
            }
        }
        return null
    }

    /**
     * Get the path template for the remote log storage
     * @param execution any execution to extract the path template to store log files for all the project
     * @return the value of the framework property "framework.plugin.ExecutionFileStorage.<file-storage-plugin>.path"
     */
    String getStorePathTemplateForExecution(Execution execution){
        ExecutionFileStoragePlugin plugin = getConfiguredPluginForExecution(execution, frameworkService.getFrameworkPropertyResolverFactory(execution.project))
        return plugin?.getConfiguredPathTemplate()
    }

    /**
     * Expands the pathTemplate using the execution context
     * @param execution
     * @param pathTemplate
     * @return the expanded path using the execution context
     */
    String getRemotePathForExecutionFromPathTemplate(Execution execution, String pathTemplate){
        Map<String, String> execCtx = ExecutionService.exportContextForExecution(execution,grailsLinkGenerator)

        return expandPath(pathTemplate, execCtx)
    }

    /**
     * Expand a path given a context:
     * Done by replacing keys between "${}" in the pathFormat using the corresponding values from the context.
     * Eg: ${job.execId} will be replaced with content of context['job.execId']
     *
     * @param pathFormat eg: path/to/${job.execId}
     * @param context from which variable values are extracted
     * @return an expanded path
     */
    static String expandPath(String pathFormat, Map<String, String> context) {
        String result = pathFormat.replaceAll("^/+", "");
        if (null != context) {
            result = DataContextUtils.replaceDataReferencesInString(
                    result,
                    DataContextUtils.addContext("job", context, new HashMap<>()),
                    null,
                    false,
                    true
            );
        }
        result = result.replaceAll("/+", "/");

        return result;
    }

    /**
     * Return an ExecutionLogFileReader containing state of logfile availability, and reader if available
     * @param e execution
     * @param performLoad if true, perform remote file transfer
     * @param resolver @return
     */
    ExecutionLogReader requestLogFileReader(Execution e, String filetype, boolean performLoad = true) {
        def loader= requestLogFileLoad(e, filetype, performLoad)
        def reader=null
        if (loader.file && loader.state.isAvailableOrPartial()) {
            reader = getLogReaderForFile(loader.file)
        }
        return new ExecutionLogReader(state: loader.state, reader: reader,
                                      errorCode: loader.errorCode,
                                      errorData: loader.errorData,
                                      retryBackoff: loader.retryBackoff
        )
    }

    def LogFileLoader requestFileLoad(ExecutionReference e, String filetype, boolean performLoad)
            throws ExecutionNotFound {
        requestLogFileLoad(getExecutionByReferenceOrFail(e), filetype, performLoad)
    }

    public Execution getExecutionByReferenceOrFail(ExecutionReference e) throws ExecutionNotFound {
        Execution execution1 = Execution.get(e.id)
        if (!execution1) {
            throw new ExecutionNotFound('Not Found', e.id, e.project)
        }
        execution1
    }

    def LogFileLoader requestLogFileLoad(Execution e, String filetype, boolean performLoad) {
        requestLogFileLoadAsync(e, filetype, performLoad, false).get()
    }

    CompletableFuture<ExecutionFileLoader> requestFileLoadAsync(
            ExecutionReference e,
            String filetype
    ) throws ExecutionNotFound {
        requestLogFileLoadAsync(getExecutionByReferenceOrFail(e), filetype, true, true)
    }

    /**
     * Async result for log file load request, which will return either the
     * current state of the loader (Future with result immediately available),
     * or a Future which completes when the load completes (when async is true, and performLoad is true)
     * @param e
     * @param filetype
     * @param performLoad if true, request a remote load, if false, only the local/remote state is returned
     * @param async if true, and performLoad is true, the returned future will complete when the remote load completes
     * @return future with completed loader, or if async and performLoad is used, a future that will will complete when requested load completes
     */
    def CompletableFuture<LogFileLoader> requestLogFileLoadAsync(
            Execution e,
            String filetype,
            boolean performLoad,
            boolean async
    ) {
        //handle cases where execution is still running or just started
        //and the file may not be available yet

        def plugin = getConfiguredPluginForExecution(e, frameworkService.getFrameworkPropertyResolverFactory(e.project))
        def partialRetrieveSupported = plugin && pluginSupportsPartialRetrieval(plugin)
        def isClusterExec = frameworkService.isClusterModeEnabled() && e.serverNodeUUID !=
                frameworkService.getServerUUID()
        def isRunning = e.dateCompleted == null && e.dateStarted != null
        def useStoredPath = e.scheduledExecution == null

        if (isRunning) {
            //execution is running
            if (isClusterExec && plugin) {
                //execution on another rundeck server, we have to wait until it is complete
                if (!partialRetrieveSupported) {
                    return CompletableFuture.completedFuture(new LogFileLoader(state: ExecutionFileState.PENDING_REMOTE))
                }
            } else if (!e.outputfilepath) {
                //no filepath defined: execution started, hasn't created output file yet.
                return CompletableFuture.completedFuture(new LogFileLoader(state: ExecutionFileState.WAITING))
            }
        }

        boolean forcePartialCheck = forcePartialCheckConfig()
        //check active request
        ExecutionFileState state
        //check the state via local file, cache results, and plugin
        def result = getLogFileState(e, filetype, plugin, (isClusterExec||forcePartialCheck))
        state = result.state
        def file = null
        long retryBackoff = result.retryBackoff ?: 0
        log.debug("requestLogFileLoad(${e.id},${filetype}): file state: ${state}: ${result}")
        switch (state) {
            case ExecutionFileState.AVAILABLE:
                file = getFileForExecutionFiletype(e, filetype, useStoredPath, false)
                break
            case ExecutionFileState.AVAILABLE_PARTIAL:
                file = getFileForExecutionFiletype(e, filetype, useStoredPath, true)
                retryBackoff = Math.max(getBackoffForPartialFile(e, filetype), retryBackoff)
                if (performLoad && result.remotePartialState == LogFileState.AVAILABLE_PARTIAL) {
                    //intiate another partial retrieval if delay interval has passed
                    def retriev = requestLogFileRetrievalPartial(e, filetype, plugin)
                }
                break
            case ExecutionFileState.AVAILABLE_REMOTE:
                if (performLoad) {
                    if (async) {
                        CompletableFuture<RetrieveFileResult> promise = new CompletableFuture<RetrieveFileResult>()
                        CompletableFuture<LogFileLoader> resultFuture = promise.thenApply(
                                { RetrieveFileResult res ->
                                    new LogFileLoader(
                                            state: res.success ? ExecutionFileState.AVAILABLE :
                                                   res.error ? ExecutionFileState.ERROR : ExecutionFileState.NOT_FOUND,
                                            file: res.file,
                                            errorCode: res.error ? 'execution.log.storage.retrieval.ERROR' : null,
                                            errorData: res.error ? ['', res.error] : null
                                    )
                                }
                        )
                        state = requestLogFileRetrieval(e, filetype, plugin, promise)
                        return resultFuture
                    } else {
                        state = requestLogFileRetrieval(e, filetype, plugin)
                    }
                }
                break
            case ExecutionFileState.AVAILABLE_REMOTE_PARTIAL:
                if (performLoad && result.remotePartialState == LogFileState.AVAILABLE_PARTIAL) {
                    state = requestLogFileRetrievalPartial(e, filetype, plugin)
                    if (state == ExecutionFileState.AVAILABLE_PARTIAL) {
                        file = getFileForExecutionFiletype(e, filetype, useStoredPath, true)
                        retryBackoff = Math.max(getBackoffForPartialFile(e, filetype), retryBackoff)
                    }
                }
                break
        }
        log.debug("requestLogFileLoad(${e.id},${filetype},${performLoad}): result ${state}")

        return CompletableFuture.completedFuture(
                new LogFileLoader(
                state: state,
                file: file,
                errorCode: result.errorCode,
                errorData: result.errorData,
                retryBackoff: retryBackoff
                )
        )
    }

    /**
     * Return a key to identify a request
     * @param execution
     * @return
     */
    private static String logFileRetrievalKey(Execution execution, String filetype, boolean partial = false) {
        return execution.id.toString() + ":" + filetype + (partial ? ":part" : '')
    }

    /**
     * Get the state of any existing retrieval request, or null if none exists
     * @param execution the execution
     * @return state of the log file
     */
    private ExecutionFileState logFileRetrievalRequestState(
            Execution execution,
            String filetype,
            boolean partial = false
    )
    {
        def key = logFileRetrievalKey(execution, filetype, partial)
        def pending = logFileRetrievalRequests.get(key)
        if (pending != null) {
            log.debug("logFileRetrievalRequestState(${key}) already pending: ${pending.state}")
            //request already in progress
            return pending.state
        }
        return null
    }

    /**
     * Request a log file be retrieved, and return the current state of the execution log. If a request for the
     * same file has already been submitted, it will not be duplicated.
     * @param execution execution object
     * @param plugin storage method
     * @return state of the log file
     */
    private ExecutionFileState requestLogFileRetrieval(
            Execution execution, String filetype, ExecutionFileStorage plugin
    ) {
        requestLogFileRetrieval(execution, filetype, plugin, null)
    }

    private ExecutionFileState requestLogFileRetrieval(
            Execution execution, String filetype, ExecutionFileStorage plugin,
            CompletableFuture<RetrieveFileResult> listener
    )
    {
        def useStoredPath = execution.scheduledExecution == null
        def key=logFileRetrievalKey(execution,filetype)
        def file = getFileForExecutionFiletype(execution, filetype, useStoredPath, false)
        Map newstate = [state  : ExecutionFileState.PENDING_LOCAL, file: file, filetype: filetype,
                        ruuid  : UUID.randomUUID().toString(),
                        storage: plugin, id: key, name: getConfiguredPluginName(), count: 0]
        def previous = logFileRetrievalResults.get(key)
        if(previous!=null){
            newstate.count=previous.count
        }
        def pending=logFileRetrievalRequests.putIfAbsent(key, newstate)
        if(pending!=null){
            log.debug("requestLogFileRetrieval, already pending for ${key}: ${pending.state}")
            //request already started
            return pending.state
        }
        //remove previous result
        logFileRetrievalResults.remove(key)
        log.debug("requestLogFileRetrieval, queueing a new request (attempt ${newstate.count+1}) for ${key}...")
        if (null != listener) {
            logFileRetrievalListeners.put(newstate.ruuid, listener)
        }
        retrievalRequests<<newstate
        return ExecutionFileState.PENDING_LOCAL
    }
    /**
     * Request a partial log file be retrieved, and return the current state of the execution log. If a request for the
     * same file has already been submitted, it will not be duplicated. If
     * the partial log has been retrieved with X time period, it will not be duplicated until the time expires.
     * @param execution execution object
     * @param plugin storage method
     * @return state of the log file
     */
    private ExecutionFileState requestLogFileRetrievalPartial(
            Execution execution,
            String filetype,
            ExecutionFileStorage plugin
    )
    {
        def key = logFileRetrievalKey(execution, filetype, true)
        def file = getFileForExecutionFiletype(execution, filetype, false, true)
        Map newstate = [
                state   : ExecutionFileState.PENDING_LOCAL,
                file    : file,
                filetype: filetype,
                storage : plugin,
                id      : key,
                name    : getConfiguredPluginName(),
                count   : 0,
                date    : new Date(),
                partial : true
        ]
        def previous = logFileRetrievalResults.get(key)
        if (previous != null) {
            newstate.count = previous.count
        }

        if (file.exists() && isResultCacheItemFresh(
                [time: new Date(file.lastModified())],
                (int)Math.floor(logstoreCheckpointTimeSecondsPeriod/2)
        )) {
            log.debug(
                    "requestLogFileRetrievalPartial, partial file modified within last " +
                            "${Math.floor(logstoreCheckpointTimeSecondsPeriod/2)} secs, skipping for ${key}..."
            )
            return ExecutionFileState.AVAILABLE_PARTIAL
        }

        def pending = logFileRetrievalRequests.putIfAbsent(key, newstate)
        if (pending != null) {
            log.debug("requestLogFileRetrievalPartial, already pending for ${key}: ${pending.state}")
            //request already started
            return pending.state
        }
        //remove previous result
        logFileRetrievalResults.remove(key)
        log.debug(
                "requestLogFileRetrievalPartial, queueing a new request (attempt ${newstate.count + 1}) for ${key}..."
        )
        retrievalRequests << newstate
        return ExecutionFileState.PENDING_LOCAL
    }

    long getBackoffForPartialFile(Execution execution, String filetype) {
        def file = getFileForExecutionFiletype(execution, filetype, false, true)
        def cacheTime = (long) Math.floor(logstoreCheckpointTimeSecondsPeriod / 2)
        def lastMod = file.lastModified()
        return (cacheTime * 1000L) - (System.currentTimeMillis() - file.lastModified())
    }

    /**
     * Return true if the retrieval task result cache time is within the retry delay
     * @param previous
     * @return
     */
    boolean isResultCacheItemFresh(Map previous, int cacheLife = -1) {
        getCacheItemRemainingMS(previous, cacheLife) > 0
    }
    /**
     * Return true if the retrieval task result cache time is within the retry delay
     * @param previous
     * @return
     */
    long getCacheItemRemainingMS(Map previous, int cacheLife = -1) {
        int retryDelay = cacheLife > 0 ? cacheLife : getConfiguredRetrievalRetryDelay()
        long ms = TimeUnit.MILLISECONDS.convert(retryDelay, TimeUnit.SECONDS).longValue()
        Date cacheTime = previous.time
        return (cacheTime.time + ms) - System.currentTimeMillis()
    }

    /**
     * Return true if the retrieval task result retry count is within the max retries
     * @param previous
     * @return
     */
     boolean isResultCacheItemAllowedRetry(Map previous){
        int retryCount = getConfiguredRetrievalRetryCount()
        return previous.count < retryCount
    }
    /**
     * Asynchronously start a request to store a log file for a completed execution using the storage method
     * @param id the request id
     * @param file the file to store
     * @param storage the storage method
     * @param executionLogStorage the persisted object that records the result
     * @param delay seconds to delay the request
     */
    private void storeLogFileAsync(
            String id,
            ExecutionFileStorage storage,
            LogFileStorageRequestData executionLogStorage,
            int delay = 0
    )
    {
        queueLogStorageRequest(
                [
                        id       : id,
                        storage  : storage,
                        filetype : executionLogStorage.filetype,
                        request  : executionLogStorage,
                        requestId: executionLogStorage.id,
                        execId   : executionLogStorage.executionId,
                        executionUuid: executionLogStorage.executionUuid,
                        partial  : false
                ],
                delay
        )
    }
    /**
     * Asynchronously start a request to store a log file for a completed execution using the storage method
     * @param id the request id
     * @param file the file to store
     * @param storage the storage method
     * @param executionLogStorage the persisted object that records the result
     * @param delay seconds to delay the request
     * @param executionUuid the execution UUID
     */
    private void storeLogFileAsyncPartial(
            String id,
            ExecutionFileStorage storage,
            Long execId,
            String filetype,
            int delay = 0,
            String executionUuid
    )
    {
        queueLogStorageRequest(
                [
                        id      : id,
                        storage : storage,
                        filetype: filetype,
                        execId  : execId,
                        partial : true,
                        executionUuid: executionUuid
                ],
                delay
        )
    }
    /**
     * Queue the request to store a log file
     * @param execution
     * @param storage plugin that is already initialized
     */
    private void queueLogStorageRequest(Map task, int delay=0) {
        if (delay > 0) {

            if (!task.partial) {
                retryRequests.add(task.requestId)
                storageQueueCounter?.inc()
            }
            logFileStorageTaskScheduler.schedule({
                if (!task.partial) {
                    retryRequests.remove(task.requestId)
                    storageQueueCounter?.dec()
                }
                queueLogStorageRequest(task, -1)
            }, new Date(System.currentTimeMillis() + (delay * 1000)))
        } else {
            if (!task.partial) {
                storageQueueCounter?.inc()
            }
            storageRequests << task
        }
    }
    /**
     * Return true if all non-generated execution files are present locally
     * @param execution
     * @return false if any local file is not present
     */
    boolean areAllExecutionFilesPresent(Execution execution) {
        for (def bean : listExecutionFileProducers()) {
            if (!bean.isExecutionFileGenerated()) {
                def execution1 = bean.produceStorageFileForExecution(execution.asReference())
                if (execution1.shouldBeStored && !execution1.localFile.exists()) {
                    return false
                }
            }
        }
        true
    }

    Map<String, ExecutionFile> getExecutionFiles(@NotNull Execution execution, List<String> filters, boolean checkpoint) {
        Collection<ExecutionFileProducer> beans = listExecutionFileProducers(filters)
        def result = [:]
        beans?.each { bean ->
            def execFile= checkpoint ?
                    bean.produceStorageCheckpointForExecution(execution.asReference()) :
                    bean.produceStorageFileForExecution(execution.asReference())
            if(execFile.shouldBeStored){
                result[bean.getExecutionFileType()] = execFile
            }
        }
        log.debug("found beans of ExecutionFileProducer result: $result")
        result?:[:]
    }

    private Collection<ExecutionFileProducer> listExecutionFileProducers(List<String> filters=null) {
        def type = applicationContext.getBeansOfType(ExecutionFileProducer)
        def all = type?.find { it.key.endsWith('Profiled') } ? type?.findAll { it.key.endsWith('Profiled') } : type
        def beans = all?.values()
        if (filters) {
            beans = beans.findAll { it.executionFileType in filters }
        }
        beans
    }

    private deleteExecutionFilePerPolicy(ExecutionFile file, boolean canRetrieve) {
        ExecutionFileUtil.deleteExecutionFilePerPolicy(file, canRetrieve)
    }

    /**
     * Store all files for a completed execution using the storage method
     * @param filter, list of types to filter by, or null/empty to include all types
     * @param storage plugin that is already initialized
     * @param ident storage identifier
     * @param files available files by type
     */
    private List storeLogFiles(
            List<String> filter,
            ExecutionFileStorage storage,
            String ident,
            Map<String, ExecutionFile> files,
            boolean partial = false
    )
    {
        log.debug("Storage request [ID#${ident}], start, type ${filter}")
        def success = false
        if(filter) {
            files = files.subMap(filter.findAll{it in files.keySet()})
        }
        def list = [:]
        def List<ExecutionFile> deletions=[]
        if (storage instanceof ExecutionMultiFileStorage) {
            list = storeMultiLogFiles(files, storage, ident, partial)
        } else {
            files.each { type, file ->
                def (result, message) = storeSingleLogFile(file.localFile, type, storage, ident, partial)
                if (!result) {
                    list[type]=message
                }
            }
        }
        if(!list){
            success=true
        }
        files.keySet().each{
            if(!(it in list.keySet())){
                deletions << files[it]
            }
        }
        boolean canRetrieve = pluginSupportsRetrieve(storage)
        deletions.each{
            deleteExecutionFilePerPolicy(it, canRetrieve)
        }

        log.debug("Storage request [ID#${ident}], finish: ${success}")
        return [success,list]
    }

    /**
     * Store multiple files at once using the multi-file-storage plugin
     * @param  files files
     * @param storage plugin
     * @param ident storage request ident
     * @return list of filetypes which were not successful
     */
    private Map<String, String> storeMultiLogFiles(
            Map<String, ExecutionFile> files,
            ExecutionMultiFileStorage storage,
            String ident,
            boolean partial = false
    )
    {
        log.debug("Storage request storeMultiLogFiles [ID#${ident}], start")

        Map<String,String> failures = [:]

        Map<String, File> localfiles = files.collectEntries { [it.key, it.value.localFile] }

        def request = new MultiFileStorageRequestImpl(files: localfiles, completed: !partial)

        storage.storeMultiple(request)

        //determine results
        files.keySet().each { String filetype ->
            def succeeded = request.completion[filetype]
            if (!succeeded) {
                failures[filetype] = request.errors[filetype]?:('No failure message (filetype: ' + filetype + ')')
            }
        }

        failures
    }
    /**
     * Store the log file for a completed execution using the storage method
     * @param execution
     * @param storage plugin that is already initialized
     */
    private def storeSingleLogFile(
            File file,
            String filetype,
            ExecutionFileStorage storage,
            String ident,
            boolean partial = false
    )
    {
        log.debug("Storage request [ID#${ident}], start")
        def success = false
        String message=null
        Date lastModified = new Date(file.lastModified())
        long length = file.length()
        try{
            file.withInputStream { input ->
                if (partial) {
                    success = storage.partialStore(filetype, input, length, lastModified)
                } else {
                    success = storage.store(filetype, input, length, lastModified)
                }
                message="No message"
            }
        }catch (Throwable e) {
            log.error("Storage request [ID#${ident}] error: ${e.message}")
            log.debug("Storage request [ID#${ident}] error: ${e.message}", e)
            message=e.message
        }
        log.debug("Storage request [ID#${ident}], finish: ${success}")
        return [success,message]
    }

    /**
     * Retrieves a log file for the given execution using a storage method
     * @param execution
     * @param storage plugin that is already initialized
     * @return Map containing success: true/false, and error: String indicating the error if there was one
     */
    private RetrieveFileResult retrieveLogFile(
            File file,
            String filetype,
            ExecutionFileStorage storage,
            String ident,
            boolean partial = false
    )
    {
        def tempfile = File.createTempFile("temp-storage","logfile")
        tempfile.deleteOnExit()
        def success=false
        def psuccess=false
        def errorMessage=null
        try {
            tempfile.withOutputStream { out ->
                try {
                    psuccess = partial ? storage.partialRetrieve(filetype, out) : storage.retrieve(filetype, out)
                } catch (ExecutionFileStorageException e) {
                    errorMessage=e.message
                }
            }
            if(psuccess) {
                if (!file.getParentFile().isDirectory()) {
                    if (!file.getParentFile().mkdirs()) {
                        errorMessage="Failed to create directories for file: ${file}"
                    }
                }
                Files.move(
                        tempfile.toPath(),
                        file.toPath(),
                        StandardCopyOption.REPLACE_EXISTING
                )
                success = true
            }
            log.debug("Retrieval request [ID#${ident}], result: ${success}, error? ${errorMessage}, dest: $file")

        } catch (Throwable t) {
            errorMessage = "Failed retrieve log file: ${t.message}"
            log.debug("Retrieval request [ID#${ident}]: Failed retrieve log file: ${t.message}", t)
        }
        if(!success){
            log.error("Retrieval request [ID#${ident}] error: ${errorMessage}")
            tempfile.delete()
        }
        return new RetrieveFileResult(success: success, error: errorMessage, file: file, partial: partial)
    }

    /**
     * Remove log storage file
     * @param execution
     * @param file type
     * @return Map containing success: true/false, and error: String indicating the error if there was one
     */
   Map removeRemoteLogFile(Execution e, String filetype, int delay=0) {
        def plugin = getConfiguredPluginForExecution(e, frameworkService.getFrameworkPropertyResolverFactory(e.project))

       if(!plugin){
           return [started: false, error: "Not plugin enabled"]
       }

        def remote = plugin.isAvailable(filetype)
        def started=false
        def errorMessage=null

        if(remote){
            try{
                logFileStorageDeleteRemoteTask.execute({
                    plugin.deleteFile(filetype)
                })

                started = true

            }catch(Exception ex){
                errorMessage = "Failed retrieve log file: ${ex.message}"
                log.warn("removing the remote log file failed: ${ex.message}")
            }
        }else{
            errorMessage = "Remote file is not available"
        }

        return [started: started, error: errorMessage]
    }

    @Override
    void onApplicationEvent(final ContextClosedEvent event) {
        cleanup()
    }

    boolean forcePartialCheckConfig() {
        configurationService.getBoolean(FORCE_PARTIAL_CHECKING, false)
    }
}

class RetrieveFileResult {
    boolean success
    File file
    String error
    boolean partial
}
