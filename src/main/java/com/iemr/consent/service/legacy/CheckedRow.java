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
import java.util.List;

import com.iemr.consent.data.legacy.LegacyRowStatus;
import com.iemr.consent.model.legacy.LegacyRowInput;

/**
 * One legacy row after checking (feature 008): its normalised values and every reason it cannot be imported.
 *
 * @param consentDate the parsed date, or {@code null} when missing or not {@code YYYY-MM-DD}
 * @param purposes    the purpose codes trimmed, without repeats, joined with {@code ;}; {@code null} when none
 * @param language    the trimmed, lower-case language code, or {@code null}
 * @param mode        the trimmed, upper-case mode, or {@code null}
 * @param reasons     reason codes in the data-model order; empty for a valid row
 */
record CheckedRow(LegacyRowInput input, LocalDate consentDate, String purposes, String language, String mode,
		List<String> reasons) {

	LegacyRowStatus status() {
		return reasons.isEmpty() ? LegacyRowStatus.VALID : LegacyRowStatus.ERROR;
	}

	@Override
	public String toString() {
		return "CheckedRow[rowNo=" + input.rowNo() + ", reasons=" + reasons + "]";
	}
}
