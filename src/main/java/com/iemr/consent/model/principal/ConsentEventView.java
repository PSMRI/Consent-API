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

package com.iemr.consent.model.principal;

import java.time.Instant;
import java.util.List;

import com.iemr.consent.data.consent.ConsentMode;
import com.iemr.consent.data.consent.ConsentingParty;
import com.iemr.consent.data.consent.EventType;
import com.iemr.consent.data.consent.PurposeStatus;
import com.iemr.consent.data.consent.SyncStatus;

/** One consent event in a patient's history (API 8 {@code history[]}). */
public record ConsentEventView(String consentId, EventType eventType, ConsentingParty consentingParty,
		Long noticeVersionId, String languageCode, ConsentMode mode, Long capturedBy, Instant capturedAt,
		Integer facilityId, String serviceLine, String deviceId, SyncStatus syncStatus,
		List<PurposeOutcome> purposes) {

	/** The outcome for one purpose within the event, as stored. */
	public record PurposeOutcome(String purposeCode, PurposeStatus status, Integer versionNo) {
	}
}
