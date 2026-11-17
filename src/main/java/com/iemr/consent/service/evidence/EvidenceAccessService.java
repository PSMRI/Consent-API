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

import com.iemr.consent.model.evidence.EvidenceView;
import com.iemr.consent.utils.auth.AuthenticatedUser;

/** Listing and retrieving the proof files of saved consents; every retrieval is audited. */
public interface EvidenceAccessService {

	/** The files linked to a consent, oldest first; {@code NOT_FOUND} when the consent does not exist. */
	List<EvidenceView> listForConsent(String consentId);

	/**
	 * The decrypted file of evidence linked to a consent. Unknown, unlinked (not yet saved), purged and
	 * non-file evidence is {@code NOT_FOUND}. Appends {@code EVIDENCE_ACCESSED} to the audit log.
	 */
	EvidenceFile open(String evidenceId, boolean download, AuthenticatedUser user, String sourceIp);
}
