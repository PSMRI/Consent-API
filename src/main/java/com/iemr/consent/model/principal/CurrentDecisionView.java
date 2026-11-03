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

import com.iemr.consent.data.consent.PurposeStatus;

/**
 * A patient's current decision for one purpose (API 8 {@code current[]}); {@code status} is the
 * effective status (an expired grant shows EXPIRED).
 */
public record CurrentDecisionView(String purposeCode, String name, boolean isMandatory, boolean isWithdrawable,
		PurposeStatus status, Instant validUntil, Integer versionNo) {
}
