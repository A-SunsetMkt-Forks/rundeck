const messages = {
  Edit: "Editar",
  Save: "Guardar",
  Delete: "Borrar",
  Cancel: "Cancelar",
  Revert: "Deshacer",
  jobAverageDurationPlaceholder:
    "Dejar en blanco para una duración de trabajo promedio",
  resourcesEditor: {
    "Dispatch to Nodes": "Despachar a Nodos",
    Nodes: "Nodos",
  },
  uiv: {
    modal: {
      cancel: "Cancelar",
      ok: "OK",
    },
  },
  cron: {
    section: {
      0: "Segundos",
      1: "Minutos",
      2: "Horas",
      3: "Día del Mes",
      4: "Mes",
      5: "Día de la Semana",
      6: "Año",
    },
  },
  message_communityNews: "Noticias de la Comunidad",
  message_connectionError:
    "It appears an error occured when connecting to Community News.",
  message_readMore: "Read More",
  message_refresh: "Please refresh the page or visit us at",
  message_subscribe: "Suscribir",
  message_delete: "Borrar el campo",
  message_duplicated: "El campo ya existe",
  message_select: "Seleccionar un nuevo campo",
  message_description: "Descripcion",
  message_fieldLabel: "Label",
  message_fieldKey: "Key",
  message_fieldFilter: "Filtrar campos",
  message_empty: "Puede dejarse vacio",
  message_cancel: "Cancelar",
  message_add: "Agregar",
  message_addField: "Agregar Nuevo Campo",
  message_pageUsersSummary: "Lista de usuarios de Rundeck.",
  message_pageUsersLoginLabel: "Login",
  message_pageUsersCreatedLabel: "Creado",
  message_pageUsersUpdatedLabel: "Actualizado",
  message_pageUsersLastjobLabel: "Ultima ejecucion de un trabajo",
  message_domainUserFirstNameLabel: "Nombre",
  message_domainUserLastNameLabel: "Apellido",
  message_domainUserEmailLabel: "Email",
  message_domainUserLabel: "Usuario",
  message_pageUsersTokensLabel: "N\u00BA Tokens",
  message_pageUsersTokensHelp:
    "Puedes administrar los token en la página del Perfil de Usuario.",
  message_pageUsersLoggedStatus: "Logged Status",
  message_pageUserLoggedOnly: "Logged Users Only",
  message_pageUserNotSet: "No Establecido",
  message_pageUserNone: "Ninguno",
  message_pageFilterLogin: "Login",
  message_pageFilterHostName: "Host Name",
  message_pageFilterSessionID: "Session ID",
  message_pageFilterBtnSearch: "Buscar",
  message_pageUsersSessionIDLabel: "Session ID",
  message_pageUsersHostNameLabel: "Host Name",
  message_pageUsersLastLoginInTimeLabel: "Event Time",
  message_pageUsersTotalFounds: "Total De Usuarios Encontrados",
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
  message_hello: "Hola!",
  message_updateAvailable: "Update Available",
  message_sidebarNotificationText: "Actualización de Rundeck disponible",
  message_updateHasBeenReleased: "An update to Rundeck has been released.",
  message_installedVersion: "The installed version of Rundeck is",
  message_currentVersion: "The most recent release of Rundeck is",
  message_getUpdate: "Get Update",
  message_dismissMessage:
    "To dismiss this notification until the next release, please click here.",
  message_close: "Cerrar",
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
  CreateAcl: "Crear ACL",
  CreateAclName: "Descripción de la ACL",
  CreateAclTitle: "Crear Key Storage ACL para el proyecto",
  "Edit Nodes": "Editar Nodos",
  Modify: "Modificar",
  "Edit Node Sources": "Editar Configuracion de Nodes",
  "The Node Source had an error": "La configuracion tiene errores",
  "Validation errors": "Validación de errores",

  "unauthorized.status.help.1":
    'Un plugin Node Source retornó un "Unauthorized" mensaje .',
  "unauthorized.status.help.2":
    "El plugin Node Source podria necesitar acceso al Key Storage. Se podria habilitar creando una nueva ACL Policy, or alguna ACL existente necesita ser modificada.",
  "unauthorized.status.help.3":
    'Por favor asegurate de que la ACL Policy tiene access de lectura ("read") para el Key Storage en este proyecto ( urn:project:name). ',
  "unauthorized.status.help.4":
    "Ir a {0} para crear una ACL Policy al nivel de proyecto. ",
  "unauthorized.status.help.5":
    "Ir a {0} para crear una ACL Policy a nivel del sistema. ",

  "acl.config.link.title": "Project Settings > Access Control",
  "acl.config.system.link.title": "System Settings > Access Control",

  "acl.example.summary": "Ejemplo de ACL Policy",

  "page.keyStorage.description":
    "Key Storage provides a global directory-like structure to save Public and Private Keys and Passwords, for use with Node Execution authentication.",

  Duplicate: "duplicar",
  "bulk.delete": "Borrar en lote",
  "select.none": "Seleccionar ninguno",
  "select.all": "Seleccionar todos",
  "cancel.bulk.delete": "Cancelar Elimación a Granel",
  "delete.selected.executions": "Eliminar Ejecuciones seleccionadas",
  "click.to.refresh": "Hacer clic para actualizar",
  "count.nodes.matched": "{0} {1} Matched",
  "count.nodes.shown": "{0} nodes shown.",
  "delete.this.filter.confirm": "¿Seguro que quieres borrar este filtro?",
  "enter.a.node.filter": "Introduzca un filtro de nodo",
  "execute.locally": "Ejecutar localmente",
  "execution.page.show.tab.Nodes.title": "Nodes",
  "execution.show.mode.Log.title": "Salida de registo",
  filter: "Filter:",
  "loading.matched.nodes": "Cargando nodos emparejados...",
  "loading.text": "Cargando...",
  "loglevel.debug": "Depurar",
  "loglevel.normal": "Normal",
  "matched.nodes.prompt": "Nodos coincidentes",
  no: "no",
  "node.access.not-runnable.message":
    "Usted no tiene acceso para ejecutar comandos en este nodo.",
  "Node.count.vue": "Nodo | Nodos",
  "node.filter": "Filtro de Nodo",
  "node.filter.exclude": "Excluir filtro",
  "node.metadata.os": "Sistema Operativo",
  "node.metadata.status": "Estado",
  nodes: "Nodos",
  "notification.event.onfailure": "Por Fracaso",
  "notification.event.onsuccess": "Por Éxito",
  "notification.event.onstart": "Al Inicio",
  "notification.event.onavgduration": "Cuando excede la duración promedio",
  "notification.event.onretryablefailure": "Por Fracaso Reintentable",
  refresh: "refrescar",
  "save.filter.ellipsis": "Guardar Filtro\u2026",
  "search.ellipsis": "Search\u2026",
  "ScheduledExecution.page.edit.title": "Editar Trabajo",
  "ScheduledExecution.page.create.title": "Crear Nuevo Trabajo",
  "scheduledExecution.property.defaultTab.label": "Pestaña por defecto",
  "scheduledExecution.property.defaultTab.description":
    "Pestaña por defecto para ser mostrada cuando sigues una ejecución.",
  "scheduledExecution.property.excludeFilterUncheck.label":
    "Mostrar nodos excluidos",
  "scheduledExecution.property.excludeFilterUncheck.description":
    "Al ser seleccionado, los nodos excluidos serán mostrados al correr el Trabajo. De lo contrario no saldrá información alguna.",
  "scheduledExecution.property.logOutputThreshold.label":
    "Límite de Registro de Salida",
  "scheduledExecution.property.logOutputThreshold.description":
    'Ingrese el número máximo de líneas (e.g. "100"), máximo número de linea por nodo ("100/nodo"), o tamaño máximo de archivo de registro "100MB", "100KB", etc.), usando "GB","MB","KB","B" como Giga- Mega- Kilo- y bytes.',
  "scheduledExecution.property.logOutputThreshold.placeholder":
    "Ejemplo como '100', '100/nodo' o '100MB",
  "scheduledExecution.property.logOutputThresholdAction.description":
    "Acción a realizar si se alcanza el límite de salida.",
  "scheduledExecution.property.logOutputThresholdAction.halt.label":
    "Detener con el estado:",
  "scheduledExecution.property.logOutputThresholdAction.label":
    "Acción de Límite de Registro",
  "scheduledExecution.property.logOutputThresholdAction.truncate.label":
    "Truncar y continuar",
  "scheduledExecution.property.logOutputThresholdStatus.placeholder":
    '"fallido", "abortado" o cualquier cadena',
  "scheduledExecution.property.loglevel.help":
    "Depurar los niveles produce más registros",
  "scheduledExecution.property.maxMultipleExecutions.label":
    "¿Límite para las múltiples ejecuciones?",
  "scheduledExecution.property.maxMultipleExecutions.description":
    "Número máximo de ejecuciones múltiples. Dejar en blanco o 0 para no establecer un límite.",
  "scheduledExecution.property.multipleExecutions.description":
    "¿Permitir que este trabajo se ejecute más de una vez al mismo tiempo?",
  "scheduledExecution.property.nodeKeepgoing.false.description":
    "Fallar el paso sin que se ejecutan los nodos restantes.",
  "scheduledExecution.property.nodeKeepgoing.prompt": "Si un nodo falla",
  "scheduledExecution.property.nodeKeepgoing.true.description":
    "Continuar ejecutando en los nodos restantes antes de fallar el paso.",
  "scheduledExecution.property.nodeRankAttribute.description":
    "Atributo de nodo para ordenar. El valor predeterminado es el nombre del nodo.",
  "scheduledExecution.property.nodeRankAttribute.label": "Rango de Atributo",
  "scheduledExecution.property.nodeRankOrder.ascending.label": "Ascendiente",
  "scheduledExecution.property.nodeRankOrder.descending.label": "Descendiente",
  "scheduledExecution.property.nodeRankOrder.label": "Rango de Orden",
  "scheduledExecution.property.nodeThreadcount.label": "Número de Subprocesos",
  "scheduledExecution.property.nodeThreadcount.description":
    "El número máximo de subprocesos paralelos a usar. (Predeterminado: 1)",
  "scheduledExecution.property.nodefiltereditable.label": "Filtro Editable",
  "scheduledExecution.property.nodesSelectedByDefault.label":
    "Selección de nodo",
  "scheduledExecution.property.nodesSelectedByDefault.true.description":
    "Los nodos de destino se seleccionan de forma predeterminada",
  "scheduledExecution.property.nodesSelectedByDefault.false.description":
    "El usuario tiene que seleccionar explícitamente nodos de destino",
  "scheduledExecution.property.notifyAvgDurationThreshold.label": "Threshold",
  "scheduledExecution.property.notifyAvgDurationThreshold.description":
    "Optional duration threshold to trigger the notifications. If not specified, the Job Average duration will be used.\n\n" +
    "- percentage of average: `20%`\n" +
    "- time delta from the average: `+20s`, `+20`\n" +
    "- absolute time: `30s`, `5m`\n" +
    "Use `s`,`m`,`h`,`d`,`w`,`y` etc as time units for seconds, minutes, hours, etc.\n" +
    "Unit will be seconds if it is not specified.\n\n" +
    "Can include option value references like `{'$'}{'{'}option{'.'}avgDurationThreshold{'}'}`.",
  "scheduledExecution.property.orchestrator.label": "Orchestrator",
  "scheduledExecution.property.orchestrator.description":
    "Esto se puede utilizar para controlar el orden y el momento en que se procesan los nodos.",
  "scheduledExecution.property.retry.delay.description":
    "El tiempo entre una ejecución fallida y el reintento. Tiempo en segundos, " +
    'o especifique unidades: "120m", "2h", "3d".  Deje en blanco o 0 para indicar que no hay demora. Puede incluir valor de opción ' +
    "referencias como \"{'$'}{'{'}option{'.'}delay{'}'}\".",
  "scheduledExecution.property.retry.description":
    "Número máximo de veces para reintentar la ejecución cuando se invoque directamente este trabajo. El reintento ocurrirá si el trabajo falla o el tiempo finalice, pero no si es detenido de forma manual. Puede utilizar una referencia de valor de opción como \"{'$'}{'{'}option{'.'}retry{'}'}\".",
  "scheduledExecution.property.successOnEmptyNodeFilter.prompt":
    "Si el conjunto de nodos esta vacío",
  "scheduledExecution.property.successOnEmptyNodeFilter.true.description":
    "Continuar la ejecución.",
  "scheduledExecution.property.successOnEmptyNodeFilter.false.description":
    "Fallar el trabajo.",
  "scheduledExecution.property.timeout.description":
    "El tiempo máximo para una ejecución. Tiempo en segundos, " +
    'o especifique unidades: "120m", "2h", "3d".  Deje en blanco o 0 para indicar tiempo fuera. Puede incluir valor de opción ' +
    "referencias como \"{'$'}{'{'}option{'.'}timeout{'}'}\".",
  "scheduledExecution.property.scheduleEnabled.description":
    "¿Permitir que este trabajo se programe?",
  "scheduledExecution.property.scheduleEnabled.label":
    "¿Permitir programación?",
  "scheduledExecution.property.executionEnabled.description":
    "¿Permitir que este trabajo sea ejecutado?",
  "scheduledExecution.property.executionEnabled.label": "¿Habilitar Ejecución?",
  "scheduledExecution.property.timezone.prompt": "Zona horaria",
  "scheduledExecution.property.timezone.description":
    'Una Zona Horaria válidad, puede ser una abreviatura como "PST", o un nombre copleto como "America/Los_Angeles", o un ID personalizado como "GMT-8{\':\'}00".',
  "documentation.reference.cron.url":
    "https{':'}//www.quartz-scheduler.org/documentation/quartz-2.3.0/tutorials/crontrigger.html",
  "set.as.default.filter": "Set as Default Filter",
  "show.all.nodes": "Show all nodes",
  yes: "Sí",
  // job query field labels
  "jobquery.title.titleFilter": "Comando Adhoc",
  "jobquery.title.contextFilter": "Contexto",
  "jobquery.title.actionFilter": "Acción",
  "jobquery.title.maprefUriFilter": "Fuente URI",
  "jobquery.title.reportIdFilter": "Nombre",
  "jobquery.title.tagsFilter": "Etiquetas",
  "jobquery.title.nodeFilter": "Nodo",
  "jobquery.title.nodeFilter.plural": "Nodos",
  "jobquery.title.messageFilter": "Mensaje",
  "jobquery.title.reportKindFilter": "Tipo de Informe",
  "jobquery.title.recentFilter": "Dentro",
  "jobquery.title.actionTypeFilter": "Acción",
  "jobquery.title.itemTypeFilter": "Tipo de Ítem",
  "jobquery.title.filter": "Filtro",
  "jobquery.title.jobFilter": "Nombre del Trabajo",
  "jobquery.title.idlist": "ID del Trabajo",
  "jobquery.title.jobIdFilter": "ID del Trabajo",
  "jobquery.title.descFilter": "Descripción del Trabajo",
  "jobquery.title.scheduledFilter": "Programado",
  "jobquery.title.serverNodeUUIDFilter": "UUID del Nodo Servidor",
  "jobquery.title.objFilter": "Fuente",
  "jobquery.title.typeFilter": "Tipo",
  "jobquery.title.cmdFilter": "Comando",
  "jobquery.title.userFilter": "Usuario",
  "jobquery.title.projFilter": "Proyecto",
  "jobquery.title.statFilter": "Resultado",
  "jobquery.title.startFilter": "Hora de Inicio",
  "jobquery.title.startbeforeFilter": "Comenzar Antes",
  "jobquery.title.startafterFilter": "Comenzar Después",
  "jobquery.title.endbeforeFilter": "Terminar Antes",
  "jobquery.title.endafterFilter": "Terminar Después",
  "jobquery.title.endFilter": "Tiempo",
  "jobquery.title.durationFilter": "Duración",
  "jobquery.title.outFilter": "Salida",
  "jobquery.title.objinfFilter": "Información de Fuente",
  "jobquery.title.cmdinfFilter": "Información de Comando",
  "jobquery.title.groupPath": "Grupo",
  "jobquery.title.summary": "Resumen",
  "jobquery.title.duration": "Duración",
  "jobquery.title.loglevelFilter": "Nivel",
  "jobquery.title.loglevelFilter.label.DEBUG": "Depurar",
  "jobquery.title.loglevelFilter.label.VERBOSE": "Minuciosidad",
  "jobquery.title.loglevelFilter.label.INFO": "Información",
  "jobquery.title.loglevelFilter.label.WARN": "Advertencia",
  "jobquery.title.loglevelFilter.label.ERR": "Error",
  "jobquery.title.adhocExecutionFilter": "Tipo de Trabajo",
  "jobquery.title.adhocExecutionFilter.label.true": "Comando",
  "jobquery.title.adhocExecutionFilter.label.false": "Comando Definido",
  "jobquery.title.adhocLocalStringFilter": "Contenido del Script",
  "jobquery.title.adhocRemoteStringFilter": "Comando Shell",
  "jobquery.title.adhocFilepathFilter": "Ruta de Archivo Script",
  "jobquery.title.argStringFilter": "Argumentos del Archivo Script",
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
