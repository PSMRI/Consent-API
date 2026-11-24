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

package com.iemr.consent.service.consent;

import com.iemr.consent.model.consent.ConsentDetail;
import com.iemr.consent.utils.auth.AuthenticatedUser;

/** Reads one consent event in full (front-end API 17). */
public interface ConsentDetailService {

	/**
	 * The consent with everything recorded for it. Appends {@code CONSENT_VIEWED} to the audit log.
	 *
	 * @param consentId the id as sent (an 11-digit id or an older UUID)
	 * @return the consent; {@code NOT_FOUND} when no such consent exists
	 */
	ConsentDetail detail(String consentId, AuthenticatedUser user, String sourceIp);
}
