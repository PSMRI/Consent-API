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

package com.iemr.consent.service.legacy;

import com.iemr.consent.model.legacy.LegacyBatchView;
import com.iemr.consent.model.legacy.LegacyCheckRequest;
import com.iemr.consent.utils.auth.AuthenticatedUser;

/** Admin legacy consent upload (A9–A10, feature 008). */
public interface LegacyConsentService {

	/** A9: checks every row and stores the batch; no consent is written. Replays come from the stored batch. */
	LegacyBatchView check(LegacyCheckRequest request, String requestKey, String requestHash, AuthenticatedUser user,
			String sourceIp);

	/** A10: imports the valid rows of a checked batch, once. Replays come from the stored batch. */
	LegacyBatchView importBatch(String batchId, String requestKey, String requestHash, AuthenticatedUser user,
			String sourceIp);
}
