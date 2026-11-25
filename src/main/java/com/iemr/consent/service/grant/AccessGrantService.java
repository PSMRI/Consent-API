/*
 * AMRIT – Accessible Medical Records via Integrated Technology
 * Integrated EHR (Electronic Health Records) Solution
 *
 * Copyright (C) "Piramal Swasthya Management and Research Institute"
 *
 * This file is part of AMRIT.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see https://www.gnu.org/licenses/.
 */

package com.iemr.consent.service.grant;

import java.util.Collection;
import java.util.List;
import java.util.Set;

import com.iemr.consent.data.grant.AccessGrant;
import com.iemr.consent.data.grant.GrantStatus;
import com.iemr.consent.model.grant.AccessGrantView;
import com.iemr.consent.model.grant.AccessRequest;
import com.iemr.consent.model.grant.ApproveRequest;
import com.iemr.consent.utils.auth.AuthenticatedUser;

/** A doctor's time-limited access to a patient: request, list (API 12), approve (13), revoke (14). */
public interface AccessGrantService {

	/** Creates a PENDING grant for the logged-in user; a repeat with the same key returns the original. */
	AccessGrantView request(AccessRequest request, String requestKey, String requestHash, AuthenticatedUser user,
			String sourceIp);

	/** @param statuses effective statuses to keep; empty = all */
	List<AccessGrantView> list(Long benRegId, Set<GrantStatus> statuses);

	/** @param requestHash hash of the grant id and the body, so a key cannot replay another grant */
	AccessGrantView approve(String grantId, ApproveRequest request, String requestKey, String requestHash,
			AuthenticatedUser user, String sourceIp);

	AccessGrantView revoke(String grantId, String requestKey, String requestHash, AuthenticatedUser user,
			String sourceIp);

	/**
	 * Revokes, inside the caller's transaction, each pending or active grant that covers a withdrawn purpose
	 * (FR-012); grants already past their end date are left as they are.
	 */
	void revokeForWithdrawal(List<AccessGrant> grants, Collection<String> withdrawnCodes, AuthenticatedUser user,
			String sourceIp);
}
