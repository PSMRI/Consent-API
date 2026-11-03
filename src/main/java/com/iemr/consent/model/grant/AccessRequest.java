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

package com.iemr.consent.model.grant;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.iemr.consent.model.consent.ConsentPatterns;
import com.iemr.consent.model.principal.PurposeCodes;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

/**
 * A doctor's request for time-limited access to a patient (clarification 2; contract {@code AccessRequest}).
 * The maximum number of days is checked by the service against {@code consent.access-grant.max-days}.
 */
public record AccessRequest(@NotNull @Positive Long beneficiaryRegId, @NotNull List<String> purposeCodes,
		@NotNull @Min(1) Integer days, @NotBlank @Pattern(regexp = ConsentPatterns.TEXT) String reason,
		Integer facilityId) {

	@JsonIgnore
	@AssertTrue(message = "must be 1 to 20 unique purpose codes, at most 255 characters in all")
	public boolean isPurposeCodesValid() {
		return purposeCodes == null
				|| PurposeCodes.valid(purposeCodes, 20) && PurposeCodes.fitColumn(purposeCodes);
	}

	@Override
	public String toString() {
		return "AccessRequest[days=" + days + "]";
	}
}
