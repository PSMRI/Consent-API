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

package com.iemr.consent.service.principal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.ArgumentCaptor;

import com.iemr.consent.data.consent.Consent;
import com.iemr.consent.data.consent.ConsentMode;
import com.iemr.consent.data.consent.ConsentPurpose;
import com.iemr.consent.data.consent.ConsentingParty;
import com.iemr.consent.data.consent.Decision;
import com.iemr.consent.data.consent.EventType;
import com.iemr.consent.data.consent.GuardianLink;
import com.iemr.consent.data.consent.PurposeStatus;
import com.iemr.consent.data.evidence.ConsentEvidence;
import com.iemr.consent.data.evidence.EvidenceType;
import com.iemr.consent.data.notice.Notice;
import com.iemr.consent.data.notice.NoticeStatus;
import com.iemr.consent.model.otp.OtpReason;
import com.iemr.consent.model.principal.PreferenceRequest;
import com.iemr.consent.repository.consent.ConsentPurposeRepository;
import com.iemr.consent.repository.consent.ConsentRepository;
import com.iemr.consent.repository.consent.GuardianLinkRepository;
import com.iemr.consent.repository.evidence.ConsentEvidenceRepository;
import com.iemr.consent.repository.notice.NoticeRepository;
import com.iemr.consent.repository.notice.PurposeRepository;
import com.iemr.consent.service.audit.AuditLogService;
import com.iemr.consent.service.otp.OtpChallenge;
import com.iemr.consent.service.otp.OtpService;
import com.iemr.consent.support.ConsentFixtures;
import com.iemr.consent.support.LogCapture;
import com.iemr.consent.support.PrincipalFixtures;
import com.iemr.consent.support.TestFixtures;
import com.iemr.consent.utils.crypto.FieldEncryptor;
import com.iemr.consent.utils.crypto.Sha256;
import com.iemr.consent.utils.exception.ConsentApiException;
import com.iemr.consent.utils.exception.ErrorCode;

class PreferenceChangeWriterTest {

	private static final Long BEN = PrincipalFixtures.BEN;

	private static final String IP = "10.0.0.1";

	private static final UUID OTP_ID = UUID.fromString("3f2504e0-4f89-11d3-9a0c-0305e82c3301");

	@RegisterExtension
	final LogCapture logs = new LogCapture();

	private final FieldEncryptor encryptor = new FieldEncryptor(TestFixtures.consentProperties());

	private ConsentRepository consents;

	private ConsentPurposeRepository decisions;

	private GuardianLinkRepository links;

	private NoticeRepository notices;

	private OtpService otp;

	private ConsentEvidenceRepository evidence;

	private AuditLogService audit;

	private PreferenceChangeWriter writer;

	private final List<ConsentPurpose> saved = new ArrayList<>();

	private final Consent source = PrincipalFixtures.event("c-1", TestFixtures.NOW.minusSeconds(3600));

	@BeforeEach
	void setUp() {
		consents = mock(ConsentRepository.class);
		decisions = mock(ConsentPurposeRepository.class);
		links = mock(GuardianLinkRepository.class);
		notices = mock(NoticeRepository.class);
		otp = mock(OtpService.class);
		evidence = mock(ConsentEvidenceRepository.class);
		audit = mock(AuditLogService.class);
		PurposeRepository purposes = mock(PurposeRepository.class);
		when(purposes.findByPurposeCodeInAndActiveTrueAndDeletedFalse(any())).thenReturn(ConsentFixtures.purposes());
		when(consents.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
		when(consents.findByConsentIdIn(any())).thenReturn(List.of(source));
		when(decisions.save(any())).thenAnswer(i -> {
			saved.add(i.getArgument(0));
			return i.getArgument(0);
		});
		when(notices.findById(ConsentFixtures.NOTICE_ID))
				.thenReturn(Optional.of(ConsentFixtures.notice(NoticeStatus.PUBLISHED)));
		when(otp.requireUsableFor(anyString(), any(), anyString(), any(), any())).thenReturn(challenge());
		PrincipalEventFactory events = new PrincipalEventFactory(consents, decisions, links, encryptor,
				TestFixtures.CLOCK);
		writer = new PreferenceChangeWriter(events, purposes, consents, notices, otp, evidence,
				new EffectiveStatus(TestFixtures.CLOCK), audit, TestFixtures.CLOCK);
	}

	private static OtpChallenge challenge() {
		return new OtpChallenge(OTP_ID.toString(), OtpReason.CONSENT, "enc", "hash", "******3210", "1001", 42L,
				TestFixtures.NOW, TestFixtures.NOW, false, 0);
	}

	private void current(ConsentPurpose... rows) {
		for (ConsentPurpose row : rows) {
			when(decisions.findCurrent(BEN, row.getPurposeCode())).thenReturn(Optional.of(row));
		}
	}

	private static PreferenceRequest request(Decision decision, String code, int version) {
		return new PreferenceRequest(List.of(code), decision, Map.of(code, version),
				decision == Decision.GRANT ? OTP_ID : null, null, null, null, null);
	}

	private void write(PreferenceRequest request) {
		writer.write(BEN, request, "key-1", "hash-1", ConsentFixtures.WORKER, IP);
	}

	private void assertRefused(PreferenceRequest request, ErrorCode code, String purposeCode) {
		assertThatThrownBy(() -> write(request)).isInstanceOf(ConsentApiException.class)
				.extracting("errorCode", "purposeCode").containsExactly(code, purposeCode);
		verify(consents, never()).saveAndFlush(any());
		verify(evidence, never()).save(any());
		verify(audit, never()).append(anyString(), any(), any(), any(), any(), any(), any());
	}

	@Test
	@DisplayName("C10: granting with a verified OTP gives the next version, a new end date and OTP proof")
	void grantWithOtpRecordsTheNextVersionAndProof() {
		current(PrincipalFixtures.row("c-1", "P-REM", PurposeStatus.DENIED, 1, null));

		write(request(Decision.GRANT, "P-REM", 1));

		verify(otp).requireUsableFor(OTP_ID.toString(), Set.of(OtpReason.CONSENT), "1001", ConsentFixtures.WORKER,
				null);
		ArgumentCaptor<Consent> event = ArgumentCaptor.forClass(Consent.class);
		verify(consents).saveAndFlush(event.capture());
		assertThat(event.getValue().getEventType()).isEqualTo(EventType.UPDATE);
		assertThat(event.getValue().getMode()).isEqualTo(ConsentMode.OTP);
		assertThat(saved).singleElement().satisfies(row -> {
			assertThat(row.getStatus()).isEqualTo(PurposeStatus.GRANTED);
			assertThat(row.getVersionNo()).isEqualTo(2);
			assertThat(row.getValidUntil()).isEqualTo(TestFixtures.NOW.plus(Duration.ofDays(365)));
		});
		ArgumentCaptor<ConsentEvidence> proof = ArgumentCaptor.forClass(ConsentEvidence.class);
		verify(evidence).save(proof.capture());
		assertThat(proof.getValue().getType()).isEqualTo(EvidenceType.OTP);
		assertThat(proof.getValue().getOtpStatus()).isEqualTo(PreferenceChangeWriter.VERIFIED);
		assertThat(proof.getValue().getMaskedPhone()).isEqualTo("******3210");
		assertThat(proof.getValue().getConsentId()).isEqualTo(event.getValue().getConsentId());
		verify(otp).markUsed(OTP_ID.toString());
		verify(audit).append(AuditLogService.PREFERENCE_CHANGED, event.getValue().getConsentId(), BEN,
				"P-REM:GRANTED", "GRANTED", 42L, IP);
	}

	@Test
	void grantOfAPurposeWithoutValidityHasNoEndDate() {
		current(PrincipalFixtures.row("c-1", "P-REG", PurposeStatus.WITHDRAWN, 2, null));
		write(request(Decision.GRANT, "P-REG", 2));
		assertThat(saved).singleElement().extracting(ConsentPurpose::getValidUntil).isNull();
	}

	@Test
	void grantOfAPurposeMissingFromTheCatalogueHasNoEndDate() {
		current(PrincipalFixtures.row("c-1", "P-OLD", PurposeStatus.DENIED, 1, null));
		write(request(Decision.GRANT, "P-OLD", 1));
		assertThat(saved).singleElement().extracting(ConsentPurpose::getValidUntil).isNull();
	}

	@Test
	void denyNeedsNoOtpAndHasNoEndDate() {
		current(PrincipalFixtures.row("c-1", "P-REM", PurposeStatus.GRANTED, 2, TestFixtures.NOW.plusSeconds(60)));

		write(request(Decision.DENY, "P-REM", 2));

		verifyNoInteractions(otp, evidence);
		assertThat(saved).singleElement().satisfies(row -> {
			assertThat(row.getStatus()).isEqualTo(PurposeStatus.DENIED);
			assertThat(row.getVersionNo()).isEqualTo(3);
			assertThat(row.getValidUntil()).isNull();
		});
		ArgumentCaptor<Consent> event = ArgumentCaptor.forClass(Consent.class);
		verify(consents).saveAndFlush(event.capture());
		assertThat(event.getValue().getMode()).isNull();
		verify(audit).append(AuditLogService.PREFERENCE_CHANGED, event.getValue().getConsentId(), BEN,
				"P-REM:DENIED", "DENIED", 42L, IP);
	}

	@Test
	void aWithdrawnPurposeCanBeDenied() {
		current(PrincipalFixtures.row("c-1", "P-REG", PurposeStatus.WITHDRAWN, 2, null));
		write(request(Decision.DENY, "P-REG", 2));
		assertThat(saved).singleElement().extracting(ConsentPurpose::getStatus).isEqualTo(PurposeStatus.DENIED);
	}

	@Test
	@DisplayName("T9: a guardian's consent needs the guardian's OTP from the guardian's phone")
	void guardianGrantNeedsTheGuardiansOtp() {
		source.setConsentingParty(ConsentingParty.GUARDIAN);
		GuardianLink link = new GuardianLink();
		link.setGuardianPhoneEncrypted(encryptor.encrypt(ConsentFixtures.GUARDIAN_PHONE));
		when(links.findFirstByConsentIdAndDeletedFalse("c-1")).thenReturn(Optional.of(link));
		current(PrincipalFixtures.row("c-1", "P-REM", PurposeStatus.DENIED, 1, null));

		write(request(Decision.GRANT, "P-REM", 1));

		verify(otp).requireUsableFor(OTP_ID.toString(), Set.of(OtpReason.GUARDIAN), "1001", ConsentFixtures.WORKER,
				Sha256.hex(ConsentFixtures.GUARDIAN_PHONE));
		ArgumentCaptor<Consent> event = ArgumentCaptor.forClass(Consent.class);
		verify(consents).saveAndFlush(event.capture());
		assertThat(event.getValue().getConsentingParty()).isEqualTo(ConsentingParty.GUARDIAN);
		logs.assertNoneContain(ConsentFixtures.GUARDIAN_PHONE, "******3210");
	}

	@Test
	void guardianWithoutAPhoneCannotGrant() {
		source.setConsentingParty(ConsentingParty.GUARDIAN);
		when(links.findFirstByConsentIdAndDeletedFalse("c-1")).thenReturn(Optional.of(new GuardianLink()));
		current(PrincipalFixtures.row("c-1", "P-REM", PurposeStatus.DENIED, 1, null));
		assertRefused(request(Decision.GRANT, "P-REM", 1), ErrorCode.OTP_INVALID, null);
	}

	@Test
	void anUnusableOtpChangesNothing() {
		current(PrincipalFixtures.row("c-1", "P-REM", PurposeStatus.DENIED, 1, null));
		when(otp.requireUsableFor(anyString(), any(), anyString(), any(), any()))
				.thenThrow(new ConsentApiException(ErrorCode.OTP_INVALID));
		assertRefused(request(Decision.GRANT, "P-REM", 1), ErrorCode.OTP_INVALID, null);
	}

	@Test
	@DisplayName("C14 / C16: a purpose needing re-consent goes back to the consent screen")
	void grantRefusedWhenReconsentIsNeeded() {
		current(PrincipalFixtures.row("c-1", "P-REM", PurposeStatus.RECONSENT_REQUIRED, 1, null));
		assertRefused(request(Decision.GRANT, "P-REM", 1), ErrorCode.BUSINESS_RULE_VIOLATION, "P-REM");

		current(PrincipalFixtures.row("c-1", "P-REM", PurposeStatus.GRANTED, 1, TestFixtures.NOW.minusSeconds(1)));
		assertRefused(request(Decision.GRANT, "P-REM", 1), ErrorCode.BUSINESS_RULE_VIOLATION, "P-REM");

		Notice newer = ConsentFixtures.notice(NoticeStatus.PUBLISHED);
		newer.setVersionNo(2);
		newer.setMaterial(true);
		when(notices.findNewerMaterial("MMU-REGISTRATION", 1)).thenReturn(List.of(newer));
		current(PrincipalFixtures.row("c-1", "P-REM", PurposeStatus.DENIED, 1, null));
		assertRefused(request(Decision.GRANT, "P-REM", 1), ErrorCode.BUSINESS_RULE_VIOLATION, "P-REM");
	}

	@Test
	void aMissingSourceNoticeIsNotTreatedAsSuperseded() {
		when(notices.findById(ConsentFixtures.NOTICE_ID)).thenReturn(Optional.empty());
		current(PrincipalFixtures.row("c-1", "P-REM", PurposeStatus.DENIED, 1, null));
		write(request(Decision.GRANT, "P-REM", 1));
		assertThat(saved).hasSize(1);
	}

	@Test
	void denyOfAGrantedPurposeThatCannotBeWithdrawnIsRefused() {
		current(PrincipalFixtures.row("c-1", "P-REG", PurposeStatus.GRANTED, 1, null));
		assertRefused(request(Decision.DENY, "P-REG", 1), ErrorCode.PURPOSE_NOT_WITHDRAWABLE, "P-REG");

		current(PrincipalFixtures.row("c-1", "P-OLD", PurposeStatus.GRANTED, 1, null));
		assertRefused(request(Decision.DENY, "P-OLD", 1), ErrorCode.PURPOSE_NOT_WITHDRAWABLE, "P-OLD");
	}

	@Test
	void sameDecisionAsNowIsAStateConflict() {
		current(PrincipalFixtures.row("c-1", "P-REM", PurposeStatus.GRANTED, 1, null));
		assertRefused(request(Decision.GRANT, "P-REM", 1), ErrorCode.STATE_CONFLICT, "P-REM");

		current(PrincipalFixtures.row("c-1", "P-REM", PurposeStatus.DENIED, 1, null));
		assertRefused(request(Decision.DENY, "P-REM", 1), ErrorCode.STATE_CONFLICT, "P-REM");
	}

	@Test
	@DisplayName("C20: a stale version changes nothing")
	void staleVersionChangesNothing() {
		current(PrincipalFixtures.row("c-1", "P-REM", PurposeStatus.DENIED, 2, null));
		assertRefused(request(Decision.GRANT, "P-REM", 1), ErrorCode.STALE_VERSION, null);
	}
}
