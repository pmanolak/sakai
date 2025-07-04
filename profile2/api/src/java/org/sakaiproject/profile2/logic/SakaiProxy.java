/**
 * Copyright (c) 2008-2012 The Sakai Foundation
 *
 * Licensed under the Educational Community License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *             http://www.osedu.org/licenses/ECL-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.sakaiproject.profile2.logic;

import java.util.List;
import java.util.Map;

import org.sakaiproject.api.common.edu.person.SakaiPerson;
import org.sakaiproject.profile2.model.MimeTypeByteArray;
import org.sakaiproject.site.api.Site;
import org.sakaiproject.site.api.SiteService.SiteTitleValidationStatus;
import org.sakaiproject.tool.api.Tool;
import org.sakaiproject.user.api.User;

/**
 * An interface for abstracting Sakai specific parts away from the main logic.
 *
 * @author Steve Swinsburg (s.swinsburg@lancaster.ac.uk)
 *
 */
public interface SakaiProxy {

	/**
	 * Get current siteid
	 *
	 * @return
	 */
	public String getCurrentSiteId();

	/**
	 * Get current user id
	 *
	 * @return
	 */
	public String getCurrentUserId();

	/**
	 * Get current user
	 *
	 * @return
	 */
	public User getCurrentUser();

	/**
	 * Convert internal userid to eid (jsmith26)
	 *
	 * @return
	 */
	public String getUserEid(String userId);

	/**
	 * Convert eid to internal userid
	 *
	 * @return
	 */
	public String getUserIdForEid(String eid);

	/**
	 * Get displayname of a given userid (internal id)
	 *
	 * @return
	 */
	public String getUserDisplayName(String userId);

	/**
	 * Get firstname of a given userid (internal id)
	 *
	 * @return
	 */
	public String getUserFirstName(String userId);

	/**
	 * Get lastname of a given userid (internal id)
	 *
	 * @return
	 */
	public String getUserLastName(String userId);

	/**
	 * Get email address for a given userid (internal id)
	 *
	 * @return
	 */
	public String getUserEmail(String userId);

	/**
	 * Check if a user with the given internal id (ie 6ec73d2a-b4d9-41d2-b049-24ea5da03fca) exists
	 *
	 * @param userId
	 * @return
	 */
	public boolean checkForUser(String userId);

	/**
	 * Check if a user with the given eid (ie jsmith26) exists
	 *
	 * @param userId
	 * @return
	 */
	public boolean checkForUserByEid(String eid);

	/**
	 * Is the current user a superUser? (anyone in admin realm)
	 *
	 * @return
	 */
	public boolean isSuperUser();

	/**
	 * Is the current user a superUser and are they performing an action on another user's profile?
	 *
	 * @param userId - userId of other user
	 * @return
	 */
	public boolean isSuperUserAndProxiedToUser(String userId);

	/**
	 * Is the current user viewing the site as another role via View Site As
	 *
	 * @return
	 */
	public boolean isUserRoleSwapped();

	/**
	 * Get the type of this user's account
	 *
	 * @param userId
	 * @return
	 */
	public String getUserType(String userId);

	/**
	 * Get a user
	 *
	 * @param userId internal user id
	 * @return
	 */
	public User getUserById(String userId);

	/**
	 * Get the User object for the given userId.
	 * <p>
	 * This will not log errors so that we can quietly use it to check if a User exists for a given profile, ie in a search result, for
	 * example.
	 * </p>
	 *
	 * @param userId
	 * @return
	 */
	public User getUserQuietly(String userId);

	/**
	 * Get the title of the current tool. If no current tool is set, returns 'Profile'.
	 *
	 * @return
	 */
	public String getCurrentToolTitle();

	/**
	 * Get a list of Users for the given userIds
	 *
	 * @param userUuids uuids
	 * @return List<User>
	 */
	public List<User> getUsers(List<String> userUuids);

	/**
	 * Get a list of uuids for each of the given Users
	 *
	 * @param users Users
	 * @return List<String> of uuids
	 */
	public List<String> getUuids(List<User> users);

	/**
	 * Get a SakaiPerson for a user
	 *
	 * @param userId
	 * @return
	 */
	public SakaiPerson getSakaiPerson(String userId);

	/**
	 * Get a SakaiPerson Jpeg photo for a user. Checks UserMutableType record first, then goes for SystemMutableType if none.
	 * <p>
	 * Returns null if nothing found.
	 *
	 * @param userId
	 * @return
	 */
	public byte[] getSakaiPersonJpegPhoto(String userId);

	/**
	 * Get a SakaiPerson image URL for a user. Checks UserMutableType record first, then goes for SystemMutableType if none.
	 * <p>
	 * Returns null if nothing found.
	 *
	 * @param userId
	 * @return
	 */
	public String getSakaiPersonImageUrl(String userId);

	/**
	 * Get a SakaiPerson prototype if they don't have a profile.
	 * <p>
	 * This is not persistable so should only be used for temporary views. Use createSakaiPerson if need persistable object for saving a
	 * profile.
	 *
	 * @param userId
	 * @return
	 */
	public SakaiPerson getSakaiPersonPrototype();

	/**
	 * Create a new persistable SakaiPerson object for a user
	 *
	 * @param userId
	 * @return
	 */
	public SakaiPerson createSakaiPerson(String userId);

	/**
	 * Update a SakaiPerson object in the db.
	 * <p>
	 * If you are doing this from the UI you should use the {@link ProfileLogic.updateUserProfile} method instead as it will also handle any
	 * email notifications that may be required.
	 *
	 * <p>
	 * We keep this because for direct actions like converting profiles etc we dont need email notifications.
	 *
	 * @param sakaiPerson
	 * @return
	 */
	public boolean updateSakaiPerson(SakaiPerson sakaiPerson);

	/**
	 * Get the maximum filesize that can be uploaded (profile2.picture.max=2)
	 *
	 * @return
	 */
	public int getMaxProfilePictureSize();

	/**
	 * Get the location for a profileImage given the user and type
	 *
	 * @param userId
	 * @param type
	 * @return
	 */
	public String getProfileImageResourcePath(String userId, int type);

	/**
	 * Save a file to CHS
	 *
	 * @param fullResourceId
	 * @param userId
	 * @param fileName
	 * @param mimeType
	 * @param fileData
	 * @return
	 */
	public boolean saveFile(String fullResourceId, String userId, String fileName, String mimeType, byte[] fileData);

	/**
	 * Retrieve a resource from ContentHosting with byte[] and mimetype
	 *
	 * @param resourceId the full resourceId of the file
	 */
	public MimeTypeByteArray getResource(String resourceId);

	/**
	 * Removes the specified resource.
	 *
	 * @param resourceId the ID of the resource to remove.
	 * @return <code>true</code> if the resource is successfully removed, <code>false</code> if the remove operation fails.
	 */
	public boolean removeResource(String resourceId);

	/**
	 * Post an event to Sakai
	 *
	 * @param event name of event
	 * @param reference reference
	 * @param modify true if something changed, false if just access
	 *
	 */
	public void postEvent(String event, String reference, boolean modify);

	/**
	 * Get the name of this Sakai installation (ie Sakai@Lancs)
	 *
	 * @return
	 */
	public String getServiceName();

	/**
	 * Gets the portalUrl configuration parameter (ie http://sakai.lancs.ac.uk/portal) Will not work outside of /portal context (ie won't
	 * work from an entityprovider)
	 *
	 * @return
	 */
	public String getPortalUrl();

	/**
	 * Gets the serverUrl configuration parameter (http://sakai.lancs.ac.uk:8080)
	 *
	 * @return
	 */
	public String getServerUrl();

	/**
	 * Get the DNS name of this Sakai server (ie sakai.lancs.ac.uk)
	 *
	 * @return
	 */
	public String getServerName();

	/**
	 * Get the URL to the current user's my workspace
	 *
	 * @return
	 */
	public String getUserHomeUrl();

	/**
	 * Gets the portal path, generally /portal unless override in sakai.properties
	 *
	 * @return
	 */
	public String getPortalPath();

	/**
	 * Are we using the normal /portal? Deep links are broken in xsl-portal, so we need a workaround to drop the toolstate param. See
	 * PRFL-264
	 *
	 * @return
	 */
	public boolean isUsingNormalPortal();

	/**
	 * Gets the full portal url by adding getServerUrl() and getPortalPath() together This WILL work outside the portal context so safe to
	 * use from an entityprovider
	 *
	 * @return
	 */
	public String getFullPortalUrl();

	/**
	 * Updates a user's email address If the user is a provided user (ie from LDAP) this will probably fail so only internal accounts can be
	 * updated.
	 *
	 * @param userId uuid of the user
	 * @param email
	 */
	public void updateEmailForUser(String userId, String email);

	/**
	 * Updates a user's name If the user is a provided user (ie from LDAP) this will probably fail so only internal accounts can be updated.
	 *
	 * @param userId uuid of the user
	 * @param email
	 */
	public void updateNameForUser(String userId, String firstName, String lastName);

	/**
	 * Creates a direct URL to a user's profile page on their My Workspace Any other parameters supplied in string are appended and encoded.
	 *
	 * @param userId uuid of the user
	 * @param extraParams any extra params to add to the query string
	 * @return
	 * @deprecated
	 */
	@Deprecated
	public String getDirectUrlToUserProfile(String userId, String extraParams);

	/**
	 * Creates a direct URL to a component (profile, messages etc) of a user's profile page on their My Workspace
	 *
	 * @param userId uuid of the user
	 * @param component - currently only 'viewprofile' is supported.
	 * @param extraParams a map of params that each component may need, used to build the URL
	 * @return
	 */
	public String getDirectUrlToProfileComponent(String userId, String component, Map<String, String> extraParams);

	/**
	 * Creates a direct URL to a component (profile, messages etc) of a user's profile page
	 * from another user's My Workspace.
	 *
	 * @param viewerUuid uuid of the viewing user
	 * @param viewedUuid uuid of the user being viewed
	 * @param component - currently only 'viewprofile' is supported.
	 * @param extraParams a map of params that each component may need, used to build the URL
	 * @return
	 */
	public String getDirectUrlToProfileComponent(String viewerUuid, String viewedUuid, String component, Map<String, String> extraParams);

	/**
	 * Check if a user is allowed to update their account. The User could come from LDAP so updates not allowed. This will check if any
	 * updates are allowed.
	 *
	 * Note userDirectoryService.allowUpdateUserEmail etc are NOT the right methods to use as they don't check if account updates are
	 * allowed, just if the user doing the update is allowed.
	 *
	 * @param userId
	 * @return
	 */
	public boolean isAccountUpdateAllowed(String userId);

	/**
	 * Is the profile2.picture.change.enabled flag set in sakai.properties? If not set, defaults to true
	 *
	 * <p>
	 * Depending on this setting, users will be able to upload their own image. This can be useful to disable for institutions which may
	 * provide official photos for students.
	 * </p>
	 *
	 * @return
	 */
	public boolean isProfilePictureChangeEnabled();

	/**
	 * Get the profile2.picture.type setting in sakai.properties
	 * <p>
	 * Possible values for the sakai property are 'upload', 'url' and 'official'. If not set, defaults to 'upload'.
	 * </p>
	 * <p>
	 * This returns an int which matches one of: ProfileConstants.PICTURE_SETTING_UPLOAD, ProfileConstants.PICTURE_SETTING_URL,
	 * ProfileConstants.PICTURE_SETTING_OFFICIAL.
	 * </p>
	 *
	 * <p>
	 * Depending on this setting, Profile2 will decide how it retrieves a user's profile image, and the method by which users can add their
	 * own image. ie by uploading their own image, providing a URL, or not at all (for official)
	 * </p>
	 *
	 * @return
	 */
	public int getProfilePictureType();

	/**
	 * Gets the profile2.profile.entity.set.academic list of properties that should be used in the academic profile view. Returns default
	 * list of ProfileConstants.ENTITY_SET_ACADEMIC if none.
	 *
	 * @return
	 */
	public List<String> getAcademicEntityConfigurationSet();

	/**
	 * Gets the profile2.profile.entity.set.minimal list of properties that should be used in the minimal profile view. Returns default list
	 * of ProfileConstants.ENTITY_SET_MINIMAL if none.
	 *
	 * @return
	 */
	public List<String> getMinimalEntityConfigurationSet();

	/**
	 * Convenience method to ensure the given userId(eid or internal id) is returned as a valid uuid.
	 *
	 * <p>
	 * External integrations must pass input through this method, where the input can be either form, since all data is keyed on the
	 * internal user id.
	 *
	 * @param userId
	 * @return uuid or null
	 */
	public String ensureUuid(String userId);

	/**
	 * Convenience method to check if the user making a request is the same as the current user
	 *
	 * @param userUuid uuid to check against current user
	 * @return
	 */
	public boolean currentUserMatchesRequest(String userUuid);

	/**
	 * Gets the profile2.invisible.users List for user's that should never show in searches or connection lists
	 *
	 * @return
	 */
	public List<String> getInvisibleUsers();

	/**
	 * Toggle a profile's locked status.
	 *
	 * @param userId
	 * @param locked
	 * @return
	 */
	public boolean toggleProfileLocked(String userId, boolean locked);

	/**
	 * Is profile2.official.image.enabled true? If so, allow use of this image and preference.
	 *
	 * @return
	 */
	public boolean isOfficialImageEnabledGlobally();

	/**
	 * Checks if profile2.picture.type=official 
	 *
	 * @return
	 */
	public boolean isUsingOfficialImage();
	/**
	 * Checks if the conditions are appropriate for a user to be able to select whether to use the official image or an alternate of their
	 * choice
	 *
	 * @return
	 */
	public boolean isUsingOfficialImageButAlternateSelectionEnabled();

	/**
	 * Gets the value of the profile2.official.image.source attribute from sakai.properties. If not set, defaults to
	 * ProfileConstants.OFFICIAL_SETTING_DEFAULT
	 *
	 * This should be specified if profile2.picture.type=official
	 *
	 * @return
	 */
	public String getOfficialImageSource();

	/**
	 * Gets the value of the profile2.official.image.directory attribute from sakai.properties. If not set, defaults to /official-photos
	 *
	 * This should be specified if profile2.picture.type=official
	 *
	 * @return The root directory where official images are stored
	 */
	public String getOfficialImagesDirectory();

	/**
	 * Get the value of the profile2.official.image.directory.pattern attribute from sakai.properties. If not set, defaults to TWO_DEEP.
	 *
	 * <br />
	 * Options:
	 * <ul>
	 * <li>ONE_DEEP = first letter of a user's eid, then a slash, then the eid suffixed by '.jpg'. For example BASEDIR/a/adrian.jpg,
	 * BASEDIR/s/steve.jpg</li>
	 * <li>TWO_DEEP = first letter of a user's eid, then a slash, then the second letter of the eid followed by a slash and finally the eid
	 * suffixed by '.jpg'. For example BASEDIR/a/d/adrian.jpg, BASEDIR/s/t/steve.jpg</li>
	 * <li>ALL_IN_ONE = all files in the one directory. For example BASEDIR/adrian.jpg, BASEDIR/steve.jpg</li>
	 * </ul>
	 *
	 * This is optional but if you have your images on the filesystem in a pattern that isnt default, you need to set a pattern.
	 *
	 * @return
	 */
	public String getOfficialImagesFileSystemPattern();

	/**
	 * Gets the value of the profile2.official.image.attribute from sakai.properties If not set, defaults to
	 * ProfileConstants.USER_PROPERTY_JPEG_PHOTO
	 *
	 * This should be specified if profile2.official.image.source=provided
	 *
	 * @return
	 */
	public String getOfficialImageAttribute();

	/**
	 * Get a UUID from the IdManager
	 *
	 * @return
	 */
	public String createUuid();

	/**
	 * Does the user have a current Sakai session
	 *
	 * @param userUuid user to check
	 * @return true/false
	 */
	public boolean isUserActive(String userUuid);

	/**
	 * Get the list of users with active Sakai sessions, given the supplied list of userIds.
	 *
	 * @param userUuids List of users to check
	 * @return List of userUuids that have active Sakai sessions
	 */
	public List<String> getActiveUsers(List<String> userUuids);

	/**
	 * Get the last event time for the user.
	 *
	 * @param userUuid user to check
	 * @return Long of time in milliseconds since epoch, or null if no event.
	 */
	public Long getLastEventTimeForUser(String userUuid);

	/**
	 * Get the last event time for each of the given users
	 *
	 * @param userUuids users to check
	 * @return Map of userId to Long of time in milliseconds since epoch, or null if no event.
	 */
	public Map<String, Long> getLastEventTimeForUsers(List<String> userUuids);

	/**
	 * Generic method to get a configuration parameter from sakai.properties
	 *
	 * @param key key of property
	 * @param def default value
	 * @return value or default if none
	 */
	public String getServerConfigurationParameter(String key, String def);

	/**
	 * Check if specified site is a My Workspace site
	 *
	 * @param siteId id of site
	 * @return true if site is a My Workspace site, false otherwise.
	 */
	public boolean isUserMyWorkspace(String siteId);

	/**
	 * Generic method to check if user has permission in site
	 *
	 * @param userId userId of user to check permission
	 * @param permission the permission to check in site
	 * @param siteId site id
	 * @return
	 */
	public boolean isUserAllowedInSite(String userId, String permission, String siteId);

	/**
	 * Check if the given siteid is valid
	 *
	 * @param siteId
	 * @return
	 */
	public boolean checkForSite(String siteId);

	/**
	 * Does user have site.add permission?
	 *
	 * @return <code>true</code> if user allowed to create worksites, else <code>false</code>.
	 */
	public boolean isUserAllowedAddSite(String userUuid);

	/**
	 * Add a new site.
	 *
	 * @param id the id of the site.
	 * @param type the type of the site e.g. project.
	 * @return a reference to the new site or <code>null</code> if there is a problem creating the site.
	 */
	public Site addSite(String id, String type);

	/**
	 * Save an existing site.
	 *
	 * @param site a reference to the site to save.
	 * @return <code>true</code> if successful, otherwise <code>false</code>.
	 */
	public boolean saveSite(Site site);

	/**
	 * Return a reference to the specified site.
	 *
	 * @param siteId
	 * @return a reference to the specified site.
	 */
	public Site getSite(String siteId);

	/**
	 * Return all user sites for the current user (i.e. worksites that the user has at least 'access' permission to).
	 *
	 * @return all user sites for the current user.
	 */
	public List<Site> getUserSites();

	/**
	 * Returns a reference to the specified Sakai tool.
	 *
	 * @param id the id of the tool required.
	 * @return a reference to the specified Sakai tool or <code>null</code> if a reference cannot be obtained.
	 */
	public Tool getTool(String id);

	/**
	 * Returns a list of the tool types required for the specified site type.
	 *
	 * @param category the type of site e.g. 'project'
	 * @return a list of the tool types required for the specified site type
	 */
	public List<String> getToolsRequired(String category);

	/**
	 * Is the profile2.integration.google.enabled flag set to true in sakai.properties? If not set, defaults to false
	 *
	 * <p>
	 * Depending on this setting, the UI will allow a user to add their Google account. For institutions to use this there additional setup
	 * required.
	 * </p>
	 *
	 * @return
	 */
	public boolean isGoogleIntegrationEnabledGlobally();

	/**
	 * Helper to check if the current user is logged in
	 *
	 * @return
	 */
	public boolean isLoggedIn();

	/**
	 * Is the profile2.profile.fields.enabled flag set in sakai.properties? If not set, defaults to true.
	 *
	 * <p>
	 * This setting controls the display of the profile fields.
	 *
	 * @return true or false.
	 */
	public boolean isProfileFieldsEnabled();

	/**
	 * Is the profile2.profile.social.enabled flag set in sakai.properties? If not set, defaults to true.
	 *
	 * @return <code>true</code> if the profile2.profile.social.enabled flag is set, otherwise returns <code>false</code>.
	 */
	public boolean isSocialProfileEnabled();

	/**
	 * Is the profile2.profile.name.pronunciation.enabled flag set in sakai.properties? If not set, defaults to true
	 *
	 * @return <code>true</code> if the profile2.profile.name.pronunciation.enabled flag is set, otherwise returns <code>false</code>.
	 */
	public boolean isNamePronunciationProfileEnabled();

	/**
	 * Is the profile2.menu.enabled flag set in sakai.properties? If not set, defaults to true.
	 *
	 * <p>
	 * If enabled, the profile sub-sections will be displayed. This setting controls the display of the profile sub-sections: * Messaging *
	 * Privacy * Gallery * Preferences * Search * Messaging * Connections
	 * </p>
	 *
	 * @return <code>true</code> if the profile2.menu.enabled flag is set, otherwise returns <code>false</code>.
	 */
	public boolean isMenuEnabledGlobally();

	/**
	 * Is the profile2.preference.enabled flag set in sakai.properties? If not set, defaults to true.
	 *
	 * <p>
	 * If enabled, the ability to modify one's preferences within profile will be available.
	 * </p>
	 *
	 * @return <code>true</code> if the profile2.preference.enabled flag is set, otherwise returns <code>false</code>.
	 */
	public boolean isPreferenceEnabledGlobally();

	/**
	 * Given the original and stripped site titles, determine that validation status of the stripped string.
	 * @param orig the original, unaltered text as input by the user
	 * @param stripped the HTML stripped text
	 * @return {@link SiteTitleValidationStatus}
	 */
	public SiteTitleValidationStatus validateSiteTitle(String orig, String stripped);

	/**
	 * Returns the name pronunciation examples link
	 * @return the name pronunciation examples link, empty if it is not configured in sakai.properties
	 */
	public String getNamePronunciationExamplesLink();

	/**
	 * Returns the name pronunciation duration in seconds
	 * @return the name pronunciation duration in seconds. 10 seconds if it is not configured in sakai.properties
	 */
	public int getNamePronunciationDuration();

	/**
	 * Check if a user is member of a site
	 *
	 * @param userId userId of user to check membership
	 * @param siteId id of site
	 * @return true if the user is member of that site
	 */
	public boolean isUserMemberOfSite(String userId, String siteId);

	/**
	 * Check if two users have any site membership in common
	 *
	 * @param userId1 userId of user to check membership
	 * @param userId2 userId of user to check membership
	 * @return true if both users are members of one common site
	 */
	public boolean areUsersMembersOfSameSite(String userId1, String userId2);
}
