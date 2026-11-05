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

import java.time.LocalDate;

import com.iemr.consent.data.legacy.LegacyBatch;
import com.iemr.consent.data.legacy.LegacyBatchStatus;
import com.iemr.consent.data.legacy.LegacyRow;
import com.iemr.consent.data.legacy.LegacyRowStatus;
import com.iemr.consent.model.legacy.LegacyRowInput;
import com.iemr.consent.support.TestFixtures;

/** Feature 008 test data. */
final class LegacyFixtures {

	static final String BATCH = "8a1c3e2b-1111-4222-8333-944455556666";

	static final String BEN_ID = "100245678901";

	private LegacyFixtures() {
	}

	/** A valid row of patient 7001, consent date 2025-06-12, P-REG and P-REM, Hindi, PAPER. */
	static LegacyRowInput input(int rowNo) {
		return new LegacyRowInput(rowNo, BEN_ID, 7001L, "2025-06-12", "P-REG;P-REM", "hi", "PAPER", null, null,
				null);
	}

	static LegacyRowInput input(int rowNo, Long regId, String date, String purposes, String language, String mode,
			String representative) {
		return new LegacyRowInput(rowNo, BEN_ID, regId, date, purposes, language, mode, representative, null,
				null);
	}

	static LegacyBatch batch(LegacyBatchStatus status) {
		LegacyBatch batch = new LegacyBatch();
		batch.setBatchId(BATCH);
		batch.setFileName("old.csv");
		batch.setStatus(status);
		batch.setTotal(3);
		batch.setValid(2);
		batch.setFailed(1);
		batch.setUploadedBy("worker42");
		batch.setUploadedAt(TestFixtures.NOW);
		batch.setExpiresAt(TestFixtures.NOW.plusSeconds(86400));
		batch.setRequestKey("k-check");
		batch.setRequestHash("h-check");
		return batch;
	}

	static LegacyRow row(int rowNo, LegacyRowStatus status, Long regId, LocalDate date, String purposes) {
		LegacyRow row = new LegacyRow();
		row.setBatchId(BATCH);
		row.setRowNo(rowNo);
		row.setBeneficiaryId(BEN_ID);
		row.setBeneficiaryRegId(regId);
		row.setConsentDate(date);
		row.setPurposes(purposes);
		row.setLanguageCode("hi");
		row.setMode("PAPER");
		row.setStatus(status);
		return row;
	}
}
