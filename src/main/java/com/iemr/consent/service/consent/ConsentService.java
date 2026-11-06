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

import com.iemr.consent.model.consent.SaveConsentRequest;
import com.iemr.consent.model.consent.SaveConsentResult;
import com.iemr.consent.utils.auth.AuthenticatedUser;
import com.iemr.consent.utils.http.ForwardedAuth;

/** Saving consent decisions (FR-009 – FR-017). */
public interface ConsentService {

	/**
	 * Validates and saves a consent; a repeated request key returns the first answer. When the request has
	 * {@code notify}, the SMS is sent after commit with the caller's login ({@code auth}) and never fails the save.
	 */
	SaveConsentResult save(SaveConsentRequest request, String requestKey, String requestHash,
			AuthenticatedUser user, String sourceIp, ForwardedAuth auth);
}
