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

import java.util.List;

import com.iemr.consent.data.consent.Consent;
import com.iemr.consent.data.consent.ConsentPurpose;
import com.iemr.consent.data.consent.Decision;
import com.iemr.consent.data.consent.PurposeStatus;
import com.iemr.consent.model.consent.PurposeResult;
import com.iemr.consent.model.consent.SaveConsentResult;

/**
 * Rebuilds a save's original answer from its stored rows, for a replay (C18, research R4): the
 * status comes from the stored {@code decision}, never from the row's current status, so a later
 * change cannot alter the replayed answer.
 */
final class ConsentResultMapper {

	private ConsentResultMapper() {
	}

	static SaveConsentResult fromStored(Consent consent, List<ConsentPurpose> rows) {
		List<PurposeResult> purposes = rows.stream().map(row -> new PurposeResult(row.getPurposeCode(),
				statusOf(row.getDecision()), row.getVersionNo())).toList();
		return new SaveConsentResult(consent.getConsentId(), consent.getSyncStatus(), purposes);
	}

	static PurposeStatus statusOf(Decision decision) {
		return decision == Decision.GRANT ? PurposeStatus.GRANTED : PurposeStatus.DENIED;
	}
}
