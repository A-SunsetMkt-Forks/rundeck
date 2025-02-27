package rundeck.services

import com.dtolabs.rundeck.core.authorization.UserAndRolesAuthContext
import com.dtolabs.rundeck.core.common.IRundeckProject
import grails.gorm.transactions.Rollback
import grails.testing.mixin.integration.Integration
import org.hibernate.StaleObjectStateException
import org.quartz.JobDetail
import org.quartz.Scheduler
import org.quartz.SimpleTrigger
import org.rundeck.app.authorization.AppAuthContextProcessor
import rundeck.CommandExec
import rundeck.Execution
import rundeck.Option
import rundeck.ScheduledExecution
import rundeck.Workflow
import rundeck.services.feature.FeatureService
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Integration tests for the ScheduledExecutionService.
 */
@Integration
@Rollback
class ScheduledExecutionServiceIntegrationSpec extends Specification {
    public static final String TEST_UUID1 = 'BB27B7BB-4F13-44B7-B64B-D2435E2DD8C7'
    public static final String TEST_UUID2 = '490966E0-2E2F-4505-823F-E2665ADC66FB'

    @Shared
    ScheduledExecutionService service = new ScheduledExecutionService()

    private Map createJobParams(Map overrides = [:]) {
        [
                jobName       : 'blue',
                project       : 'AProject',
                groupPath     : 'some/where',
                description   : 'a job',
                argString     : '-a b -c d',
                workflow      : new Workflow(
                        keepgoing: true,
                        commands: [new CommandExec([adhocRemoteString: 'test buddy']).save(flush: true, failOnError: true)]
                ).save(flush: true, failOnError: true),
                serverNodeUUID: null,
                scheduled     : true,
                userRoleList  : ''
        ] + overrides
    }

    def "reclaimAndScheduleJobByJob successfully moves a scheduled job to the new current server"() {
        given:
        def project = 'testProject'
        def seOneId = UUID.randomUUID().toString()

        // Create the resource in a separate transaction so that it can be seen by the separate transactions
        // in the takeover.
        def se = ScheduledExecution.withNewTransaction {
            def workflow = new Workflow(commands: []).save(flush: true, failOnError: true)
            return new ScheduledExecution(
                    jobName: 'callisto-one',
                    groupPath: 'group/reclaimAndScheduleJobByJob',
                    uuid: seOneId,
                    serverNodeUUID: TEST_UUID1,
                    project: project,
                    workflow: workflow,
                    scheduled: true
            ).save(flush: true, failOnError: true)
        }

        service.frameworkService = Stub(FrameworkService) {
            existsFrameworkProject(project) >> true
            isClusterModeEnabled() >> true
            getServerUUID() >> TEST_UUID2
        }
        service.executionServiceBean = Mock(ExecutionService) {
            getExecutionsAreActive() >> true
        }
        service.jobSchedulesService = Mock(JobSchedulesService) {
            1 * getSchedulesJobToClaim(TEST_UUID2, null, true, null, null) >> [se]
            isScheduled(se.uuid) >> se.scheduled
        }

        when:
        def results = service.reclaimAndScheduleJobByJob()

        then:
        def actualScheduledExecution = ScheduledExecution.get(se.id)

        results[seOneId]["success"] == true
        actualScheduledExecution.serverNodeUUID == TEST_UUID2
    }

    def "reclaimAndScheduleJobByJob retries claim job on failure"() {
        given:
        def project = 'testProject'
        def seOneId = UUID.randomUUID().toString()

        // Create the resource in a separate transaction so that it can be seen by the separate transactions
        // in the takeover.
        def se = ScheduledExecution.withNewTransaction {
            def workflow = new Workflow(commands: []).save(flush: true, failOnError: true)
            return new ScheduledExecution(
                    jobName: 'callisto-two',
                    groupPath: 'group/reclaimAndScheduleJobByJob',
                    uuid: seOneId,
                    serverNodeUUID: TEST_UUID1,
                    project: project,
                    workflow: workflow,
                    scheduled: true
            ).save(flush: true, failOnError: true)
        }

        // Hold a lock on the resource that gets released after at least one retry.
        CountDownLatch testLatch = new CountDownLatch(1)
        CountDownLatch lockLatch = new CountDownLatch(2)
        Thread.start {
            ScheduledExecution.withNewTransaction {
                // Acquire a lock and hold it until the latch is released.
                ScheduledExecution.get(se.id).lock()
                testLatch.countDown()
                lockLatch.await(1, TimeUnit.MINUTES)
            }
        }
        // Continue the test after the lock is acquired.
        testLatch.await(1, TimeUnit.MINUTES)

        service.frameworkService = Stub(FrameworkService) {
            existsFrameworkProject(project) >> true
            isClusterModeEnabled() >> true
            getServerUUID() >> TEST_UUID2
        }
        service.executionServiceBean = Mock(ExecutionService) {
            getExecutionsAreActive() >> true
        }
        service.jobSchedulesService = Mock(JobSchedulesService) {
            1 * getSchedulesJobToClaim(TEST_UUID2, null, true, null, null) >> [se]
            (2.._) * isScheduled(se.uuid) >> {
                lockLatch.countDown()
                se.scheduled
            }
        }

        when:
        def results = service.reclaimAndScheduleJobByJob()

        then:
        def actualScheduledExecution = ScheduledExecution.get(se.id)

        results[seOneId]["success"] == true
        actualScheduledExecution.serverNodeUUID == TEST_UUID2
    }

    def "reclaiming scheduled jobs includes ad hoc scheduled"() {
        given:
        def project = 'testProject'
        service.executionServiceBean    = Mock(ExecutionService)
        service.quartzScheduler         = Mock(Scheduler)
        service.jobSchedulerService = Mock(JobSchedulerService)
        service.frameworkService        = Stub(FrameworkService) {
            existsFrameworkProject(project) >> true
            isClusterModeEnabled() >> true
            getServerUUID() >> TEST_UUID2
            getRundeckBase() >> ''
            getFrameworkProject(project) >> Mock(IRundeckProject){
                getProjectProperties()>>[:]
            }
        }
        service.fileUploadService=Mock(FileUploadService)
        service.rundeckAuthContextProcessor=Mock(AppAuthContextProcessor){
            getAuthContextForUserAndRolesAndProject(_,_,_)>>Mock(UserAndRolesAuthContext)
        }

        String jobUuid  = UUID.randomUUID().toString()
        def workflow = new Workflow(commands: []).save(flush: true,
                                                       failOnError: true)
        def se = new ScheduledExecution(
                jobName: 'viridian',
                groupPath: 'test/group',
                uuid: jobUuid,
                serverNodeUUID: TEST_UUID1,
                project: project,
                workflow: workflow,
                scheduled: false
        ).save(flush: true, failOnError: true)

        // Create an execution with a scheduled time +1 day
        def startTime   = new Date()
        startTime       = startTime.plus(1)

        def e = new Execution(
                scheduledExecution: se,
                argString: '-test args',
                user: 'testuser',
                project: project,
                loglevel: 'WARN',
                doNodedispatch: false,
                serverNodeUUID: TEST_UUID1,
                status: 'scheduled',
                dateStarted: startTime
        ).save(flush: true, failOnError: true)

        se.executions = [e]
        se.save(flush: true, failOnError: true)
        service.jobSchedulesService = Mock(JobSchedulesService){
            getAllScheduled(_) >> [se]
            getSchedulesJobToClaim(_,_,_,_,_) >> [se]
        }

        when:
        def results = service.reclaimAndScheduleJobs(TEST_UUID1, true, project, [jobUuid])
//        ScheduledExecution.withSession { session ->
//            session.flush()
//            se.refresh()
//            e.refresh()
//        }


        then:
        1 * service.executionServiceBean.getExecutionsAreActive() >> true
        jobUuid in results
        // This job should have been claimed by the node
        results[jobUuid].job.jobName == 'viridian'
        results[jobUuid].success
        se.serverNodeUUID == TEST_UUID1
    }

    def "reclaiming scheduled jobs should include both ad hoc and fixed"() {
        given:
        def project = 'testProject'
        service.executionServiceBean    = Mock(ExecutionService)
        service.quartzScheduler         = Mock(Scheduler)
        service.jobSchedulerService = Mock(JobSchedulerService)
        service.frameworkService        = Stub(FrameworkService) {
            existsFrameworkProject(project) >> true
            isClusterModeEnabled() >> true
            getServerUUID() >> TEST_UUID2
            getRundeckBase() >> ''
            getFrameworkProject(project) >> Mock(IRundeckProject){
                getProjectProperties()>>[:]
            }
        }
        service.fileUploadService=Mock(FileUploadService)
        service.rundeckAuthContextProcessor=Mock(AppAuthContextProcessor){
            getAuthContextForUserAndRolesAndProject(_,_,_)>>Mock(UserAndRolesAuthContext)
        }

        String jobUuid  = UUID.randomUUID().toString()
        String jobUuid2 = UUID.randomUUID().toString()
        def workflow = new Workflow(commands: []).save(flush: true, failOnError: true)
        def se = new ScheduledExecution(
                createJobParams(
                    jobName: 'xanadu',
                    groupPath: 'test/group',
                    uuid: jobUuid,
                    serverNodeUUID: TEST_UUID1,
                    project: project,
                    workflow: workflow,
                    scheduled: false
                )
        ).save(flush: true, failOnError: true)

        def se2 = new ScheduledExecution(
                createJobParams(
                    jobName: 'amaranth',
                    groupPath: 'test/group',
                    uuid: jobUuid2,
                    serverNodeUUID: TEST_UUID1,
                    project: project,
                    workflow: workflow,
                    user: 'skywalker',
                    userRoleList: 'jedi',
                    scheduled: true
                )
        ).save(flush: true, failOnError: true)

        def startTime   = new Date()
        startTime       = startTime.plus(1)

        def e = new Execution(
                scheduledExecution: se,
                argString: '-test args',
                user: 'testuser',
                project: project,
                loglevel: 'WARN',
                doNodedispatch: false,
                serverNodeUUID: TEST_UUID1,
                status: 'scheduled',
                dateStarted: startTime
        ).save(flush: true)

        se.executions = [e]
        se.save(flush: true, failOnError: true)
        service.jobSchedulesService = Mock(JobSchedulesService){
            getAllScheduled(_) >> [se]
            isScheduled(se.uuid) >> se.scheduled
            isScheduled(se2.uuid) >> se2.scheduled
            getSchedulesJobToClaim(_,_,_,_,_) >> [se, se2]
        }

        when:
        def results = service.reclaimAndScheduleJobs(TEST_UUID1, true, project)
        ScheduledExecution.withSession { session ->
            session.flush()
            [se, se2, e]*.refresh()
        }


        then:
        1 * service.executionServiceBean.getExecutionsAreActive() >> true
        // Both jobs should've been claimed
        jobUuid in results
        jobUuid2 in results
        results[jobUuid].job.jobName == 'xanadu'
        results[jobUuid].success
        results[jobUuid2].job.jobName == 'amaranth'
        results[jobUuid2].success
        se.serverNodeUUID == TEST_UUID1
        se2.serverNodeUUID == TEST_UUID2
    }

    def "ad hoc scheduled job should be rescheduled via Quartz"() {
        given:
        def project                     = 'testProject'
        def projectMock = Mock(IRundeckProject) {
            getProjectProperties() >> [:]
        }
        service.featureService = Mock(FeatureService)
        service.executionServiceBean    = Mock(ExecutionService)
        service.fileUploadService = Mock(FileUploadService)
        service.quartzScheduler         = Mock(Scheduler)
        service.jobSchedulerService = Mock(JobSchedulerService)
        service.frameworkService = Stub(FrameworkService) {
            existsFrameworkProject(project) >> true
            isClusterModeEnabled() >> true
            getServerUUID() >> TEST_UUID2
            getRundeckBase() >> ''
            getFrameworkProject(_) >> projectMock
        }
        service.rundeckAuthContextProcessor=Mock(AppAuthContextProcessor){
            getAuthContextForUserAndRolesAndProject(_,_,_)>>Mock(UserAndRolesAuthContext)
        }

        String jobUuid  = UUID.randomUUID().toString()
        def workflow = new Workflow(commands: []).save(failOnError: true)
        def se = new ScheduledExecution(
                createJobParams(
                    jobName: 'cerulean',
                    groupPath: 'test/group',
                    uuid: jobUuid,
                    serverNodeUUID: TEST_UUID1,
                    project: project,
                    workflow: workflow,
                    scheduled: false
                )
        ).save(failOnError: true, flush: true)

        def startTime   = new Date()
        startTime       = startTime.plus(1)

        def e = new Execution(
                scheduledExecution: se,
                argString: '-test args',
                user: 'testuser',
                project: project,
                loglevel: 'WARN',
                doNodedispatch: false,
                serverNodeUUID: TEST_UUID1,
                status: 'scheduled',
                dateStarted: startTime,
                dateCompleted: null
        ).save(flush: true)

        se.executions = [e]
        se.save(flush: true)
        service.jobSchedulesService = Mock(JobSchedulesService){
            getSchedulesJobToClaim(_,_,_,_,_) >> service.getSchedulesJobToClaim(TEST_UUID2, TEST_UUID1, false, project, null)
        }


        when:
        def results = service.reclaimAndScheduleJobs(TEST_UUID1, true, project)
        ScheduledExecution.withSession { session ->
            session.flush()
            [se, e]*.refresh()
        }


        then:
        e != null
        e.serverNodeUUID == TEST_UUID2
        se != null
        1 * service.executionServiceBean.executionsAreActive >> true
        1 * service.jobSchedulerService.scheduleJob(_,_,_, startTime, false) >>  startTime

        // Both jobs should've been claimed
        jobUuid in results
        results[jobUuid].job.jobName == 'cerulean'
        results[jobUuid].success
        se.serverNodeUUID == TEST_UUID1
        e.serverNodeUUID == TEST_UUID2
    }

    def "should not be rescheduled ad hoc if executions disabled"() {
        given:
        def project                     = 'testProject'
        service.executionServiceBean    = Mock(ExecutionService)
        service.quartzScheduler         = Mock(Scheduler)
        service.frameworkService = Stub(FrameworkService) {
            existsFrameworkProject(project) >> true
            isClusterModeEnabled() >> true
            getServerUUID() >> TEST_UUID2
            getRundeckBase() >> ''
            getFrameworkProject(project) >> Mock(IRundeckProject){
                getProjectProperties()>>[:]
            }
        }
        service.rundeckAuthContextProcessor=Mock(AppAuthContextProcessor){
            getAuthContextForUserAndRolesAndProject(_,_,_)>>Mock(UserAndRolesAuthContext)
        }

        String jobUuid  = UUID.randomUUID().toString()
        def workflow = new Workflow(commands: []).save(failOnError: true)
        def se = new ScheduledExecution(
                jobName: 'manatee',
                groupPath: 'test/group',
                uuid: jobUuid,
                serverNodeUUID: TEST_UUID1,
                project: project,
                workflow: workflow,
                scheduled: false,
                userRoleList: ''
        ).save(failOnError: true)

        def startTime   = new Date()
        startTime       = startTime.plus(1)

        def e = new Execution(
                scheduledExecution: se,
                argString: '-test args',
                user: 'testuser',
                project: project,
                loglevel: 'WARN',
                doNodedispatch: false,
                serverNodeUUID: TEST_UUID1,
                status: 'scheduled',
                dateStarted: startTime
        ).save(failOnError: true)

        se.executions = [e]
        se.save(flush: true, failOnError: true)
        service.jobSchedulesService = Mock(JobSchedulesService){
            getSchedulesJobToClaim(_,_,_,_,_) >> service.getSchedulesJobToClaim(TEST_UUID2, TEST_UUID1, false, project, null)
        }

        when:
        def results = service.reclaimAndScheduleJobs(TEST_UUID1, true, project)
//        ScheduledExecution.withSession { session ->
//            session.flush()
//            [se, e]*.refresh()
//        }


        then:
        1 * service.executionServiceBean.getExecutionsAreActive() >> false
        // Should not have been scheduled sa executions are not active
        0 * service.quartzScheduler.scheduleJob(_ as JobDetail, _ as SimpleTrigger)

        jobUuid in results
        results[jobUuid].job.jobName == 'manatee'
        results[jobUuid].success
        se.serverNodeUUID == TEST_UUID1
    }

    def "job with secure input options should be cleaned up"() {
        given:
        def project                     = 'testProject'
        service.executionServiceBean    = Mock(ExecutionService)
        service.quartzScheduler         = Mock(Scheduler)
        service.frameworkService = Stub(FrameworkService) {
            existsFrameworkProject(project) >> true
            isClusterModeEnabled() >> true
            getServerUUID() >> TEST_UUID2
            getRundeckBase() >> ''
            getFrameworkProject(project) >> Mock(IRundeckProject){
                getProjectProperties()>>[:]
            }
        }

        String jobUuid  = UUID.randomUUID().toString()
        def workflow = new Workflow(commands: []).save(failOnError: true)
        def se = new ScheduledExecution(
                jobName: 'byzantium',
                groupPath: 'test/group',
                uuid: jobUuid,
                serverNodeUUID: TEST_UUID1,
                project: project,
                workflow: workflow,
                scheduled: false,
				user: 'yoda',
                userRoleList: 'jedi,master',
				options: [new Option(name: 'foo', defaultValue: 'bar', enforced: false,
							secureInput: true, secureExposed: true, required: true)]
        ).save(failOnError: true)

        def startTime   = new Date()
        startTime       = startTime.plus(2)

        def e = new Execution(
                scheduledExecution: se,
                argString: '-test args',
                user: 'testuser',
                project: project,
                loglevel: 'WARN',
                doNodedispatch: false,
                serverNodeUUID: TEST_UUID1,
                status: 'scheduled',
                dateStarted: startTime
        ).save(failOnError: true)

        se.executions = [e]
        se.save(flush: true, failOnError: true)
        service.jobSchedulesService = Mock(JobSchedulesService){
            getSchedulesJobToClaim(_,_,_,_,_) >> service.getSchedulesJobToClaim(TEST_UUID2, TEST_UUID1, false, project, null)
        }


        when:
        def results = service.reclaimAndScheduleJobs(TEST_UUID1, true, project)
//        ScheduledExecution.withSession { session ->
//            session.flush()
//            [se, e]*.refresh()
//        }


        then:
		1 * service.executionServiceBean.cleanupRunningJobs(_ as List) >> {
			arguments ->
            List<Execution> receivedExecutions = arguments[0]
            assert receivedExecutions.size() == 1
			assert receivedExecutions.get(0).id == e.id
            assert receivedExecutions.get(0).status == 'scheduled'
            assert receivedExecutions.get(0).dateStarted.getTime() == startTime.getTime()
		}
        // Should not have been scheduled as the job has secure input options
        0 * service.quartzScheduler.scheduleJob(_ as JobDetail, _ as SimpleTrigger)

        jobUuid in results
        results[jobUuid].job.jobName == 'byzantium'
        results[jobUuid].success
        se.serverNodeUUID == TEST_UUID1
	}

    void testClaimScheduledJobsUnassigned() {
        def (ScheduledExecution job1, String serverUUID2, ScheduledExecution job2, ScheduledExecution job3,
         String serverUUID) = setupTestClaimScheduledJobs()
        ScheduledExecutionService testService = new ScheduledExecutionService()

        assertEquals(null, job1.serverNodeUUID)
        assertEquals(serverUUID2, job2.serverNodeUUID)
        assertEquals(null, job3.serverNodeUUID)

        def resultMap = testService.claimScheduledJobs(serverUUID)

        assertTrue(resultMap[job1.extid].success)
        assertEquals(null, resultMap[job2.extid])
        assertEquals(null, resultMap[job3.extid])
//        ScheduledExecution.withSession {session->
//            session.flush()
//
//            job1 = ScheduledExecution.get(job1.id)
//            job1.refresh()
//            job2 = ScheduledExecution.get(job2.id)
//            job2.refresh()
//            job3 = ScheduledExecution.get(job3.id)
//            job3.refresh()
//        }


        assertEquals(serverUUID, job1.serverNodeUUID)
        assertEquals(serverUUID2, job2.serverNodeUUID)
        assertEquals(null, job3.serverNodeUUID)

    }

    void testClaimScheduledJobsFromServerUUID() {
        def (ScheduledExecution job1, String serverUUID2, ScheduledExecution job2, ScheduledExecution job3,
         String serverUUID) = setupTestClaimScheduledJobs()
        ScheduledExecutionService testService = new ScheduledExecutionService()
        assertEquals(null, job1.serverNodeUUID)
        assertEquals(serverUUID2, job2.serverNodeUUID)
        assertEquals(null, job3.serverNodeUUID)

        def resultMap = testService.claimScheduledJobs(serverUUID, serverUUID2)

//        ScheduledExecution.withSession { session ->
//            session.flush()
//
//            job1 = ScheduledExecution.get(job1.id)
//            job1.refresh()
//            job2 = ScheduledExecution.get(job2.id)
//            job2.refresh()
//            job3 = ScheduledExecution.get(job3.id)
//            job3.refresh()
//        }

        assertEquals(null, job1.serverNodeUUID)
        assertEquals(serverUUID, job2.serverNodeUUID)
        assertEquals(null, job3.serverNodeUUID)

        assertEquals(null, resultMap[job1.extid])
        assertTrue(resultMap[job2.extid].success)
        assertEquals(null, resultMap[job3.extid])
    }

    @Unroll
    def "get scheduled jobs to claim"() {
        given:
            ScheduledExecution.list().each{
                it.executions*.delete()
                it.executions=[]
                it.delete()
            }
            def servers=[
                target:UUID.randomUUID().toString(),
                a:UUID.randomUUID().toString(),
                b:UUID.randomUUID().toString(),
            ]
            def uuids=[
                a:UUID.randomUUID().toString(),
                b:UUID.randomUUID().toString(),
                c:UUID.randomUUID().toString(),
                d:UUID.randomUUID().toString(),
                e:UUID.randomUUID().toString(),
            ]
            ScheduledExecution job1 = new ScheduledExecution(
                createJobParams(jobName: 'blue1', project: 'AProject', serverNodeUUID: null, uuid:uuids.a)
            ).save()
            ScheduledExecution job2 = new ScheduledExecution(
                createJobParams(jobName: 'blue2', project: 'AProject2', serverNodeUUID: servers.a, uuid:uuids.b)
            ).save()
            ScheduledExecution job3 = new ScheduledExecution(
                createJobParams(jobName: 'blue3', project: 'AProject2', serverNodeUUID: servers.b, uuid:uuids.c)
            ).save()
            ScheduledExecution job3x = new ScheduledExecution(
                createJobParams(jobName: 'blue4', project: 'AProject2', serverNodeUUID: servers.target, uuid:uuids.d)
            ).save()
            ScheduledExecution job4 = new ScheduledExecution(
                createJobParams(jobName: 'blue5', project: 'AProject2', scheduled: false, uuid:uuids.e)
            ).save()
        when:
            def resultList = service.getSchedulesJobToClaim(servers.target, fromServer?servers[fromServer]:null, isSelectAll, isProject, isJobids?isJobids.collect{uuids[it]}:null,isScheduledIgnore)

        then:

            resultList*.extid == expectIdList.collect{uuids[it]}
        where:
            fromServer | isSelectAll | isProject   | isJobids   | isScheduledIgnore | expectIdList
            null       | true        | null        | null       | false             | ['a', 'b', 'c']
            null       | true        | null        | ['a', 'b'] | false             | ['a', 'b']
            null       | true        | 'AProject'  | null       | false             | ['a']
            null       | true        | 'AProject'  | ['b']      | false             | []
            null       | true        | 'AProject'  | ['a']      | false             | ['a']
            null       | true        | 'AProject2' | null       | false             | ['b', 'c']
            null       | true        | 'AProject2' | ['b']      | false             | ['b']
            null       | true        | 'AProject2' | ['c']      | false             | ['c']
            null       | true        | 'AProject2' | ['b', 'c'] | false             | ['b', 'c']
            null       | true        | 'AProject2' | null       | true              | ['b', 'c', 'e']
            null       | false       | 'AProject'  | null       | false             | ['a']
            null       | false       | 'AProject2' | null       | false             | []
            null       | false       | 'AProject2' | null       | true              | ['e']
            'a'        | false       | 'AProject2' | null       | false             | ['b']
            'b'        | false       | 'AProject2' | null       | false             | ['c']
    }
    @Unroll
    def "get scheduled jobs to claim with scheduled executions"() {
        given:
            ScheduledExecution.list().each{
                it.executions*.delete()
                it.executions=[]
                it.delete()
            }
            def servers=[
                target:UUID.randomUUID().toString(),
                a:UUID.randomUUID().toString(),
                b:UUID.randomUUID().toString(),
                c:UUID.randomUUID().toString(),
            ]
            def uuids=[
                a:UUID.randomUUID().toString(),
                b:UUID.randomUUID().toString(),
                c:UUID.randomUUID().toString(),
                d:UUID.randomUUID().toString(),
                e:UUID.randomUUID().toString(),
                f:UUID.randomUUID().toString(),
            ]
            ScheduledExecution job1 = new ScheduledExecution(
                createJobParams(jobName: 'blue1', project: 'AProject', serverNodeUUID: null, uuid:uuids.a)
            ).save()
            ScheduledExecution job2 = new ScheduledExecution(
                createJobParams(jobName: 'blue2', project: 'AProject2', serverNodeUUID: servers.a, uuid:uuids.b)
            ).save()
            ScheduledExecution job3 = new ScheduledExecution(
                createJobParams(jobName: 'blue3', project: 'AProject2', serverNodeUUID: servers.b, uuid:uuids.c)
            ).save()
            ScheduledExecution job3x = new ScheduledExecution(
                createJobParams(jobName: 'blue4', project: 'AProject2', serverNodeUUID: servers.target, uuid:uuids.d)
            ).save()
            ScheduledExecution job4 = new ScheduledExecution(
                createJobParams(jobName: 'blue5', project: 'AProject2', scheduled: false, uuid:uuids.e)
            ).save()
            ScheduledExecution job5 = new ScheduledExecution(
                createJobParams(jobName: 'blue6', project: 'AProject3', scheduled: false, uuid:uuids.f)
            ).save()
            Date startTime = new Date() + 2
            Execution e = new Execution(
                scheduledExecution: job5,
                argString: '-test args',
                user: 'testuser',
                project: 'AProject3',
                loglevel: 'WARN',
                doNodedispatch: false,
                serverNodeUUID: servers.c,
                status: 'scheduled',
                dateStarted: startTime
            ).save(flush: true, failOnError: true)
            job5.executions = [e]
        when:
            def resultList = service.getSchedulesJobToClaim(servers.target, fromServer?servers[fromServer]:null, isSelectAll, isProject, isJobids?isJobids.collect{uuids[it]}:null,isScheduledIgnore)

        then:

            resultList*.extid == expectIdList.collect{uuids[it]}
        where:
            fromServer | isSelectAll | isProject   | isJobids   | isScheduledIgnore | expectIdList
            null       | true        | null        | null       | false             | ['a', 'b', 'c', 'f']
            null       | true        | null        | ['a', 'b'] | false             | ['a', 'b', 'f']
            null       | true        | 'AProject'  | null       | false             | ['a']
            null       | true        | 'AProject'  | ['b']      | false             | []
            null       | true        | 'AProject'  | ['a']      | false             | ['a']
            null       | true        | 'AProject2' | null       | false             | ['b', 'c']
            null       | true        | 'AProject2' | ['b']      | false             | ['b']
            null       | true        | 'AProject2' | ['c']      | false             | ['c']
            null       | true        | 'AProject2' | ['b', 'c'] | false             | ['b', 'c']
            null       | true        | 'AProject2' | null       | true              | ['b', 'c', 'e']
            null       | false       | 'AProject'  | null       | false             | ['a']
            null       | false       | 'AProject2' | null       | false             | []
            null       | false       | 'AProject2' | null       | true              | ['e']
            'a'        | false       | 'AProject2' | null       | false             | ['b']
            'b'        | false       | 'AProject2' | null       | false             | ['c']
            null       | true        | 'AProject3' | null       | false             | ['f']
            'c'        | false       | 'AProject3' | null       | false             | ['f']
            'c'        | false       | null        | null       | false             | ['f']
    }
    def "claim all scheduled jobs"() {
        given:
        def targetserverUUID = UUID.randomUUID().toString()
        def serverUUID1 = UUID.randomUUID().toString()
        def serverUUID2 = UUID.randomUUID().toString()
            ScheduledExecution.list().each{
                it.executions*.delete()
                it.executions=[]
                it.delete()
            }
        ScheduledExecution job1 = new ScheduledExecution(
                createJobParams(jobName: 'blue1', project: 'AProject', serverNodeUUID: null, uuid:UUID.randomUUID().toString())
        ).save()
        ScheduledExecution job2 = new ScheduledExecution(
                createJobParams(jobName: 'blue2', project: 'AProject2', serverNodeUUID: serverUUID1, uuid:UUID.randomUUID().toString())
        ).save()
        ScheduledExecution job3 = new ScheduledExecution(
                createJobParams(jobName: 'blue3', project: 'AProject2', serverNodeUUID: serverUUID2, uuid:UUID.randomUUID().toString())
        ).save()
        ScheduledExecution job3x = new ScheduledExecution(
                createJobParams(jobName: 'blue3', project: 'AProject2', serverNodeUUID: targetserverUUID, uuid:UUID.randomUUID().toString())
        ).save()
        ScheduledExecution job4 = new ScheduledExecution(
                createJobParams(jobName: 'blue4', project: 'AProject2', scheduled: false, uuid:UUID.randomUUID().toString())
        ).save()
        def jobs = [job1, job2, job3, job3x, job4]
        service.jobSchedulesService = Mock(JobSchedulesService){
            getAllScheduled(_) >> [job1, job2, job3, job3x, job4]
            isScheduled(job1.uuid) >> job1.scheduled
            isScheduled(job2.uuid) >> job2.scheduled
            isScheduled(job3.uuid) >> job3.scheduled
            isScheduled(job3x.uuid) >> job3x.scheduled
            isScheduled(job4.uuid) >> job4.scheduled
            getSchedulesJobToClaim(_,_,_,_,_) >> service.getSchedulesJobToClaim(targetserverUUID, null, true, null, null)
        }
        when:
        def resultMap = service.claimScheduledJobs(targetserverUUID, null, true)

//        ScheduledExecution.withSession { session ->
//            session.flush()
//            jobs*.refresh()
//        }
        then:

        assert job1.scheduled
        assert job2.scheduled
        assert job3.scheduled
        assert job3x.scheduled

        [job1, job2, job3, job3x] == jobs.findAll { it.serverNodeUUID == targetserverUUID }
        [job1, job2, job3]*.extid == resultMap.keySet() as List
    }

    def "claim all scheduled jobs includes adhoc scheduled executions"() {
        given:
        def targetserverUUID = UUID.randomUUID().toString()
        def serverUUID1 = UUID.randomUUID().toString()
        def serverUUID2 = UUID.randomUUID().toString()
            ScheduledExecution.list().each{
                it.executions*.delete()
                it.executions=[]
                it.delete()
            }
        ScheduledExecution job1 = new ScheduledExecution(
                createJobParams(jobName: 'blue1', project: 'AProject', serverNodeUUID: null, uuid:UUID.randomUUID().toString())
        ).save()
        ScheduledExecution job2 = new ScheduledExecution(
                createJobParams(jobName: 'blue2', project: 'AProject2', serverNodeUUID: serverUUID1, uuid:UUID.randomUUID().toString())
        ).save()
        ScheduledExecution job3 = new ScheduledExecution(
                createJobParams(jobName: 'blue3', project: 'AProject2', serverNodeUUID: serverUUID2, uuid:UUID.randomUUID().toString())
        ).save()
        ScheduledExecution job4 = new ScheduledExecution(
                createJobParams(jobName: 'blue4', project: 'AProject2', serverNodeUUID: targetserverUUID, uuid:UUID.randomUUID().toString())
        ).save()
        ScheduledExecution job5 = new ScheduledExecution(
                createJobParams(jobName: 'blue5', project: 'AProject2', scheduled: false, uuid:UUID.randomUUID().toString())
        ).save()
        def jobs = [job1, job2, job3, job4, job5]
        Date startTime = new Date() + 2
        Execution e = new Execution(
            scheduledExecution: job5,
            argString: '-test args',
            user: 'testuser',
            project: 'AProject2',
            loglevel: 'WARN',
            doNodedispatch: false,
            serverNodeUUID: serverUUID2,
            status: 'scheduled',
            dateStarted: startTime
        ).save(flush: true, failOnError: true)
        job5.executions = [e]
        job5.save()

        service.jobSchedulesService = Mock(JobSchedulesService){
            getAllScheduled(_) >> [job1, job2, job3, job4]
            isScheduled(job1.uuid) >> job1.scheduled
            isScheduled(job2.uuid) >> job2.scheduled
            isScheduled(job3.uuid) >> job3.scheduled
            isScheduled(job4.uuid) >> job4.scheduled
            isScheduled(job5.uuid) >> false
            getSchedulesJobToClaim(_,_,_,_,_) >> {
                return service.getSchedulesJobToClaim(targetserverUUID, null, true, null, null)
            }
        }
        when:
        def resultMap = service.claimScheduledJobs(targetserverUUID, null, true)

        then:

        job1.scheduled
        job2.scheduled
        job3.scheduled
        job4.scheduled
        !job5.scheduled

        [job1, job2, job3, job4] == jobs.findAll { it.serverNodeUUID == targetserverUUID }
        e.serverNodeUUID==targetserverUUID
        [job1, job2, job3,job5]*.extid == resultMap.keySet() as List
    }

    def "claim all scheduled jobs in a project"(
            String targetProject,
            String targetServerUUID,
            String serverUUID1,
            List<Map> dataList,
            List<String> resultList
    )
    {
        setup:
            ScheduledExecution.list().each{
                it.executions*.delete()
                it.executions=[]
                it.delete()
            }
        def jobs = dataList.collect {
            new ScheduledExecution(createJobParams(it)).save()
        }
        service.jobSchedulesService = Mock(JobSchedulesService){
            getSchedulesJobToClaim(_,_,_,_,_) >> service.getSchedulesJobToClaim(targetServerUUID, null, true, targetProject, null)
        }
        when:
        def resultMap = service.claimScheduledJobs(targetServerUUID, null, true, targetProject)

//        ScheduledExecution.withSession { session ->
//            session.flush()
//            jobs*.refresh()
//        }
        then:

        resultList == resultMap.keySet() as List

        where:
        targetProject | targetServerUUID |
                serverUUID1 |
                dataList |
                resultList
        'AProject'    | TEST_UUID1       |
                TEST_UUID2  |
                [[uuid: 'job3', project: 'AProject', serverNodeUUID: TEST_UUID1], [uuid: 'job1', serverNodeUUID: TEST_UUID2], [project: 'AProject2', uuid: 'job2']] |
                ['job1']
        'AProject2'   | TEST_UUID1       |
                TEST_UUID2  |
                [[uuid: 'job3', project: 'AProject2', serverNodeUUID: TEST_UUID1], [uuid: 'job1', serverNodeUUID: TEST_UUID2], [project: 'AProject2', uuid: 'job2']] |
                ['job2']
    }

    def "test max retry on claim scheduled job"() {
        given:
        def targetserverUUID = UUID.randomUUID().toString()
        ScheduledExecution job1 = new ScheduledExecution(
                createJobParams(jobName: 'blue1', project: 'AProject', serverNodeUUID: null)
        ).save()
        String jid = job1.id.toString()
        ScheduledExecution.metaClass.static.refresh = {-> throw new StaleObjectStateException("scheduleExecution", "")}
        service.jobSchedulesService = Mock(JobSchedulesService){
            getSchedulesJobToClaim(_,_,_,_,_) >> service.getSchedulesJobToClaim(targetserverUUID, null, false, null, null)
        }
        when:
        def resultMap = service.claimScheduledJobs(targetserverUUID, null, true)

        then:
        !resultMap[jid].success

    }
}

