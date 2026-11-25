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

package com.iemr.consent.service.evidence;

import java.util.List;
import java.util.UUID;

import com.iemr.consent.data.evidence.ConsentEvidence;
import com.iemr.consent.model.evidence.EvidenceUploadResult;
import com.iemr.consent.utils.auth.AuthenticatedUser;

/** Proof files for the consent dialog (FR-007, FR-008). */
public interface EvidenceService {

	EvidenceUploadResult upload(EvidenceUpload upload, String requestKey, AuthenticatedUser user);

	/**
	 * Uploaded files a save may link: each must exist, be a file, belong to this user
	 * ({@code NOT_FOUND} otherwise) and not be linked yet ({@code STATE_CONFLICT}).
	 */
	List<ConsentEvidence> requireLinkable(List<UUID> evidenceIds, AuthenticatedUser user);
}
