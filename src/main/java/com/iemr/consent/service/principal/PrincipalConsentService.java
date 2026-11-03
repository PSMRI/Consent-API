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

package com.iemr.consent.service.principal;

import com.iemr.consent.model.principal.PatientConsents;
import com.iemr.consent.model.principal.PreferenceRequest;
import com.iemr.consent.model.principal.WithdrawalRequest;
import com.iemr.consent.model.principal.WithdrawalResult;
import com.iemr.consent.utils.auth.AuthenticatedUser;

/** A patient's consents in the preference centre (front-end APIs 8, 10 and 11). */
public interface PrincipalConsentService {

	/** API 8: current decisions and full history; both empty for a patient with no consent. */
	PatientConsents consents(Long benRegId);

	/**
	 * API 10. A repeat with the same request key changes nothing and returns the current consents (FR-018).
	 *
	 * @param requestHash hash of the patient id and the body, so a key cannot replay another patient
	 */
	PatientConsents changePreference(Long benRegId, PreferenceRequest request, String requestKey,
			String requestHash, AuthenticatedUser user, String sourceIp);

	/** API 11; repeats behave as for {@link #changePreference}. */
	WithdrawalResult withdraw(Long benRegId, WithdrawalRequest request, String requestKey, String requestHash,
			AuthenticatedUser user, String sourceIp);
}
