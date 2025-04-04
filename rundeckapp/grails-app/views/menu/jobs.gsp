%{--
  - Copyright 2016 SimplifyOps, Inc. (http://simplifyops.com)
  -
  - Licensed under the Apache License, Version 2.0 (the "License");
  - you may not use this file except in compliance with the License.
  - You may obtain a copy of the License at
  -
  -     http://www.apache.org/licenses/LICENSE-2.0
  -
  - Unless required by applicable law or agreed to in writing, software
  - distributed under the License is distributed on an "AS IS" BASIS,
  - WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  - See the License for the specific language governing permissions and
  - limitations under the License.
  --}%

<%@ page import="org.rundeck.core.auth.AuthConstants;grails.util.Environment" %>
<html>
<head>
    <asset:javascript src="static/pages/job/head/scm-action-buttons.js" asset-defer="true" />
    <g:set var="rkey" value="${g.rkey()}" />
    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8"/>
    <meta name="layout" content="base"/>
    <meta name="tabpage" content="jobs"/>
    <meta name="skipPrototypeJs" content="true"/>
    <g:set var="projectName" value="${params.project ?: request.project}"/>
    <g:set var="projectLabel" value="${session.frameworkLabels?session.frameworkLabels[projectName]:projectName}"/>
    <g:set var="paginateJobs" value="${cfg.getString(config: "gui.paginatejobs.enabled") in [true,'true']}"/>
    <g:set var="paginateJobsPerPage" value="${cfg.getInteger(config: "gui.paginatejobs.max.per.page", default: 20)}"/>


    <title><g:message code="gui.menu.Workflows"/> - <g:enc>${projectLabel}</g:enc></title>

    <g:set var="projAdminAuth" value="${auth.resourceAllowedTest(
            context: AuthConstants.CTX_APPLICATION,
            type: AuthConstants.TYPE_PROJECT,
            name: projectName,
            action: [AuthConstants.ACTION_ADMIN, AuthConstants.ACTION_APP_ADMIN],
            any: true
    )}"/>
    <g:set var="deleteExecAuth" value="${auth.resourceAllowedTest(
            context: AuthConstants.CTX_APPLICATION,
            type: AuthConstants.TYPE_PROJECT,
            name: projectName,
            action: AuthConstants.ACTION_DELETE_EXECUTION
    ) || projAdminAuth}"/>

    <asset:javascript src="menu/jobs.js"/>
    <g:embedJSON data="${projectNames ?: []}" id="projectNamesData"/>
    <g:embedJSON data="${jobListIds ?: []}" id="nextScheduled"/>
    <g:embedJSON id="pageParams" data="${[project: params.project?:request.project,]}"/>
    <g:jsMessages code="Node,Node.plural,job.starting.execution,job.scheduling.execution,option.value.required,options.remote.dependency.missing.required,,option.default.button.title,option.default.button.text,option.select.choose.text"/>
    <g:jsMessages
        id="bulkEditMessages"
        code="scm.export.status.EXPORT_NEEDED.description,scm.export.status.CREATE_NEEDED.description,scm.export.status.CLEAN.description,scm.import.status.IMPORT_NEEDED.description,scm.import.status.DELETE_NEEDED.description,scm.import.status.CLEAN.description,scm.import.status.REFRESH_NEEDED.description,scm.import.status.UNKNOWN.description,scm.export.status.EXPORT_NEEDED.display.text,scm.export.status.CREATE_NEEDED.display.text,scm.export.status.REFRESH_NEEDED.display.text,scm.export.status.DELETED.display.text,scm.export.status.CLEAN.display.text,scm.import.status.IMPORT_NEEDED.display.text,scm.import.status.REFRESH_NEEDED.display.text,scm.import.status.UNKNOWN.display.text,scm.import.status.CLEAN.display.text,scm.export.status.LOADING.display.text,scm.import.status.LOADING.display.text,scm.export.status.LOADING.description,scm.import.status.LOADING.description"/>
    <g:jsMessages id="queryformmessages"
    code="jobquery.title.name,jobquery.title.jobFilter,jobquery.title.projFilter,jobquery.title.groupPath,jobquery.title.descFilter,jobquery.title.loglevelFilter,jobquery.title.idlist,jobquery.title.scheduledFilter,jobquery.title.serverNodeUUIDFilter"/>
    <!--[if (gt IE 8)|!(IE)]><!--> <asset:javascript src="ace-bundle.js"/><!--<![endif]-->

      <asset:stylesheet href="static/css/pages/project-dashboard.css"/>
      <g:jsMessages code="jobslist.date.format.ko,select.all,select.none,delete.selected.executions,cancel.bulk.delete,cancel,close,all,bulk.delete,running"/>
      <g:jsMessages code="search.ellipsis
jobquery.title.titleFilter
jobquery.title.jobFilter
jobquery.title.jobIdFilter
jobquery.title.userFilter
jobquery.title.statFilter
jobquery.title.filter
jobquery.title.recentFilter
jobquery.title.startbeforeFilter
jobquery.title.startafterFilter
jobquery.title.endbeforeFilter
jobquery.title.endafterFilter
saved.filters
search
"/>
    <script type="text/javascript">

        function showError(message){
             appendText('#error',message);
             jQuery("#error").show();
        }
        function showErrorModal(message){
             setText('#modalErrorContent',message);
             jQuery("#errorModal").modal('show');
        }
        var _jobExecUnloadHandlers=new Array();
        function _registerJobExecUnloadHandler(handler){
            _jobExecUnloadHandlers.push(handler);
        }
        function unloadExec(){
            if(_jobExecUnloadHandlers.length>0){
                for(var i =0;i<_jobExecUnloadHandlers.length;i++){
                    _jobExecUnloadHandlers[i].call();
                }
                _jobExecUnloadHandlers.length=0;
            }

            jQuery('#execDiv').modal('hide');
            clearHtml('execDivContent');

            jQuery('#busy').hide();
        }
        function requestError(item,message){
            unloadExec();
            showError("Failed request: "+item+". Result: "+message);
        }
        function loadExec(id,eparams) {
            jQuery("#error").hide();
            var params=eparams;
            if(!params){
                params={id:id};
            }
            jQuery('#execDivContent').load(_genUrl(appLinks.scheduledExecutionExecuteFragment, params),
                function(response,status,xhr){
                if (status == "success") {
                    loadedFormSuccess(!!id,id);
                } else {
                    requestError("executeFragment for [" + id + "]",xhr.statusText);
                }
            });
        }
        function execSubmit(elem, target) {
            var data = new FormData(jQuery('#' + elem + ' form')[0]);
            jQuery.ajax({
                url: target,
                type: 'POST',
                data: data,
                contentType: false,
                dataType: 'json',
                processData: false,
                success: function (result) {
                    if (result.id) {
                        if (result.follow && result.href) {
                            document.location = result.href;
                        } else {

                            unloadExec();
                        }
                    } else if (result.error === 'invalid') {
                        // reload form for validation
                        loadExec(null, jQuery('#' + elem + ' form').serialize() + "&dovalidate=true");
                    } else {
                        unloadExec();
                        showErrorModal("Failed to run job: "+(result.message ? result.message : result.error ? result.error : "Failed request"));
                    }
                },
                error: function (data, jqxhr, err) {
                    requestError("runJobInline", err);
                }
            });
        }
        function loadedFormSuccess(doShow,id){
            jQuery('#execDivContent .exec-options-body').addClass('modal-body')
            jQuery('#execDivContent .exec-options-footer').addClass('modal-footer')
            if (jQuery('#execFormCancelButton').length) {
                jQuery('#execFormCancelButton').on('click',function(evt) {
                    stopEvent(evt);
                    unloadExec();
                    return false;
                });
                jQuery('#execFormCancelButton').attr('name', "_x");
            }
            if (jQuery('#execFormRunButton').length) {
                let clicked=false
                jQuery('#execFormRunButton').on('click', function(evt) {
                    stopEvent(evt);
                    if (clicked) {
                        return false;
                    }
                    clicked = true;
                    jQuery('#execOptFormRunButtons').hide()
                    jQuery('#execOptFormRunJobSpinner').css('display', 'flex')
                    execSubmit('execDivContent', appLinks.scheduledExecutionRunJobInline);
                    return false;
                });
            }
            jQuery('#showScheduler').on('shown.bs.popover', function() {
                if (jQuery('#scheduleAjaxButton').length) {
                    jQuery('#scheduleAjaxButton').on( 'click', function(evt) {
                        stopEvent(evt);
                        if (isValidDate()) {
                            toggleAlert(true);
		                    execSubmit('execDivContent',
                                appLinks.scheduledExecutionScheduleJobInline);
		                    //$('formbuttons').loading(message('job.scheduling.execution'));
                        } else {
                            toggleAlert(false);
                        }
                        return false;
                    });
                }
            });

            //setup option handling
            //setup option edit
            var joboptiondata = loadJsonData('jobOptionData');
            var joboptions = new JobOptions(joboptiondata);

            if (document.getElementById('optionSelect')) {
                // ko.applyBindings(joboptions, document.getElementById('optionSelect'));
            }

            var remoteoptionloader = new RemoteOptionLoader({
                url: "${createLink(controller:'scheduledExecution',action:'loadRemoteOptionValues',params:[format:'json'])}",
                id:id,
                fieldPrefix: "extra.option."
            });
            var remotecontroller = new RemoteOptionController({
                loader: remoteoptionloader,
            });
            remotecontroller.setupOptions(joboptions);

            remotecontroller.loadData(loadJsonData('remoteOptionData'));
            if (typeof(_registerJobExecUnloadHandler) == 'function') {
                _registerJobExecUnloadHandler(remotecontroller.unsubscribeAll);
            }
            joboptions.remoteoptions = remotecontroller;
            remotecontroller.begin();

            jQuery('input').on('keydown', function (evt) {
                return noenter(evt);
            });
            initKoBind('#execDiv',{joboptions:joboptions},/*'menu/jobs'*/)
            if(doShow){
                jQuery('#execDiv').modal('show');
            }
            jQuery('#busy').hide();
        }




        //set box filterselections



        /** now running section update */
        function _pageUpdateNowRunning(count){
        }
        var lastRunExec=0;
        /**
         * Handle embedded content updates
         */
        function _updateBoxInfo(name,data){
            if(name==='events' && data.lastDate){
                histControl.setHiliteSince(data.lastDate);
            }
            if (name == 'nowrunning' && data.lastExecId && data.lastExecId != lastRunExec) {
                lastRunExec = data.lastExecId;
            }
        }

        function initJobActionMenus(){
            jQuery('.act_job_action_dropdown').on('click',function(){
                var id=jQuery(this).data('jobId');
                var el=jQuery(this).parent().find('.dropdown-menu');
                el.load(
                    _genUrl(appLinks.scheduledExecutionActionMenuFragment,{id:id})
                );
            });
        }

        function init(){
            initJobActionMenus();

            PageActionHandlers.registerHandler('job_delete_single',function(el){
                bulkeditor.activateActionForJob(bulkeditor.DELETE,el.data('jobId'));
            });
            PageActionHandlers.registerHandler('enable_job_execution_single',function(el){
                bulkeditor.activateActionForJob(bulkeditor.ENABLE_EXECUTION,el.data('jobId'));
            });
            PageActionHandlers.registerHandler('disable_job_execution_single',function(el){
                bulkeditor.activateActionForJob(bulkeditor.DISABLE_EXECUTION,el.data('jobId'));
            });
            PageActionHandlers.registerHandler('disable_job_schedule_single',function(el){
                bulkeditor.activateActionForJob(bulkeditor.DISABLE_SCHEDULE,el.data('jobId'));
            });
            PageActionHandlers.registerHandler('enable_job_schedule_single',function(el){
                bulkeditor.activateActionForJob(bulkeditor.ENABLE_SCHEDULE,el.data('jobId'));
            });

            PageActionHandlers.registerHandler('copy_other_project',function(el){
                jQuery('#jobid').val(el.data('jobId'));
                jQuery('#selectProject').modal();
                jQuery.ajax({
                    dataType:'json',
                    method: 'GET',
                    url:_genUrl(appLinks.authProjectsToCreateAjax),
                    success:function(data){
                        jQuery('#jobProject').empty();
                        for (let i in data.projectNames ) {
                            jQuery('#jobProject').append(
                                '<option value="' + data.projectNames[i] + '">' + data.projectNames[i] + '</option>'
                            );
                        }
                    }
                });
            });


        }

        var bulkeditor;
        jQuery(document).ready(function () {
            init();
            jQuery(document).on('click','.act_execute_job',function(evt){
                evt.preventDefault();
               loadExec(jQuery(this).data('jobId'));
            });
            jQuery('#wffilterform input').each(function(ndx,elem){
                if(elem.type=='text'){
                    jQuery(elem).on('keypress',noenter);
                }
            });
            bulkeditor=new BulkEditor({messages:window.Messages});




            jQuery(document).on('click','#togglescm',function(evt){
                evt.preventDefault();
                jQuery.ajax({
                    dataType:'json',
                    method: "POST",
                    url:_genUrl(appLinks.togglescm),
                    params:nextScheduled,
                    success:function(data,status,xhr){
                        console.log(data);
                    }
                });
            });

            var pageParams = loadJsonData('pageParams');
            var nextScheduled = loadJsonData('nextScheduled');

            jQuery.ajax({
                dataType:'json',
                type: "POST",
                url: appLinks.scmjobs,
                data: JSON.stringify(nextScheduled),
                contentType: 'application/json; charset=utf-8',
                success:function(data,status,xhr){
                    bulkeditor.scmExportEnabled(data.scmExportEnabled);
                    bulkeditor.scmStatus(data.scmStatus);
                    bulkeditor.scmExportStatus(data.scmExportStatus);
                    bulkeditor.scmExportActions(data.scmExportActions);
                    bulkeditor.scmExportRenamed(data.scmExportRenamed);

                    bulkeditor.scmImportEnabled(data.scmImportEnabled);
                    bulkeditor.scmImportJobStatus(data.scmImportJobStatus);
                    bulkeditor.scmImportStatus(data.scmImportStatus);
                    bulkeditor.scmImportActions(data.scmImportActions);
                    if( data.warning !== undefined ){
                        showError(data.warning)
                    }

                    bulkeditor.scmDone(true);
                }
            });

            initKoBind(null,{bulkeditor:bulkeditor})
        });


    </script>

    <style type="text/css">
    .error{
        color:red;
    }

        #histcontent table{
            width:100%;
        }
        .gsp-pager .step { padding: 0 2px; }
        .gsp-pager .currentStep { padding: 0 2px; }
    </style>


      <g:javascript>

    window._rundeck = Object.assign(window._rundeck || {}, {
        data:{
            projectAdminAuth:${enc(js:projAdminAuth)},
            deleteExecAuth:${enc(js:deleteExecAuth)},
            jobslistDateFormatMoment:"${enc(js:g.message(code:'jobslist.date.format.ko'))}",
            runningDateFormatMoment:"${enc(js:g.message(code:'jobslist.running.format.ko'))}",
            activityUrl: appLinks.reportsEventsAjax,
            bulkDeleteUrl: appLinks.apiExecutionsBulkDelete,
            activityPageHref:"${enc(js:createLink(controller:'reports',action:'index',params:[project:projectName]))}",
            sinceUpdatedUrl:"${enc(js:g.createLink(controller:'reports',action: 'since.json', params: [project:projectName]))}",
            pagination:{
                max: ${enc(js:params.max?params.int('max',10):10)}
          },
          query:{
              jobIdFilter:'!null'
            },
            filterOpts: {
                showFilter: false,
                showRecentFilter: true,
                showSavedFilter: false
            },
            runningOpts: {
                loadRunning:false,
                allowAutoRefresh: false
            }
    }
})
      </g:javascript>
    <g:set var="wasfiltered" value="${paginateParams?.keySet().grep(~/(?!proj).*Filter|groupPath|customFilters|idlist$/)}"/>
    <g:embedJSON data="${paginateParams?.subMap(wasfiltered)?:[:]}" id="filterParams"/>
      <asset:javascript src="static/pages/project-activity.js" defer="defer"/>
      <asset:javascript src="static/pages/job/browse.js" defer="defer"/>
      <asset:javascript src="static/pages/nodes.js" defer="defer"/>
      <asset:stylesheet href="static/css/pages/job/browse.css" />
</head>
<body>

<div class="content">
<div id="layoutBody">
<div class="title">
        <div class="subtitle-head-item flex-container flex-align-items-baseline">
            <div class="flex-item-auto text-h3">

                <i class="fas fa-tasks"></i>

                <g:if test="${wasfiltered && wasfiltered.contains('groupPath') }">
                    <g:render template="/scheduledExecution/groupBreadcrumbs" model="[groupPath:paginateParams.groupPath,project:params.project]"/>

                </g:if>
                <a class="link-quiet" href="#">

                    <g:if test="${wasfiltered}">

                        <g:if test="${wasfiltered.contains('groupPath') && wasfiltered.size()>1 || wasfiltered.size()>0 }">

                            <span class="query-section">
                                <g:each in="${wasfiltered.sort()}" var="qparam">
                                    <g:if test="${qparam!='groupPath'}">
                                        <g:if test="${paginateParams[qparam] instanceof Map}">
                                            <g:each in="${paginateParams[qparam]}" var="customParam">
                                                <span class="text-secondary">${customParam.key}:</span>
                                                <span class="text-info">${customParam.value}</span>
                                            </g:each>
                                        </g:if>
                                        <g:else>
                                            <span class="text-secondary"><g:message code="jobquery.title.${qparam}"/>:</span>

                                            <span class="text-info">
                                                ${g.message(code:'jobquery.title.'+qparam+'.label.'+paginateParams[qparam].toString(),default:enc(html:paginateParams[qparam].toString()).toString())}
                                            </span>
                                        </g:else>

                                    </g:if>
                                </g:each>
                            </span>

                        </g:if>
                    </g:if>
                    <g:else>
                        All Jobs
                    </g:else>

                </a>

                <span
                    class="label label-secondary has_tooltip"
                    data-container="#section-content"
                    data-placement="auto bottom"
                    title="${totalauthorized} Jobs Found">
                        <g:enc>${totalauthorized}</g:enc>
                </span>

            </div>

            <div class="flex">
                <span title="Click to modify filter" class="btn btn-default btn-md query" data-toggle="modal" data-target="#jobs_filters">
                    <g:message code="advanced.search" />
                </span>
                <g:form class="search" name="form" action="jobs" params="[project:params.project, jobListType:params.jobListType]" method="POST" style="position: relative;">
                    <span><g:icon name="search"/></span>
                    <input type="search" name="jobFilter" placeholder="Search" class="form-control input-md"/>
                </g:form>
                <g:submitButton name="Search" form="form" value="Search" class="btn btn-primary"/>
            </div>

        </div>
    </div>
<g:jsonToken id="ajaxFilterTokens" />

<div id="page_jobs" class="container-fluid">
  <g:if test="${flash.bulkJobResult?.errors}">
      <div class="alert alert-warning">
          <a class="close" data-dismiss="alert" href="#" aria-hidden="true">&times;</a>
          <ul>
              <g:if test="${flash.bulkJobResult.errors instanceof org.springframework.validation.Errors}">
                  <g:renderErrors bean="${flash.bulkJobResult.errors}" as="list"/>
              </g:if>
              <g:else>
                  <g:each in="${flash.bulkJobResult.errors*.message}" var="message">
                      <li><g:autoLink>${message}</g:autoLink></li>
                  </g:each>
              </g:else>
          </ul>
      </div>
  </g:if>
  <g:if test="${flash.bulkJobResult?.success}">
      <div class="alert alert-info">
          <a class="close" data-dismiss="alert" href="#" aria-hidden="true">&times;</a>
          <ul>
          <g:each in="${flash.bulkJobResult.success*.message}" var="message">
              <li><g:autoLink>${message}</g:autoLink></li>
          </g:each>
          </ul>
      </div>
  </g:if>
  <div class="row">
    <div class="col-xs-12">

      <div class="card">
        <div class="card-content">
          <div class="runbox primary jobs " id="indexMain">
          <div id="error" class="alert alert-danger" style="display:none;"></div>
          <g:render template="workflowsFull"
                    model="${[
                            jobExpandLevel    : jobExpandLevel,
                            jobgroups         : jobgroups,
                            wasfiltered       : wasfiltered ? true : false,
                            clusterMap        : clusterMap,
                            nextExecutions    : nextExecutions,
                            jobauthorizations : jobauthorizations,
                            authMap           : authMap,
                            nowrunningtotal   : nowrunningtotal,
                            max               : max,
                            offset            : offset,
                            paginateParams    : paginateParams,
                            sortEnabled       : true,
                            rkey              : rkey,
                            clusterModeEnabled: clusterModeEnabled
                    ]}"/>
          </div>

          <g:if test="${paginateJobs && !wasfiltered}">
          <div>
            Showing ${offset+1}-${offset+max > total ? total : offset+max} of ${total}
          </div>
           <div class="gsp-pager">
            <g:paginate next="Next" prev="Previous" max="${paginateJobsPerPage}"
            controller="menu" maxsteps="10"
            action="jobs" total="${total}" params="${[max:params.max,offset:params.offset,project:params.project,jobListType:params.jobListType]}" />
            </div>
            </g:if>
        </div>

      </div>
    </div>
  </div>

  <auth:resourceAllowed project="${projectName}" action="${[AuthConstants.ACTION_READ]}" kind="${AuthConstants.TYPE_EVENT}">
  <div class="row">
    <div class="col-xs-12">
      <div class="card card-plain">
          <div class="card-header">
            <h3 class="card-title"><g:message code="page.section.Activity.for.jobs" /></h3>
          </div>
      </div>
      <div class="card"  id="activity_section" >

            <div class="card-content">

                <div  class="_history_content vue-project-activity">

                    <activity-list :event-bus="EventBus"></activity-list>

                </div>

            </div>
      </div>
    </div>
  </div>
  </auth:resourceAllowed>
</div>

<div class="modal fade" id="execDiv" role="dialog" aria-labelledby="execJobModalLabel" aria-hidden="true">
  <div class="modal-dialog modal-lg">
    <div class="modal-content">
      <div class="modal-header">
        <button type="button" class="close" data-dismiss="modal" aria-hidden="true">&times;</button>
        <h4 class="modal-title" id="execJobModalLabel"><g:message code="job.execute.action.button" /></h4>
      </div>
      <div class="" id="execDivContent"></div>
    </div>
  </div>
</div>
<div class="modal fade" id="errorModal" role="dialog" aria-labelledby="execErrModalLabel" aria-hidden="true">
  <div class="modal-dialog modal-lg">
    <div class="modal-content">
      <div class="modal-header">
        <button type="button" class="close" data-dismiss="modal" aria-hidden="true">&times;</button>
        <h4 class="modal-title" id="execErrModalLabel"><g:message code="request.error.title" /></h4>
      </div>
        <div class="modal-body">
            <div class="alert alert-danger" id="modalErrorContent"></div>
        </div>
        <div class="modal-footer">
            <button type="button" class="btn btn-default" data-dismiss="modal"><g:message code="ok" default="OK"/></button>
        </div>
    </div>
  </div>
</div>
</div>
</div>
<g:render template="/menu/copyModal" model="[projectNames: projectNames]"/>

</body>
</html>
