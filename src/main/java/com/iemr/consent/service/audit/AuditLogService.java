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

package com.iemr.consent.service.audit;

/** Appends to the tamper-evident consent audit log, inside the caller's transaction (FR-013). */
public interface AuditLogService {

	/** Audit action for a saved consent. */
	String CONSENT_CAPTURED = "CONSENT_CAPTURED";

	/** Audit action for a preference change (API 10). */
	String PREFERENCE_CHANGED = "PREFERENCE_CHANGED";

	/** Audit action for a withdrawal (API 11). */
	String CONSENT_WITHDRAWN = "CONSENT_WITHDRAWN";

	/**
	 * Audit actions for access grants. Their {@code consentId} is null and their purpose summary is
	 * {@code grant:<grantId>}; the grant row holds the purposes.
	 */
	String ACCESS_REQUESTED = "ACCESS_REQUESTED";

	/** See {@link #ACCESS_REQUESTED}. */
	String ACCESS_APPROVED = "ACCESS_APPROVED";

	/** See {@link #ACCESS_REQUESTED}. */
	String ACCESS_REVOKED = "ACCESS_REVOKED";

	/**
	 * Audit action for unlinked evidence removed by the clean-up job (feature 004). Its {@code consentId} is
	 * null and its purpose summary is {@code evidence:<evidenceId>}; no personal data.
	 */
	String EVIDENCE_PURGED = "EVIDENCE_PURGED";

	/**
	 * @param purposeSummary purpose codes with statuses, e.g. {@code P-REG:GRANTED,P-REM:DENIED}; no
	 *                       personal values
	 */
	void append(String action, String consentId, Long beneficiaryRegId, String purposeSummary, String status,
			Long initiator, String sourceIp);
}
