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
import com.iemr.consent.data.consent.ConsentingParty;
import com.iemr.consent.data.consent.GuardianLink;
import com.iemr.consent.data.consent.GuardianVerification;
import com.iemr.consent.data.evidence.ConsentEvidence;
import com.iemr.consent.data.evidence.EvidenceType;
import com.iemr.consent.model.consent.GuardianInfo;
import com.iemr.consent.model.consent.SaveConsentRequest;
import com.iemr.consent.model.otp.OtpReason;
import com.iemr.consent.repository.consent.GuardianLinkRepository;
import com.iemr.consent.repository.evidence.ConsentEvidenceRepository;
import com.iemr.consent.service.otp.OtpChallenge;
import com.iemr.consent.service.otp.OtpService;
import com.iemr.consent.utils.auth.AuthenticatedUser;
import com.iemr.consent.utils.crypto.FieldEncryptor;
import com.iemr.consent.utils.http.AfterCommit;

/**
 * Records the guardian who consented (C6, C7, C8): name and phone encrypted; verification other
 * than OTP is flagged for follow-up (D-03); an OTP verification records the masked phone and marks
 * the challenge used after commit; a witness is recorded with an encrypted name.
 */
@Component
public class GuardianProofWriter implements ConsentProofWriter {

	private final GuardianLinkRepository guardianRepository;

	private final ConsentEvidenceRepository evidenceRepository;

	private final OtpService otpService;

	private final FieldEncryptor encryptor;

	private final Clock clock;

	public GuardianProofWriter(GuardianLinkRepository guardianRepository,
			ConsentEvidenceRepository evidenceRepository, OtpService otpService, FieldEncryptor encryptor,
			Clock clock) {
		this.guardianRepository = guardianRepository;
		this.evidenceRepository = evidenceRepository;
		this.otpService = otpService;
		this.encryptor = encryptor;
		this.clock = clock;
	}

	@Override
	public void write(Consent consent, SaveConsentRequest request, AuthenticatedUser user) {
		if (request.consentingParty() != ConsentingParty.GUARDIAN) {
			return;
		}
		GuardianInfo guardian = request.guardian();
		GuardianLink link = new GuardianLink();
		link.setConsentId(consent.getConsentId());
		link.setBeneficiaryRegId(consent.getBeneficiaryRegId());
		link.setGuardianType(guardian.guardianType());
		link.setGuardianNameEncrypted(encryptor.encrypt(guardian.name()));
		link.setGuardianPhoneEncrypted(guardian.phone() == null ? null : encryptor.encrypt(guardian.phone()));
		link.setRelationship(guardian.relationship());
		link.setVerificationMethod(guardian.verification());
		link.setFollowUpRequired(guardian.verification() != GuardianVerification.OTP);
		link.setCreatedBy(user.auditName());
		if (guardian.verification() == GuardianVerification.OTP) {
			OtpChallenge challenge = otpService.requireUsable(guardian.otpChallengeId().toString(),
					OtpReason.GUARDIAN, user);
			link.setVerifiedAt(challenge.verifiedAt());
			ConsentEvidence otp = ProofRows.row(consent, EvidenceType.OTP, user, clock);
			otp.setOtpStatus(OtpProofWriter.VERIFIED);
			otp.setMaskedPhone(challenge.maskedPhone());
			evidenceRepository.save(otp);
			AfterCommit.run(() -> otpService.markUsed(challenge.challengeId()));
		}
		if (!ConsentRules.blank(guardian.witnessName())) {
			ConsentEvidence witness = ProofRows.row(consent, EvidenceType.GUARDIAN_WITNESS, user, clock);
			witness.setWitnessNameEncrypted(encryptor.encrypt(guardian.witnessName()));
			evidenceRepository.save(witness);
		}
		guardianRepository.save(link);
	}
}
