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

import java.util.List;

import org.springframework.stereotype.Component;

import com.iemr.consent.data.consent.ConsentMode;
import com.iemr.consent.data.evidence.ConsentEvidence;
import com.iemr.consent.model.consent.SaveConsentRequest;
import com.iemr.consent.model.otp.OtpReason;
import com.iemr.consent.service.otp.OtpService;
import com.iemr.consent.utils.auth.AuthenticatedUser;

/** OTP mode: a verified CONSENT challenge created by the same worker and not used before. */
@Component
public class OtpProofRule implements ModeProofRule {

	private final OtpService otpService;

	public OtpProofRule(OtpService otpService) {
		this.otpService = otpService;
	}

	@Override
	public ConsentMode mode() {
		return ConsentMode.OTP;
	}

	@Override
	public void check(SaveConsentRequest request, AuthenticatedUser user, List<ConsentEvidence> evidence) {
		if (request.proof() == null || request.proof().otpChallengeId() == null) {
			throw ConsentRules.invalid("proof.otpChallengeId", "is required for OTP mode");
		}
		otpService.requireUsable(request.proof().otpChallengeId().toString(), OtpReason.CONSENT, user);
	}
}
