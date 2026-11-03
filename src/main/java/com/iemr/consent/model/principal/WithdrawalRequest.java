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

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.iemr.consent.model.consent.ConsentPatterns;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Withdraw purposes or {@code ALL} (front-end API 11; contract {@code WithdrawalRequest}).
 *
 * @param purposeCodes the purposes, or {@code "ALL"}
 * @param versions     version last seen per purpose; must cover every purpose that will change
 */
public record WithdrawalRequest(@NotNull PurposeSelection purposeCodes,
		@NotNull Map<@NotNull String, @NotNull @Min(1) Integer> versions,
		@Size(max = 100) @Pattern(regexp = ConsentPatterns.CODE) String deviceId, Integer facilityId,
		Integer providerServiceMapId,
				@Size(max = 30) @Pattern(regexp = ConsentPatterns.CODE) String serviceLine)
		implements PrincipalChange {

	@JsonIgnore
	@AssertTrue(message = "must be \"ALL\" or 1 to 50 unique purpose codes")
	public boolean isPurposeCodesValid() {
		return purposeCodes == null || purposeCodes.all() || PurposeCodes.valid(purposeCodes.codes(), 50);
	}
}
