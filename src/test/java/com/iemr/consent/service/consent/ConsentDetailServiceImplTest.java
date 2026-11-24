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

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.iemr.consent.data.consent.Consent;
import com.iemr.consent.data.consent.ConsentingParty;
import com.iemr.consent.data.consent.Decision;
import com.iemr.consent.data.consent.GuardianLink;
import com.iemr.consent.data.consent.GuardianType;
import com.iemr.consent.data.consent.GuardianVerification;
import com.iemr.consent.data.consent.PurposeStatus;
import com.iemr.consent.data.evidence.ConsentEvidence;
import com.iemr.consent.data.evidence.EvidenceType;
import com.iemr.consent.data.notice.NoticeStatus;
import com.iemr.consent.data.notify.NotificationStatus;
import com.iemr.consent.model.consent.ConsentDetail;
import com.iemr.consent.model.consent.NotificationResult;
import com.iemr.consent.repository.consent.ConsentPurposeRepository;
import com.iemr.consent.repository.consent.ConsentRepository;
import com.iemr.consent.repository.consent.GuardianLinkRepository;
import com.iemr.consent.repository.evidence.ConsentEvidenceRepository;
import com.iemr.consent.repository.notice.NoticeRepository;
import com.iemr.consent.repository.notice.NoticeTranslationRepository;
import com.iemr.consent.repository.notice.PurposeRepository;
import com.iemr.consent.service.audit.AuditLogService;
import com.iemr.consent.service.notify.ConsentNotifier;
import com.iemr.consent.service.principal.EffectiveStatus;
import com.iemr.consent.support.ConsentFixtures;
import com.iemr.consent.support.PrincipalFixtures;
import com.iemr.consent.support.TestFixtures;
import com.iemr.consent.utils.crypto.FieldEncryptor;
import com.iemr.consent.utils.exception.ConsentApiException;
import com.iemr.consent.utils.exception.ErrorCode;

/** API 17: one consent in full, by consent id. */
class ConsentDetailServiceImplTest {

	private static final String ID = "26102948271";

	private static final Instant AT = TestFixtures.NOW.minus(Duration.ofDays(1));

	private ConsentRepository consents;

	private ConsentPurposeRepository decisions;

	private PurposeRepository purposes;

	private NoticeRepository notices;

	private NoticeTranslationRepository translations;

	private GuardianLinkRepository guardians;

	private ConsentEvidenceRepository evidence;

	private ConsentNotifier notifier;

	private AuditLogService audit;

	private FieldEncryptor encryptor;

	private ConsentDetailServiceImpl service;

	@BeforeEach
	void setUp() {
		consents = mock(ConsentRepository.class);
		decisions = mock(ConsentPurposeRepository.class);
		purposes = mock(PurposeRepository.class);
		notices = mock(NoticeRepository.class);
		translations = mock(NoticeTranslationRepository.class);
		guardians = mock(GuardianLinkRepository.class);
		evidence = mock(ConsentEvidenceRepository.class);
		notifier = mock(ConsentNotifier.class);
		audit = mock(AuditLogService.class);
		encryptor = new FieldEncryptor(TestFixtures.consentProperties());
		service = new ConsentDetailServiceImpl(consents, decisions, purposes, notices, translations, guardians,
				evidence, notifier, new EffectiveStatus(TestFixtures.CLOCK), encryptor, audit);
		when(notifier.stored(anyString())).thenReturn(NotificationResult.notRequested());
	}

	private void stored(Consent consent) {
		when(consents.findById(consent.getConsentId())).thenReturn(Optional.of(consent));
	}

	@Test
	void returnsThePatientNoticePurposesAndProofs() {
		stored(PrincipalFixtures.event(ID, AT));
		when(decisions.findByConsentIdOrderByConsentPurposeIdAsc(ID)).thenReturn(List.of(
				PrincipalFixtures.row(ID, "P-REM", PurposeStatus.DENIED, 1, null),
				PrincipalFixtures.row(ID, "P-REG", PurposeStatus.GRANTED, 1, null)));
		when(purposes.findByPurposeCodeInAndActiveTrueAndDeletedFalse(any())).thenReturn(ConsentFixtures.purposes());
		when(notices.findById(ConsentFixtures.NOTICE_ID))
				.thenReturn(Optional.of(ConsentFixtures.notice(NoticeStatus.PUBLISHED)));
		when(translations.findByNoticeVersionIdAndLanguageCodeAndDeletedFalse(ConsentFixtures.NOTICE_ID, "hi"))
				.thenReturn(Optional.of(ConsentFixtures.translation("hi", null)));
		ConsentEvidence clip = new ConsentEvidence();
		clip.setEvidenceId("e1");
		clip.setType(EvidenceType.VOICE_CLIP);
		clip.setFileRef("ref");
		clip.setContentType("audio/webm");
		clip.setDurationSec(BigDecimal.TEN);
		ConsentEvidence declaration = new ConsentEvidence();
		declaration.setEvidenceId("e2");
		declaration.setType(EvidenceType.DECLARATION);
		declaration.setDeclarationText(ConsentFixtures.DEFAULT_DECLARATION);
		when(evidence.findByConsentIdAndDeletedFalseOrderByCreatedAtAsc(ID)).thenReturn(List.of(clip, declaration));
		when(notifier.stored(ID)).thenReturn(new NotificationResult(NotificationStatus.SENT, "******3210"));

		ConsentDetail detail = service.detail(ID, ConsentFixtures.WORKER, "10.0.0.1");

		assertThat(detail.consentId()).isEqualTo(ID);
		assertThat(detail.beneficiaryRegId()).isEqualTo(PrincipalFixtures.BEN);
		assertThat(detail.capture().capturedBy()).isEqualTo(42L);
		assertThat(detail.capture().deviceId()).isEqualTo("van-07");
		assertThat(detail.notice().noticeCode()).isEqualTo("MMU-REGISTRATION");
		assertThat(detail.notice().dataItems()).isEqualTo("items");
		assertThat(detail.notice().recipients()).isEqualTo("recipients");
		assertThat(detail.purposes()).extracting(ConsentDetail.PurposeDecision::purposeCode)
				.containsExactly("P-REG", "P-REM");
		assertThat(detail.purposes().get(0).name()).isEqualTo("name P-REG");
		assertThat(detail.purposes().get(0).description()).isEqualTo("desc P-REG");
		assertThat(detail.purposes().get(0).decision()).isEqualTo(Decision.GRANT);
		assertThat(detail.purposes().get(1).status()).isEqualTo(PurposeStatus.DENIED);
		assertThat(detail.guardian()).isNull();
		assertThat(detail.proofs()).extracting(ConsentDetail.Proof::isFile).containsExactly(true, false);
		assertThat(detail.proofs().get(1).declarationText()).isEqualTo(ConsentFixtures.DEFAULT_DECLARATION);
		assertThat(detail.notification().status()).isEqualTo(NotificationStatus.SENT);
		verify(audit).append(AuditLogService.CONSENT_VIEWED, ID, PrincipalFixtures.BEN,
				"P-REG:GRANTED,P-REM:DENIED", "VIEWED", 42L, "10.0.0.1");
	}

	@Test
	void guardianAndWitnessNamesAreShownAndPhonesMasked() {
		Consent consent = PrincipalFixtures.event(ID, AT);
		consent.setConsentingParty(ConsentingParty.GUARDIAN);
		stored(consent);
		GuardianLink link = new GuardianLink();
		link.setGuardianType(GuardianType.PARENT);
		link.setGuardianNameEncrypted(encryptor.encrypt("Asha"));
		link.setGuardianPhoneEncrypted(encryptor.encrypt(ConsentFixtures.GUARDIAN_PHONE));
		link.setRelationship("MOTHER");
		link.setVerificationMethod(GuardianVerification.OTP);
		when(guardians.findFirstByConsentIdAndDeletedFalse(ID)).thenReturn(Optional.of(link));
		ConsentEvidence witness = new ConsentEvidence();
		witness.setType(EvidenceType.GUARDIAN_WITNESS);
		witness.setWitnessNameEncrypted(encryptor.encrypt("Ravi"));
		witness.setWitnessRelationship("NEIGHBOUR");
		when(evidence.findByConsentIdAndDeletedFalseOrderByCreatedAtAsc(ID)).thenReturn(List.of(witness));

		ConsentDetail detail = service.detail(ID, ConsentFixtures.WORKER, "ip");

		assertThat(detail.guardian().name()).isEqualTo("Asha");
		assertThat(detail.guardian().maskedPhone()).isEqualTo("******3210");
		assertThat(detail.guardian().relationship()).isEqualTo("MOTHER");
		assertThat(detail.proofs().get(0).witnessName()).isEqualTo("Ravi");
		assertThat(detail.proofs().get(0).isFile()).isFalse();
	}

	@Test
	void guardianWithoutPhoneAndMissingNoticeText() {
		stored(PrincipalFixtures.event(ID, AT));
		GuardianLink link = new GuardianLink();
		link.setGuardianNameEncrypted(encryptor.encrypt("Asha"));
		when(guardians.findFirstByConsentIdAndDeletedFalse(ID)).thenReturn(Optional.of(link));
		when(notices.findById(ConsentFixtures.NOTICE_ID))
				.thenReturn(Optional.of(ConsentFixtures.notice(NoticeStatus.RETIRED)));

		ConsentDetail detail = service.detail(ID, ConsentFixtures.WORKER, "ip");

		assertThat(detail.guardian().maskedPhone()).isNull();
		assertThat(detail.notice().noticeVersionId()).isEqualTo(ConsentFixtures.NOTICE_ID);
		assertThat(detail.notice().body()).isNull();
		assertThat(detail.notice().languageName()).isNull();
	}

	@Test
	void unknownNoticeOrNoneGivesNoNotice() {
		Consent consent = PrincipalFixtures.event(ID, AT);
		stored(consent);
		assertThat(service.detail(ID, ConsentFixtures.WORKER, "ip").notice()).isNull();
		consent.setNoticeVersionId(null);
		assertThat(service.detail(ID, ConsentFixtures.WORKER, "ip").notice()).isNull();
	}

	@Test
	void expiredCurrentGrantShowsExpiredAndReplacedRowsKeepTheirStatus() {
		stored(PrincipalFixtures.event(ID, AT));
		when(decisions.findByConsentIdOrderByConsentPurposeIdAsc(ID)).thenReturn(List.of(
				PrincipalFixtures.row(ID, "P-REM", PurposeStatus.GRANTED, 2, AT),
				PrincipalFixtures.row(ID, "P-RES", PurposeStatus.SUPERSEDED, 1, AT)));

		ConsentDetail detail = service.detail(ID, ConsentFixtures.WORKER, "ip");

		assertThat(detail.purposes().get(0).status()).isEqualTo(PurposeStatus.EXPIRED);
		assertThat(detail.purposes().get(0).current()).isTrue();
		assertThat(detail.purposes().get(1).status()).isEqualTo(PurposeStatus.SUPERSEDED);
		assertThat(detail.purposes().get(1).current()).isFalse();
		assertThat(detail.purposes().get(1).name()).isNull();
		assertThat(detail.purposes().get(1).isMandatory()).isFalse();
	}

	@Test
	void oldUuidIdsAreAcceptedInAnyCase() {
		String uuid = "3f2504e0-4f89-11d3-9a0c-0305e82c3301";
		stored(PrincipalFixtures.event(uuid, AT));
		assertThat(service.detail(uuid.toUpperCase(), ConsentFixtures.WORKER, "ip").consentId()).isEqualTo(uuid);
	}

	@Test
	void unknownOrMalformedIdsAreNotFoundAndNotAudited() {
		when(consents.findById(ID)).thenReturn(Optional.empty());
		assertThatThrownBy(() -> service.detail(ID, ConsentFixtures.WORKER, "ip"))
				.isInstanceOfSatisfying(ConsentApiException.class,
						e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND));
		assertThatThrownBy(() -> service.detail("x", ConsentFixtures.WORKER, "ip"))
				.isInstanceOf(ConsentApiException.class);
		verify(consents, never()).findById("x");
		verifyNoInteractions(audit);
	}
}
