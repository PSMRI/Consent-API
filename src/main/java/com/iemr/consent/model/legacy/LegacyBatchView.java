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

package com.iemr.consent.model.legacy;

import java.time.Instant;
import java.util.List;

import com.iemr.consent.data.legacy.LegacyBatchStatus;

/**
 * A legacy batch as returned by A9 and A10 (feature 008, data-model "Response: legacy batch").
 *
 * @param failed the number of {@code ERROR} rows
 */
public record LegacyBatchView(String batchId, String fileName, String uploadedBy, Instant uploadedAt,
		LegacyBatchStatus status, int total, int valid, int imported, int skipped, int failed,
		List<LegacyRowView> rows) {
}
