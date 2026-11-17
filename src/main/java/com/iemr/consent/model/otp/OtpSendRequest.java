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

package com.iemr.consent.model.otp;

import com.iemr.consent.model.consent.ConsentPatterns;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Send an OTP (front-end API 4). The phone is personal data: never logged.
 *
 * @param purpose CONSENT, GUARDIAN or ACCESS_GRANT
 * @param phone   10-digit mobile number
 * @param refId   optional reference, e.g. a grant id
 */
public record OtpSendRequest(@NotNull OtpReason purpose,
		@NotNull @Pattern(regexp = ConsentPatterns.PHONE) String phone,
		@Size(max = 64) @Pattern(regexp = ConsentPatterns.CODE) String refId) {

	@Override
	public String toString() {
		return "OtpSendRequest[purpose=" + purpose + "]";
	}
}
