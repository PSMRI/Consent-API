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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.iemr.consent.data.consent.ConsentMode;
import com.iemr.consent.data.consent.GuardianVerification;
import com.iemr.consent.data.evidence.ConsentEvidence;
import com.iemr.consent.data.evidence.EvidenceType;
import com.iemr.consent.model.consent.Proof;
import com.iemr.consent.model.consent.SaveConsentRequest;
import com.iemr.consent.model.otp.OtpReason;
import com.iemr.consent.service.otp.OtpChallenge;
import com.iemr.consent.service.otp.OtpService;
import com.iemr.consent.support.ConsentFixtures;
import com.iemr.consent.utils.crypto.Sha256;
import com.iemr.consent.utils.exception.ConsentApiException;
import com.iemr.consent.utils.exception.ErrorCode;

class ProofRulesTest {

	private static final List<ConsentEvidence> NONE = List.of();

	private static ConsentEvidence file(EvidenceType type) {
		ConsentEvidence e = new ConsentEvidence();
		e.setType(type);
		return e;
	}

	private static void assertInvalid(Runnable call, String field) {
		assertThatThrownBy(call::run).isInstanceOf(ConsentApiException.class).satisfies(e -> {
			ConsentApiException error = (ConsentApiException) e;
			assertThat(error.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED);
			assertThat(error.getDetails().get(0).field()).isEqualTo(field);
		});
	}

	private static OtpChallenge challenge(String phone) {
		return new OtpChallenge("c1", OtpReason.GUARDIAN, "enc", Sha256.hex(phone), "******3210", null, 42L,
				Instant.EPOCH, Instant.EPOCH, false, 0);
	}

	@Test
	void ruleModes() {
		OtpService otp = mock(OtpService.class);
		assertThat(List.of(new OtpProofRule(otp).mode(), new VoiceClipProofRule().mode(),
				new SignaturePhotoProofRule().mode(), new WitnessedProofRule().mode(), new ReadAloudProofRule().mode()))
				.containsExactly(ConsentMode.OTP, ConsentMode.VOICE_CLIP, ConsentMode.SIGNATURE_PHOTO,
						ConsentMode.WITNESSED, ConsentMode.READ_ALOUD);
	}

	@Test
	void otpModeNeedsAUsableConsentChallenge() {
		OtpService otp = mock(OtpService.class);
		OtpProofRule rule = new OtpProofRule(otp);
		UUID id = UUID.randomUUID();

		rule.check(ConsentFixtures.request().proof(new Proof(null, null, null, id)).build(), ConsentFixtures.WORKER,
				NONE);
		verify(otp).requireUsable(id.toString(), OtpReason.CONSENT, ConsentFixtures.WORKER);
		assertInvalid(() -> rule.check(ConsentFixtures.request().build(), ConsentFixtures.WORKER, NONE),
				"proof.otpChallengeId");
		assertInvalid(() -> rule.check(ConsentFixtures.request().proof(new Proof(true, null, null, null)).build(),
				ConsentFixtures.WORKER, NONE), "proof.otpChallengeId");
	}

	@Test
	@DisplayName("M3: voice-clip mode needs exactly one voice clip")
	void voiceClip() {
		VoiceClipProofRule rule = new VoiceClipProofRule();
		SaveConsentRequest request = ConsentFixtures.request().build();

		assertThatNoException().isThrownBy(() -> rule.check(request, ConsentFixtures.WORKER,
				List.of(file(EvidenceType.VOICE_CLIP), file(EvidenceType.PATIENT_PHOTO))));
		assertInvalid(() -> rule.check(request, ConsentFixtures.WORKER, NONE), "evidenceIds");
		assertInvalid(() -> rule.check(request, ConsentFixtures.WORKER,
				List.of(file(EvidenceType.VOICE_CLIP), file(EvidenceType.VOICE_CLIP))), "evidenceIds");
	}

	@Test
	@DisplayName("M3: signature-photo mode needs a photo")
	void signaturePhoto() {
		SignaturePhotoProofRule rule = new SignaturePhotoProofRule();
		SaveConsentRequest request = ConsentFixtures.request().build();

		assertThatNoException().isThrownBy(() -> rule.check(request, ConsentFixtures.WORKER,
				List.of(file(EvidenceType.SIGNED_FORM_PHOTO))));
		assertInvalid(() -> rule.check(request, ConsentFixtures.WORKER, List.of(file(EvidenceType.VOICE_CLIP))),
				"evidenceIds");
	}

	@Test
	@DisplayName("M3: witnessed mode needs witness name and relationship")
	void witnessed() {
		WitnessedProofRule rule = new WitnessedProofRule();

		assertThatNoException().isThrownBy(() -> rule.check(ConsentFixtures.request()
				.proof(new Proof(null, "Gita Bai", "Neighbour", null)).build(), ConsentFixtures.WORKER, NONE));
		for (Proof proof : new Proof[] { null, new Proof(null, null, "Neighbour", null),
			new Proof(null, "Gita", " ", null) }) {
			assertInvalid(() -> rule.check(ConsentFixtures.request().proof(proof).build(), ConsentFixtures.WORKER,
					NONE), "proof.witnessName");
		}
	}

	@Test
	@DisplayName("H2: read-aloud mode needs the worker's confirmation")
	void readAloud() {
		ReadAloudProofRule rule = new ReadAloudProofRule();

		assertThatNoException().isThrownBy(() -> rule.check(ConsentFixtures.request()
				.proof(new Proof(true, null, null, null)).build(), ConsentFixtures.WORKER, NONE));
		for (Proof proof : new Proof[] { null, new Proof(false, null, null, null), new Proof(null, null, null, null) }) {
			assertInvalid(() -> rule.check(ConsentFixtures.request().proof(proof).build(), ConsentFixtures.WORKER,
					NONE), "proof.workerConfirmed");
		}
	}

	@Test
	@DisplayName("C6/C8: guardian OTP needs a usable GUARDIAN challenge for the same phone")
	void guardianOtp() {
		OtpService otp = mock(OtpService.class);
		GuardianRule rule = new GuardianRule(otp);
		UUID id = UUID.randomUUID();
		when(otp.requireUsable(id.toString(), OtpReason.GUARDIAN, ConsentFixtures.WORKER))
				.thenReturn(challenge(ConsentFixtures.GUARDIAN_PHONE), challenge("9000000000"));

		rule.check(ConsentFixtures.request().guardian(ConsentFixtures.guardianOtp(id)).build(),
				ConsentFixtures.WORKER);
		assertThatThrownBy(() -> rule.check(ConsentFixtures.request().guardian(ConsentFixtures.guardianOtp(id))
				.build(), ConsentFixtures.WORKER)).isInstanceOf(ConsentApiException.class).extracting("errorCode")
				.isEqualTo(ErrorCode.OTP_INVALID);
		assertInvalid(() -> rule.check(ConsentFixtures.request().guardian(ConsentFixtures.guardianOtp(null))
				.build(), ConsentFixtures.WORKER), "guardian.otpChallengeId");
		var noPhone = new com.iemr.consent.model.consent.GuardianInfo(
				ConsentFixtures.guardianOtp(id).guardianType(), "Sita", "Mother", null, GuardianVerification.OTP, id,
				null, null);
		assertInvalid(() -> rule.check(ConsentFixtures.request().guardian(noPhone).build(), ConsentFixtures.WORKER),
				"guardian.otpChallengeId");
	}

	@Test
	@DisplayName("C7: guardian without OTP needs a witness")
	void guardianFallback() {
		GuardianRule rule = new GuardianRule(mock(OtpService.class));

		rule.check(ConsentFixtures.request()
				.guardian(ConsentFixtures.guardianFallback(GuardianVerification.ID_SIGHTED, "Gita Bai")).build(),
				ConsentFixtures.WORKER);
		assertInvalid(() -> rule.check(ConsentFixtures.request()
				.guardian(ConsentFixtures.guardianFallback(GuardianVerification.UNVERIFIED, null)).build(),
				ConsentFixtures.WORKER), "guardian.witnessName");
		assertInvalid(() -> rule.check(ConsentFixtures.request().party(
				com.iemr.consent.data.consent.ConsentingParty.GUARDIAN).build(), ConsentFixtures.WORKER), "guardian");
	}
}
