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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.iemr.consent.data.legacy.LegacyBatch;
import com.iemr.consent.data.legacy.LegacyBatchStatus;
import com.iemr.consent.data.legacy.LegacyRow;
import com.iemr.consent.data.legacy.LegacyRowStatus;
import com.iemr.consent.data.notice.Notice;
import com.iemr.consent.data.notice.NoticeContext;
import com.iemr.consent.data.notice.NoticeStatus;
import com.iemr.consent.data.notice.NoticeTranslation;
import com.iemr.consent.model.legacy.LegacyBatchView;
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

/** Feature 008, US2 (A10): a batch is claimed once, then its valid rows are imported. */
class LegacyConsentServiceImplImportTest {

	private static final String KEY = "k-import";

	private LegacyBatchRepository batches;

	private LegacyRowRepository rows;

	private LegacyImporter importer;

	private NoticeRepository notices;

	private NoticeTranslationRepository translations;

	private LegacyConsentServiceImpl service;

	private final Notice notice = new Notice();

	private final NoticeTranslation english = new NoticeTranslation();

	@BeforeEach
	void setUp() {
		batches = mock(LegacyBatchRepository.class);
		rows = mock(LegacyRowRepository.class);
		importer = mock(LegacyImporter.class);
		notices = mock(NoticeRepository.class);
		translations = mock(NoticeTranslationRepository.class);
		notice.setNoticeVersionId(11L);
		english.setTextHash("e".repeat(64));
		when(notices.findFirstByContextAndProjectIdIsNullAndStatusAndDeletedFalseOrderByVersionNoDesc(
				NoticeContext.REGISTRATION, NoticeStatus.PUBLISHED)).thenReturn(Optional.of(notice));
		when(translations.findByNoticeVersionIdAndLanguageCodeAndDeletedFalse(11L, "en"))
				.thenReturn(Optional.of(english));
		when(batches.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
		service = new LegacyConsentServiceImpl(batches, rows, mock(LegacyRowChecker.class), importer, notices,
				translations, new FieldEncryptor(TestFixtures.consentProperties()),
				mock(AuditLogService.class),
				TestFixtures.CLOCK, TestFixtures.consentProperties());
	}

	private LegacyBatchView importIt(String hash) {
		return service.importBatch(LegacyFixtures.BATCH, KEY, hash, ConsentFixtures.WORKER, "10.0.0.5");
	}

	private void claim(int result) {
		when(batches.claimForImport(LegacyFixtures.BATCH, "worker42", TestFixtures.NOW, KEY, "h",
				TestFixtures.NOW))
				.thenReturn(result);
	}

	@Test
	void theClaimedBatchIsImportedAndCounted() {
		LegacyBatch claimed = LegacyFixtures.batch(LegacyBatchStatus.IMPORTED);
		when(batches.findById(LegacyFixtures.BATCH)).thenReturn(Optional.empty(), Optional.of(claimed));
		claim(1);
		LocalDate day = LocalDate.of(2025, 6, 12);
		List<LegacyRow> result = List.of(LegacyFixtures.row(1, LegacyRowStatus.IMPORTED, 7001L, day, "P-REG"),
				LegacyFixtures.row(2, LegacyRowStatus.SKIPPED, 7002L, day, "P-REG"),
				LegacyFixtures.row(3, LegacyRowStatus.ERROR, null, null, null));
		when(importer.importRows(claimed, notice, english, ConsentFixtures.WORKER, "10.0.0.5"))
				.thenReturn(result);

		LegacyBatchView view = importIt("h");

		assertThat(view.status()).isEqualTo(LegacyBatchStatus.IMPORTED);
		assertThat(view.imported()).isEqualTo(1);
		assertThat(view.skipped()).isEqualTo(1);
		assertThat(view.failed()).isEqualTo(1);
		assertThat(view.rows()).extracting("status").containsExactly(LegacyRowStatus.IMPORTED,
				LegacyRowStatus.SKIPPED, LegacyRowStatus.ERROR);
		verify(batches).saveAndFlush(claimed);
	}

	@Test
	void aReplayReturnsTheStoredBatch() {
		LegacyBatch done = LegacyFixtures.batch(LegacyBatchStatus.IMPORTED);
		done.setImportRequestKey(KEY);
		done.setImportRequestHash("h");
		when(batches.findById(LegacyFixtures.BATCH)).thenReturn(Optional.of(done));
		when(rows.findByBatchIdOrderByRowNoAsc(LegacyFixtures.BATCH)).thenReturn(List.of());

		assertThat(importIt("h").status()).isEqualTo(LegacyBatchStatus.IMPORTED);
		assertThatThrownBy(() -> importIt("other")).isInstanceOf(ConsentApiException.class)
				.extracting("errorCode")
				.isEqualTo(ErrorCode.STATE_CONFLICT);
		verify(batches, never()).claimForImport(any(), any(), any(), any(), any(), any());
		verifyNoInteractions(importer);
	}

	@Test
	void withoutACentralNoticeTheBatchIsNotClaimed() {
		when(batches.findById(LegacyFixtures.BATCH)).thenReturn(Optional.empty());
		when(translations.findByNoticeVersionIdAndLanguageCodeAndDeletedFalse(11L, "en"))
				.thenReturn(Optional.empty());
		assertThatThrownBy(() -> importIt("h")).isInstanceOf(ConsentApiException.class)
				.hasMessage(LegacyConsentServiceImpl.NO_CENTRAL_NOTICE).extracting("errorCode")
				.isEqualTo(ErrorCode.BUSINESS_RULE_VIOLATION);

		when(notices.findFirstByContextAndProjectIdIsNullAndStatusAndDeletedFalseOrderByVersionNoDesc(
		NoticeContext.REGISTRATION, NoticeStatus.PUBLISHED)).thenReturn(Optional.empty());
		assertThatThrownBy(() -> importIt("h")).hasMessage(LegacyConsentServiceImpl.NO_CENTRAL_NOTICE);
		verify(batches, never()).claimForImport(any(), any(), any(), any(), any(), any());
	}

	@Test
	void aBatchThatCannotBeClaimedIsRefusedWithTheReason() {
		claim(0);
		LegacyBatch imported = LegacyFixtures.batch(LegacyBatchStatus.IMPORTED);
		imported.setImportRequestKey("another-key");
		LegacyBatch expired = LegacyFixtures.batch(LegacyBatchStatus.CHECKED);
		LegacyBatch deleted = LegacyFixtures.batch(LegacyBatchStatus.CHECKED);
		deleted.setDeleted(true);
		when(batches.findById(LegacyFixtures.BATCH)).thenReturn(Optional.empty(), Optional.empty(),
				Optional.of(imported), Optional.of(imported), Optional.of(expired),
				Optional.of(expired),
				Optional.of(deleted), Optional.of(deleted));

		assertThatThrownBy(() -> importIt("h")).extracting("errorCode").isEqualTo(ErrorCode.NOT_FOUND);
		assertThatThrownBy(() -> importIt("h")).hasMessage(LegacyConsentServiceImpl.ALREADY_IMPORTED)
				.extracting("errorCode").isEqualTo(ErrorCode.STATE_CONFLICT);
		assertThatThrownBy(() -> importIt("h")).hasMessage(LegacyConsentServiceImpl.EXPIRED)
				.extracting("errorCode").isEqualTo(ErrorCode.BUSINESS_RULE_VIOLATION);
		assertThatThrownBy(() -> importIt("h")).extracting("errorCode").isEqualTo(ErrorCode.NOT_FOUND);
		verify(importer, never()).importRows(any(), any(), any(), any(), anyString());
	}
}
