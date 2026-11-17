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

import java.time.Clock;

import org.springframework.stereotype.Component;

import com.iemr.consent.data.consent.Consent;
import com.iemr.consent.data.consent.ConsentMode;
import com.iemr.consent.data.evidence.ConsentEvidence;
import com.iemr.consent.data.evidence.EvidenceType;
import com.iemr.consent.model.consent.SaveConsentRequest;
import com.iemr.consent.model.otp.OtpReason;
import com.iemr.consent.repository.evidence.ConsentEvidenceRepository;
import com.iemr.consent.service.otp.OtpChallenge;
import com.iemr.consent.service.otp.OtpService;
import com.iemr.consent.utils.auth.AuthenticatedUser;
import com.iemr.consent.utils.http.AfterCommit;

/**
 * OTP mode proof: records the verified OTP (masked phone only) and marks the challenge used once
 * the save commits, so it cannot prove a second consent.
 */
@Component
public class OtpProofWriter implements ConsentProofWriter {

	static final String VERIFIED = "VERIFIED";

	private final OtpService otpService;

	private final ConsentEvidenceRepository repository;

	private final Clock clock;

	public OtpProofWriter(OtpService otpService, ConsentEvidenceRepository repository, Clock clock) {
		this.otpService = otpService;
		this.repository = repository;
		this.clock = clock;
	}

	@Override
	public void write(Consent consent, SaveConsentRequest request, AuthenticatedUser user) {
		if (request.mode() != ConsentMode.OTP || !ProofRows.anyGrant(request)) {
			return;
		}
		OtpChallenge challenge = otpService.requireUsable(request.proof().otpChallengeId().toString(),
				OtpReason.CONSENT, user);
		ConsentEvidence row = ProofRows.row(consent, EvidenceType.OTP, user, clock);
		row.setOtpStatus(VERIFIED);
		row.setMaskedPhone(challenge.maskedPhone());
		repository.save(row);
		AfterCommit.run(() -> otpService.markUsed(challenge.challengeId()));
	}
}
