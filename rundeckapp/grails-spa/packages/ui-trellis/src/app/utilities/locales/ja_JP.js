const messages = {
  Edit: "Edit",
  Save: "Save",
  Delete: "Delete",
  Cancel: "Cancel",
  Revert: "Revert",
  jobAverageDurationPlaceholder: "leave blank for Job Average duration",
  resourcesEditor: {
    "Dispatch to Nodes": "Dispatch to Nodes",
    Nodes: "Nodes",
  },
  uiv: {
    modal: {
      cancel: "Cancel",
      ok: "OK",
    },
  },
  cron: {
    section: {
      0: "Seconds",
      1: "Minutes",
      2: "Hours",
      3: "Day of Month",
      4: "Month",
      5: "Day of Week",
      6: "Year",
    },
  },
  message_communityNews: "Community News",
  message_connectionError:
    "It appears an error occured when connecting to Community News.",
  message_readMore: "Read More",
  message_refresh: "Please refresh the page or visit us at",
  message_subscribe: "Subscribe",
  message_delete: "Delete this field",
  message_duplicated: "Field already exists",
  message_select: "Select a Field",
  message_description: "Description",
  message_fieldLabel: "Field Label",
  message_fieldKey: "Field Key",
  message_fieldFilter: "Type to filter a field",
  message_empty: "Can be empty",
  message_cancel: "Cancel",
  message_add: "Add",
  message_addField: "Add Custom Field",
  message_pageUsersSummary: "List of Rundeck users.",
  message_pageUsersLoginLabel: "Username",
  message_pageUsersCreatedLabel: "Created",
  message_pageUsersUpdatedLabel: "Updated",
  message_pageUsersLastjobLabel: "Last Job Execution",
  message_domainUserFirstNameLabel: "First Name",
  message_domainUserLastNameLabel: "Last Name",
  message_domainUserEmailLabel: "Email",
  message_domainUserLabel: "User",
  message_pageUsersTokensLabel: "N\u00BA Tokens",
  message_pageUsersTokensHelp:
    "You can administrate the tokens in the User Profile page.",
  message_pageUsersLoggedStatus: "Status",
  message_pageUserLoggedOnly: "Logged In Users Only",
  message_pageUserNotSet: "Not Set",
  message_pageUserNone: "None",
  message_pageFilterLogin: "Login",
  message_pageFilterHostName: "Host Name",
  message_pageFilterSessionID: "Session ID",
  message_pageFilterBtnSearch: "Search",
  message_pageUsersSessionIDLabel: "Session ID",
  message_pageUsersHostNameLabel: "Host Name",
  message_pageUsersLastLoginInTimeLabel: "Last Login",
  message_pageUsersTotalFounds: "Total Users Found",
  message_paramIncludeExecTitle: "Show Last Execution",
  message_loginStatus: {
    "LOGGED IN": "Logged In",
    "NOT LOGGED": "Never",
    ABANDONED: "Expired",
    "LOGGED OUT": "Logged Out",
  },
  message_userSummary: {
    desc: "This is a list of User Profiles which have logged in to Rundeck.",
  },
  message_webhookPageTitle: "Webhooks",
  message_webhookListTitle: "Webhooks",
  message_webhookDetailTitle: "Webhook Detail",
  message_webhookListNameHdr: "Name",
  message_addWebhookBtn: "Add",
  message_webhookEnabledLabel: "Enabled",
  message_webhookPluginCfgTitle: "Plugin Configuration",
  message_webhookSaveBtn: "Save",
  message_webhookCreateBtn: "Create Webhook",
  message_webhookDeleteBtn: "Delete",
  message_webhookPostUrlLabel: "Post URL",
  message_webhookPostUrlHelp:
    "When a HTTP POST request to this URL is received, the Webhook Plugin chosen below will receive the data.",
  message_webhookPostUrlPlaceholder:
    "URL will be generated after the Webhook is created",
  message_webhookNameLabel: "Name",
  message_webhookUserLabel: "User",
  message_webhookUserHelp:
    "The authorization username assumed when running this webhook. All ACL policies matching this username will apply.",
  message_webhookRolesLabel: "Roles",
  message_webhookRolesHelp:
    "The authorization roles assumed when running this webhook (comma separated). All ACL policies matching these roles will apply.",
  message_webhookAuthLabel: "HTTP Authorization String",
  message_webhookGenerateSecurityLabel: "Use Authorization Header",
  message_webhookGenerateSecretCheckboxHelp:
    "[Optional] A Webhook authorization string can be generated to increase security of this webhook. All posts will need to include the generated string in the Authorization header.",
  message_webhookSecretMessageHelp:
    "Copy this authorization string now. After you navigate away from this webhook you will no longer be able to see the string.",
  message_webhookRegenClicked:
    "A new authorization string will be generated and displayed when the webhook is saved.",
  message_webhookPluginLabel: "Choose Webhook Plugin",
  message_hello: "hello world",
  message_sidebarNotificationText: "Rundeck update available",
  message_updateAvailable: "Update Available",
  message_updateHasBeenReleased: "An update to Rundeck has been released.",
  message_installedVersion: "The installed version of Rundeck is",
  message_currentVersion: "The most recent release of Rundeck is",
  message_getUpdate: "Get Update",
  message_dismissMessage:
    "To dismiss this notification until the next release, please click here.",
  message_close: "Close",
  "bulk.edit": "Bulk Edit",
  "in.of": "in",
  execution: "Execution | Executions",
  "execution.count": "1 Execution | {0} Executions",
  "Bulk Delete Executions: Results": "Bulk Delete Executions: Results",
  "Requesting bulk delete, please wait.":
    "Requesting bulk delete, please wait.",
  "bulkresult.attempted.text": "{0} Executions were attempted.",
  "bulkresult.success.text": "{0} Executions were successfully deleted.",
  "bulkresult.failed.text": "{0} Executions could not be deleted:",
  "delete.confirm.text": "Really delete {0} {1}?",
  "clearselected.confirm.text":
    "Clear all {0} selected items, or only items shown on this page?",
  "bulk.selected.count": "{0} selected",
  "results.empty.text": "No results for the query",
  "Only shown executions": "Only shown executions",
  "Clear bulk selection": "Clear Bulk Selection",
  "Click to edit Search Query": "Click to edit Search Query",
  "Auto refresh": "Auto refresh",
  "error.message.0": "An Error Occurred: {0}",
  "info.completed.0": "Completed: {0}",
  "info.completed.0.1": "Completed: {0} {1}",
  "info.missed.0.1": "Marked Missed: {0} {1}",
  "info.started.0": "Started: {0}",
  "info.started.expected.0.1": "Started: {0}, Estimated Finish: {1}",
  "info.scheduled.0": "Scheduled; starting {0}",
  "job.execution.starting.0": "Starting {0}",
  "job.execution.queued": "Queued",
  "info.newexecutions.since.0":
    "1 New Result. Click to load. | {0} New Results. Click to load.",
  "In the last Day": "In the last Day",
  Referenced: "Referenced",
  "job.has.been.deleted.0": "(Job {0} has been deleted)",
  Filters: "Filters",
  "filter.delete.named.text": 'Delete Filter "{0}"...',
  "Delete Saved Filter": "Delete Saved Filter",
  "filter.delete.confirm.text":
    'Are you sure you want to delete the Saved Filter named "{0}"?',
  "filter.save.name.prompt": "Name:",
  "filter.save.validation.name.blank": "Name Cannot be blank",
  "filter.save.button": "Save Filter...",
  failed: "failed",
  ok: "ok",
  "0.total": "{0} total",

  period: {
    label: {
      All: "any time",
      Hour: "in the last Hour",
      Day: "in the last Day",
      Week: "in the last Week",
      Month: "in the last Month",
    },
  },
  "empty.message.default": "None configured. Click {0} to add a new plugin.",
  CreateAcl: "Create ACL",
  CreateAclName: "ACL Description",
  CreateAclTitle: "Create Key Storage ACL for the project",
  "Edit Nodes": "Edit Nodes",
  Modify: "Modify",
  "Edit Node Sources": "Edit Node Sources",
  "The Node Source had an error": "The Node Source had an error",
  "Validation errors": "Validation errors",

  "unauthorized.status.help.1":
    'Some Node Source returned an "Unauthorized" message.',
  "unauthorized.status.help.2":
    "The Node Source plugin might need access to the Key Storage Resource. it could be enabled by Access Control Policy entries.",
  "unauthorized.status.help.3":
    'Please be sure that the ACL policies enable "read" access to the Key Storage in this project for the project URN path (urn:project:name). ',
  "unauthorized.status.help.4": "Go to {0} to create a Project ACL ",
  "unauthorized.status.help.5": "Go to {0} to create a System ACL ",

  "acl.config.link.title": "Project Settings > Access Control",
  "acl.config.system.link.title": "System Settings > Access Control",
  "acl.example.summary": "Example ACL Policy",

  "page.keyStorage.description":
    "Key Storage provides a global directory-like structure to save Public and Private Keys and Passwords, for use with Node Execution authentication.",

  Duplicate: "duplicate",
  "Node.count.vue": "Node | Nodes",
  "bulk.delete": "\u4e00\u62ec\u524a\u9664",
  "select.none": "\u9078\u629e\u89e3\u9664",
  "select.all": "\u3059\u3079\u3066\u9078\u629e",
  "cancel.bulk.delete": "Cancel Bulk Delete",
  "delete.selected.executions": "Delete Selected Executions",
  "click.to.refresh":
    "\u30af\u30ea\u30c3\u30af\u3057\u3066\u30ea\u30d5\u30ec\u30c3\u30b7\u30e5",
  "count.nodes.matched": "{0} {1} Matched",
  "count.nodes.shown": "{0} nodes shown.",
  "delete.this.filter.confirm": "Really delete this filter?",
  "enter.a.node.filter":
    "\u30ce\u30fc\u30c9\u30d5\u30a3\u30eb\u30bf\u3092\u5165\u529b\u3059\u308b\u304b\u3001.*\u3067\u3059\u3079\u3066\u306e\u30ce\u30fc\u30c9\u3092\u9078\u629e",
  "execute.locally": "\u30ed\u30fc\u30ab\u30eb\u3067\u5b9f\u884c",
  "execution.page.show.tab.Nodes.title": "Nodes",
  "execution.show.mode.Log.title": "\u30ed\u30b0\u51fa\u529b",
  filter: "Filter\\:",
  "loading.matched.nodes": "Loading matched nodes...",
  "loading.text": "\u30ed\u30fc\u30c9\u4e2d...",
  "loglevel.debug": "Debug",
  "loglevel.normal": "Normal",
  "matched.nodes.prompt": "\u30de\u30c3\u30c1\u3057\u305f\u30ce\u30fc\u30c9",
  no: "\u3044\u3044\u3048",
  "node.access.not-runnable.message":
    "\u3053\u306e\u30ce\u30fc\u30c9\u306e\u30b3\u30de\u30f3\u30c9\u5b9f\u884c\u3078\u306e\u30a2\u30af\u30bb\u30b9\u3092\u6301\u3063\u3066\u3044\u307e\u305b\u3093\u3002",
  "node.filter": "\u30ce\u30fc\u30c9\u30d5\u30a3\u30eb\u30bf",
  "node.filter.exclude": "Exclude Filter",
  "node.metadata.os": "Operating System",
  "node.metadata.status": "Status",
  nodes: "\u30ce\u30fc\u30c9\\:",
  "notification.event.onfailure": "\u5931\u6557\u6642",
  "notification.event.onsuccess": "\u6210\u529f\u6642",
  "notification.event.onstart": "\u958b\u59cb\u6642",
  "notification.event.onavgduration": "Average Duration Exceeded",
  "notification.event.onretryablefailure": "On Retryable Failure",
  refresh: "\u66f4\u65b0",
  "save.filter.ellipsis": "Save Filter\u2026",
  "search.ellipsis": "Search\u2026",
  "ScheduledExecution.page.edit.title": "\u30b8\u30e7\u30d6\u306e\u7de8\u96c6",
  "ScheduledExecution.page.create.title":
    "\u65b0\u3057\u3044\u30b8\u30e7\u30d6\u3092\u4f5c\u6210",
  "scheduledExecution.property.defaultTab.label": "Default Tab",
  "scheduledExecution.property.defaultTab.description": "Default Tab",
  "scheduledExecution.property.excludeFilterUncheck.label":
    "Show Excluded Nodes",
  "scheduledExecution.property.excludeFilterUncheck.description":
    "If true, the excluded nodes will be indicated when running the Job. Otherwise they will not be shown at all.",
  "scheduledExecution.property.logOutputThreshold.label":
    "\u30ed\u30b0\u51fa\u529b\u306e\u4e0a\u9650",
  "scheduledExecution.property.logOutputThreshold.description":
    'Enter either maximum total line-count (e.g. "100"), maximum per-node line-count ("100/node"), or maximum log file size ' +
    '("100MB", "100KB", etc.), using "GB","MB","KB","B" as Giga- Mega- Kilo- and bytes.',
  "scheduledExecution.property.logOutputThreshold.placeholder":
    "E.g as '100', '100/node' or '100MB'",
  "scheduledExecution.property.logOutputThresholdAction.label":
    "Log Limit Action",
  "scheduledExecution.property.logOutputThresholdAction.description":
    "Action to perform if the output limit is reached.",
  "scheduledExecution.property.logOutputThresholdAction.halt.label":
    "Halt with status:",
  "scheduledExecution.property.logOutputThresholdAction.truncate.label":
    "Truncate and continue",
  "scheduledExecution.property.logOutputThresholdStatus.placeholder":
    "'failed','aborted', or any string",
  "scheduledExecution.property.loglevel.help":
    "Debug\u30ec\u30d9\u30eb\u306e\u5834\u5408\u3001\u3088\u308a\u591a\u304f\u51fa\u529b\u3055\u308c\u308b",
  "scheduledExecution.property.maxMultipleExecutions.label":
    "Limit Multiple Executions?",
  "scheduledExecution.property.maxMultipleExecutions.description":
    "Max number of multiple executions. Use blank or 0 to indicate no limit.",
  "scheduledExecution.property.multipleExecutions.description":
    "\u3053\u306e\u30b8\u30e7\u30d6\u3092\u4e00\u5ea6\u306b\u8907\u6570\u5b9f\u884c\u3059\u308b\u304b",
  "scheduledExecution.property.nodeKeepgoing.prompt":
    "\u30ce\u30fc\u30c9\u304c\u5931\u6557\u3057\u305f\u3089",
  "scheduledExecution.property.nodeKeepgoing.true.description":
    "\u30b9\u30c6\u30c3\u30d7\u3092\u5931\u6557\u306b\u3059\u308b\u524d\u306b\u6b8b\u308a\u306e\u30ce\u30fc\u30c9\u306e\u5b9f\u884c\u3092\u7d9a\u3051\u308b",
  "scheduledExecution.property.nodeKeepgoing.false.description":
    "\u6b8b\u308a\u306e\u30ce\u30fc\u30c9\u306e\u5b9f\u884c\u3092\u305b\u305a\u306b\u30b9\u30c6\u30c3\u30d7\u3092\u5931\u6557\u3059\u308b",
  "scheduledExecution.property.nodeRankAttribute.label":
    "\u30e9\u30f3\u30af\u5c5e\u6027",
  "scheduledExecution.property.nodeRankAttribute.description":
    "\u9806\u5e8f\u306e\u305f\u3081\u306e\u30ce\u30fc\u30c9\u306e\u5c5e\u6027\u3002\u30c7\u30d5\u30a9\u30eb\u30c8\u306f\u30ce\u30fc\u30c9\u306e\u540d\u524d",
  "scheduledExecution.property.nodeRankOrder.label":
    "\u30e9\u30f3\u30af\u30aa\u30fc\u30c0\u30fc",
  "scheduledExecution.property.nodeRankOrder.ascending.label": "\u6607\u9806",
  "scheduledExecution.property.nodeRankOrder.descending.label": "\u964d\u9806",
  "scheduledExecution.property.nodeThreadcount.label":
    "\u30b9\u30ec\u30c3\u30c9\u6570",
  "scheduledExecution.property.nodeThreadcount.description":
    "\u5229\u7528\u3059\u308b\u4e26\u5217\u30b9\u30ec\u30c3\u30c9\u306e\u6700\u5927\u6570(\u30c7\u30d5\u30a9\u30eb\u30c8: 1)",
  "scheduledExecution.property.nodefiltereditable.label": "Editable filter",
  "scheduledExecution.property.nodesSelectedByDefault.label":
    "\u30ce\u30fc\u30c9\u306e\u9078\u629e",
  "scheduledExecution.property.nodesSelectedByDefault.true.description":
    "\u30bf\u30fc\u30b2\u30c3\u30c8\u30ce\u30fc\u30c9\u306f\u30c7\u30d5\u30a9\u30eb\u30c8\u3067\u9078\u629e\u3055\u308c\u3066\u3044\u308b",
  "scheduledExecution.property.nodesSelectedByDefault.false.description":
    "\u660e\u793a\u7684\u306b\u30ce\u30fc\u30c9\u3092\u9078\u629e\u3059\u308b\u5fc5\u8981\u304c\u3042\u308a\u307e\u3059",
  "scheduledExecution.property.notifyAvgDurationThreshold.label": "Threshold",
  "scheduledExecution.property.notifyAvgDurationThreshold.description":
    "Optional duration threshold to trigger the notifications. If not specified, the Job Average duration will be used.\n\n" +
    "- percentage of average: `20%`\n" +
    "- time delta from the average: `+20s`, `+20`\n" +
    "- absolute time: `30s`, `5m`\n" +
    "Use `s`,`m`,`h`,`d`,`w`,`y` etc as time units for seconds, minutes, hours, etc.\n" +
    "Unit will be seconds if it is not specified.\n\n" +
    "Can include option value references like `{'$'}{'{'}option{'.'}avgDurationThreshold{'}'}`.",
  "scheduledExecution.property.orchestrator.label":
    "\u30aa\u30fc\u30b1\u30b9\u30c8\u30ec\u30fc\u30bf",
  "scheduledExecution.property.orchestrator.description":
    "\u9806\u5e8f\u3092\u5236\u5fa1\u306b\u4f7f\u3046\u3002This can be used to control the order and timing in which nodes are processed",
  "scheduledExecution.property.retry.delay.description":
    "The time between the failed execution and the retry. Time in seconds, " +
    'or specify time units: "120m", "2h", "3d".  Use blank or 0 to indicate no delay. Can include option value ' +
    "references like \"{'$'}{'{'}option{'.'}delay{'}'}\".",
  "scheduledExecution.property.retry.description":
    "\u30b8\u30e7\u30d6\u304c\u76f4\u63a5\u5b9f\u884c\u3055\u308c\u305f\u3068\u304d\u306e\u6700\u5927\u30ea\u30c8\u30e9\u30a4\u5b9f\u884c\u56de\u6570\u3002\u30ea\u30c8\u30e9\u30a4\u306f\u30b8\u30e7\u30d6\u304c\u5931\u6557\u3057\u305f\u3068\u304d\u3001\u307e\u305f\u306f\u30bf\u30a4\u30e0\u30a2\u30a6\u30c8\u306b\u306a\u3063\u305f\u3068\u304d\u306b\u5b9f\u884c\u3055\u308c\u308b\u3002\u624b\u52d5\u3067Kill\u3055\u308c\u305f\u5834\u5408\u306f\u3001\u30ea\u30c8\u30e9\u30a4\u3055\u308c\u306a\u3044\u3002\"{'$'}{'{'}option{'.'}retry{'}'}\"\u306e\u3088\u3046\u306a\u66f8\u5f0f\u3067\u30aa\u30d7\u30b7\u30e7\u30f3\u5024\u3092\u53c2\u7167\u3067\u304d\u308b\u3002",
  "scheduledExecution.property.successOnEmptyNodeFilter.prompt":
    "If node set empty",
  "scheduledExecution.property.successOnEmptyNodeFilter.false.description":
    "Fail the job.",
  "scheduledExecution.property.successOnEmptyNodeFilter.true.description":
    "Continue execution.",
  "scheduledExecution.property.timeout.description":
    "\u6700\u5927\u5b9f\u884c\u6642\u9593\u3002\u79d2\u3067\u8a2d\u5b9a\u3059\u308b\u3002\u307e\u305f\u306f\u3001\u6b21\u306e\u3088\u3046\u306a\u5358\u4f4d\u3067\u5165\u690d\u3059\u308b:\"120m\", \"2h\", \"3d\"\u3002\u7a7a\u307e\u305f\u306f0\u306e\u5834\u5408\u306f\u3001\u30bf\u30a4\u30e0\u30a2\u30a6\u30c8\u306a\u3057\u3002\"{'$'}{'{'}option{'.'}timeout{'}'}\"\u306e\u3088\u3046\u306a\u66f8\u5f0f\u3067\u30aa\u30d7\u30b7\u30e7\u30f3\u5024\u3092\u53c2\u7167\u3067\u304d\u308b\u3002",
  "scheduledExecution.property.scheduleEnabled.description":
    "\u3053\u306e\u30b8\u30e7\u30d6\u306e\u30b9\u30b1\u30b8\u30e5\u30fc\u30eb\u5316\u3092\u8a31\u53ef\u3059\u308b\u304b",
  "scheduledExecution.property.scheduleEnabled.label":
    "\u30b9\u30b1\u30b8\u30e5\u30fc\u30eb\u3092\u6709\u52b9\u306b\u3059\u308b",
  "scheduledExecution.property.executionEnabled.description":
    "\u3053\u306e\u30b8\u30e7\u30d6\u306e\u5b9f\u884c\u3092\u8a31\u53ef\u3059\u308b\u304b",
  "scheduledExecution.property.executionEnabled.label":
    "\u5b9f\u884c\u3092\u8a31\u53ef\u3059\u308b",
  "scheduledExecution.property.timezone.prompt": "Time Zone",
  "scheduledExecution.property.timezone.description":
    'A valid Time Zone, either an abbreviation such as "PST", a full name such as "America/Los_Angeles",or a custom ID such as "GMT-8{\':\'}00".',
  "documentation.reference.cron.url":
    "https{':'}//www.quartz-scheduler.org/documentation/quartz-2.3.0/tutorials/crontrigger.html",
  "set.as.default.filter": "Set as Default Filter",
  "show.all.nodes": "Show all nodes",
  yes: "\u306f\u3044",
  // job query field labels
  "jobquery.title.titleFilter":
    "\u30a2\u30c9\u30db\u30c3\u30af\u30b3\u30de\u30f3\u30c9",
  "jobquery.title.contextFilter": "\u30b3\u30f3\u30c6\u30ad\u30b9\u30c8",
  "jobquery.title.actionFilter": "\u30a2\u30af\u30b7\u30e7\u30f3",
  "jobquery.title.maprefUriFilter": "\u30ea\u30bd\u30fc\u30b9URI",
  "jobquery.title.reportIdFilter": "\u540d\u524d",
  "jobquery.title.tagsFilter": "\u30bf\u30b0",
  "jobquery.title.nodeFilter": "\u30ce\u30fc\u30c9",
  "jobquery.title.nodeFilter.plural": "\u30ce\u30fc\u30c9",
  "jobquery.title.messageFilter": "\u30e1\u30c3\u30bb\u30fc\u30b8",
  "jobquery.title.reportKindFilter":
    "\u30ec\u30dd\u30fc\u30c8\u30bf\u30a4\u30d7",
  "jobquery.title.recentFilter": "Within",
  "jobquery.title.actionTypeFilter": "\u30a2\u30af\u30b7\u30e7\u30f3",
  "jobquery.title.itemTypeFilter": "\u30a2\u30a4\u30c6\u30e0\u30bf\u30a4\u30d7",
  "jobquery.title.filter": "Filter",
  "jobquery.title.jobFilter": "\u30b8\u30e7\u30d6\u540d",
  "jobquery.title.idlist": "\u30b8\u30e7\u30d6ID",
  "jobquery.title.jobIdFilter": "\u30b8\u30e7\u30d6ID",
  "jobquery.title.descFilter": "\u30b8\u30e7\u30d6\u306e\u6982\u8981",
  "jobquery.title.objFilter": "\u30ea\u30bd\u30fc\u30b9",
  "jobquery.title.scheduledFilter": "Scheduled",
  "jobquery.title.serverNodeUUIDFilter": "Server Node UUID",
  "jobquery.title.typeFilter": "\u30bf\u30a4\u30d7",
  "jobquery.title.cmdFilter": "\u30b3\u30de\u30f3\u30c9",
  "jobquery.title.userFilter": "\u30e6\u30fc\u30b6\u30fc",
  "jobquery.title.projFilter": "\u30d7\u30ed\u30b8\u30a7\u30af\u30c8",
  "jobquery.title.statFilter": "\u7d50\u679c",
  "jobquery.title.startFilter": "\u958b\u59cb\u6642\u9593",
  "jobquery.title.startbeforeFilter": "\u4ee5\u524d\u306b\u958b\u59cb",
  "jobquery.title.startafterFilter": "\u4ee5\u964d\u306b\u958b\u59cb",
  "jobquery.title.endbeforeFilter": "\u4ee5\u524d\u306b\u7d42\u4e86",
  "jobquery.title.endafterFilter": "\u4ee5\u964d\u306b\u7d42\u4e86",
  "jobquery.title.endFilter": "\u6642\u9593",
  "jobquery.title.durationFilter": "\u671f\u9593",
  "jobquery.title.outFilter": "\u51fa\u529b",
  "jobquery.title.objinfFilter": "\u30ea\u30bd\u30fc\u30b9\u60c5\u5831",
  "jobquery.title.cmdinfFilter": "\u30b3\u30de\u30f3\u30c9\u60c5\u5831",
  "jobquery.title.groupPath": "\u30b0\u30eb\u30fc\u30d7",
  "jobquery.title.summary": "\u30b5\u30de\u30ea",
  "jobquery.title.duration": "\u671f\u9593",
  "jobquery.title.loglevelFilter": "\u30ed\u30b0\u30ec\u30d9\u30eb",
  "jobquery.title.loglevelFilter.label.DEBUG": "Debug",
  "jobquery.title.loglevelFilter.label.VERBOSE": "Verbose",
  "jobquery.title.loglevelFilter.label.INFO": "Information",
  "jobquery.title.loglevelFilter.label.WARN": "Warning",
  "jobquery.title.loglevelFilter.label.ERR": "Error",
  "jobquery.title.adhocExecutionFilter":
    "\u30b8\u30e7\u30d6\u306e\u30bf\u30a4\u30d7",
  "jobquery.title.adhocExecutionFilter.label.true": "\u30b3\u30de\u30f3\u30c9",
  "jobquery.title.adhocExecutionFilter.label.false":
    "\u5b9a\u7fa9\u3055\u308c\u305f\u30b3\u30de\u30f3\u30c9",
  "jobquery.title.adhocLocalStringFilter":
    "\u30b9\u30af\u30ea\u30d7\u30c8\u5185\u5bb9",
  "jobquery.title.adhocRemoteStringFilter":
    "\u30b7\u30a7\u30eb\u30b3\u30de\u30f3\u30c9",
  "jobquery.title.adhocFilepathFilter":
    "\u30b9\u30af\u30ea\u30d7\u30c8\u30d5\u30a1\u30a4\u30eb\u30d1\u30b9",
  "jobquery.title.argStringFilter":
    "\u30b9\u30af\u30ea\u30d7\u30c8\u30d5\u30a1\u30a4\u30eb\u5f15\u6570",
  "page.unsaved.changes": "You have unsaved changes",
  "edit.nodes.file": "Edit Nodes File",
  "project.node.file.source.label": "Source",
  "file.display.format.label": "Format",
  "project.node.file.source.description.label": "Description",
  "project.nodes.edit.save.error.message": "Error Saving Content:",
  "project.nodes.edit.empty.description": "Note: No content was available.",
  "button.action.Cancel": "Cancel",
  "button.action.Save": "Save",
};

export default messages;
