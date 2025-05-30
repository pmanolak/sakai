<%@ page import="java.util.*, javax.faces.context.*, javax.faces.application.*,
                 javax.faces.el.*, org.sakaiproject.tool.messageforums.*"%>
<%@ taglib uri="http://java.sun.com/jsf/html" prefix="h" %>
<%@ taglib uri="http://java.sun.com/jsf/core" prefix="f" %>
<%@ taglib uri="http://sakaiproject.org/jsf2/sakai" prefix="sakai" %>
<%@ taglib uri="http://sakaiproject.org/jsf/messageforums" prefix="mf" %>
<jsp:useBean id="msgs" class="org.sakaiproject.util.ResourceLoader" scope="session">
   <jsp:setProperty name="msgs" property="baseName" value="org.sakaiproject.api.app.messagecenter.bundle.Messages"/>
</jsp:useBean>
<f:view>
	<sakai:view title="#{msgs.cdfm_discussion_forum_settings}" toolCssHref="/messageforums-tool/css/msgcntr.css">
	<script>includeLatestJQuery("msgcntr");</script>
	<script>includeWebjarLibrary("momentjs");</script>
	<script src="/messageforums-tool/js/jquery.charcounter.js"> </script>
	<script src="/messageforums-tool/js/sak-10625.js"></script>
	<script src="/messageforums-tool/js/forum.js"></script>
	<script src="/messageforums-tool/js/messages.js"></script>
	<script src="/messageforums-tool/js/permissions_header.js"></script>
	<script src="/library/js/lang-datepicker/lang-datepicker.js"></script>
	<script type="module" src="/vuecomponents/js/sakai.min.js<h:outputText value="#{ForumTool.CDNQuery}" />"></script>
	<script type="module" src="/webcomponents/bundles/rubric-association-requirements.js<h:outputText value="#{ForumTool.CDNQuery}" />"></script>
	<link href="/library/webjars/jquery-ui/1.12.1/jquery-ui.min.css" rel="stylesheet" type="text/css" />
	<%
	  	String thisId = request.getParameter("panel");
  		if (thisId == null) 
  		{
    		thisId = "Main" + org.sakaiproject.tool.cover.ToolManager.getCurrentPlacement().getId();
  		}
	%>
	<script>
		function resize(){
  			mySetMainFrameHeight('<%= org.sakaiproject.util.Web.escapeJavascript(thisId)%>');
  		}
	</script> 
	<script>
		var isGradebookGroupEnabled = <h:outputText value="#{ForumTool.gradebookGroupEnabled}"/>;
	
	$(document).ready(function() {
		var forumGradingExists = document.getElementById("revise:forum_grading") !== null;
		if (isGradebookGroupEnabled && forumGradingExists) {
			window.syncGbSelectorInput("gb-selector", "revise:group_view:forum_assignments");
		}

		const radioButtonRestrictedAvailability = document.getElementById('revise:availabilityRestricted:1');
		if (radioButtonRestrictedAvailability.checked && $(".calWidget")[0].style['display'] === 'none') {
			setDatesEnabled(radioButtonRestrictedAvailability);
		}
	});

	function setDatesEnabled(radioButton){
		$(".calWidget, .lockForumAfterCloseDateSpan").fadeToggle('slow');
	}

	function updateGradeAssignment(){
	const forumAssignments = isGradebookGroupEnabled
			? document.getElementById("revise:group_view:forum_assignments")
			: document.getElementById("revise:non_group_view:forum_assignments");
    if (forumAssignments?.value === undefined || forumAssignments.value === "Default_0") return;

    const elems = document.getElementsByTagName('sakai-rubric-association');
    const createTaskGroup = document.getElementById("revise:createTaskGroup");
    const isTasksWidgetAvailable = !!document.querySelector("sakai-tasks");

    if (isTasksWidgetAvailable) {
			for (var i = 0; i < elems.length; i++) {
				elems[i].setAttribute("entity-id", forumAssignments.value);
				elems[i].style.display = 'inline';
			}
			createTaskGroup.style.display = 'inline';
    } else {
			for (var i = 0; i < elems.length; i++) {
				elems[i].style.display = 'none';
			}
			createTaskGroup.style.display = 'none';
    }
	}

	window.onload = function(){
		const sendCheckbox = document.getElementById("revise:sendOpenCloseDateToCalendar");
		sendCheckbox?.disabled && (sendCheckbox.checked = false);	//make sure that Calendar sending is not checked when it's disabled/when the site has no calendar
	};

	function setAutoCreatePanel() {
		$(".createOneForumPanel").slideToggle("fast");
		$(".createForumsForGroupsPanel").slideToggle("fast", function() {
			if ($('#createForumsForGroupsPanel').is(':hidden')) {
			   document.getElementById("revise:saveandadd").disabled = false;
			}
			else {
			   document.getElementById("revise:saveandadd").disabled = true;
			}
		});
	};

	function disableFieldsBeforeSubmit() {
		if (isGradebookGroupEnabled) {
			$('select[id^="revise\\:perm"]').each(function() {
				let elementId = $(this).attr("id");
				let rowIndex = elementId.split(":")[2];

				const element = $("#revise\\:perm\\" + ":" + rowIndex + "\\:level");

				if (element) {
					element.prop("disabled", false);
				}
			});
		}
	}
	</script>

  <!-- Y:\msgcntr\messageforums-app\src\webapp\jsp\dfReviseForumSettingsAttach.jsp -->
    <h:form id="revise" onsubmit="disableFieldsBeforeSubmit()">
		  <script>
            $(document).ready(function(){
				// Improve accessibility in error messages.adding the error as title
				var errorMessages = $('#revise\\:errorMessages');
				if (errorMessages !== undefined) {
					errorMessages.attr("role", "alert");
					errorMessages.find('td').each(function() {
						$(this).attr('title', $(this).html());
					});
				}

				$('#revise\\:forum_locked, #revise\\:moderated, #revise\\:postFirst').each(function() {
					$(this).attr('aria-labelledby', 'forum_posting_head ' + $(this).attr('id') + '_label');
				});

                $('#revise\\:availabilityRestricted\\:0, #revise\\:availabilityRestricted\\:1').each(function() {
                   let label = $('#revise\\:forumAvailabilityLabel2').text() + " " + $(this).next().text();
                   $(this).attr('aria-label', label);
                });
                $('input[id*="forum_siteGroupCheck"]').each(function() {
                      $(this).attr('aria-labelledby', 'revise:createForumsForGroups:1 ' + $(this).attr('id') + '_label');
                });
				$('.displayMore').click(function(e){
					e.preventDefault();
					$('.displayMorePanel').fadeIn('slow')
				})
				$('.displayMoreAutoCreate').click(function(e){
					e.preventDefault();
					$('.displayMoreAutoCreatePanel').fadeIn('slow')
				})
				if ($('.gradeSelector').find('option').length ===1){
					$('.gradeSelector').find('select').hide();
					$('.gradeSelector').find('.instrWithGrades').hide();
					$('.gradeSelector').find('.instrWOGrades').show();
				}
				$('.displayMore').click(function(e){
						e.preventDefault();
						$('.displayMorePanel').fadeIn('slow')
				})
				var charRemFormat = $('.charRemFormat').text();
				$(".forum_shortDescriptionClass").charCounter(255, {
					container: ".charsRemaining",
					format: charRemFormat
				 });
				 updateGradeAssignment();

                if(document.getElementById("revise:createForumsForGroups:1") && document.getElementById("revise:createForumsForGroups:1").checked) {
                    setAutoCreatePanel();
                }

                var menuLink = $('#forumsNewMenuLink');
                var menuLinkSpan = menuLink.closest('span');
                menuLinkSpan.addClass('current');
                menuLinkSpan.html(menuLink.text());

			 });
        </script>
		<%@ include file="/jsp/discussionForum/menu/forumsMenu.jsp" %>
		<h:panelGroup layout="block" styleClass="page-header">
			<h1><h:outputText value="#{msgs.cdfm_discussion_forum_settings}" /></h1>
		</h:panelGroup>
		<h:panelGroup layout="block" styleClass="instruction">
		  <h:outputText id="instruction"  value="#{msgs.cdfm_settings_instruction}"/>
		  <h:outputText value="#{msgs.cdfm_info_required_sign}" styleClass="reqStarInline" />
		</h:panelGroup>
			<h:messages layout="table" styleClass="sak-banner-error" id="errorMessages" rendered="#{! empty facesContext.maximumSeverity}"/>
     
			<h:panelGrid columns="1" styleClass="jsfFormTable" columnClasses="shorttext">
				<h:panelGroup>
					<%-- //designNote: does this text input need a maxlength attribute ? --%>
					<h:outputLabel id="outputLabel" for="forum_title" styleClass="block strong" style="padding-bottom:.3em;display:block;clear:both;float:none;">
					<h:outputText id="req_star"  value="#{msgs.cdfm_info_required_sign}" styleClass="reqStar"/>	
						<h:outputText  value="#{msgs.cdfm_forum_title}" />
					</h:outputLabel>
					<h:inputText size="50" id="forum_title"  maxlength="250" value="#{ForumTool.selectedForum.forum.title}" validatorMessage="#{msgs.forums_revise_title_validation}">
						<f:validateLength minimum="1" maximum="255"/>
					</h:inputText>
				</h:panelGroup>
			</h:panelGrid>
			<%-- //designNote: rendered attr below should resolve to false only if there is no prior short description
			 		and if there is server property (TBD) saying not to use it  - below just checking for pre-existing short description--%>
			<h:panelGrid columns="1"  columnClasses="longtext" rendered="#{ForumTool.showForumShortDescription}">
				<h:panelGroup >
					<h:outputText value="" />
					<%-- //designNote: this label should alert that textarea has a 255 max chars limit --%>
					<h:outputLabel id="outputLabel1" for="forum_shortDescription"  value="#{msgs.cdfm_shortDescription}" styleClass="strong"/>
							<h:outputText value="#{msgs.cdfm_shortDescriptionCharsRem}"  styleClass="charRemFormat" style="display:none"/>
							<%--
						
							<h:outputText value="%1 chars remain"  styleClass="charRemFormat" style="display:none"/>
						--%>
							<h:outputText value="" styleClass="charsRemaining" style="padding-left:3em;font-size:.85em;"/>
							<h:outputText value=""  style="display:block"/>
					
					<h:inputTextarea rows="3" cols="45" id="forum_shortDescription"  value="#{ForumTool.selectedForum.forum.shortDescription}" styleClass="forum_shortDescriptionClass" style="float:none"/>
					<h:outputText value="" />
				</h:panelGroup>
      	</h:panelGrid>

			<%--RTEditor area - if enabled--%>
			<h:panelGroup rendered="#{! ForumTool.disableLongDesc}">
				<f:verbatim><input type="hidden" id="ckeditor-autosave-context" name="ckeditor-autosave-context" value="forums_dfReviseForumSettingsAttach" /></f:verbatim>
       				<h:panelGroup rendered="#{ForumTool.selectedForum.forum.id!=null}"><f:verbatim><input type="hidden" id="ckeditor-autosave-entity-id" name="ckeditor-autosave-entity-id" value="</f:verbatim><h:outputText value="#{ForumTool.selectedForum.forum.id}"/><f:verbatim>"/></f:verbatim></h:panelGroup>

				<h:outputText id="outputLabel2" value="#{msgs.cdfm_fullDescription}" styleClass="labeled"/>
			<sakai:inputRichText textareaOnly="#{PrivateMessagesTool.mobileSession}" rows="#{ForumTool.editorRows}" cols="132" id="df_compose_description" value="#{ForumTool.selectedForum.forum.extendedDescription}">
			</sakai:inputRichText>
	      	</h:panelGroup>

			<%--Attachment area  --%>
	      <h2>
		        <h:outputText value="#{msgs.cdfm_att}"/>
	      </h2>

				<%--designNote: would be nice to make this an include, as well as a more comprehensive MIME type check  --%> 
			<h:dataTable styleClass="attachPanel" id="attmsg"  value="#{ForumTool.attachments}" var="eachAttach"  cellpadding="0" cellspacing="0" columnClasses="attach,bogus,specialLink,bogus,bogus" rendered="#{!empty ForumTool.attachments}">
				<h:column>
					<f:facet name="header">   <h:outputText value=" "/>
						</f:facet>
						<sakai:contentTypeMap fileType="#{eachAttach.attachment.attachmentType}" mapType="image" var="imagePath" pathPrefix="/library/image/"/>
						<h:graphicImage id="exampleFileIcon" value="#{imagePath}" />
						</h:column>
						<h:column>
						<f:facet name="header">
							<h:outputText value="#{msgs.cdfm_title}"/>
						</f:facet>
							<h:outputText value="#{eachAttach.attachment.attachmentName}"/>
					</h:column>
					<h:column>
					<f:facet name="header">
						<h:outputText value=" "/>
					</f:facet>
						<h:commandLink action="#{ForumTool.processDeleteAttachSetting}" 
								immediate="true"
								title="#{msgs.cdfm_remove}">
							<h:outputText value="#{msgs.cdfm_remove}"/>
								<f:param value="#{eachAttach.attachment.attachmentId}" name="dfmsg_current_attach"/>
							</h:commandLink>
				  </h:column>
					<h:column rendered="#{!empty ForumTool.attachments}">
						<f:facet name="header">
							<h:outputText value="#{msgs.cdfm_attsize}" />
						</f:facet>
						<h:outputText value="#{ForumTool.getAttachmentReadableSize(eachAttach.attachment.attachmentSize)}"/>
					</h:column>
					<h:column rendered="#{!empty ForumTool.attachments}">
						<f:facet name="header">
		  			  <h:outputText value="#{msgs.cdfm_atttype}" />
						</f:facet>
					<%--//designNote: do we really need this info if the lookup has worked? I Suppose till the MIME type check is more comprehensive, yes --%>
						<h:outputText value="#{eachAttach.attachment.attachmentType}"/>
					</h:column>
			</h:dataTable>

			<h:panelGroup rendered="#{empty ForumTool.attachments}" styleClass="instruction">
				<h:outputText value="#{msgs.cdfm_no_attachments}" />
			</h:panelGroup>
			<h:panelGroup layout="block" styleClass="act" style="padding:0 0 1em 0;">
				<h:commandButton  action="#{ForumTool.processAddAttachmentRedirect}"
					value="#{msgs.cdfm_button_bar_add_attachment_more_redirect}"  
					style="font-size:96%"
					rendered="#{!empty ForumTool.attachments}"/>
				<h:commandButton  action="#{ForumTool.processAddAttachmentRedirect}"
					value="#{msgs.cdfm_button_bar_add_attachment_redirect}"  
					style="font-size:96%"
					rendered="#{empty ForumTool.attachments}"
					/>
			</h:panelGroup>
			<%--general posting  forum settings --%>
			<h2 id="forum_posting_head">
				<h:outputText value="#{msgs.cdfm_forum_posting}" />
			</h2>

				<p class="checkbox">
					<h:selectBooleanCheckbox
						title="ForumLocked" value="#{ForumTool.selectedForum.forumLocked}"
						id="forum_locked">
					</h:selectBooleanCheckbox>
					<h:outputLabel for="forum_locked" value="#{msgs.cdfm_lock_forum}" />
				</p>
				<p class="checkbox">
					<h:selectBooleanCheckbox
						title="Moderated" value="#{ForumTool.selectedForum.forumModerated}"
						id="moderated">
					</h:selectBooleanCheckbox>
					<h:outputLabel for="moderated" value="#{msgs.cdfm_moderate_forum}" />
				</p>
				<p class="checkbox">
					<h:selectBooleanCheckbox
						title="postFirst" value="#{ForumTool.selectedForum.forumPostFirst}"
						id="postFirst">
					</h:selectBooleanCheckbox>
					<h:outputLabel for="postFirst" value="#{msgs.cdfm_postFirst}" />
				</p>

			<h2><h:outputText id="forumAvailabilityLabel2" value="#{msgs.cdfm_forum_availability}" /></h2>
			<h:panelGroup layout="block" styleClass="indnt1">
              <h:panelGroup styleClass="checkbox">
                 <h:selectOneRadio layout="pageDirection" onclick="this.blur()" onchange="setDatesEnabled(this);" disabled="#{not ForumTool.editMode}" id="availabilityRestricted"  value="#{ForumTool.selectedForum.availabilityRestricted}">
                  <f:selectItem itemValue="false" itemLabel="#{msgs.cdfm_forum_avail_show}"/>
                  <f:selectItem itemValue="true" itemLabel="#{msgs.cdfm_forum_avail_date}"/>
               </h:selectOneRadio>
               </h:panelGroup>
               <h:panelGroup id="openDateSpan" styleClass="indnt2 openDateSpan calWidget" style="display: #{ForumTool.selectedForum.availabilityRestricted ? 'block' : 'none'}">
                   <h:outputLabel value="#{msgs.openDate}: " for="openDate"/>
                   <h:inputText id="openDate" styleClass="openDate" value="#{ForumTool.selectedForum.openDate}" onchange="storeOpenDateISO(event)"/>
                   <h:inputText id="openDateISO" styleClass="openDateISO hidden" value="#{ForumTool.selectedForum.openDateISO}"></h:inputText>
               </h:panelGroup>
               <h:panelGroup id="closeDateSpan" styleClass="indnt2 closeDateSpan calWidget" style="display: #{ForumTool.selectedForum.availabilityRestricted ? 'block' : 'none'}">
                   <h:outputLabel value="#{msgs.closeDate}: " for="closeDate" />
                   <h:inputText id="closeDate" styleClass="closeDate" value="#{ForumTool.selectedForum.closeDate}" onchange="storeCloseDateISO(event)"/>
                   <h:inputText id="closeDateISO" styleClass="closeDateISO hidden" value="#{ForumTool.selectedForum.closeDateISO}"></h:inputText>
               </h:panelGroup>
				<h:panelGroup layout="block" styleClass="checkbox" style="display: #{ForumTool.doesSiteHaveCalendar ? '' : 'none'}">
					<h:panelGroup id="sendOpenCloseDateToCalendarSpan"
								  styleClass="indnt2 lockForumAfterCloseDateSpan calWidget"
								  style="display: #{ForumTool.selectedForum.availabilityRestricted ? '' : 'none'}">
						<h:selectBooleanCheckbox id="sendOpenCloseDateToCalendar" styleClass="ms-0 me-3"
												 disabled="#{not ForumTool.doesSiteHaveCalendar}"
												 value="#{ForumTool.selectedForum.forum.sendOpenCloseToCalendar}"/>
						<h:outputLabel for="sendOpenCloseDateToCalendar" styleClass="p-0" value="#{msgs.sendOpenCloseToCalendar}" />
					</h:panelGroup>
				</h:panelGroup>
               <h:panelGroup layout="block" id="lockForumAfterCloseDateSpan" styleClass="indnt2 lockForumAfterCloseDateSpan" style="display: #{ForumTool.selectedForum.availabilityRestricted ? '' : 'none'}">
                   <h:selectBooleanCheckbox id="lockForumAfterCloseDate" styleClass="ms-0 me-3" value="#{ForumTool.selectedForum.forum.lockedAfterClosed}"/>
                   <h:outputLabel for="lockForumAfterCloseDate" styleClass="p-0" value="#{msgs.lockForumAfterCloseDate}" />
               </h:panelGroup>
			</h:panelGroup>

			<script>
				function storeOpenDateISO(e) {
					e.preventDefault();
					document.getElementById("revise:openDateISO").value = document.getElementById("openDateISO8601").value;
				}

				function storeCloseDateISO(e) {
					e.preventDefault();
					document.getElementById("revise:closeDateISO").value = document.getElementById("closeDateISO8601").value;
				}

				localDatePicker({
					input: '.openDate',
					allowEmptyDate: true,
					ashidden: { iso8601: 'openDateISO8601' },
					value: '.openDate',
					useTime: 1
				});

				localDatePicker({
					input: '.closeDate',
					allowEmptyDate: true,
					ashidden: { iso8601: 'closeDateISO8601' },
					value: '.closeDate',
					useTime: 1
				});
			</script>

		<h2><h:outputText value="#{msgs.cdfm_forum_mark_read}"/></h2>
			
			<p class="checkbox">
				<h:selectBooleanCheckbox
					title="autoMarkThreadsRead"
					value="#{ForumTool.selectedForum.forumAutoMarkThreadsRead}"
					id="autoMarkThreadsRead">
				</h:selectBooleanCheckbox>
				<h:outputLabel for="autoMarkThreadsRead"	value="#{msgs.cdfm_auto_mark_threads_read}" />
			</p>

	      <%--designNote: gradebook assignment - need to finesse this - make aware that functionality exists, but flag that there are no gb assignments to select --%>
				<%--designNote:  How is this a "permission" item? --%>  
				<h2><h:outputText value="#{msgs.perm_choose_assignment_head}" rendered="#{ForumTool.gradebookExist}" /></h2>

				<h:panelGroup layout="block" rendered="#{ForumTool.discussionGeneric}">
					<h:outputText styleClass="sak-banner-info" value="#{msgs.group_sitegradebook_simple_forum}" />
				</h:panelGroup>

				<h:panelGroup layout="block" styleClass="row form-group" id="forum_grading" rendered="#{not ForumTool.discussionGeneric}">
					<h:outputLabel for="forum_assignments" value="#{msgs.perm_choose_assignment}" styleClass="col-md-2 col-sm-2"></h:outputLabel>  
					<h:panelGroup layout="block" styleClass="col-md-10 col-sm-10">
						<h:panelGrid>
							<h:panelGroup layout="block" styleClass="row">
							    <f:subview id="non_group_view" rendered="#{!ForumTool.gradebookGroupEnabled}">
									<h:panelGroup styleClass="gradeSelector itemAction actionItem">
										<h:selectOneMenu id="forum_assignments" onchange="updateGradeAssignment()"
														 value="#{ForumTool.selectedForum.gradeAssign}"
														 disabled="#{not ForumTool.editMode}">
											<f:selectItems value="#{ForumTool.assignments}" />
										</h:selectOneMenu>
										<h:outputText value="#{msgs.perm_choose_assignment_none_f}" styleClass="instrWOGrades" style="display:none;margin-left:0"/>
										<h:outputText value=" #{msgs.perm_choose_instruction_forum} " styleClass="instrWithGrades" style="margin-left:0;"/>
										<h:outputLink value="#" style="text-decoration:none" styleClass="instrWithGrades">
											<h:outputText styleClass="displayMore" value="#{msgs.perm_choose_instruction_more_link}"/>
										</h:outputLink>
									</h:panelGroup>
							    </f:subview>
							    <f:subview id="group_view" rendered="#{ForumTool.gradebookGroupEnabled}">
									<sakai-multi-gradebook
											id="gb-selector"
											app-name="sakai.forums"
											site-id='<h:outputText value="#{ForumTool.siteId}" />'
											user-id='<h:outputText value="#{ForumTool.userId}" />'
											group-id='<h:outputText value="#{ForumTool.groupId}" />'
											selected-temp='<h:outputText value="#{ForumTool.selectedForum.gradeAssign}" />'>
									</sakai-multi-gradebook>
									<h:inputHidden id="forum_assignments" value="#{ForumTool.selectedForum.gradeAssign}" />
							    </f:subview>
					        </h:panelGroup>
							<h:panelGroup layout="block" styleClass="row">
								<h:panelGroup styleClass="displayMorePanel" style="display:none" ></h:panelGroup>
								<h:panelGroup styleClass="itemAction actionItem displayMorePanel" style="display:none" >
									<h:outputText styleClass="displayMorePanel" value="#{msgs.perm_choose_instruction_forum_more}"/>
								</h:panelGroup>
							</h:panelGroup>
							<h:panelGroup id="createTaskGroup" style="display:#{((ForumTool.selectedForum.gradeAssign != null && ForumTool.selectedForum.gradeAssign != 'Default_0') ? 'block' : 'none')}">
								<h:selectBooleanCheckbox id="createTask" title="createTask" value="#{ForumTool.selectedForum.createTask}"/>
								<h:outputLabel for="createTask" value="#{msgs.perm_create_task_forum}" style="margin-left:5px"/>
							</h:panelGroup>
						</h:panelGrid>
					</h:panelGroup>
				</h:panelGroup>
			
		<sakai-rubric-association styleClass="checkbox" style="margin-left:10px;display:none;"

            site-id='<h:outputText value="#{ForumTool.siteId}" />'
			dont-associate-label='<h:outputText value="#{msgs.forum_dont_associate_label}" />'
			associate-label='<h:outputText value="#{msgs.forum_associate_label}" />'
			read-only="true"
			tool-id="sakai.gradebookng"
			fine-tune-points='<h:outputText value="#{msgs.option_pointsoverride}" />'
			hide-student-preview='<h:outputText value="#{msgs.option_studentpreview}" />'

		></sakai-rubric-association>

			<h:panelGroup rendered="#{ForumTool.selectedForum.forum.id==null && !empty ForumTool.siteGroups}">
					<f:verbatim><h2></f:verbatim><h:outputText  value="#{msgs.cdfm_autocreate_forums_header}" /><f:verbatim></h2></f:verbatim>
				</h:panelGroup>
				<h:panelGroup layout="block" styleClass="indnt1">
					<h:panelGrid columns="1" columnClasses="longtext,checkbox" cellpadding="0" cellspacing="0" >
						<h:panelGroup rendered="#{ForumTool.selectedForum.forum.id==null && !empty ForumTool.siteGroups}">
							<h:selectOneRadio layout="pageDirection" onclick="this.blur()" onchange="setAutoCreatePanel();" disabled="#{not ForumTool.editMode}" id="createForumsForGroups" value="#{ForumTool.selectedForum.restrictPermissionsForGroups}">
								<f:selectItem itemValue="false" itemLabel="#{msgs.cdfm_create_one_forum}"/>
								<f:selectItem itemValue="true" itemLabel="#{msgs.cdfm_autocreate_forums_for_groups}"/>
							</h:selectOneRadio>
						</h:panelGroup>
					</h:panelGrid>
				</h:panelGroup>

				<h:panelGroup layout="block" styleClass="createOneForumPanel" id="createOneForumPanel">
					<%@ include file="/jsp/discussionForum/permissions/permissions_include.jsp"%>
				</h:panelGroup>

				<h:panelGroup layout="block" styleClass="createForumsForGroupsPanel" id="createForumsForGroupsPanel" style="display:none;" >
				<h:panelGroup rendered="#{ForumTool.selectedForum.forum.id==null && !empty ForumTool.siteGroups}"> 
					<h:outputText value="#{msgs.cdfm_autocreate_forums_desc}" rendered="#{ForumTool.selectedForum.forum.id==null && !empty ForumTool.siteGroups}" />
					<h:panelGroup styleClass="itemAction">
						<h:outputLink value="#" style="text-decoration:none"  styleClass="instrWithGrades">
							<h:outputText styleClass="displayMoreAutoCreate" value="#{msgs.perm_choose_instruction_more_link}"/>
						</h:outputLink>
					</h:panelGroup>
					<f:verbatim><br/></f:verbatim>
					<h:panelGroup styleClass="displayMoreAutoCreatePanel instruction" style="display:none">
						<h:outputText value="#{ForumTool.autoRolesNoneDesc}" />
						<h:outputText value="#{ForumTool.autoGroupsDesc}" />
					</h:panelGroup>
					<h:dataTable value="#{ForumTool.siteGroups}" var="siteGroup" cellpadding="0" cellspacing="0" styleClass="indnt1 jsfFormTable" 
								 rendered="#{ForumTool.selectedForum.forum.id==null}">
						<h:column>
						    <h:selectBooleanCheckbox value="#{siteGroup.createForumForGroup}" id="forum_siteGroupCheck" />
                            <h:outputText value="#{siteGroup.group.title}" id="forum_siteGroupCheck_label" />
						</h:column>
					</h:dataTable>
				</h:panelGroup>
				</h:panelGroup>
				
        
      <h:panelGroup layout="block" styleClass="act">
          <h:commandButton action="#{ForumTool.processActionSaveForumSettings}" value="#{msgs.cdfm_button_bar_save_setting}"
          								 rendered="#{!ForumTool.selectedForum.markForDeletion}" accesskey="s" styleClass="blockMeOnClick active">
    	 	  	<f:param value="#{ForumTool.selectedForum.forum.id}" name="forumId"/>         
          </h:commandButton>
				<h:commandButton id="saveandadd" action="#{ForumTool.processActionSaveForumAndAddTopic}" value="#{msgs.cdfm_button_bar_save_setting_add_topic}" accesskey="t"
          								 rendered = "#{!ForumTool.selectedForum.markForDeletion}" styleClass="blockMeOnClick">
	        	<f:param value="#{ForumTool.selectedForum.forum.id}" name="forumId"/>
          </h:commandButton>  
				<h:commandButton action="#{ForumTool.processActionSaveForumAsDraft}" value="#{msgs.cdfm_button_bar_save_draft}" accesskey="v"
          								 rendered = "#{!ForumTool.selectedForum.markForDeletion}" styleClass="blockMeOnClick">
	        	<f:param value="#{ForumTool.selectedForum.forum.id}" name="forumId"/>
          </h:commandButton>
				<%-- // designNote: these next 2 actions  should be available in the list view instead of here --%>
          <h:commandButton id="delete_confirm" action="#{ForumTool.processActionDeleteForumConfirm}" 
                           value="#{msgs.cdfm_button_bar_delete_forum}" rendered="#{!ForumTool.selectedForum.markForDeletion && ForumTool.displayForumDeleteOption}"
                           accesskey="d" styleClass="blockMeOnClick">
	        	<f:param value="#{ForumTool.selectedForum.forum.id}" name="forumId"/>
          </h:commandButton>
          
          <h:commandButton id="delete" action="#{ForumTool.processActionDeleteForum}" 
                           value="#{msgs.cdfm_button_bar_delete_forum}" rendered="#{ForumTool.selectedForum.markForDeletion}"
                           accesskey="d"  styleClass="blockMeOnClick">
	        	<f:param value="#{ForumTool.selectedForum.forum.id}" name="forumId"/>
          </h:commandButton>
          <h:commandButton immediate="true" action="#{ForumTool.processActionHome}" value="#{msgs.cdfm_button_bar_cancel}" accesskey="x" />
          <h:outputText styleClass="sak-banner-info" style="display:none" value="#{msgs.cdfm_processing_submit_message}" />
       </h:panelGroup>
	 </h:form>
    </sakai:view>
</f:view>
