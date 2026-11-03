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

package com.iemr.consent.model.consent;

import java.util.List;
import java.util.UUID;

import com.iemr.consent.data.consent.GuardianType;
import com.iemr.consent.data.consent.GuardianVerification;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * The guardian giving consent for a child or a person with a disability (C6, C7, C8). Name and
 * phone are personal data: stored encrypted, never logged.
 *
 * @param guardianType   PARENT or DISABILITY_GUARDIAN
 * @param name           guardian name
 * @param relationship   relationship to the patient
 * @param phone          10-digit mobile; required for OTP verification
 * @param verification   OTP, ID_SIGHTED or UNVERIFIED (D-03)
 * @param otpChallengeId verified GUARDIAN challenge, for OTP
 * @param witnessName    required for ID_SIGHTED and UNVERIFIED
 * @param evidenceIds    optional photos of the guardian's signature or ID
 */
public record GuardianInfo(@NotNull GuardianType guardianType,
		@NotBlank @Size(max = 100) @Pattern(regexp = ConsentPatterns.NAME) String name,
		@NotBlank @Size(max = 50) @Pattern(regexp = ConsentPatterns.NAME) String relationship,
		@Pattern(regexp = ConsentPatterns.PHONE) String phone, @NotNull GuardianVerification verification,
		UUID otpChallengeId, @Size(max = 100) @Pattern(regexp = ConsentPatterns.NAME) String witnessName,
		List<UUID> evidenceIds) {

	@Override
	public String toString() {
		return "GuardianInfo[guardianType=" + guardianType + ", verification=" + verification + "]";
	}
}
