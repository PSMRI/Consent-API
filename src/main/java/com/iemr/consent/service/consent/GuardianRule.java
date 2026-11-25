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

package com.iemr.consent.service.consent;

import org.springframework.stereotype.Component;

import com.iemr.consent.data.consent.GuardianVerification;
import com.iemr.consent.model.consent.GuardianInfo;
import com.iemr.consent.model.consent.SaveConsentRequest;
import com.iemr.consent.model.otp.OtpReason;
import com.iemr.consent.service.otp.OtpChallenge;
import com.iemr.consent.service.otp.OtpService;
import com.iemr.consent.utils.auth.AuthenticatedUser;
import com.iemr.consent.utils.crypto.Sha256;
import com.iemr.consent.utils.exception.ConsentApiException;
import com.iemr.consent.utils.exception.ErrorCode;

/**
 * Guardian consent (C6, C7, C8, D-03): OTP needs a verified, unused GUARDIAN challenge for the same
 * phone; ID_SIGHTED and UNVERIFIED need a witness and are flagged for follow-up when saved.
 */
@Component
public class GuardianRule {

	private final OtpService otpService;

	public GuardianRule(OtpService otpService) {
		this.otpService = otpService;
	}

	public void check(SaveConsentRequest request, AuthenticatedUser user) {
		GuardianInfo guardian = request.guardian();
		if (guardian == null) {
			throw ConsentRules.invalid("guardian", "is required when the guardian consents");
		}
		if (guardian.verification() != GuardianVerification.OTP) {
			if (ConsentRules.blank(guardian.witnessName())) {
				throw ConsentRules.invalid("guardian.witnessName", "required without OTP");
			}
			return;
		}
		if (ConsentRules.blank(guardian.phone()) || guardian.otpChallengeId() == null) {
			throw ConsentRules.invalid("guardian.otpChallengeId", "phone and verified OTP are required");
		}
		String challengeId = guardian.otpChallengeId().toString();
		OtpChallenge challenge = otpService.requireUsable(challengeId, OtpReason.GUARDIAN, user);
		if (!challenge.phoneHash().equals(Sha256.hex(guardian.phone()))) {
			throw new ConsentApiException(ErrorCode.OTP_INVALID);
		}
	}
}
