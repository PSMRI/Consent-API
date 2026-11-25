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

import java.util.UUID;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Proof details for the chosen mode. Names are personal data: stored encrypted, never logged.
 *
 * @param workerConfirmed     READ_ALOUD: the worker confirms the notice was read out
 * @param witnessName         WITNESSED
 * @param witnessRelationship WITNESSED
 * @param otpChallengeId      OTP: the verified challenge
 */
public record Proof(Boolean workerConfirmed,
		@Size(max = 100) @Pattern(regexp = ConsentPatterns.NAME) String witnessName,
		@Size(max = 50) @Pattern(regexp = ConsentPatterns.NAME) String witnessRelationship,
		UUID otpChallengeId) {

	@Override
	public String toString() {
		return "Proof[workerConfirmed=" + workerConfirmed + ", otpChallengeId=" + otpChallengeId + "]";
	}
}
