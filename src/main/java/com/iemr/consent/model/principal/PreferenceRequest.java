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

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.iemr.consent.data.consent.Decision;
import com.iemr.consent.model.consent.ConsentPatterns;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Change purposes to GRANT or DENY (front-end API 10; contract {@code PreferenceRequest}). A GRANT needs a
 * verified OTP challenge for the patient or guardian (clarification 1).
 */
public record PreferenceRequest(@NotNull List<String> purposeCodes, @NotNull Decision decision,
		@NotNull Map<@NotNull String, @NotNull @Min(1) Integer> versions, UUID otpChallengeId,
		@Size(max = 100) @Pattern(regexp = ConsentPatterns.CODE) String deviceId, Integer facilityId,
		Integer providerServiceMapId,
				@Size(max = 30) @Pattern(regexp = ConsentPatterns.CODE) String serviceLine)
		implements PrincipalChange {

	@JsonIgnore
	@AssertTrue(message = "must be 1 to 50 unique purpose codes")
	public boolean isPurposeCodesValid() {
		return purposeCodes == null || PurposeCodes.valid(purposeCodes, 50);
	}

	@JsonIgnore
	@AssertTrue(message = "must name exactly the purposes in purposeCodes")
	public boolean isVersionsMatchCodes() {
		return purposeCodes == null || versions == null || versions.keySet()
				.equals(new HashSet<>(purposeCodes));
	}

	@JsonIgnore
	@AssertTrue(message = "is required to grant a purpose")
	public boolean isOtpChallengeIdPresent() {
		return decision != Decision.GRANT || otpChallengeId != null;
	}
}
