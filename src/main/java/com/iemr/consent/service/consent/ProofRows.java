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

import java.time.Clock;
import java.util.UUID;

import com.iemr.consent.data.consent.Consent;
import com.iemr.consent.data.consent.Decision;
import com.iemr.consent.data.evidence.ConsentEvidence;
import com.iemr.consent.data.evidence.EvidenceType;
import com.iemr.consent.model.consent.SaveConsentRequest;
import com.iemr.consent.utils.auth.AuthenticatedUser;

/** Builds proof-detail rows of {@code cm_consent_evidence} for a saved consent or preference change. */
public final class ProofRows {

	private ProofRows() {
	}

	public static ConsentEvidence row(Consent consent, EvidenceType type, AuthenticatedUser user, Clock clock) {
		ConsentEvidence row = new ConsentEvidence();
		row.setEvidenceId(UUID.randomUUID().toString());
		row.setConsentId(consent.getConsentId());
		row.setType(type);
		row.setUploadedBy(user.userId());
		row.setCreatedAt(clock.instant());
		row.setCreatedBy(user.auditName());
		return row;
	}

	static boolean anyGrant(SaveConsentRequest request) {
		return request.decisions().stream().anyMatch(d -> d.decision() == Decision.GRANT);
	}
}
