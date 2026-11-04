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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.iemr.consent.data.consent.Consent;
import com.iemr.consent.data.consent.ConsentPurpose;
import com.iemr.consent.data.consent.Decision;
import com.iemr.consent.data.consent.DeclarationWording;
import com.iemr.consent.data.consent.PurposeStatus;
import com.iemr.consent.data.consent.SyncStatus;
import com.iemr.consent.data.notify.NotifyChannel;
import com.iemr.consent.model.consent.NotifyInput;
import com.iemr.consent.model.consent.PurposeResult;
import com.iemr.consent.model.consent.SaveConsentRequest;
import com.iemr.consent.model.consent.SaveConsentResult;
import com.iemr.consent.repository.consent.ConsentPurposeRepository;
import com.iemr.consent.repository.consent.ConsentRepository;
import com.iemr.consent.service.audit.AuditLogService;
import com.iemr.consent.service.notify.ConsentNotifier;
import com.iemr.consent.support.ConsentFixtures;
import com.iemr.consent.support.TestFixtures;
import com.iemr.consent.utils.auth.AuthenticatedUser;

class ConsentWriterTest {

	private ConsentRepository consents;

	private ConsentPurposeRepository purposes;

	private ConsentProofWriter proofWriter;

	private AuditLogService audit;

	private ConsentWriter writer;

	private DeclarationProofWriter declarationWriter;

	private ConsentNotifier notifier;

	private final ValidatedConsent validated = new ValidatedConsent(ConsentFixtures.publishedNotice(), List.of(),
			null);

	@BeforeEach
	void setUp() {
		consents = mock(ConsentRepository.class);
		purposes = mock(ConsentPurposeRepository.class);
		proofWriter = mock(ConsentProofWriter.class);
		audit = mock(AuditLogService.class);
		declarationWriter = mock(DeclarationProofWriter.class);
		when(consents.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
		when(purposes.findCurrent(any(), anyString())).thenReturn(Optional.empty());
		notifier = mock(ConsentNotifier.class);
		writer = new ConsentWriter(consents, purposes, List.of(proofWriter), declarationWriter, notifier, audit,
				TestFixtures.CLOCK,
				TestFixtures.consentProperties());
	}

	private SaveConsentResult write(SaveConsentRequest request) {
		return writer.write(request, validated, "key-1", "hash-1", ConsentFixtures.WORKER, "10.0.0.5");
	}

	private Consent savedConsent() {
		ArgumentCaptor<Consent> saved = ArgumentCaptor.forClass(Consent.class);
		verify(consents).saveAndFlush(saved.capture());
		return saved.getValue();
	}

	private List<ConsentPurpose> savedPurposes() {
		ArgumentCaptor<ConsentPurpose> saved = ArgumentCaptor.forClass(ConsentPurpose.class);
		verify(purposes, org.mockito.Mockito.atLeastOnce()).save(saved.capture());
		return saved.getAllValues();
	}

	@Test
	@DisplayName("C1 / H1: everything granted, worker from the login, audit written")
	void allGranted() {
		SaveConsentResult result = write(ConsentFixtures.request().build());

		assertThat(result.syncStatus()).isEqualTo(SyncStatus.SYNCED);
		assertThat(result.purposes()).containsExactly(new PurposeResult("P-REG", PurposeStatus.GRANTED, 1),
				new PurposeResult("P-REM", PurposeStatus.GRANTED, 1));
		Consent consent = savedConsent();
		assertThat(consent.getCapturedBy()).as("login, not the request's 999").isEqualTo(42L);
		assertThat(consent.getServerReceivedAt()).isEqualTo(TestFixtures.NOW);
		assertThat(consent.isCaptureTimeFlagged()).isFalse();
		assertThat(consent.getRequestKey()).isEqualTo("key-1");
		assertThat(consent.getRequestHash()).isEqualTo("hash-1");
		assertThat(consent.getProjectId()).isEqualTo(12);
		assertThat(consent.getConfigVersion()).isEqualTo(3);
		List<ConsentPurpose> rows = savedPurposes();
		assertThat(rows.get(0).getValidUntil()).as("P-REG has no expiry").isNull();
		assertThat(rows.get(1).getValidUntil()).isEqualTo(TestFixtures.NOW.plus(Duration.ofDays(365)));
		assertThat(rows).allMatch(ConsentPurpose::isCurrent);
		verify(proofWriter).write(consent, ConsentFixtures.request().build(), ConsentFixtures.WORKER);
		verify(notifier).record(consent.getConsentId(), null, ConsentFixtures.WORKER);
		verify(audit).append(AuditLogService.CONSENT_CAPTURED, consent.getConsentId(), 1001L,
				"P-REG:GRANTED,P-REM:GRANTED", "GRANTED", 42L, "10.0.0.5");
	}

	@Test
	@DisplayName("C2 (004): the declaration is stored and its hash prefix is in the audit summary")
	void declarationIsStoredAndInTheAuditChain() {
		DeclarationCheck declaration = new DeclarationCheck("I agree", DeclarationWording.PATIENT, true,
				"0123456789abcdef" + "f".repeat(48));
		SaveConsentRequest request = ConsentFixtures.request().build();

		writer.write(request, new ValidatedConsent(ConsentFixtures.publishedNotice(), List.of(), declaration), "key-1",
				"hash-1", ConsentFixtures.WORKER, "10.0.0.5");

		Consent consent = savedConsent();
		verify(declarationWriter).write(consent, declaration, ConsentFixtures.WORKER);
		verify(audit).append(AuditLogService.CONSENT_CAPTURED, consent.getConsentId(), 1001L,
				"P-REG:GRANTED,P-REM:GRANTED;D=0123456789abcdef", "GRANTED", 42L, "10.0.0.5");
	}

	@Test
	@DisplayName("U2: with 16 purposes and a declaration the audit summary still fits 255 characters")
	void auditSummaryFitsWithSixteenPurposes() {
		List<PurposeResult> results = java.util.stream.IntStream.rangeClosed(1, 16)
				.mapToObj(i -> new PurposeResult(String.format("P-%03d", i), PurposeStatus.GRANTED, 1)).toList();
		DeclarationCheck declaration = new DeclarationCheck("I agree", DeclarationWording.PATIENT, false,
				"a".repeat(64));

		assertThat(ConsentWriter.summary(results, declaration)).hasSizeLessThanOrEqualTo(255).endsWith(";D=" + "a".repeat(16));
		assertThat(ConsentWriter.summary(results, null)).doesNotContain(";D=");
	}

	@Test
	@DisplayName("C1 (004): a requested SMS is recorded inside the save transaction")
	void notifyIsRecordedInTheSave() {
		NotifyInput notify = new NotifyInput(NotifyChannel.SMS, "9876543210");
		write(ConsentFixtures.request().notifyBy(notify).build());

		String consentId = savedConsent().getConsentId();
		verify(notifier).record(consentId, notify, ConsentFixtures.WORKER);
	}

	@Test
	@DisplayName("C2: partial consent")
	void partial() {
		SaveConsentResult result = write(ConsentFixtures.request().decisions(Decision.GRANT, Decision.DENY).build());

		assertThat(result.purposes()).extracting(PurposeResult::status).containsExactly(PurposeStatus.GRANTED,
				PurposeStatus.DENIED);
		assertThat(savedPurposes().get(1).getValidUntil()).isNull();
		verify(audit).append(eq(AuditLogService.CONSENT_CAPTURED), anyString(), eq(1001L),
				eq("P-REG:GRANTED,P-REM:DENIED"), eq("PARTIAL"), eq(42L), eq("10.0.0.5"));
	}

	@Test
	@DisplayName("C3: decline")
	void decline() {
		write(ConsentFixtures.request().mode(null).decisions(Decision.DENY, Decision.DENY).build());

		verify(audit).append(eq(AuditLogService.CONSENT_CAPTURED), anyString(), eq(1001L),
				eq("P-REG:DENIED,P-REM:DENIED"), eq("DENIED"), eq(42L), eq("10.0.0.5"));
	}

	@Test
	void earlierCurrentDecisionIsSupersededAndVersionGoesUp() {
		ConsentPurpose previous = new ConsentPurpose();
		previous.setCurrent(true);
		previous.setStatus(PurposeStatus.GRANTED);
		previous.setVersionNo(2);
		when(purposes.findCurrent(1001L, "P-REM")).thenReturn(Optional.of(previous));

		SaveConsentResult result = write(ConsentFixtures.request().build());

		assertThat(previous.isCurrent()).isFalse();
		assertThat(previous.getStatus()).isEqualTo(PurposeStatus.SUPERSEDED);
		verify(purposes).saveAndFlush(previous);
		assertThat(result.purposes().get(1).versionNo()).isEqualTo(3);
		assertThat(result.purposes().get(0).versionNo()).isEqualTo(1);
	}

	@Test
	void consentBeforeRegistrationIsStoredUnlinked() {
		write(ConsentFixtures.request().beneficiary(null).build());

		verify(purposes, never()).findCurrent(any(), anyString());
		assertThat(savedPurposes()).allMatch(row -> row.getBeneficiaryRegId() == null && row.getVersionNo() == 1);
	}

	@Test
	void deviceTimeFarFromServerTimeIsFlaggedNotRefused() {
		write(ConsentFixtures.request().capturedAt(TestFixtures.NOW.minus(Duration.ofDays(2))).build());
		assertThat(savedConsent().isCaptureTimeFlagged()).isTrue();
	}

	@Test
	void mobileUserWithoutNameStillWorks() {
		AuthenticatedUser mobile = AuthenticatedUser.of(7, null, com.iemr.consent.utils.auth.ClientType.MOBILE);
		writer.write(ConsentFixtures.request().build(), validated, "k", "h", mobile, null);
		assertThat(savedConsent().getCapturedBy()).isEqualTo(7L);
		assertThat(mobile.auditName()).isEqualTo("7");
	}
}
