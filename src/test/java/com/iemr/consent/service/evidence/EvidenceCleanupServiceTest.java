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

package com.iemr.consent.service.evidence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.PlatformTransactionManager;

import com.iemr.consent.data.evidence.ConsentEvidence;
import com.iemr.consent.data.evidence.EvidenceType;
import com.iemr.consent.repository.evidence.ConsentEvidenceRepository;
import com.iemr.consent.service.audit.AuditLogService;
import com.iemr.consent.support.LogCapture;
import com.iemr.consent.support.TestFixtures;

/** Removal of evidence no consent linked within 24 hours (feature 004, FR-009, R8). */
class EvidenceCleanupServiceTest {

	@RegisterExtension
	final LogCapture logs = new LogCapture();

	private ConsentEvidenceRepository repository;

	private EvidenceStorage storage;

	private AuditLogService audit;

	private PlatformTransactionManager transactions;

	private EvidenceCleanupService service;

	@BeforeEach
	void setUp() {
		repository = mock(ConsentEvidenceRepository.class);
		storage = mock(EvidenceStorage.class);
		audit = mock(AuditLogService.class);
		transactions = mock(PlatformTransactionManager.class);
		service = new EvidenceCleanupService(repository, storage, audit, transactions, TestFixtures.CLOCK,
				TestFixtures.consentProperties());
	}

	private static ConsentEvidence unlinked(String id) {
		ConsentEvidence e = new ConsentEvidence();
		e.setEvidenceId(id);
		e.setType(EvidenceType.VOICE_CLIP);
		e.setFileRef(id + ".enc");
		return e;
	}

	@Test
	@DisplayName("US4: files older than 24 h are soft-deleted, audited and removed from storage")
	void purgesOldUnlinkedFiles() {
		when(repository.findPurgeCandidates(any(), any(), any())).thenReturn(List.of(unlinked("e1"), unlinked("e2")));
		when(repository.markPurged(anyString(), eq("evidence-cleanup"))).thenReturn(1);

		assertThat(service.purgeExpired()).isEqualTo(2);

		ArgumentCaptor<java.time.Instant> cutoff = ArgumentCaptor.forClass(java.time.Instant.class);
		@SuppressWarnings("unchecked")
		ArgumentCaptor<Collection<EvidenceType>> types = ArgumentCaptor.forClass(Collection.class);
		ArgumentCaptor<Pageable> page = ArgumentCaptor.forClass(Pageable.class);
		verify(repository).findPurgeCandidates(cutoff.capture(), types.capture(), page.capture());
		assertThat(cutoff.getValue()).isEqualTo(TestFixtures.NOW.minus(Duration.ofHours(24)));
		assertThat(types.getValue()).containsExactlyInAnyOrder(EvidenceType.VOICE_CLIP, EvidenceType.PATIENT_PHOTO,
				EvidenceType.SIGNED_FORM_PHOTO);
		assertThat(page.getValue().getPageSize()).isEqualTo(EvidenceCleanupService.BATCH);
		verify(audit).append(AuditLogService.EVIDENCE_PURGED, null, null, "evidence:e1", "PURGED", null, null);
		verify(storage).delete("e1.enc");
		verify(storage).delete("e2.enc");
		verify(transactions, times(2)).commit(any());
	}

	@Test
	@DisplayName("US4: a file linked or purged by another node meanwhile is left alone")
	void linkedMeanwhileIsKept() {
		when(repository.findPurgeCandidates(any(), any(), any())).thenReturn(List.of(unlinked("e1")));
		when(repository.markPurged("e1", "evidence-cleanup")).thenReturn(0);

		assertThat(service.purgeExpired()).isZero();
		verify(audit, never()).append(any(), any(), any(), any(), any(), any(), any());
		verify(storage, never()).delete(any());
	}

	@Test
	@DisplayName("US4: a storage failure is logged by class name only and does not stop the batch")
	void storageFailureDoesNotStopTheBatch() {
		when(repository.findPurgeCandidates(any(), any(), any())).thenReturn(List.of(unlinked("e1"), unlinked("e2")));
		when(repository.markPurged(anyString(), anyString())).thenReturn(1);
		doThrow(new IllegalStateException("disk /secret/path")).when(storage).delete("e1.enc");

		assertThat(service.purgeExpired()).isEqualTo(2);
		verify(storage).delete("e2.enc");
		assertThat(logs.messages()).anyMatch(m -> m.contains("IllegalStateException"));
		logs.assertNoneContain("/secret/path");
	}

	@Test
	@DisplayName("US4: full batches are fetched again until a short batch")
	void fetchesUntilAShortBatch() {
		List<ConsentEvidence> full = new ArrayList<>();
		IntStream.range(0, EvidenceCleanupService.BATCH).forEach(i -> full.add(unlinked("f" + i)));
		when(repository.findPurgeCandidates(any(), any(), any())).thenReturn(full, List.of());
		when(repository.markPurged(anyString(), anyString())).thenReturn(1);

		assertThat(service.purgeExpired()).isEqualTo(EvidenceCleanupService.BATCH);
		verify(repository, times(2)).findPurgeCandidates(any(), any(), any());
	}
}
