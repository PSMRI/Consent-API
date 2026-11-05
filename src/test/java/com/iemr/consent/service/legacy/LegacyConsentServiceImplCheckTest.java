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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;

import com.iemr.consent.data.legacy.LegacyBatch;
import com.iemr.consent.data.legacy.LegacyBatchStatus;
import com.iemr.consent.data.legacy.LegacyRow;
import com.iemr.consent.data.legacy.LegacyRowStatus;
import com.iemr.consent.model.legacy.LegacyBatchView;
import com.iemr.consent.model.legacy.LegacyCheckRequest;
import com.iemr.consent.model.legacy.LegacyRowInput;
import com.iemr.consent.model.legacy.LegacyRowView;
import com.iemr.consent.repository.legacy.LegacyBatchRepository;
import com.iemr.consent.repository.legacy.LegacyRowRepository;
import com.iemr.consent.repository.notice.NoticeRepository;
import com.iemr.consent.repository.notice.NoticeTranslationRepository;
import com.iemr.consent.service.audit.AuditLogService;
import com.iemr.consent.support.ConsentFixtures;
import com.iemr.consent.support.TestFixtures;
import com.iemr.consent.utils.crypto.FieldEncryptor;
import com.iemr.consent.utils.exception.ConsentApiException;
import com.iemr.consent.utils.exception.ErrorCode;
import com.iemr.consent.utils.response.FieldErrorDetail;

/** Feature 008, US1 (A9): a check stores the batch and its rows, audits it, and writes no consent. */
class LegacyConsentServiceImplCheckTest {

	private LegacyBatchRepository batches;

	private LegacyRowRepository rows;

	private LegacyRowChecker checker;

	private LegacyImporter importer;

	private AuditLogService audit;

	private final FieldEncryptor encryptor = new FieldEncryptor(TestFixtures.consentProperties());

	private LegacyConsentServiceImpl service;

	@BeforeEach
	void setUp() {
		batches = mock(LegacyBatchRepository.class);
		rows = mock(LegacyRowRepository.class);
		checker = mock(LegacyRowChecker.class);
		importer = mock(LegacyImporter.class);
		audit = mock(AuditLogService.class);
		when(batches.findByUploadedByUserIdAndRequestKey(any(), any())).thenReturn(Optional.empty());
		when(batches.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
		when(rows.saveAll(anyList())).thenAnswer(i -> i.getArgument(0));
		service = new LegacyConsentServiceImpl(batches, rows, checker, importer, mock(NoticeRepository.class),
				mock(NoticeTranslationRepository.class), encryptor, audit, TestFixtures.CLOCK,
				TestFixtures.consentProperties());
	}

	private static LegacyRowInput withRepresentative(int rowNo) {
		return new LegacyRowInput(rowNo, " 100245678901 ", 7001L, "2025-06-12", "P-REG", "hi", "WITNESSED",
				" Gita Bai ", " Neighbour ", " signed at camp ");
	}

	@Test
	void theBatchAndItsRowsAreStoredWithPersonalFieldsEncrypted() {
		LegacyRowInput good = withRepresentative(2);
		LegacyRowInput bad = new LegacyRowInput(3, "100245678902", null, "x", "P-REG", "hi", "PAPER", null, " ",
				null);
		when(checker.check(List.of(good, bad))).thenReturn(List.of(
				new CheckedRow(good, LocalDate.of(2025, 6, 12), "P-REG", "hi", "WITNESSED", List.of()),
				new CheckedRow(bad, null, "P-REG", "hi", "PAPER", List.of("UNKNOWN_BENEFICIARY",
						"BAD_DATE"))));

		LegacyBatchView view = service.check(new LegacyCheckRequest(1, " old\u0007.csv ", List.of(good, bad)),
		"k-1",
				"h-1", ConsentFixtures.WORKER, "10.0.0.5");

		ArgumentCaptor<LegacyBatch> batch = ArgumentCaptor.forClass(LegacyBatch.class);
		verify(batches).saveAndFlush(batch.capture());
		LegacyBatch saved = batch.getValue();
		assertThat(saved.getStatus()).isEqualTo(LegacyBatchStatus.CHECKED);
		assertThat(saved.getFileName()).isEqualTo("old.csv");
		assertThat(saved.getServiceProviderId()).isEqualTo(1);
		assertThat(saved.getUploadedByUserId()).isEqualTo(42L);
		assertThat(saved.getUploadedBy()).isEqualTo("worker42");
		assertThat(saved.getCreatedBy()).isEqualTo("worker42");
		assertThat(saved.getUploadedAt()).isEqualTo(TestFixtures.NOW);
		assertThat(saved.getExpiresAt()).isEqualTo(TestFixtures.NOW.plus(Duration.ofDays(30)));
		assertThat(saved.getRequestKey()).isEqualTo("k-1");
		assertThat(saved.getRequestHash()).isEqualTo("h-1");

		@SuppressWarnings("unchecked")
		ArgumentCaptor<List<LegacyRow>> stored = ArgumentCaptor.forClass(List.class);
		verify(rows).saveAll(stored.capture());
		LegacyRow first = stored.getValue().get(0);
		assertThat(first.getBatchId()).isEqualTo(saved.getBatchId());
		assertThat(first.getBeneficiaryId()).isEqualTo("100245678901");
		assertThat(first.getBeneficiaryRegId()).isEqualTo(7001L);
		assertThat(first.getConsentDate()).isEqualTo(LocalDate.of(2025, 6, 12));
		assertThat(first.getPurposes()).isEqualTo("P-REG");
		assertThat(first.getMode()).isEqualTo("WITNESSED");
		assertThat(first.getRepresentativeNameEncrypted()).isNotEqualTo("Gita Bai").doesNotContain("Gita");
		assertThat(encryptor.decrypt(first.getRepresentativeNameEncrypted())).isEqualTo("Gita Bai");
		assertThat(encryptor.decrypt(first.getRemarksEncrypted())).isEqualTo("signed at camp");
		assertThat(first.getRepresentativeRelationship()).isEqualTo("Neighbour");
		assertThat(first.getStatus()).isEqualTo(LegacyRowStatus.VALID);
		assertThat(first.getReasons()).isNull();
		assertThat(first.getCreatedBy()).isEqualTo("worker42");
		LegacyRow second = stored.getValue().get(1);
		assertThat(second.getStatus()).isEqualTo(LegacyRowStatus.ERROR);
		assertThat(second.getReasons()).isEqualTo("UNKNOWN_BENEFICIARY,BAD_DATE");
		assertThat(second.getRepresentativeNameEncrypted()).isNull();
		assertThat(second.getRemarksEncrypted()).isNull();
		assertThat(second.getRepresentativeRelationship()).isNull();

		assertThat(view.batchId()).isEqualTo(saved.getBatchId());
		assertThat(view.total()).isEqualTo(2);
		assertThat(view.valid()).isEqualTo(1);
		assertThat(view.failed()).isEqualTo(1);
		assertThat(view.imported()).isZero();
		assertThat(view.rows()).containsExactly(
				new LegacyRowView(2, "100245678901", LegacyRowStatus.VALID, List.of()),
				new LegacyRowView(3, "100245678902", LegacyRowStatus.ERROR,
						List.of("UNKNOWN_BENEFICIARY",
						"BAD_DATE")));
		assertThat(view.toString()).doesNotContain("Gita").doesNotContain("camp");

		verify(audit).append(AuditLogService.LEGACY_CHECKED, null, null,
		"batch:" + saved.getBatchId() + ";rows:2;valid:1", "CHECKED", 42L, "10.0.0.5");
		verifyNoInteractions(importer);
	}

	@Test
	void aReplayReturnsTheStoredBatchWithoutWriting() {
		LegacyBatch earlier = LegacyFixtures.batch(LegacyBatchStatus.CHECKED);
		when(batches.findByUploadedByUserIdAndRequestKey(42L, "k-check")).thenReturn(Optional.of(earlier));
		LegacyRow row = LegacyFixtures.row(2, LegacyRowStatus.ERROR, null, null, null);
		row.setReasons("UNKNOWN_BENEFICIARY");
		when(rows.findByBatchIdOrderByRowNoAsc(LegacyFixtures.BATCH)).thenReturn(List.of(row));

		LegacyBatchView view = service.check(new LegacyCheckRequest(null, null,
		List.of(LegacyFixtures.input(2))),
				"k-check", "h-check", ConsentFixtures.WORKER, "10.0.0.5");

		assertThat(view.batchId()).isEqualTo(LegacyFixtures.BATCH);
		assertThat(view.rows().get(0).reasons()).containsExactly("UNKNOWN_BENEFICIARY");
		verify(batches, never()).saveAndFlush(any());
		verifyNoInteractions(checker, audit);

		assertThatThrownBy(() -> service.check(new LegacyCheckRequest(null, null,
		List.of(LegacyFixtures.input(2))),
				"k-check", "other", ConsentFixtures.WORKER, "10.0.0.5"))
				.isInstanceOf(ConsentApiException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.STATE_CONFLICT);
	}

	@Test
	void repeatedRowNumbersAreRefused() {
		assertThatThrownBy(() -> service.check(new LegacyCheckRequest(null, null,
				List.of(LegacyFixtures.input(2), LegacyFixtures.input(5), LegacyFixtures.input(2))),
				"k-1", "h-1",
				ConsentFixtures.WORKER, "10.0.0.5")).isInstanceOf(ConsentApiException.class)
						.extracting("details")
				.isEqualTo(List.of(new FieldErrorDetail("rows[2].rowNo", "is repeated")));
		verifyNoInteractions(checker, audit);
	}

	@Test
	void aSimultaneousCheckWithTheSameKeyIsAConflict() {
		LegacyRowInput row = LegacyFixtures.input(1);
		when(checker.check(List.of(row))).thenReturn(List.of(new CheckedRow(row, null, null, null, null,
				List.of("BAD_DATE"))));
		when(batches.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("duplicate"));

		assertThatThrownBy(() -> service.check(new LegacyCheckRequest(null, "  ", List.of(row)), "k-1", "h-1",
		ConsentFixtures.WORKER, "10.0.0.5")).isInstanceOf(ConsentApiException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.STATE_CONFLICT);
		verify(rows, never()).saveAll(anyList());
		verify(audit, never()).append(anyString(), any(), any(), anyString(), anyString(), any(), isNull());
	}

	@Test
	void fileNamesAreCleaned() {
		assertThat(LegacyConsentServiceImpl.fileName(null)).isNull();
		assertThat(LegacyConsentServiceImpl.fileName(" \t ")).isNull();
		assertThat(LegacyConsentServiceImpl.fileName(" a\nb.csv ")).isEqualTo("ab.csv");
	}
}
