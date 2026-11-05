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

package com.iemr.consent.service.legacy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.iemr.consent.data.consent.Channel;
import com.iemr.consent.data.consent.Consent;
import com.iemr.consent.data.consent.ConsentMode;
import com.iemr.consent.data.consent.ConsentingParty;
import com.iemr.consent.data.consent.Decision;
import com.iemr.consent.data.consent.EventType;
import com.iemr.consent.data.consent.PurposeStatus;
import com.iemr.consent.data.consent.SyncStatus;
import com.iemr.consent.data.evidence.ConsentEvidence;
import com.iemr.consent.data.evidence.EvidenceType;
import com.iemr.consent.data.legacy.LegacyBatch;
import com.iemr.consent.data.legacy.LegacyBatchStatus;
import com.iemr.consent.data.legacy.LegacyRow;
import com.iemr.consent.data.legacy.LegacyRowStatus;
import com.iemr.consent.data.notice.Notice;
import com.iemr.consent.data.notice.NoticeTranslation;
import com.iemr.consent.data.notice.Purpose;
import com.iemr.consent.model.consent.PurposeResult;
import com.iemr.consent.repository.consent.ConsentRepository;
import com.iemr.consent.repository.evidence.ConsentEvidenceRepository;
import com.iemr.consent.repository.legacy.LegacyRowRepository;
import com.iemr.consent.repository.notice.NoticeTranslationRepository;
import com.iemr.consent.repository.notice.PurposeRepository;
import com.iemr.consent.service.audit.AuditLogService;
import com.iemr.consent.service.consent.ConsentDecisionWriter;
import com.iemr.consent.support.ConsentFixtures;
import com.iemr.consent.support.TestFixtures;
import com.iemr.consent.utils.crypto.FieldEncryptor;

/** Feature 008, research R5: valid legacy rows become LEGACY consent events; newer consents win. */
class LegacyImporterTest {

	private static final LocalDate DAY = LocalDate.of(2025, 6, 12);

	private ConsentRepository consents;

	private ConsentDecisionWriter decisionWriter;

	private ConsentEvidenceRepository evidence;

	private LegacyRowRepository rows;

	private NoticeTranslationRepository translations;

	private PurposeRepository purposes;

	private AuditLogService audit;

	private final FieldEncryptor encryptor = new FieldEncryptor(TestFixtures.consentProperties());

	private LegacyImporter importer;

	private final LegacyBatch batch = LegacyFixtures.batch(LegacyBatchStatus.IMPORTED);

	private final Notice notice = new Notice();

	private final NoticeTranslation english = translation("en", "e");

	@BeforeEach
	void setUp() {
		consents = mock(ConsentRepository.class);
		decisionWriter = mock(ConsentDecisionWriter.class);
		evidence = mock(ConsentEvidenceRepository.class);
		rows = mock(LegacyRowRepository.class);
		translations = mock(NoticeTranslationRepository.class);
		purposes = mock(PurposeRepository.class);
		audit = mock(AuditLogService.class);
		notice.setNoticeVersionId(11L);
		when(translations.findByNoticeVersionIdAndDeletedFalseOrderByLanguageCodeAsc(11L))
				.thenReturn(List.of(english, translation("hi", "h")));
		when(purposes.findByPurposeCodeInAndActiveTrueAndDeletedFalse(anyCollection()))
				.thenAnswer(i -> ConsentFixtures.purposes().stream()
				.filter(p -> i.<Collection<String>>getArgument(0)
								.contains(p.getPurposeCode())).toList());
		when(consents.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
		when(consents.findFirstByBeneficiaryRegIdAndEventTypeOrderByCapturedAtDesc(any(), any()))
				.thenReturn(Optional.empty());
		when(decisionWriter.write(any(), anyString(), eq(Decision.GRANT), any()))
				.thenAnswer(i -> new PurposeResult(i.getArgument(1), PurposeStatus.GRANTED, 1));
		importer = new LegacyImporter(consents, decisionWriter, evidence, rows, translations, purposes,
				encryptor, audit, TestFixtures.CLOCK);
	}

	private static NoticeTranslation translation(String language, String hashChar) {
		NoticeTranslation t = new NoticeTranslation();
		t.setLanguageCode(language);
		t.setTextHash(hashChar.repeat(64));
		return t;
	}

	private List<LegacyRow> run(LegacyRow... batchRows) {
		when(rows.findByBatchIdOrderByRowNoAsc(LegacyFixtures.BATCH)).thenReturn(List.of(batchRows));
		return importer.importRows(batch, notice, english, ConsentFixtures.WORKER, "10.0.0.5");
	}

	private Consent savedConsent() {
		ArgumentCaptor<Consent> consent = ArgumentCaptor.forClass(Consent.class);
		verify(consents).saveAndFlush(consent.capture());
		return consent.getValue();
	}

	@Test
	void aValidRowIsStoredAsALegacyConsent() {
		LegacyRow row = LegacyFixtures.row(2, LegacyRowStatus.VALID, 7001L, DAY, "P-REG;P-REM");
		row.setRepresentativeNameEncrypted(encryptor.encrypt("Gita Bai"));
		row.setRepresentativeRelationship("Neighbour");

		run(row);

		Consent consent = savedConsent();
		assertThat(consent.getEventType()).isEqualTo(EventType.LEGACY);
		assertThat(consent.getConsentingParty()).isEqualTo(ConsentingParty.SELF);
		assertThat(consent.getNoticeVersionId()).isEqualTo(11L);
		assertThat(consent.getLanguageCode()).isEqualTo("hi");
		assertThat(consent.getTextHash()).isEqualTo("h".repeat(64));
		assertThat(consent.getChannel()).isEqualTo(Channel.FACILITY);
		assertThat(consent.getMode()).isEqualTo(ConsentMode.PAPER);
		assertThat(consent.getCapturedBy()).isEqualTo(42L);
		assertThat(consent.getDeviceId()).isEqualTo("legacy-import");
		assertThat(consent.getCapturedAt()).isEqualTo(Instant.parse("2025-06-11T18:30:00Z"));
		assertThat(consent.getServerReceivedAt()).isEqualTo(TestFixtures.NOW);
		assertThat(consent.isCaptureTimeFlagged()).isFalse();
		assertThat(consent.getSyncStatus()).isEqualTo(SyncStatus.SYNCED);
		assertThat(consent.getBeneficiaryRegId()).isEqualTo(7001L);
		assertThat(consent.getRequestKey()).isEqualTo(UUID.nameUUIDFromBytes((LegacyFixtures.BATCH + ":2")
				.getBytes(StandardCharsets.UTF_8)).toString());
		assertThat(consent.getRequestHash()).hasSize(64);

		verify(decisionWriter).write(eq(consent), eq("P-REG"), eq(Decision.GRANT), any());
		verify(decisionWriter).write(eq(consent), eq("P-REM"), eq(Decision.GRANT), any());

		ArgumentCaptor<ConsentEvidence> witness = ArgumentCaptor.forClass(ConsentEvidence.class);
		verify(evidence).save(witness.capture());
		assertThat(witness.getValue().getType()).isEqualTo(EvidenceType.WITNESS);
		assertThat(witness.getValue().getConsentId()).isEqualTo(consent.getConsentId());
		assertThat(witness.getValue().getWitnessNameEncrypted())
				.isNotEqualTo(row.getRepresentativeNameEncrypted())
				.doesNotContain("Gita");
		assertThat(encryptor.decrypt(witness.getValue().getWitnessNameEncrypted())).isEqualTo("Gita Bai");
		assertThat(witness.getValue().getWitnessRelationship()).isEqualTo("Neighbour");

		verify(audit).append(AuditLogService.LEGACY_IMPORTED, consent.getConsentId(), 7001L,
		"P-REG:GRANTED,P-REM:GRANTED;batch:" + LegacyFixtures.BATCH + ";row:2", "GRANTED", 42L,
						"10.0.0.5");
		assertThat(row.getStatus()).isEqualTo(LegacyRowStatus.IMPORTED);
		assertThat(row.getConsentId()).isEqualTo(consent.getConsentId());
		assertThat(row.getModifiedBy()).isEqualTo("worker42");
		verify(rows).saveAndFlush(row);
	}

	@Test
	void withoutARepresentativeThereIsNoWitnessAndALostLanguageUsesEnglish() {
		LegacyRow row = LegacyFixtures.row(2, LegacyRowStatus.VALID, 7001L, DAY, "P-REG");
		row.setLanguageCode("ta");

		run(row);

		Consent consent = savedConsent();
		assertThat(consent.getLanguageCode()).isEqualTo("ta");
		assertThat(consent.getTextHash()).isEqualTo("e".repeat(64));
		verify(evidence, never()).save(any());
	}

	@Test
	void aConsentTakenInTheAppWins() {
		when(consents.existsByBeneficiaryRegIdAndEventTypeNot(7001L, EventType.LEGACY)).thenReturn(true);
		LegacyRow row = LegacyFixtures.row(2, LegacyRowStatus.VALID, 7001L, DAY, "P-REG");

		run(row);

		assertThat(row.getStatus()).isEqualTo(LegacyRowStatus.SKIPPED);
		assertThat(row.getReasons()).isEqualTo("NEWER_CONSENT_EXISTS");
		verify(consents, never()).saveAndFlush(any());
		verify(audit, never()).append(any(), any(), any(), any(), any(), any(), any());
	}

	@Test
	void aNewerLegacyConsentWinsButAnOlderOneIsSuperseded() {
		Consent newer = new Consent();
		newer.setCapturedAt(Instant.parse("2025-07-01T00:00:00Z"));
		Consent older = new Consent();
		older.setCapturedAt(Instant.parse("2025-01-01T00:00:00Z"));
		when(consents.findFirstByBeneficiaryRegIdAndEventTypeOrderByCapturedAtDesc(7001L, EventType.LEGACY))
				.thenReturn(Optional.of(newer));
		when(consents.findFirstByBeneficiaryRegIdAndEventTypeOrderByCapturedAtDesc(7002L, EventType.LEGACY))
				.thenReturn(Optional.of(older));
		LegacyRow skipped = LegacyFixtures.row(2, LegacyRowStatus.VALID, 7001L, DAY, "P-REG");
		LegacyRow stored = LegacyFixtures.row(3, LegacyRowStatus.VALID, 7002L, DAY, "P-REG");

		run(skipped, stored);

		assertThat(skipped.getStatus()).isEqualTo(LegacyRowStatus.SKIPPED);
		assertThat(skipped.getReasons()).isEqualTo("NEWER_CONSENT_EXISTS");
		assertThat(stored.getStatus()).isEqualTo(LegacyRowStatus.IMPORTED);
	}

	@Test
	void importedMeanwhileOrAnInactivePurposeIsSkipped() {
		when(rows.existsByBeneficiaryRegIdAndConsentDateAndStatus(7001L, DAY, LegacyRowStatus.IMPORTED))
				.thenReturn(true);
		LegacyRow meanwhile = LegacyFixtures.row(2, LegacyRowStatus.VALID, 7001L, DAY, "P-REG");
		LegacyRow inactive = LegacyFixtures.row(3, LegacyRowStatus.VALID, 7002L, DAY, "P-REG;P-OLD");

		run(meanwhile, inactive);

		assertThat(meanwhile.getReasons()).isEqualTo("ALREADY_IMPORTED");
		assertThat(inactive.getStatus()).isEqualTo(LegacyRowStatus.SKIPPED);
		assertThat(inactive.getReasons()).isEqualTo("UNKNOWN_PURPOSE");
		verify(consents, never()).saveAndFlush(any());
	}

	@Test
	void rowsAreTakenByDateAndErrorRowsAreLeftAlone() {
		LegacyRow late = LegacyFixtures.row(2, LegacyRowStatus.VALID, 7001L, LocalDate.of(2025, 8, 1), "P-REG");
		LegacyRow early = LegacyFixtures.row(5, LegacyRowStatus.VALID, 7001L, LocalDate.of(2025, 2, 1),
				"P-REG");
		LegacyRow sameDayLaterRow = LegacyFixtures.row(4, LegacyRowStatus.VALID, 7002L, LocalDate.of(2025, 2,
				1),
				"P-REG");
		LegacyRow error = LegacyFixtures.row(3, LegacyRowStatus.ERROR, null, null, null);
		error.setReasons("BAD_DATE");

		List<LegacyRow> result = run(late, error, sameDayLaterRow, early);

		assertThat(result).containsExactly(late, error, sameDayLaterRow, early);
		ArgumentCaptor<Consent> saved = ArgumentCaptor.forClass(Consent.class);
		verify(consents, times(3)).saveAndFlush(saved.capture());
		assertThat(saved.getAllValues()).extracting(Consent::getRequestKey).containsExactly(
				key(4), key(5), key(2));
		assertThat(error.getStatus()).isEqualTo(LegacyRowStatus.ERROR);
		assertThat(error.getReasons()).isEqualTo("BAD_DATE");
		verify(rows, never()).saveAndFlush(error);
	}

	@Test
	void repeatedTranslationsOrPurposeVersionsUseTheFirst() {
		when(translations.findByNoticeVersionIdAndDeletedFalseOrderByLanguageCodeAsc(11L))
				.thenReturn(List.of(english, translation("hi", "h"), translation("hi", "x")));
		when(purposes.findByPurposeCodeInAndActiveTrueAndDeletedFalse(anyCollection())).thenReturn(List.of(
				ConsentFixtures.purpose("P-REG", true, null, 1), ConsentFixtures.purpose("P-REG", true, 30, 2)));

		run(LegacyFixtures.row(2, LegacyRowStatus.VALID, 7001L, DAY, "P-REG"));

		assertThat(savedConsent().getTextHash()).isEqualTo("h".repeat(64));
		ArgumentCaptor<Purpose> purpose = ArgumentCaptor.forClass(Purpose.class);
		verify(decisionWriter).write(any(), eq("P-REG"), eq(Decision.GRANT), purpose.capture());
		assertThat(purpose.getValue().getVersion()).isEqualTo(1);
	}

	@Test
	void aBatchWithoutValidRowsWritesNothing() {
		LegacyRow error = LegacyFixtures.row(3, LegacyRowStatus.ERROR, null, null, null);

		assertThat(run(error)).containsExactly(error);
		verify(purposes, never()).findByPurposeCodeInAndActiveTrueAndDeletedFalse(any());
		verify(consents, never()).saveAndFlush(any());
	}

	@Test
	void longSummariesFitTheAuditColumn() {
		List<PurposeResult> many = java.util.stream.IntStream.range(0, 40)
				.mapToObj(i -> new PurposeResult("P-" + i, PurposeStatus.GRANTED, 1)).toList();
		String summary = LegacyImporter.summary(many, LegacyFixtures.BATCH, 4999);
		assertThat(summary).hasSize(LegacyImporter.SUMMARY_MAX).endsWith(";batch:" + LegacyFixtures.BATCH
				+ ";row:4999");
		assertThat(LegacyImporter.summary(List.of(), "b", 1)).isEqualTo(";batch:b;row:1");
	}

	private static String key(int rowNo) {
		return UUID.nameUUIDFromBytes((LegacyFixtures.BATCH + ":" + rowNo).getBytes(StandardCharsets.UTF_8))
				.toString();
	}
}
