/**
 * $URL$
 * $Id$
 *
 * Copyright (c) 2006-2009 The Sakai Foundation
 *
 * Licensed under the Educational Community License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *             http://www.opensource.org/licenses/ECL-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.sakaiproject.sitestats.tool.wicket.pages;

import org.apache.wicket.request.mapper.parameter.PageParameters;
import org.apache.wicket.markup.head.IHeaderResponse;
import org.apache.wicket.markup.head.JavaScriptHeaderItem;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.sakaiproject.sitestats.api.StatsAuthz;
import org.sakaiproject.sitestats.api.StatsManager;
import org.sakaiproject.sitestats.tool.facade.Locator;
import org.sakaiproject.sitestats.tool.wicket.components.LastJobRun;
import org.sakaiproject.sitestats.tool.wicket.components.Menus;
import org.sakaiproject.sitestats.tool.wicket.widget.ActivityWidget;
import org.sakaiproject.sitestats.tool.wicket.widget.LessonsWidget;
import org.sakaiproject.sitestats.tool.wicket.widget.ResourcesWidget;
import org.sakaiproject.sitestats.tool.wicket.widget.VisitsWidget;

/**
 * @author Nuno Fernandes
 */
public class OverviewPage extends BasePage {
	private static final long			serialVersionUID	= 1L;

	private String						realSiteId;
	private String						siteId;
	private String						currentUserId;

	private boolean						siteStatsView = false;
	private boolean						siteStatsViewAll = false;
	
	public OverviewPage() {
		this(null);
	}

	public OverviewPage(PageParameters pageParameters) {
		realSiteId = Locator.getFacade().getToolManager().getCurrentPlacement().getContext();
		if(pageParameters != null) {
			siteId = pageParameters.get("siteId").toString();
		}
		if(siteId == null){
			siteId = realSiteId;
		}
		StatsAuthz statsAuthz = Locator.getFacade().getStatsAuthz();
		siteStatsView = statsAuthz.isUserAbleToViewSiteStats(siteId);
		siteStatsViewAll = statsAuthz.isUserAbleToViewSiteStatsAll(siteId);
		currentUserId = statsAuthz.getCurrentSessionUserId();
		if(siteStatsView) {
			renderBody();
			Locator.getFacade().getStatsManager().logEvent(null, StatsManager.LOG_ACTION_VIEW, siteId, true);
		}else{
			setResponsePage(NotAuthorizedPage.class);
		}
	}

	@Override
	public void renderHead(IHeaderResponse response) {
		response.render(JavaScriptHeaderItem.forUrl(JQUERYSCRIPT));
		super.renderHead(response);
	}

	private void renderBody() {
		setVersioned(false);
		setRenderBodyOnly(true);
		add(new Menus("menu", siteId));
		
		// SiteStats services
		StatsManager statsManager = Locator.getFacade().getStatsManager();
		
		// Last job run
		add(new LastJobRun("lastJobRun", siteId));
		
		// Widgets ----------------------------------------------------
		
		// Visits
		boolean visitsVisible = statsManager.getEnableSiteVisits() && statsManager.getVisitsInfoAvailable();
		if(visitsVisible) {
			add(new VisitsWidget("visitsWidget", siteId, currentUserId));
		}else{
			add(new WebMarkupContainer("visitsWidget").setRenderBodyOnly(true));
		}
		
		// Activity
		boolean activityVisible = statsManager.isEnableSiteActivity();
		if(activityVisible && siteStatsViewAll) {
			add(new ActivityWidget("activityWidget", siteId));
		}else{
			add(new WebMarkupContainer("activityWidget").setRenderBodyOnly(true));
		}
		
		// Resources
		boolean resourcesVisible = false;
		try{
			resourcesVisible = statsManager.isEnableResourceStats() &&
								(Locator.getFacade().getSiteService().getSite(siteId).getToolForCommonId(StatsManager.RESOURCES_TOOLID) != null);
		}catch(Exception e) {
			resourcesVisible = false;
		}
		if(resourcesVisible && siteStatsViewAll) {
			add(new ResourcesWidget("resourcesWidget", siteId));
		}else{
			add(new WebMarkupContainer("resourcesWidget").setRenderBodyOnly(true));
		}

		// Lessons
		boolean lessonsVisible = false;
		try {
			lessonsVisible = statsManager.isEnableLessonsStats() &&
					(Locator.getFacade().getSiteService().getSite(siteId).getToolForCommonId(StatsManager.LESSONS_TOOLID) != null);
		}catch (Exception e) {
			lessonsVisible = false;
		}

		if (lessonsVisible && siteStatsViewAll) {
			add(new LessonsWidget("lessonsWidget", siteId));
		}else{
			add(new WebMarkupContainer("lessonsWidget").setRenderBodyOnly(true));
		}
	}
}

