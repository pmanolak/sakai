/**
 * Copyright (c) 2010-2017 The Apereo Foundation
 *
 * Licensed under the Educational Community License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *             http://opensource.org/licenses/ecl2
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
/*
* Licensed to The Apereo Foundation under one or more contributor license
* agreements. See the NOTICE file distributed with this work for
* additional information regarding copyright ownership.
*
* The Apereo Foundation licenses this file to you under the Educational
* Community License, Version 2.0 (the "License"); you may not use this file
* except in compliance with the License. You may obtain a copy of the
* License at:
*
* http://opensource.org/licenses/ecl2.txt
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/

package org.sakaiproject.roster.tool.entityprovider;

import java.io.IOException;
import java.io.OutputStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.sakaiproject.entitybroker.EntityReference;
import org.sakaiproject.entitybroker.EntityView;
import org.sakaiproject.entitybroker.entityprovider.annotations.EntityCustomAction;
import org.sakaiproject.entitybroker.entityprovider.capabilities.ActionsExecutable;
import org.sakaiproject.entitybroker.entityprovider.capabilities.AutoRegisterEntityProvider;
import org.sakaiproject.entitybroker.entityprovider.capabilities.RequestAware;
import org.sakaiproject.entitybroker.entityprovider.extension.ActionReturn;
import org.sakaiproject.entitybroker.entityprovider.extension.RequestGetter;
import org.sakaiproject.entitybroker.exception.EntityException;
import org.sakaiproject.entitybroker.util.AbstractEntityProvider;
import org.sakaiproject.roster.api.RosterEnrollment;
import org.sakaiproject.roster.api.RosterFunctions;
import org.sakaiproject.roster.api.RosterGroup;
import org.sakaiproject.roster.api.RosterMember;
import org.sakaiproject.roster.api.RosterSite;
import org.sakaiproject.roster.api.SakaiProxy;
import org.sakaiproject.util.ResourceLoader;

import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

/**
 * <code>RosterPOIEntityProvider</code> allows Roster to export to Excel via Apache's POI.
 *
 * @author d.b.robinson@lancaster.ac.uk
 */
@Slf4j
public class RosterPOIEntityProvider extends AbstractEntityProvider implements
		AutoRegisterEntityProvider, ActionsExecutable, RequestAware {

	public final static String ENTITY_PREFIX = "roster-export";
	public final static String DEFAULT_ID = ":ID:";

	// error messages
	public final static String MSG_INVALID_ID = "Invalid site ID";
	public final static String MSG_NO_SESSION = "Must be logged in";
	public final static String MSG_NO_SITE_ID = "Must provide a site ID";
	public final static String MSG_NO_FILE_CREATED = "Error creating file";
	public final static String MSG_NO_EXPORT_PERMISSION = "Current user does not have export permission";
	public final static String MSG_UNABLE_TO_RETRIEVE_SITE = "Unable to retrieve the requested site";

	// roster views
	public final static String VIEW_OVERVIEW = "overview";
	public final static String VIEW_ENROLLMENT_STATUS = "status";

	// key passed as parameters
	public final static String KEY_GROUP_ID = "groupId";
	public final static String KEY_ROLE_ID = "roleId";
	public final static String KEY_VIEW_TYPE = "viewType";
	public final static String KEY_BY_GROUP = "byGroup";
	public final static String KEY_ENROLLMENT_SET_ID = "enrollmentSetId";
	public final static String KEY_ENROLLMENT_STATUS = "enrollmentStatus";
	public final static String KEY_FACET_NAME = "facetName";
	public final static String KEY_FACET_USER_ID = "facetUserId";
	public final static String KEY_FACET_EMAIL = "facetEmail";
	public final static String KEY_FACET_ROLE = "facetRole";
	public final static String KEY_FACET_GROUPS = "facetGroups";
	public final static String KEY_FACET_STATUS = "facetStatus";
	public final static String KEY_FACET_CREDITS = "facetCredits";

	// defaults to use if any keys are not specified
	public final static String DEFAULT_FACET_NAME = "Name";
	public final static String DEFAULT_FACET_USER_ID = "User ID";
	public final static String DEFAULT_FACET_EMAIL = "Email Address";
	public final static String DEFAULT_FACET_ROLE = "Role";
	public final static String DEFAULT_FACET_GROUPS = "Groups";
	public final static String DEFAULT_FACET_STATUS = "Status";
	public final static String DEFAULT_FACET_CREDITS = "Credits";
	public final static String DEFAULT_GROUP_ID = "all";
	public final static String DEFAULT_ENROLLMENT_STATUS = "all";
	public final static String DEFAULT_VIEW_TYPE = VIEW_OVERVIEW;
	public final static boolean DEFAULT_BY_GROUP = false;

	// misc
	public final static String FILE_EXTENSION = ".xlsx";
	public final static String FILENAME_SEPARATOR = "_";
	public final static String FILENAME_BYGROUP = "ByGroup";
	public final static String FILENAME_UNGROUPED = "Ungrouped";

	private static final ResourceLoader rl = new ResourceLoader("roster");

	@Resource
	private SakaiProxy sakaiProxy;
	@Setter
	private RequestGetter requestGetter;

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String getEntityPrefix() {
		return ENTITY_PREFIX;
	}

	/**
	 * Gets the output data.
	 *
	 * Does not require a HTTP request/response
	 *
	 * @param out
	 * @param reference
	 * @param parameters
	 * @throws IOException
	 */
	@EntityCustomAction(action = "get-export", viewKey = EntityView.VIEW_SHOW)
	public ActionReturn getExport(final OutputStream out, final EntityReference reference,
			final Map<String, Object> parameters) {

		final String userId = getUserId(reference);
		final String siteId = getSiteId(reference);

		try {
			if (this.sakaiProxy.hasUserSitePermission(userId, RosterFunctions.ROSTER_FUNCTION_EXPORT, siteId)) {
				final RosterSite site = getSite(reference, siteId);
				final Workbook workbook = getExportData(userId, site, parameters);
				workbook.write(out);
				out.close();
                return new ActionReturn("base64",
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", out);

			} else {
				throw new EntityException(MSG_NO_EXPORT_PERMISSION, reference.getReference());
			}
		} catch (final IOException e) {
			log.error(MSG_NO_FILE_CREATED, e);
			throw new EntityException(MSG_NO_FILE_CREATED, reference.getReference());
		}
	}

	/**
	 * Gets the output data and return as an attachment
	 *
	 * @param reference
	 * @param parameters
	 */
	@EntityCustomAction(action = "export-to-excel", viewKey = EntityView.VIEW_SHOW)
	public void exportToExcel(final OutputStream out, final EntityReference reference, final Map<String, Object> parameters) {

		final String userId = getUserId(reference);
		final String siteId = getSiteId(reference);
		final HttpServletResponse response = this.requestGetter.getResponse();

		try {
			if (this.sakaiProxy.hasUserSitePermission(userId, RosterFunctions.ROSTER_FUNCTION_EXPORT, siteId)) {
				final RosterSite site = getSite(reference, siteId);

				final Map<String, String> dataMap = getProcessedParameters(site, parameters);
				final String groupId = dataMap.get("groupId");
				final String viewType = dataMap.get("viewType");
				final String enrollmentSetId = dataMap.get("enrollmentSetId");
				final String enrollmentStatus = dataMap.get("enrollmentStatus");

				final String filename = createFilename(site, groupId, viewType, enrollmentSetId, enrollmentStatus);
				response.addHeader("Content-Disposition", "attachment; filename=" + filename);
				response.addHeader("Content-Encoding", "base64");
				response.addHeader("Content-Type", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

				final Workbook workbook = getExportData(userId, site, parameters);
				workbook.write(response.getOutputStream());
				response.getOutputStream().close();
			} else {
				throw new EntityException(MSG_NO_EXPORT_PERMISSION, reference.getReference());
			}
		} catch (final IOException e) {
			log.error(MSG_NO_FILE_CREATED, e);
			throw new EntityException(MSG_NO_FILE_CREATED, reference.getReference());
		}

	}

	private RosterSite getSite(final EntityReference reference, final String siteId) {
		final RosterSite site = this.sakaiProxy.getRosterSite(siteId);
		if (null == site) {
			throw new EntityException(MSG_UNABLE_TO_RETRIEVE_SITE, reference.getReference());
		}
		return site;
	}

	private String getUserId(final EntityReference reference) {
		final String userId = this.developerHelperService.getCurrentUserId();
		if (userId == null) {
			throw new EntityException(MSG_NO_SESSION, reference.getReference());
		}
		return userId;
	}

	private String getSiteId(final EntityReference reference) {
		final String siteId = reference.getId();
		if (StringUtils.isBlank(siteId) || DEFAULT_ID.equals(siteId)) {
			throw new EntityException(MSG_NO_SITE_ID, reference.getReference());
		}
		return siteId;
	}

	private String createFilename(final RosterSite site, final String groupId,
			final String viewType, final String enrollmentSetId,
			final String enrollmentStatus) {

		StringBuffer filename = new StringBuffer();

		if (VIEW_OVERVIEW.equals(viewType)) {

			filename.append(site.getTitle());

			if (null != groupId && !DEFAULT_GROUP_ID.equals(groupId)) {

				for (final RosterGroup group : site.getSiteGroups()) {
					if (group.getId().equals(groupId)) {
						filename.append(FILENAME_SEPARATOR);
						filename.append(group.getTitle());
						break;
					}
				}
			}
		} else if (VIEW_ENROLLMENT_STATUS.equals(viewType)) {
			filename.append(enrollmentSetId);
			filename.append(FILENAME_SEPARATOR);
			filename.append(enrollmentStatus);
		}

		final Date date = new Date();
		// ISO formatted date
		final DateFormat isoFormat = new SimpleDateFormat("yyyy-MM-dd");

		filename.append(FILENAME_SEPARATOR);
		filename.append(isoFormat.format(date));

		filename = new StringBuffer(filename.toString().replaceAll("\\W", FILENAME_SEPARATOR));
		filename.append(FILE_EXTENSION);

		return filename.toString();
	}

	/**
	 * Generate the export data
	 *
	 * @param currentUserId
	 * @param site
	 * @param parameters
	 * @return
	 */
	private Workbook getExportData(final String currentUserId, final RosterSite site, final Map<String, Object> parameters) {

		final Map<String, String> dataMap = getProcessedParameters(site, parameters);
		final String groupId = dataMap.get("groupId");
		final String viewType = dataMap.get("viewType");
		final String roleId = dataMap.get("roleId");
		final String enrollmentSetId = dataMap.get("enrollmentSetId");
		final String enrollmentStatus = dataMap.get("enrollmentStatus");
		final String enrollmentSetTitle = dataMap.get("enrollmentSetTitle");

		final List<List<String>> rosterRows = new ArrayList<>();
		final List<List<String>> groupsRows = new ArrayList<>();
		final List<List<String>> sectionsRows = new ArrayList<>();
		final String siteId = site.getId();
		final String userId = this.developerHelperService.getCurrentUserId();
		final boolean canUserViewGroups = this.sakaiProxy.hasUserSitePermission(userId, RosterFunctions.ROSTER_FUNCTION_VIEWGROUP, siteId);

		createSpreadsheetTitle(rosterRows, site, groupId, viewType);

		final String siteID = site.getId();
		List<String> header = createColumnHeader(viewType, siteID, true);
		List<RosterMember> rosterMembers = Collections.EMPTY_LIST;

		if (VIEW_OVERVIEW.equals(viewType)) {

			rosterMembers = getMembership(currentUserId, siteID, groupId, roleId);

			if (CollectionUtils.isNotEmpty(rosterMembers)) {
				addOverviewRows(rosterRows, rosterMembers, header, siteID, true);
			}
		} else if (VIEW_ENROLLMENT_STATUS.equals(viewType)) {

			rosterMembers = getEnrolledMembership(currentUserId, siteID, enrollmentSetId, enrollmentStatus);

			if (CollectionUtils.isNotEmpty(rosterMembers)) {
				addEnrollmentStatusRows(rosterRows, rosterMembers, header, enrollmentSetTitle, enrollmentStatus, siteID);
			}
		}

		final Workbook workBook = new XSSFWorkbook();
		final Sheet rosterSheet = workBook.createSheet(rl.getString("facet_roster"));
		addRowsToSheet(rosterSheet, rosterRows);

		if (canUserViewGroups) {
			if (CollectionUtils.isNotEmpty(rosterMembers)) {
				// For the Groups sheet, add a single column header for the different Groups.
				header.add(rl.getString("facet_groups"));
				addGroupMembershipByGroupRows(groupsRows, rosterMembers, site, header, viewType);
			}

			if (groupsRows.size() > 0 ) {
				Sheet groupsSheet = workBook.createSheet(rl.getString("facet_groups"));
				addRowsToSheet(groupsSheet, groupsRows);

				// For the Sections sheet, add a column header for each Section & Group used in the Site.
				List<String> sectionsHeader = createColumnHeader(viewType, siteId, true);
				ImmutablePair<List<String>, List<String>> sectionsPair = createSectionHeaders(site, userId);
				sectionsHeader.addAll(sectionsPair.getLeft());
				addSectionRows(sectionsRows, rosterMembers, sectionsHeader, sectionsPair.getRight(), site);

				Sheet sectionsSheet = workBook.createSheet(rl.getString("facet_sections"));
				addRowsToSheet(sectionsSheet, sectionsRows);
			}
		}
		return workBook;
	}

	private void addRowsToSheet(Sheet sheet, List<List<String>> dataInRows) {
		for (int i = 0; i < dataInRows.size(); i++) {
			final Row row = sheet.createRow(i);
			for (int j = 0; j < dataInRows.get(i).size(); j++) {
				final Cell cell = row.createCell(j);
				cell.setCellValue(dataInRows.get(i).get(j));
			}
		}
	}

	private void addGroupMembershipByGroupRows(List<List<String>> dataInRows, List<RosterMember> rosterMembers, RosterSite site, List<String> header, String viewType) {
		if (site == null || site.getSiteGroups() == null) {// groups12345678
			return;
		}

		for (RosterGroup group : site.getSiteGroups()) {
			List<String> groupTitle = new ArrayList<>();
			groupTitle.add(group.getTitle());

			dataInRows.add(groupTitle);
			dataInRows.add(new ArrayList<>()); // blank line

			dataInRows.add(header);
			dataInRows.add(new ArrayList<>()); // blank line

			for (RosterMember member : rosterMembers) {
				if (null != member.getGroups().get(group.getId())) {
					List<String> row = new ArrayList<>();

					if (sakaiProxy.getFirstNameLastName()) {
						row.add(member.getDisplayName());
					} else {
						row.add(member.getSortName());
					}

					if (sakaiProxy.getViewUserDisplayId(site.getId())) {
						row.add(member.getDisplayId());
					}

					if (this.sakaiProxy.getViewEmail(site.getId())) {
						row.add(member.getEmail());
					}

					if (this.sakaiProxy.getViewCandidateDetails(site.getId())) {
						row.add(member.getStudentNumber());
					}

					if (this.sakaiProxy.getViewUserProperty(site.getId())) {
						List<String> props = member.getUserProperties().entrySet().stream().map(e -> e.getKey() + ":" + e.getValue()).collect(Collectors.toList());
						row.add(String.join(",", props));
					}

					if (this.sakaiProxy.getViewCandidateDetails(site.getId())) {
						List<String> specialNeeds = member.getSpecialNeeds();
						row.add(specialNeeds != null ? String.join("\n", specialNeeds) : "");
						
					}

					if (this.sakaiProxy.getViewCandidateDetails(site.getId())) {
						List<String> specialNeeds = member.getAdditionalNotes();
						row.add(specialNeeds != null ? String.join("\n", specialNeeds) : "");
					}

					if (VIEW_OVERVIEW.equals(viewType)) {
						row.add(member.getRole());
					} else if (VIEW_ENROLLMENT_STATUS.equals(viewType)) {
						row.add(member.getEnrollmentStatusText());
						row.add(member.getCredits());
					}
					row.add(member.getGroupsToString());
					dataInRows.add(row);
				}
			}

			dataInRows.add(new ArrayList<>()); // blank line
		}
	}

	private List<RosterMember> getMembership(final String userId, final String siteId, final String groupId, final String roleId) {

		List<RosterMember> rosterMembers;

		if (DEFAULT_GROUP_ID.equals(groupId)) {
			rosterMembers = this.sakaiProxy.getMembership(userId, siteId, null, roleId, null, null);
		} else {
			rosterMembers = this.sakaiProxy.getMembership(userId, siteId, groupId, roleId, null, null);
		}

		if (null == rosterMembers) {
			return null;
		}

		return rosterMembers;
	}

	private List<RosterMember> getEnrolledMembership(final String currentUserId, final String siteId, final String enrollmentSetId,
			final String enrollmentStatusId) {

		final List<RosterMember> rosterMembers = this.sakaiProxy.getMembership(currentUserId, siteId, null, null, enrollmentSetId,
				enrollmentStatusId);

		List<RosterMember> membersByStatus = null;
		if (DEFAULT_ENROLLMENT_STATUS.equals(enrollmentStatusId)) {
			membersByStatus = rosterMembers;
		} else {
			membersByStatus = new ArrayList<>();
			for (final RosterMember rosterMember : rosterMembers) {
				if (enrollmentStatusId.equals(rosterMember.getEnrollmentStatusId())) {
					membersByStatus.add(rosterMember);
				}
			}
		}

		return membersByStatus;
	}

	private void addOverviewRows(final List<List<String>> dataInRows,
			final List<RosterMember> rosterMembers, final List<String> header, final String siteId, final boolean isGroupsSheetHeader) {

		final String userId = this.developerHelperService.getCurrentUserId();

		dataInRows.add(header);
		// blank line
		dataInRows.add(new ArrayList<String>());

		for (final RosterMember member : rosterMembers) {
			final List<String> row = new ArrayList<String>();
			dataInRows.add(addUserDataRow(member, siteId));


			row.add(member.getRole());
		}

	}

	private List<String> addUserDataRow(final RosterMember member, final String siteId) {//roster12345678
		final List<String> row = new ArrayList<String>();

		if (this.sakaiProxy.getFirstNameLastName()) {
			row.add(member.getDisplayName());
		} else {
			row.add(member.getSortName());
		}

		if (this.sakaiProxy.getViewUserDisplayId(siteId)) {
			row.add(member.getDisplayId());
		}

		if (this.sakaiProxy.getViewEmail(siteId)) {
			row.add(member.getEmail());
		}

		if (this.sakaiProxy.getViewCandidateDetails(siteId)) {
			row.add(member.getStudentNumber());
		}

		if (this.sakaiProxy.getViewUserProperty(siteId)) {
			List<String> props = member.getUserProperties().entrySet().stream().map(e -> e.getKey() + ":" + e.getValue()).collect(Collectors.toList());
			row.add(String.join("\n", props));
		}

		if (this.sakaiProxy.getViewCandidateDetails(siteId)) {
			List<String> specialNeeds = member.getSpecialNeeds();
			row.add(specialNeeds != null ? String.join("\n", specialNeeds) : "");
		}

		if (this.sakaiProxy.getViewCandidateDetails(siteId)) {
			List<String> specialNeeds = member.getAdditionalNotes();
			row.add(specialNeeds != null ? String.join("\n", specialNeeds) : "");
		}

		row.add(member.getRole());

		return row;
	}

	/**
	 * Rows will be filled with Sections and regular Groups spread over different cells, taking into account that:
	 * a Section is a group with special properties, there can be multiple Sections of the same category,
	 * and a user can be part of various Sections, but only one from each category.
	 *
	 * @param dataInRows list of rows
	 * @param rosterMembers site users selection
	 * @param header column headers
	 * @param usedCategoryIds used section categories
	 * @param site the RosterSite
	 */
	private void addSectionRows(final List<List<String>> dataInRows, final List<RosterMember> rosterMembers,
			final List<String> header, final List<String> usedCategoryIds, final RosterSite site) {//section12345678

			dataInRows.add(header);

			for (final RosterMember member : rosterMembers) {
				// Add user's data at the beginning of each row.
				final List<String> row = addUserDataRow(member, site.getId());
				final List<String> rowGroupsPart = new ArrayList<String>();
				// Get all groups the current member is associated to.
				final Map<String, String> memberGroupsMap = member.getGroups();
				final Map<String, String> memberSectionsMap = new HashMap<>();

				for (final RosterGroup group : site.getSiteGroups()) {
					// Look for the site groups the user isn't part of, to account for them on the spreadsheet structure.
					final Optional<String> groupTitle = Optional.ofNullable(memberGroupsMap.get(group.getId()));
					final Optional<String> sectionCategoryId = Optional.ofNullable(group.getSectionCategory());

					if (groupTitle.isPresent()) {
						// Check if the current group is a Section or a standard Group.
						if (sectionCategoryId.isPresent()) {
							memberSectionsMap.put(sectionCategoryId.get(), groupTitle.get());
						} else {
							rowGroupsPart.add(groupTitle.get());
						}
					// If the user isn't part of the current standard Group.
					}	 else if (sectionCategoryId.isEmpty()) {
					rowGroupsPart.add(""); // Empty spreadsheet cell
				}
				}
				// Add the user Sections based on their categories, following the header order.
				for (String category : usedCategoryIds) {
					if (memberSectionsMap.containsKey(category)) {
						row.add(memberSectionsMap.get(category));
					} else {
						row.add("");
					}
				}
			// Final row will be composed by: user data + sections + groups.
			row.addAll(rowGroupsPart);
			dataInRows.add(row);
		}
	}

	private void addEnrollmentStatusRows(final List<List<String>> dataInRows,
			final List<RosterMember> enrollmentSet, /* RosterSite site, */
			final List<String> header, final String enrollmentSetTitle,
			final String enrollmentStatus, final String siteId) {

		final String userId = this.developerHelperService.getCurrentUserId();

		final List<String> enrollmentSetTitleRow = new ArrayList<>();
		enrollmentSetTitleRow.add(enrollmentSetTitle);
		dataInRows.add(enrollmentSetTitleRow);

		// blank line
		dataInRows.add(new ArrayList<>());

		final List<String> enrollmentStatusRow = new ArrayList<>();
		enrollmentStatusRow.add(enrollmentStatus);
		dataInRows.add(enrollmentStatusRow);

		// blank line
		dataInRows.add(new ArrayList<>());

		dataInRows.add(header);

		// blank line
		dataInRows.add(new ArrayList<>());

		for (final RosterMember member : enrollmentSet) {

			final List<String> row = new ArrayList<>();

			if (this.sakaiProxy.getFirstNameLastName()) {
				row.add(member.getDisplayName());
			} else {
				row.add(member.getSortName());
			}

			if (this.sakaiProxy.getViewUserDisplayId(siteId)) {
				row.add(member.getDisplayId());
			}

			if (this.sakaiProxy.getViewEmail(siteId)) {
				row.add(member.getEmail());
			}

			if (this.sakaiProxy.getViewCandidateDetails(siteId)) {
				List<String> specialNeeds = member.getSpecialNeeds();
				row.add(specialNeeds != null ? String.join("\n", specialNeeds) : "");
			}

			if (this.sakaiProxy.getViewCandidateDetails(siteId)) {
				List<String> specialNeeds = member.getAdditionalNotes();
				row.add(specialNeeds != null ? String.join("\n", specialNeeds) : "");
			}

			if (this.sakaiProxy.getViewUserProperty(siteId)) {
				List<String> props = member.getUserProperties().entrySet().stream().sorted(Map.Entry.comparingByKey()).map(e -> e.getKey() + ":" + e.getValue()).collect(Collectors.toList());
				row.add(String.join(",", props));
			}

			row.add(member.getRole());
			row.add(member.getEnrollmentStatusText());
			row.add(member.getCredits());

			dataInRows.add(row);
		}
	}

	private String getEnrollmentSetIdValue(final Map<String, Object> parameters) {
		String enrollmentSetId = null;
		if (null != parameters.get(KEY_ENROLLMENT_SET_ID)) {
			enrollmentSetId = parameters.get(KEY_ENROLLMENT_SET_ID).toString();
		}
		return enrollmentSetId;
	}

	private String getEnrollmentStatusValue(final Map<String, Object> parameters) {
		String enrollmentStatus = null;
		if (null != parameters.get(KEY_ENROLLMENT_STATUS)) {
			enrollmentStatus = parameters.get(KEY_ENROLLMENT_STATUS).toString().toLowerCase();
		}
		return enrollmentStatus;
	}

	private String getViewTypeValue(final Map<String, Object> parameters) {

		if (null != parameters.get(KEY_VIEW_TYPE)) {
			return parameters.get(KEY_VIEW_TYPE).toString();
		} else {
			return DEFAULT_VIEW_TYPE;
		}
	}

	private String getGroupIdValue(final Map<String, Object> parameters) {

		if (null != parameters.get(KEY_GROUP_ID)) {
			return parameters.get(KEY_GROUP_ID).toString();
		}
		return null;
	}

	private String getRoleIdValue(final Map<String, Object> parameters) {

		if (null != parameters.get(KEY_ROLE_ID)) {
			return parameters.get(KEY_ROLE_ID).toString();
		}
		return null;
	}

	private List<String> createColumnHeader(final String viewType, final String siteId, boolean isGroupsSheetHeader) {

		final List<String> header = new ArrayList<>();
		header.add(rl.getString("facet_name"));

		if (this.sakaiProxy.getViewUserDisplayId(siteId)) {
			header.add(rl.getString("facet_userId"));
		}

		if (this.sakaiProxy.getViewEmail(siteId)) {
			header.add(rl.getString("facet_email"));
		}

		if (this.sakaiProxy.getViewCandidateDetails(siteId)) {
			header.add(rl.getString("facet_studentNumber"));
		}

		if (this.sakaiProxy.getViewUserProperty(siteId)) {
			header.add(rl.getString("facet_userProperties"));
		}

		if (this.sakaiProxy.getViewCandidateDetails(siteId)) {
			header.add(rl.getString("facet_specialNeeds"));
		}

		if (this.sakaiProxy.getViewCandidateDetails(siteId)) {
			header.add(rl.getString("facet_additionalNotes"));
		}

		if (VIEW_OVERVIEW.equals(viewType)) {
			header.add(rl.getString("facet_role"));
		} else if (VIEW_ENROLLMENT_STATUS.equals(viewType)) {
			header.add(rl.getString("facet_status"));
			header.add(rl.getString("facet_credits"));
		}

		return header;
	}

	/**
	 * Generate the column header for each Section and standard Group on the site.
	 * A list of section categories will be also returned.
	 * To allow the rows below to keep the correct order.
	 *
	 * @param site
	 * @param userId
	 * @return pair(Section & Group headers, category ids from used sections)
	 */
	private ImmutablePair<List<String>, List<String>> createSectionHeaders(final RosterSite site, final String userId) {
		final List<String> sectionHeaders = new ArrayList<>();
		final List<String> usedCategories = new ArrayList<>();
		final List<String> groupHeaders = new ArrayList<>();
		String sectionLabel = rl.getString("facet_sections_category");
		String groupLabel = rl.getString("groups") + ": ";

		for (final RosterGroup group : site.getSiteGroups()) {
			// Check every group on the site, will be considered a Section if has any section category id.
			final Optional<String> sectionCategoryId = Optional.ofNullable(group.getSectionCategory());

			if (sectionCategoryId.isPresent()) {
				String categoryName = sakaiProxy.getCategoryName(sectionCategoryId.get());
				String fullSectionLabel = sectionLabel + categoryName;
				// Avoid repeated Section headers, as multiple Sections can be part of the same category.
				if (!sectionHeaders.contains(fullSectionLabel)) {
					sectionHeaders.add(fullSectionLabel);
					usedCategories.add(sectionCategoryId.get());
				}
			} else {
				groupHeaders.add(groupLabel + group.getTitle());
			}
		}
		sectionHeaders.addAll(groupHeaders);
		ImmutablePair<List<String>, List<String>> sectionsPair = ImmutablePair.of(sectionHeaders, usedCategories);

		return sectionsPair;
	}

	private void createSpreadsheetTitle(final List<List<String>> dataInRows,
			final RosterSite site, final String groupId, final String viewType) {

		final List<String> title = new ArrayList<>();
		title.add(site.getTitle());
		dataInRows.add(title);
		// blank line
		dataInRows.add(new ArrayList<>());

		// SAK-18513
		if (VIEW_OVERVIEW.equals(viewType)) {
			if (null != groupId && !DEFAULT_GROUP_ID.equals(groupId)) {

				// TODO look at using maps in RosterSite instead
				for (final RosterGroup group : site.getSiteGroups()) {

					if (group.getId().equals(groupId)) {
						final List<String> groupTitle = new ArrayList<>();
						groupTitle.add(group.getTitle());
						dataInRows.add(groupTitle);
						// blank line
						dataInRows.add(new ArrayList<>());

						break;
					}
				}
			}
		}
	}

	/**
	 * Get the set of params we need to do the export
	 *
	 * @param site
	 * @param parameters
	 * @return
	 */
	private Map<String, String> getProcessedParameters(final RosterSite site, final Map<String, Object> parameters) {
		final String groupId = getGroupIdValue(parameters);
		final String viewType = getViewTypeValue(parameters);
		final String roleId = getRoleIdValue(parameters);

		final String enrollmentSetId = getEnrollmentSetIdValue(parameters);
		final String enrollmentStatus = getEnrollmentStatusValue(parameters);

		String enrollmentSetTitle = null;
		if (null != enrollmentSetId) {
			for (final RosterEnrollment enrollmentSet : site.getSiteEnrollmentSets()) {
				if (enrollmentSetId.equals(enrollmentSet.getId())) {
					enrollmentSetTitle = enrollmentSet.getTitle();
					break;
				}
			}
		}

		final Map<String, String> map = new HashMap<>();
		map.put("groupId", groupId);
		map.put("viewType", viewType);
		map.put("roleId", roleId);
		map.put("enrollmentSetId", enrollmentSetId);
		map.put("enrollmentStatus", enrollmentStatus);
		map.put("enrollmentSetTitle", enrollmentSetTitle);

		return map;
	}

}
