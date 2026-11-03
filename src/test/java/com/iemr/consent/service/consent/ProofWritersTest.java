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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.iemr.consent.data.consent.Consent;
import com.iemr.consent.data.consent.ConsentMode;
import com.iemr.consent.data.consent.Decision;
import com.iemr.consent.data.consent.GuardianLink;
import com.iemr.consent.data.consent.GuardianVerification;
import com.iemr.consent.data.evidence.ConsentEvidence;
import com.iemr.consent.data.evidence.EvidenceType;
import com.iemr.consent.model.consent.GuardianInfo;
import com.iemr.consent.model.consent.Proof;
import com.iemr.consent.model.otp.OtpReason;
import com.iemr.consent.repository.consent.GuardianLinkRepository;
import com.iemr.consent.repository.evidence.ConsentEvidenceRepository;
import com.iemr.consent.service.otp.OtpChallenge;
import com.iemr.consent.service.otp.OtpService;
import com.iemr.consent.support.ConsentFixtures;
import com.iemr.consent.support.TestFixtures;
import com.iemr.consent.utils.crypto.FieldEncryptor;
import com.iemr.consent.utils.exception.ConsentApiException;
import com.iemr.consent.utils.exception.ErrorCode;

class ProofWritersTest {

	private final FieldEncryptor encryptor = new FieldEncryptor(TestFixtures.consentProperties());

	private ConsentEvidenceRepository evidence;

	private OtpService otp;

	private final Consent consent = new Consent();

	@BeforeEach
	void setUp() {
		evidence = mock(ConsentEvidenceRepository.class);
		otp = mock(OtpService.class);
		consent.setConsentId("c-1");
		consent.setBeneficiaryRegId(1001L);
	}

	private List<ConsentEvidence> savedEvidence() {
		ArgumentCaptor<ConsentEvidence> saved = ArgumentCaptor.forClass(ConsentEvidence.class);
		verify(evidence, org.mockito.Mockito.atLeastOnce()).save(saved.capture());
		return saved.getAllValues();
	}

	private static OtpChallenge challenge(String id) {
		return new OtpChallenge(id, OtpReason.GUARDIAN, "enc", "hash", "******3210", null, 42L, Instant.EPOCH,
				TestFixtures.NOW, false, 0);
	}

	@Test
	@DisplayName("M3: files are linked once; witness name is stored encrypted")
	void evidenceWriterLinksFilesAndWritesWitness() {
		EvidenceProofWriter writer = new EvidenceProofWriter(evidence, encryptor, TestFixtures.CLOCK);
		UUID clip = UUID.randomUUID();
		UUID guardianPhoto = UUID.randomUUID();
		when(evidence.linkToConsent(anyString(), anyString())).thenReturn(1);
		GuardianInfo guardian = new GuardianInfo(ConsentFixtures.guardianOtp(null).guardianType(), "Sita", "Mother",
				null, GuardianVerification.ID_SIGHTED, null, "Gita", List.of(guardianPhoto, clip));

		writer.write(consent, ConsentFixtures.request().mode(ConsentMode.WITNESSED).evidence(List.of(clip))
				.proof(new Proof(true, "Gita Bai", "Neighbour", null)).guardian(guardian).build(),
				ConsentFixtures.WORKER);

		verify(evidence).linkToConsent(clip.toString(), "c-1");
		verify(evidence).linkToConsent(guardianPhoto.toString(), "c-1");
		List<ConsentEvidence> rows = savedEvidence();
		assertThat(rows).extracting(ConsentEvidence::getType).containsExactly(EvidenceType.WITNESS,
				EvidenceType.WORKER_CONFIRMATION);
		assertThat(rows.get(0).getWitnessNameEncrypted()).isNotEqualTo("Gita Bai");
		assertThat(encryptor.decrypt(rows.get(0).getWitnessNameEncrypted())).isEqualTo("Gita Bai");
		assertThat(rows.get(0).getCreatedBy()).isEqualTo("worker42");
	}

	@Test
	void evidenceWriterWithoutProofOrFilesDoesNothing() {
		new EvidenceProofWriter(evidence, encryptor, TestFixtures.CLOCK).write(consent,
				ConsentFixtures.request().build(), ConsentFixtures.WORKER);
		new EvidenceProofWriter(evidence, encryptor, TestFixtures.CLOCK).write(consent,
				ConsentFixtures.request().proof(new Proof(false, " ", null, null)).build(), ConsentFixtures.WORKER);
		verify(evidence, never()).save(any());
		new EvidenceProofWriter(evidence, encryptor, TestFixtures.CLOCK).write(consent, ConsentFixtures.request()
				.guardian(ConsentFixtures.guardianFallback(GuardianVerification.UNVERIFIED, "W")).build(),
				ConsentFixtures.WORKER);
		verify(evidence, never()).linkToConsent(anyString(), anyString());
	}

	@Test
	void evidenceAlreadyLinkedByAnotherSaveIsAConflict() {
		when(evidence.linkToConsent(anyString(), anyString())).thenReturn(0);

		assertThatThrownBy(() -> new EvidenceProofWriter(evidence, encryptor, TestFixtures.CLOCK).write(consent,
				ConsentFixtures.request().evidence(List.of(UUID.randomUUID())).build(), ConsentFixtures.WORKER))
				.isInstanceOf(ConsentApiException.class).extracting("errorCode").isEqualTo(ErrorCode.STATE_CONFLICT);
	}

	@Test
	void otpWriterRecordsMaskedPhoneAndMarksTheChallengeUsedAfterCommit() {
		OtpProofWriter writer = new OtpProofWriter(otp, evidence, TestFixtures.CLOCK);
		UUID id = UUID.randomUUID();
		when(otp.requireUsable(id.toString(), OtpReason.CONSENT, ConsentFixtures.WORKER))
				.thenReturn(challenge(id.toString()));
		TransactionSynchronizationManager.initSynchronization();
		try {
			writer.write(consent, ConsentFixtures.request().mode(ConsentMode.OTP)
					.proof(new Proof(null, null, null, id)).build(), ConsentFixtures.WORKER);
			verify(otp, never()).markUsed(anyString());
			TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
		} finally {
			TransactionSynchronizationManager.clearSynchronization();
		}
		verify(otp).markUsed(id.toString());
		ConsentEvidence row = savedEvidence().get(0);
		assertThat(row.getType()).isEqualTo(EvidenceType.OTP);
		assertThat(row.getMaskedPhone()).isEqualTo("******3210");
		assertThat(row.getOtpStatus()).isEqualTo("VERIFIED");
	}

	@Test
	void otpWriterSkipsOtherModesAndDeclines() {
		OtpProofWriter writer = new OtpProofWriter(otp, evidence, TestFixtures.CLOCK);
		writer.write(consent, ConsentFixtures.request().build(), ConsentFixtures.WORKER);
		writer.write(consent, ConsentFixtures.request().mode(ConsentMode.OTP).decisions(Decision.DENY, Decision.DENY)
				.build(), ConsentFixtures.WORKER);
		verifyNoInteractions(otp, evidence);
	}

	@Test
	@DisplayName("C6: guardian OTP link with encrypted name and phone")
	void guardianOtpLink() {
		GuardianLinkRepository links = mock(GuardianLinkRepository.class);
		GuardianProofWriter writer = new GuardianProofWriter(links, evidence, otp, encryptor, TestFixtures.CLOCK);
		UUID id = UUID.randomUUID();
		when(otp.requireUsable(id.toString(), OtpReason.GUARDIAN, ConsentFixtures.WORKER))
				.thenReturn(challenge(id.toString()));

		writer.write(consent, ConsentFixtures.request().guardian(ConsentFixtures.guardianOtp(id)).build(),
				ConsentFixtures.WORKER);

		ArgumentCaptor<GuardianLink> saved = ArgumentCaptor.forClass(GuardianLink.class);
		verify(links).save(saved.capture());
		GuardianLink link = saved.getValue();
		assertThat(encryptor.decrypt(link.getGuardianNameEncrypted())).isEqualTo("Sita Devi");
		assertThat(encryptor.decrypt(link.getGuardianPhoneEncrypted())).isEqualTo(ConsentFixtures.GUARDIAN_PHONE);
		assertThat(link.getGuardianPhoneEncrypted()).doesNotContain(ConsentFixtures.GUARDIAN_PHONE);
		assertThat(link.isFollowUpRequired()).isFalse();
		assertThat(link.getVerifiedAt()).isEqualTo(TestFixtures.NOW);
		assertThat(link.getBeneficiaryRegId()).isEqualTo(1001L);
		assertThat(savedEvidence()).extracting(ConsentEvidence::getType).containsExactly(EvidenceType.OTP);
		verify(otp).markUsed(id.toString());
	}

	@Test
	@DisplayName("C7 / C8: fallback verification is flagged for follow-up, with an encrypted witness")
	void guardianFallbackLink() {
		GuardianLinkRepository links = mock(GuardianLinkRepository.class);
		GuardianProofWriter writer = new GuardianProofWriter(links, evidence, otp, encryptor, TestFixtures.CLOCK);

		writer.write(consent, ConsentFixtures.request()
				.guardian(ConsentFixtures.guardianFallback(GuardianVerification.ID_SIGHTED, "Gita Bai")).build(),
				ConsentFixtures.WORKER);

		ArgumentCaptor<GuardianLink> saved = ArgumentCaptor.forClass(GuardianLink.class);
		verify(links).save(saved.capture());
		assertThat(saved.getValue().isFollowUpRequired()).isTrue();
		assertThat(saved.getValue().getGuardianPhoneEncrypted()).isNull();
		ConsentEvidence witness = savedEvidence().get(0);
		assertThat(witness.getType()).isEqualTo(EvidenceType.GUARDIAN_WITNESS);
		assertThat(encryptor.decrypt(witness.getWitnessNameEncrypted())).isEqualTo("Gita Bai");
		verifyNoInteractions(otp);
	}

	@Test
	void guardianWriterSkipsSelfConsentAndBlankWitness() {
		GuardianLinkRepository links = mock(GuardianLinkRepository.class);
		GuardianProofWriter writer = new GuardianProofWriter(links, evidence, otp, encryptor, TestFixtures.CLOCK);
		writer.write(consent, ConsentFixtures.request().build(), ConsentFixtures.WORKER);
		verifyNoInteractions(links);
		writer.write(consent, ConsentFixtures.request()
				.guardian(ConsentFixtures.guardianFallback(GuardianVerification.UNVERIFIED, " ")).build(),
				ConsentFixtures.WORKER);
		verify(evidence, never()).save(any());
	}
}
