/*
 * Copyright 2019 Rundeck, Inc. (http://rundeck.com)
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

import com.dtolabs.rundeck.core.authorization.UserAndRolesAuthContext
import com.dtolabs.rundeck.core.config.FeatureService
import com.dtolabs.rundeck.core.config.Features
import com.dtolabs.rundeck.core.event.EventImpl
import com.dtolabs.rundeck.core.event.EventQueryImpl
import com.dtolabs.rundeck.core.event.EventQueryResultImpl
import com.dtolabs.rundeck.core.event.EventStoreService
import com.dtolabs.rundeck.core.plugins.ConfiguredPlugin
import com.dtolabs.rundeck.core.plugins.PluggableProviderService
import com.dtolabs.rundeck.core.plugins.PluginRegistry
import com.dtolabs.rundeck.core.plugins.ValidatedPlugin
import com.dtolabs.rundeck.core.plugins.configuration.PluginCustomConfigValidator
import com.dtolabs.rundeck.core.plugins.configuration.PropertyResolver
import com.dtolabs.rundeck.core.plugins.configuration.PropertyScope
import com.dtolabs.rundeck.core.plugins.configuration.Validator
import com.dtolabs.rundeck.core.storage.keys.KeyStorageTree
import com.dtolabs.rundeck.core.webhook.WebhookEventException
import com.dtolabs.rundeck.plugins.descriptions.PluginCustomConfig
import com.dtolabs.rundeck.plugins.webhook.DefaultWebhookResponder
import com.dtolabs.rundeck.plugins.webhook.WebhookData
import com.dtolabs.rundeck.plugins.webhook.WebhookDataImpl
import com.dtolabs.rundeck.plugins.webhook.WebhookEventContext
import com.dtolabs.rundeck.plugins.webhook.WebhookEventPlugin
import com.dtolabs.rundeck.plugins.webhook.WebhookResponder
import grails.testing.gorm.DataTest
import grails.testing.services.ServiceUnitTest
import org.rundeck.app.data.model.v1.authtoken.AuthTokenMode
import org.rundeck.app.data.model.v1.authtoken.AuthTokenType
import org.rundeck.app.data.model.v1.authtoken.AuthenticationToken
import org.rundeck.app.data.model.v1.storedevent.StoredEventQueryType
import org.rundeck.app.data.providers.GormWebhookDataProvider
import org.rundeck.app.data.providers.storedEvent.GormStoredEventProvider
import org.rundeck.app.spi.AuthorizedServicesProvider
import org.rundeck.app.spi.Services
import org.rundeck.app.spi.SimpleServiceProvider
import org.rundeck.app.util.spi.AuthTokenManager
import org.springframework.context.MessageSource
import rundeck.StoredEvent
import rundeck.services.data.WebhookDataService
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll
import webhooks.Webhook
import webhooks.WebhookService

import javax.servlet.http.HttpServletRequest

class WebhookServiceSpec extends Specification implements ServiceUnitTest<WebhookService>, DataTest {
    @Shared GormEventStoreService eventStoreService
    @Shared FrameworkService framework
    WebhookService service
    GormWebhookDataProvider webhookProvider = new GormWebhookDataProvider()
    private static class Events {
        String event
    }
    void setupSpec() {
        mockDomain Webhook
    }
    void setup(){
        mockDomain StoredEvent
        mockDataService(WebhookDataService)
        webhookProvider.webhookDataService = applicationContext.getBean(WebhookDataService)
        webhookProvider.messageSource = Mock(MessageSource)
        service = new WebhookService()
        service.webhookDataProvider = webhookProvider
        eventStoreService = new GormEventStoreService()
        framework = Mock(FrameworkService) {
            it.serverUUID >> '16b02806-f4b3-4628-9d9c-2dd2cc67d53c'
        }
        eventStoreService.frameworkService = framework
        eventStoreService.storedEventProvider = new GormStoredEventProvider()
        service.gormEventStoreService = eventStoreService
        service.rundeckAuthTokenManagerService = Mock(RundeckAuthTokenManagerService)
    }
    def "process webhook"() {
        given:
        def mockUserAuth = Mock(UserAndRolesAuthContext) {
            getUsername() >> { "webhookUser" }
            getRoles() >> { ["webhook","test"] }
        }
        WebhookDataImpl data = new WebhookDataImpl()
        data.webhook = "testhook"
        data.sender = "asender"
        data.contentType = "text/plain"
        data.data = new ByteArrayInputStream("my event data".bytes)
        service.storageService = Mock(MockStorageService)


        def mockPropertyResolver = Mock(PropertyResolver)
        def webhookProviderService = Mock(PluggableProviderService)

        service.featureService = Mock(FeatureService) {
            featurePresent(Features.EVENT_STORE) >> false
        }

        service.gormEventStoreService = Mock(EventStoreService) {
            scoped(_,_) >> { Mock(EventStoreService) }
        }

        service.rundeckAuthorizedServicesProvider = Mock(AuthorizedServicesProvider) {
            getServicesWith(_) >> { Mock(Services)}
        }
        service.frameworkService = Mock(MockFrameworkService) {
            getFrameworkPropertyResolver(_,_) >> { mockPropertyResolver }
        }
        service.rundeckPluginRegistry = Mock(PluginRegistry) {
            createPluggableService(WebhookEventPlugin) >> {
                webhookProviderService
            }
        }

        TestWebhookEventPlugin testPlugin = new TestWebhookEventPlugin()

        service.pluginService = Mock(MockPluginService) {
            configurePlugin("test-webhook-event", _, _,_) >> { new ConfiguredPlugin<WebhookEventPlugin>(testPlugin, [:] ) }
        }
        def request = Mock(HttpServletRequest) {
            getHeader("X-Rundeck-TestHdr") >> { "Hdr1" }
        }
        when:
        def responder = service.processWebhook("test-webhook-event","{}",data,mockUserAuth,request)

        then:
        1 * service.storageService.storageTreeWithContext(_) >> Mock(KeyStorageTree)
        testPlugin.captured.data.text=="my event data"
        testPlugin.captured.headers["X-Rundeck-TestHdr"] == "Hdr1"
        responder instanceof DefaultWebhookResponder

    }
    def "process webhook event store usage"() {
        given:
        def mockUserAuth = Mock(UserAndRolesAuthContext) {
            getUsername() >> { "webhookUser" }
            getRoles() >> { ["webhook","test"] }
        }
        WebhookDataImpl data = new WebhookDataImpl()
        data.webhook = "testhook"
        data.sender = "asender"
        data.contentType = "text/plain"
        data.data = new ByteArrayInputStream("my event data".bytes)
        service.storageService = Mock(MockStorageService)


        def mockPropertyResolver = Mock(PropertyResolver)
        def webhookProviderService = Mock(PluggableProviderService)

        service.featureService = Mock(FeatureService) {
            featurePresent(Features.EVENT_STORE) >> true
        }

        def scopedService=Mock(EventStoreService)
        service.gormEventStoreService = Mock(EventStoreService) {
            scoped(_,_) >> scopedService
        }

        service.rundeckAuthorizedServicesProvider = Mock(AuthorizedServicesProvider) {
            getServicesWith(_) >> new SimpleServiceProvider([:])
        }
        service.frameworkService = Mock(MockFrameworkService) {
            getFrameworkPropertyResolver(_,_) >> { mockPropertyResolver }
        }
        service.rundeckPluginRegistry = Mock(PluginRegistry) {
            createPluggableService(WebhookEventPlugin) >> {
                webhookProviderService
            }
        }

        TestLoggingWebhookEventPlugin testPlugin = new TestLoggingWebhookEventPlugin()

        service.pluginService = Mock(MockPluginService) {
            configurePlugin("test-webhook-event", _, _,_) >> { new ConfiguredPlugin<WebhookEventPlugin>(testPlugin, [:] ) }
        }
        def request = Mock(HttpServletRequest) {
            getHeader("X-Rundeck-TestHdr") >> { "Hdr1" }
        }
        when:
        def responder = service.processWebhook("test-webhook-event","{}",data,mockUserAuth,request)

        then:
        1 * service.storageService.storageTreeWithContext(_) >> Mock(KeyStorageTree)
        responder instanceof DefaultWebhookResponder

        1 * scopedService.query({
            it.queryType == StoredEventQueryType.DELETE
            it.projectName == 'aproj'
            it.topic == 'webhook test'
            it.subsystem == 'blah'
        })>> new EventQueryResultImpl(totalCount: 1,events:[])
        1*scopedService.storeEvent({
            it.projectName == 'aproj'
            it.topic == 'webhook test'
            it.subsystem == 'blah'
            it.meta.data == "my event data"
            it.meta.headers["X-Rundeck-TestHdr"] == "Hdr1"
        })
    }

    class TestLoggingWebhookEventPlugin implements WebhookEventPlugin {

        @Override
        List<String> getRequestHeadersToCopy() {
            return ["X-Rundeck-TestHdr"]
        }

        @Override
        WebhookResponder onEvent(final WebhookEventContext context, final WebhookData data) throws WebhookEventException {

            def svc = context.services.getService(EventStoreService)
            svc.storeEvent(new EventImpl(
                projectName: 'aproj',
                topic: 'webhook test',
                subsystem: 'blah',
                meta: [data: data.data.text, headers: data.headers]
            ))
            svc.query(new EventQueryImpl(
                projectName: 'aproj',
                topic: 'webhook test',
                subsystem: 'blah',
                queryType: StoredEventQueryType.DELETE
            ))
            return null
        }
    }
    class TestWebhookEventPlugin implements WebhookEventPlugin {
        WebhookData captured

        @Override
        List<String> getRequestHeadersToCopy() {
            return ["X-Rundeck-TestHdr"]
        }

        @Override
        WebhookResponder onEvent(final WebhookEventContext context, final WebhookData data) throws WebhookEventException {
            captured = data
            return null
        }
    }

    def "save new webhook"() {
        given:
        def mockUserAuth = Mock(UserAndRolesAuthContext) {
            getUsername() >> { "webhookUser" }
            getRoles() >> { ["webhook","test"] }
        }
        service.apiService = Mock(MockApiService)
        service.rundeckAuthTokenManagerService = Mock(AuthTokenManager) {
            parseAuthRoles(_) >> { ["webhook","test"] }
        }
        service.userService = Mock(MockUserService) {
            validateUserExists(_) >> { true }
        }
        service.pluginService = Mock(MockPluginService) {
            validatePluginConfig(_,_,_) >> { return new ValidatedPlugin(report: new Validator.Report(),valid:true) }
            getPlugin(_,_) >> { new TestWebhookEventPlugin() }
            listPlugins(WebhookEventPlugin) >> { ["log-webhook-event":new TestWebhookEventPlugin()] }
        }

        when:
        def result = service.saveHook(mockUserAuth,[name:"test",project:"Test",user:"webhookUser",roles:"webhook,test",eventPlugin:"log-webhook-event","config":["cfg1":"val1"]])
        Webhook created = Webhook.findByName("test")

        then:
        result.msg == "Saved webhook"
        result.uuid
        created.name == "test"
        created.project == "Test"
        created.enabled == true
        created.eventPlugin == "log-webhook-event"
        created.pluginConfigurationJson == '{"cfg1":"val1"}'
        1 * service.apiService.generateUserToken(_,_,_,_,_,_) >> { [token:"12345"] }

    }
    def "save new webhook validation fails"() {
        given:
        def mockUserAuth = Mock(UserAndRolesAuthContext) {
            getUsername() >> { "webhookUser" }
            getRoles() >> { ["webhook","test"] }
        }
        service.apiService = Mock(MockApiService)
        service.rundeckAuthTokenManagerService = Mock(AuthTokenManager) {
            parseAuthRoles(_) >> { ["webhook","test"] }
        }
        service.userService = Mock(MockUserService) {
            validateUserExists(_) >> { true }
        }
        def report=new Validator.Report()
        report.errors['test']='wrong'
        service.pluginService = Mock(MockPluginService) {
            validatePluginConfig(_,_,_) >> { return new ValidatedPlugin(report: report,valid:false) }
            getPlugin(_,_) >> { new TestWebhookEventPlugin() }
            listPlugins(WebhookEventPlugin) >> { ["log-webhook-event":new TestWebhookEventPlugin()] }
        }

        when:
        def result = service.saveHook(mockUserAuth,[name:"test",project:"Test",user:"webhookUser",roles:"webhook,test",eventPlugin:"log-webhook-event","config":["cfg1":"val1"]])
        Webhook created = Webhook.findByName("test")

        then:
        result.err ==~ /^Invalid plugin configuration.*/
        result.errors==[test:'wrong']
        !created
        0 * service.apiService.generateUserToken(_,_,_,_,_,_)

    }
    def "save new webhook token creation fails"() {
        given:
        def mockUserAuth = Mock(UserAndRolesAuthContext) {
            getUsername() >> { "webhookUser" }
            getRoles() >> { ["webhook","test"] }
        }
        service.apiService = Mock(MockApiService){
            generateUserToken(_,_,_,_,_,_)>>{
                throw new Exception("token error")
            }
        }
        service.rundeckAuthTokenManagerService = Mock(AuthTokenManager) {
            parseAuthRoles(_) >> { ["webhook","test"] }
        }
        service.userService = Mock(MockUserService) {
            validateUserExists(_) >> { true }
        }
        def report=new Validator.Report()
        service.pluginService = Mock(MockPluginService) {
            validatePluginConfig(_,_,_) >> { return new ValidatedPlugin(report: report,valid:true) }
            getPlugin(_,_) >> { new TestWebhookEventPlugin() }
            listPlugins(WebhookEventPlugin) >> { ["log-webhook-event":new TestWebhookEventPlugin()] }
        }

        when:
        def result = service.saveHook(mockUserAuth,[name:"test",project:"Test",user:"webhookUser",roles:"webhook,test",eventPlugin:"log-webhook-event","config":["cfg1":"val1"]])
        Webhook created = Webhook.findByName("test")

        then:
        result.err ==~ /^Failed to create associated Auth Token: token error$/
        !created
    }
    def "save new webhook token creation unauthorized"() {
        given:
        Webhook existing = new Webhook(name:"test",project: "Test",authToken: "12345",eventPlugin: "log-webhook-event", uuid: "existing-uuid")
        existing.save()

        def mockUserAuth = Mock(UserAndRolesAuthContext) {
            getUsername() >> { "webhookUser" }
            getRoles() >> { ["webhook","test"] }
        }
        service.apiService = Mock(MockApiService){
            generateUserToken(_,_,_,_,_,_)>>{

            }
        }
        service.rundeckAuthTokenManagerService = Mock(AuthTokenManager) {
            parseAuthRoles(_) >> { ["webhook","test","bogus"] }
            updateAuthRoles(_,_,_)>>{
                throw new Exception("Unauthorized to update roles")
            }
        }
        service.userService = Mock(MockUserService) {
            validateUserExists(_) >> { true }
        }
        def report=new Validator.Report()
        service.pluginService = Mock(MockPluginService) {
            validatePluginConfig(_,_,_) >> { return new ValidatedPlugin(report: report,valid:true) }
            getPlugin(_,_) >> { new TestWebhookEventPlugin() }
            listPlugins(WebhookEventPlugin) >> { ["log-webhook-event":new TestWebhookEventPlugin()] }
        }

        when:
        def result = service.saveHook(mockUserAuth,[id:existing.id, uuid: existing.uuid,name:"test",project:"Test",user:"webhookUser",roles:"webhook,test,bogus",eventPlugin:"log-webhook-event","config":["cfg1":"val1"]])

        then:
        result.err ==~ /^Failed to update Auth Token roles: Unauthorized to update roles$/
    }

    def "save new webhook fails due to gorm validation, token should get deleted"() {
        given:
        def mockUserAuth = Mock(UserAndRolesAuthContext) {
            getUsername() >> { "webhookUser" }
            getRoles() >> { ["webhook","test"] }
        }
        service.apiService = Mock(MockApiService)
        service.rundeckAuthTokenManagerService = Mock(AuthTokenManager) {
            parseAuthRoles(_) >> { ["webhook","test"] }
        }
        service.userService = Mock(MockUserService) {
            validateUserExists(_) >> { true }
        }
        service.pluginService = Mock(MockPluginService) {
            validatePluginConfig(_,_,_) >> { return new ValidatedPlugin(report: new Validator.Report(),valid:true) }
            getPlugin(_,_) >> { new TestWebhookEventPlugin() }
            listPlugins(WebhookEventPlugin) >> { ["log-webhook-event":new TestWebhookEventPlugin()] }
        }

        when:
        def result = service.saveHook(mockUserAuth,[project:"Test",user:"webhookUser",roles:"webhook,test",eventPlugin:"log-webhook-event","config":["cfg1":"val1"]])
        Webhook created = Webhook.findByName("test")


        then:
        result.err
        !created
        1 * service.apiService.generateUserToken(_,_,_,_,_,_) >> { [token:"12345"] }
        1 * service.rundeckAuthTokenManagerService.deleteByTokenWithType('12345', AuthTokenType.WEBHOOK )

    }
    def "webhook name could be used multiples times"() {
        given:
        def mockUserAuth = Mock(UserAndRolesAuthContext) {
            getUsername() >> { "webhookUser" }
            getRoles() >> { ["webhook","test"] }
        }
        Webhook existing = new Webhook(name:"test",project: "Test",authToken: "12345",eventPlugin: "log-webhook-event")
        existing.save()
        service.userService = Mock(MockUserService) {
            validateUserExists(_) >> { true }
        }
        service.apiService = Mock(MockApiService)
        service.pluginService = Mock(MockPluginService) {
            validatePluginConfig(_,_,_) >> { return new ValidatedPlugin(report: new Validator.Report(),valid:true) }
            getPlugin(_,_) >> { new TestWebhookEventPlugin() }
            listPlugins(WebhookEventPlugin) >> { ["log-webhook-event":new TestWebhookEventPlugin()] }
        }

        when:
        def result = service.saveHook(mockUserAuth,[name:"test",project:"Test",user:"webhookUser",roles:"webhook,test",eventPlugin:"log-webhook-event","config":["cfg1":"val1"]])

        then:
        _ * service.apiService.generateUserToken(_,_,_,_,_,_) >> { [token:"12345"] }
        result.msg == "Saved webhook"

    }

    @Unroll
    def "validate plugin config - custom validator"() {
        given:
        CustomConfigWebhookEventPlugin customPlugin = new CustomConfigWebhookEventPlugin()

        service.pluginService = Mock(MockPluginService) {
            validatePluginConfig(_,_,_) >> { return new ValidatedPlugin(report: new Validator.Report(),valid: true) }
            getPlugin(_,_) >> { customPlugin }
        }

        when:
        Tuple2<ValidatedPlugin,Boolean> vPlugin = service.validatePluginConfig("custom-cfg-webhook-event",config)

        then:
        vPlugin.first.valid == valid
        vPlugin.first.report.errors == errors


        where:
        valid | config                  | errors
        false | [:]                     | ["requiredProp":"requiredProp is required"]
        true  | [requiredProp:"value"]  | [:]
    }

    static class CustomConfigWebhookEventPlugin implements WebhookEventPlugin {
        WebhookData captured

        @PluginCustomConfig(validator = TestCustomValidator)
        Map config

        @Override
        WebhookResponder onEvent(final WebhookEventContext context, final WebhookData data) throws WebhookEventException {
            captured = data
            return new DefaultWebhookResponder()
        }
    }

    static class TestCustomValidator implements PluginCustomConfigValidator {

        @Override
        Validator.Report validate(final Map config) {
            Validator.Report report = new Validator.Report()
            if(!config.requiredProp) report.errors["requiredProp"] = "requiredProp is required"
            return report
        }
    }


    def "ReplaceSecureOpts"() {
        given:
        def mockUserAuth = Mock(UserAndRolesAuthContext) {
            getUsername() >> { "webhookUser" }
            getRoles() >> { ["webhook","test"] }
        }
        when:
        def config = [:]
        config.prop1 = "my value"
        config.prop2 = '${KS:keys/proj1/sval}'
        config.prop3 = [
                prop1: 'foo',
                prop2: '${KS:keys/proj1/sval}'
        ]
        config.prop4 = [
                'foo',
                '${KS:keys/proj1/sval}'
        ]

        config.prop5 = '${KS:keys/proj1/sval} ${KS:keys/proj1/sval2}'

        def mockStorageTree = Mock(MockStorageTree) {
            hasPassword(_) >> { true }
            readPassword("keys/proj1/sval") >> { "password".bytes }
            readPassword("keys/proj1/sval2") >> { "other".bytes }
        }
        service.storageService = Mock(MockStorageService) {
            storageTreeWithContext(_) >> { mockStorageTree }
        }
        service.replaceSecureOpts(mockUserAuth,config)

        then:
        config.prop1 == "my value"
        config.prop2 == "password"
        config.prop3['prop2'] == "password"
        config.prop4[1] == "password"
        config.prop5 == "password other"

    }

    @Unroll
    def "import webhooks regenAuthToken: #regenFlag"() {
        given:
        def uuid = "0dfb6080-935e-413d-a6a7-cdee9345cf72"
        service.pluginService = Mock(MockPluginService) {
            listPlugins(_) >> { ["log-webhook-event":new Object()]}
        }
        service.rundeckAuthTokenManagerService = Mock(AuthTokenManager)
        def authContext = Mock(UserAndRolesAuthContext) {
            getUsername() >> { "admin" }
        }
        service.apiService = Mock(MockApiService)
        service.metaClass.validatePluginConfig = { String plugin, Map config -> new Tuple2<ValidatedPlugin, Boolean>(new ValidatedPlugin(valid: true),false)}

        when:
        def result = service.importWebhook(authContext,[name:"test",
                                                        uuid: uuid,
                                                        project:"Test", authToken:'abc123', user:'webhookUser', roles:"webhook,test",
                                                        eventPlugin:"log-webhook-event",
                                                        config:'{}'],regenFlag,regenUuid)
        Webhook created = Webhook.findByName("test")

        then:
        result == [msg:"Webhook test imported"]
        created.name == "test"
        created.project == "Test"
        created.eventPlugin == "log-webhook-event"
        created.pluginConfigurationJson == '{}'
        1 * service.rundeckAuthTokenManagerService.parseAuthRoles("webhook,test") >> { new HashSet(['webhook','test']) }
        expectGenTokenCall * service.apiService.generateUserToken(_,_,_,_,_,_) >> { [token:"12345"] }
        expectImportWhkToken * service.rundeckAuthTokenManagerService.importWebhookToken(authContext,'abc123','webhookUser',new HashSet(['webhook','test'])) >> { true }
        if(regenUuid){
            created.uuid != uuid
        }
        where:
        regenFlag | expectGenTokenCall | expectImportWhkToken   |   regenUuid
        true        |                1 |                    0   |   true
        false       |                0 |                    1   |   false
    }

    def "import is allowed"() {
        given:
        service.rundeckAuthTokenManagerService=Mock(AuthTokenManager)
        when:
        boolean res = service.importIsAllowed(hook.authToken,hookData)

        then:
        res == expected

        where:
        expected | hook | hookData
        true     | new Webhook(name: "hk1", authToken: "12345") | [authToken:"12345"]
        true     | new Webhook(name: "hk1") | [authToken:"12345"]
    }

    def "import is not allowed"() {
        setup:
        new Webhook(name:"preexisting",authToken: "12345",project:"one",eventPlugin: "plugin").save()

        when:

        Webhook hook = new Webhook(name:"new")
        def hookData = [authToken:"12345"]
        boolean res = service.importIsAllowed(hook.authToken,hookData)

        then:
        !res
    }
    def "import is not allowed - token exists"() {
        setup:
        service.rundeckAuthTokenManagerService=Mock(AuthTokenManager){
            1 * getTokenWithType('12345', AuthTokenType.WEBHOOK)>>Mock(AuthenticationToken)
        }
        Webhook hook = new Webhook(name:"new")
        def hookData = [authToken:"12345"]
        when:

        boolean res = service.importIsAllowed(hook.authToken,hookData)

        then:
        !res
    }

    def "edit webhook"() {
        given:
        def mockUserAuth = Mock(UserAndRolesAuthContext) {
            getUsername() >> { "webhookUser" }
            getRoles() >> { ["webhook", "test"] }
        }
        service.apiService = Mock(MockApiService)
        service.rundeckAuthTokenManagerService = Mock(AuthTokenManager) {
            parseAuthRoles(_) >> { ["webhook", "test"] }
        }
        service.userService = Mock(MockUserService) {
            validateUserExists(_) >> { true }
        }
        service.pluginService = Mock(MockPluginService) {
            validatePluginConfig(_, _, _) >> { return new ValidatedPlugin(report: new Validator.Report(), valid: true) }
            getPlugin(_, _) >> { new TestWebhookEventPlugin() }
            listPlugins(WebhookEventPlugin) >> { ["log-webhook-event": new TestWebhookEventPlugin()] }
        }
        Webhook existing = new Webhook(id: "1", uuid: "2c2d614b-34f5-4f52-969a-9c6a90fb8b75", name: "test", project: "Test", authToken: "12345", eventPlugin: "log-webhook-event")
        existing.save()

        when:
        def result = service.saveHook(mockUserAuth, [id: 1, uuid: "2c2d614b-34f5-4f52-969a-9c6a90fb8b75", name: "test", project: "Test", user: "webhookUser", roles: "webhook,test", eventPlugin: "log-webhook-event", "config": ["cfg1": "val1"]])

        then:
        result.msg == "Saved webhook"
        result.uuid

    }

    def "edit webhook keep authConfigJson"() {
        given:
        def mockUserAuth = Mock(UserAndRolesAuthContext) {
            getUsername() >> { "webhookUser" }
            getRoles() >> { ["webhook", "test"] }
        }
        service.apiService = Mock(MockApiService)
        service.rundeckAuthTokenManagerService = Mock(AuthTokenManager) {
            parseAuthRoles(_) >> { ["webhook", "test"] }
        }
        service.userService = Mock(MockUserService) {
            validateUserExists(_) >> { true }
        }
        service.pluginService = Mock(MockPluginService) {
            validatePluginConfig(_, _, _) >> { return new ValidatedPlugin(report: new Validator.Report(), valid: true) }
            getPlugin(_, _) >> { new TestWebhookEventPlugin() }
            listPlugins(WebhookEventPlugin) >> { ["log-webhook-event": new TestWebhookEventPlugin()] }
        }
        Webhook existing = new Webhook(id: "1", uuid: "2c2d614b-34f5-4f52-969a-9c6a90fb8b75", name: "test", project: "Test", authToken: "12345", eventPlugin: "log-webhook-event", authConfigJson: '{"cfg1":"val1"}');
        existing.save()

        when:
        def result = service.saveHook(mockUserAuth, [id: 1, uuid: "2c2d614b-34f5-4f52-969a-9c6a90fb8b75", name: "test", project: "Test", user: "webhookUser", roles: "webhook,test", useAuth: true, regenAuth: false, eventPlugin: "log-webhook-event", "config": ["cfg1": "val1"]])

        then:
        result.msg == "Saved webhook"
        result.uuid
        Webhook.get("1").authConfigJson == '{"cfg1":"val1"}'

    }

    def "edit webhook's name"() {
        given:
        def mockUserAuth = Mock(UserAndRolesAuthContext) {
            getUsername() >> { "webhookUser" }
            getRoles() >> { ["webhook", "test"] }
        }
        service.apiService = Mock(MockApiService)
        service.rundeckAuthTokenManagerService = Mock(AuthTokenManager) {
            parseAuthRoles(_) >> { ["webhook", "test"] }
        }
        service.userService = Mock(MockUserService) {
            validateUserExists(_) >> { true }
        }
        service.pluginService = Mock(MockPluginService) {
            validatePluginConfig(_, _, _) >> { return new ValidatedPlugin(report: new Validator.Report(), valid: true) }
            getPlugin(_, _) >> { new TestWebhookEventPlugin() }
            listPlugins(WebhookEventPlugin) >> { ["log-webhook-event": new TestWebhookEventPlugin()] }
        }
        Webhook existing = new Webhook(id: "1", uuid: "2c2d614b-34f5-4f52-969a-9c6a90fb8b75", name: "test", project: "Test", authToken: "12345", eventPlugin: "log-webhook-event")
        existing.save()

        when:
        def result = service.saveHook(mockUserAuth, [id: 1, uuid: "2c2d614b-34f5-4f52-969a-9c6a90fb8b75", name: "test-change-name", project: "Test", user: "webhookUser", roles: "webhook,test", eventPlugin: "log-webhook-event", "config": ["cfg1": "val1"]])
        def whPersisted = Webhook.findByUuid("2c2d614b-34f5-4f52-969a-9c6a90fb8b75")

        then:
        result.msg == "Saved webhook"
        whPersisted.name == "test-change-name"
    }

    def "import a webhook with the same name and different uuid in the same project"() {
        given:
        def mockUserAuth = Mock(UserAndRolesAuthContext) {
            getUsername() >> { "webhookUser" }
            getRoles() >> { ["webhook", "test"] }
        }
        service.apiService = Mock(MockApiService)
        service.rundeckAuthTokenManagerService = Mock(AuthTokenManager) {
            parseAuthRoles(_) >> { ["webhook", "test"] }
        }
        service.userService = Mock(MockUserService) {
            validateUserExists(_) >> { true }
        }
        service.pluginService = Mock(MockPluginService) {
            validatePluginConfig(_, _, _) >> { return new ValidatedPlugin(report: new Validator.Report(), valid: true) }
            getPlugin(_, _) >> { new TestWebhookEventPlugin() }
            listPlugins(WebhookEventPlugin) >> { ["log-webhook-event": new TestWebhookEventPlugin()] }
        }
        Webhook existing = new Webhook(id: "1", uuid: "2c2d614b-34f5-4f52-969a-9c6a90fb8b75", name: "test", project: "Test", authToken: "12345", eventPlugin: "log-webhook-event")
        existing.save()

        when:
        def result = service.importWebhook(mockUserAuth, [id: 1, uuid: "d1c6dcf7-dd12-4858-9373-c12639c689d4", name: "test", project: "Test", authToken: "12345", eventPlugin: "log-webhook-event"], true,false)
        then:
        result.msg == "Webhook test imported"
        1 * service.apiService.generateUserToken(_,_,_,_,_,_) >> { [token:"12345"] }
    }

    def "Cannot import a webhook with existing token"() {
        given:"webhook exists with token in project A"
        def mockUserAuth = Mock(UserAndRolesAuthContext) {
            getUsername() >> { "webhookUser" }
            getRoles() >> { ["webhook", "test"] }
        }
        service.apiService = Mock(MockApiService)
        service.rundeckAuthTokenManagerService = Mock(AuthTokenManager) {
            parseAuthRoles(_) >> { ["webhook", "test"] }
        }
        service.userService = Mock(MockUserService) {
            validateUserExists(_) >> { true }
        }
        service.pluginService = Mock(MockPluginService) {
            validatePluginConfig(_, _, _) >> { return new ValidatedPlugin(report: new Validator.Report(), valid: true) }
            getPlugin(_, _) >> { new TestWebhookEventPlugin() }
            listPlugins(WebhookEventPlugin) >> { ["log-webhook-event": new TestWebhookEventPlugin()] }
        }
        Webhook existing = new Webhook(existingMap)
        existing.save()

        when:"import data with same token into project B"
        def result = service.importWebhook(mockUserAuth, imported, false,false)
        then:"should fail"
        result == [err:'Unable to import webhoook test. Error:Cannot import webhook: imported auth token does not exist or was changed']
        where:
            existingMap = [uuid: "2c2d614b-34f5-4f52-969a-9c6a90fb8b75", name: "test", project: "Test", authToken: "12345", eventPlugin: "log-webhook-event"]
            imported=     [uuid: "d1c6dcf7-dd12-4858-9373-c12639c689d4", name: "test", project: "Test2", authToken: "12345", eventPlugin: "log-webhook-event"]
    }

    def "import a webhook that already exits in a project"() {
        given:"webhook exists with token in project A"
        def mockUserAuth = Mock(UserAndRolesAuthContext) {
            getUsername() >> { "webhookUser" }
            getRoles() >> { ["webhook", "test"] }
        }
        service.apiService = Mock(MockApiService)
        service.rundeckAuthTokenManagerService = Mock(AuthTokenManager) {
            parseAuthRoles(_) >> { ["webhook", "test"] }
        }
        service.userService = Mock(MockUserService) {
            validateUserExists(_) >> { true }
        }
        service.pluginService = Mock(MockPluginService) {
            validatePluginConfig(_, _, _) >> { return new ValidatedPlugin(report: new Validator.Report(), valid: true) }
            getPlugin(_, _) >> { new TestWebhookEventPlugin() }
            listPlugins(WebhookEventPlugin) >> { ["log-webhook-event": new TestWebhookEventPlugin()] }
        }
        def webhookUUID="c1c6dcf7-dd12-4858-9373-c12639c689d4"
        def definition=     [uuid: webhookUUID, name: "test", project: "Test", authToken: "12345", eventPlugin: "log-webhook-event"]

        Webhook existing = new Webhook(definition)
        existing.save()


        when:"import data with same token into project A"
        def result = service.importWebhook(mockUserAuth, definition, false,false)


        then:"should have 1 webhook"
        def existingWebhooks = service.webhookDataProvider.findAllByProject("Test")
        result == [msg:'Webhook test imported']
        existingWebhooks.size() == 1

    }

    def "getWebhookWithAuth"() {
        setup:
        Webhook hook = new Webhook()
        hook.name = "hit"
        hook.project = "One"
        hook.authToken = "abc123"
        hook.eventPlugin = "do-some-action"
        hook.pluginConfigurationJson = '{"prop1":"true"}'
        hook.save()

        service.rundeckAuthTokenManagerService = Mock(AuthTokenManager) {
            1 * getTokenWithType("abc123", AuthTokenType.WEBHOOK) >> Mock(AuthenticationToken){
                getToken()>>'abc123'
                getCreator()>>'admin'
                getAuthRolesSet()>>(["webhook,role1"] as Set)
                getOwnerName()>>'webhook'
            }
        }

        when:
        def output = service.getWebhookWithAuth(hook.id.toString())

        then:
        output.id == hook.id
        output.name == "hit"
        output.project == "One"
        output.eventPlugin == "do-some-action"
        output.config == ["prop1":"true"]
        output.user == "webhook"
        output.creator == "admin"
        output.roles == "webhook,role1"
        output.authToken == "abc123"
    }

    def "getWebhookForProjectWithAuth returns null for wrong project"() {
        setup:
        def project = 'aproject'
        Webhook hook = new Webhook()
        hook.name = "hit"
        hook.project = "otherproject"
        hook.authToken = "abc123"
        hook.eventPlugin = "do-some-action"
        hook.pluginConfigurationJson = '{"prop1":"true"}'
        hook.save()

        service.rundeckAuthTokenManagerService = Mock(AuthTokenManager)

        when:
        def output = service.getWebhookForProjectWithAuth(hook.id.toString(), project)

        then:
        output == null
        0 * service.rundeckAuthTokenManagerService._(*_)
    }

    def "delete all webhooks in project"() {
        setup:
        String project = "prj1"
        new Webhook(name:"hook1",project:project,authToken: "123",eventPlugin: "plugin").save()
        new Webhook(name:"hook2",project:project,authToken: "123",eventPlugin: "plugin").save()
        new Webhook(name:"hook3",project:"prj2",authToken: "123",eventPlugin: "plugin").save()
        new Webhook(name:"hook4",project:"prj3",authToken: "123",eventPlugin: "plugin").save()

        when:
        int beforeCount = Webhook.count()
        service.deleteWebhooksForProject(project)
        int afterCount = Webhook.count()

        then:
        beforeCount == 4
        afterCount == 2
        Webhook.countByProject(project) == 0
    }

    def "delete webhook events data"(){
        setup:
        String project = "prj1"
        String hookName1 = "hook1"
        String hookUuid1 = "7903a5ae-f30a-42b2-99e1-e0e17f00ba0c"
        String authToken = "123"
        String eventPlugin = "advanced-run-job"
        // We create 2 webhooks with advanced job run as a plugin provider
        def hook1 = new Webhook(name:hookName1,
                project:project,
                authToken: authToken,
                eventPlugin: eventPlugin,
                uuid: hookUuid1
        ).save()
        service.gormEventStoreService = Mock(EventStoreService)

        when:
        service.deleteWebhookEventsData(hook1)

        then:
        1 * service.gormEventStoreService.query(
            {
                it.queryType == StoredEventQueryType.DELETE
                it.subsystem == 'webhooks'
                it.projectName == project
                it.topic == "webhook:events:*:${hookUuid1}"
            }
        )>> new EventQueryResultImpl(totalCount:2,events:[])
        0 * service.gormEventStoreService.query(*_)
    }

    interface MockUserService {
        boolean validateUserExists(String user)
    }

    interface MockFrameworkService {
        PropertyResolver getFrameworkPropertyResolver(String project, Map config)
    }

    interface MockPluginService {
        ConfiguredPlugin configurePlugin(String pluginName, Class serviceClass, PropertyResolver resolver, PropertyScope scope)
        ValidatedPlugin validatePluginConfig(String service, String pluginName, Map config)
        WebhookEventPlugin getPlugin(String service, Class pluginClass)
        Map listPlugins(Class pluginClass)
    }

    interface MockApiService {
        Map generateUserToken(UserAndRolesAuthContext ctx, Integer expiration, String user, Set<String> roles, boolean forceExpiration, AuthTokenType tokenType) throws Exception
    }

    interface MockStorageService {
        Object storageTreeWithContext(Object obj)
    }
    interface MockStorageTree {
        boolean hasPassword(String path)
        byte[] readPassword(String path)
    }

    class TestAuthenticationToken implements AuthenticationToken {

        String token
        Set<String> authRoles
        String uuid
        String creator
        String ownerName

        @Override
        Set<String> getAuthRolesSet() {
            return authRoles
        }

        @Override
        AuthTokenType getType() {
            return AuthTokenType.WEBHOOK
        }

        @Override
        String getPrintableToken() {
            return token
        }

        @Override
        Date getExpiration() {
            return null
        }

        @Override
        String getName() {
            return null
        }

        @Override
        String getClearToken() {
            return null
        }

        @Override
        AuthTokenMode getTokenMode() {
            return null
        }
    }
}
