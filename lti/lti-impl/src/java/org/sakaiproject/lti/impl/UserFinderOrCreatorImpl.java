/**
 * Copyright (c) 2009 The Sakai Foundation
 *
 * Licensed under the Educational Community License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *			 http://www.opensource.org/licenses/ECL-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.sakaiproject.lti.impl;

import java.util.Collection;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;

import lombok.extern.slf4j.Slf4j;

import org.tsugi.lti.LTIConstants;
import org.tsugi.lti.LTIUtil;

import org.sakaiproject.id.cover.IdManager;
import org.sakaiproject.lti.api.LTIException;
import org.sakaiproject.lti.api.UserFinderOrCreator;
import org.sakaiproject.user.api.User;
import org.sakaiproject.user.api.UserNotDefinedException;
import org.sakaiproject.user.api.UserDirectoryService;

import lombok.Setter;

/**
 *  @author Adrian Fish <a.fish@lancaster.ac.uk>
 */
@Slf4j
public class UserFinderOrCreatorImpl implements UserFinderOrCreator {

	@Setter private UserDirectoryService userDirectoryService;

	public User findOrCreateUser(Map payload, boolean trustedConsumer, boolean emailtrusted) throws LTIException {

		User user;
		String eid=null;
		String user_id = (String) payload.get(LTIConstants.USER_ID);

		// Get the eid, either from the value provided or if trusted get it from the user_id, otherwise construct it.
		if(!emailtrusted){
			eid = getEid(payload, trustedConsumer, user_id);
		}

		// If we did not get first and last name, split lis_person_name_full
		final String fullname = (String) payload.get(LTIConstants.LIS_PERSON_NAME_FULL);
		String fname = (String) payload.get(LTIConstants.LIS_PERSON_NAME_GIVEN);
		String lname = (String) payload.get(LTIConstants.LIS_PERSON_NAME_FAMILY);
		String email = (String) payload.get(LTIConstants.LIS_PERSON_CONTACT_EMAIL_PRIMARY);
		String subject_guid = (String) payload.get("subject_guid");
		if (emailtrusted && StringUtils.isEmpty(email)) {
			log.warn("trusting email as eid, no email provided subject_guid={}", subject_guid);
			eid = subject_guid;
		}

		if (fname == null && lname == null && fullname != null) {
			int ipos = fullname.trim().lastIndexOf(' ');
			if (ipos == -1) {
				fname = fullname;
			} else {
				fname = fullname.substring(0, ipos);
				lname = fullname.substring(ipos + 1);
			}
		}

		// If trusted consumer, login, if email trusted consumer then we look up the user info based on the email address otherwise check for existing user and create one if required
		// Note that if trusted, then the user must have already logged into Sakai in order to have an account stub created for them
		// otherwise this will fail since they don't exist. Perhaps this should be addressed?
		if (trustedConsumer) {
			try {
				if (LTIUtil.isNotBlank((String) payload.get(LTIConstants.EXT_SAKAI_PROVIDER_EID))) {
					user = userDirectoryService.getUserByEid(eid);
				} else {
					user = userDirectoryService.getUser(user_id);
				}
			} catch (UserNotDefinedException e) {
				throw new LTIException("launch.user.invalid", "user_id=" + user_id, e);
			}
			return user;
		}

		/*
		 * looking up user based on email address may return multiple results,
		 * this is not a valid case hence this is an error condition with this
		 * work flow
		 */
		if (emailtrusted && ! StringUtils.isEmpty(email)) {
			Collection<User> findUsersByEmail = userDirectoryService.findUsersByEmail((String) email);
			if (findUsersByEmail.isEmpty()) {
				eid = email;
				log.debug("Using email as eid to create new user eid={}", eid);
			} else {
				if (findUsersByEmail.size() > 1) {
					log.warn("multiple user id's exist for emailaddress= {}", email);
					throw new LTIException("launch.user.multiple.emailaddress", "email=" + email,null);
				}
				user = (User) findUsersByEmail.toArray()[0];
				return user;
			}
		}

		if ( StringUtils.isEmpty(eid)) {
			throw new LTIException("launch.user.noeid", "user_id=" + user_id);
		}


		try {
			user = userDirectoryService.getUserByEid(eid);
		} catch (UserNotDefinedException e) {
			log.debug("Will need to create user: {}", eid);
			user = null;
		}

		/*
		 * Create the user with an unguessable password.  In the future, perhaps with SSO or perhaps
		 * with password reset enabled, and if the eid is the email address, this user will be
		 * able to log into their account directly.
		 */
		if (user == null) {
			try {
				String hiddenPW = IdManager.createUuid();
				userDirectoryService.addUser(null, eid, fname, lname, email, hiddenPW, "registered", null);
				log.info("Created user={}", eid);
				user = userDirectoryService.getUserByEid(eid);
			} catch (Exception e) {
				throw new LTIException("launch.create.user", "user_id=" + user_id, e);
			}
		}

		// post the login event
		// eventTrackingService().post(eventTrackingService().newEvent(EVENT_LOGIN,
		// null, true));

		return user;
	}

	private String getEid(Map payload, boolean trustedConsumer, String user_id) throws LTIException {

		String eid;
		String oauth_consumer_key = (String) payload.get("oauth_consumer_key");
		String subject_guid = (String) payload.get("subject_guid");
		String ext_sakai_provider_eid = (String) payload.get(LTIConstants.EXT_SAKAI_PROVIDER_EID);

		if (LTIUtil.isNotBlank(ext_sakai_provider_eid)){
			eid = (String) payload.get(LTIConstants.EXT_SAKAI_PROVIDER_EID);
		} else {

			if (trustedConsumer) {
				try {
					eid = userDirectoryService.getUserEid(user_id);
				} catch (Exception e) {
					log.error(e.getLocalizedMessage(), e);
					throw new LTIException( "launch.user.invalid", "user_id="+user_id, e);
				}
			} else if ( LTIUtil.isNotBlank(subject_guid) ) {
				eid = "subject:" + subject_guid;
			} else {
				eid = oauth_consumer_key + ":" + user_id;
			}
			if (log.isDebugEnabled()) {
				log.debug("eid={}", eid);
			}
		}
		return eid;
	}


}
