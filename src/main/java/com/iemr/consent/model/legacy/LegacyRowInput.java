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

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * One row of a legacy consent file as Common-API forwards it (A9, feature 008). Values are kept as text so that
 * every problem becomes a row reason rather than a request error; only the sizes that the stored columns can hold
 * are request rules. {@code beneficiaryRegId} is set by Common-API's Identity-API lookup ({@code null} = unknown).
 * {@code toString} hides the Beneficiary ID, the representative's name and the remarks.
 *
 * @param purposesGranted purpose codes separated by {@code ;}
 * @param consentDate     {@code YYYY-MM-DD}
 */
public record LegacyRowInput(@NotNull @Positive Integer rowNo, @Size(max = 20) String beneficiaryId,
		Long beneficiaryRegId, @Size(max = 20) String consentDate, @Size(max = 255) String purposesGranted,
		@Size(max = 10) String languageCode, @Size(max = 20) String mode,
		@Size(max = 100) String representativeName, @Size(max = 50) String representativeRelationship,
		@Size(max = 1000) String remarks) {

	@Override
	public String toString() {
		return "LegacyRowInput[rowNo=" + rowNo + ", mode=" + mode + "]";
	}
}
