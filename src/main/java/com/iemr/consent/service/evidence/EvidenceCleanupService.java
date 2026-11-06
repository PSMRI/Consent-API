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

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.iemr.consent.config.ConsentProperties;
import com.iemr.consent.data.evidence.ConsentEvidence;
import com.iemr.consent.data.evidence.EvidenceType;
import com.iemr.consent.repository.evidence.ConsentEvidenceRepository;
import com.iemr.consent.service.audit.AuditLogService;
import com.iemr.consent.utils.http.AfterCommit;

/**
 * Removes evidence that no consent linked within {@code unlinked-retention-hours} (default 24, TODO-DPO) of upload
 * (feature 004, FR-009, research R8): the row is soft-deleted and the purge audited in one transaction per file, then
 * the encrypted file is deleted after commit. Idempotent and safe on several nodes (conditional update).
 */
@Service
public class EvidenceCleanupService {

	static final String CLEANUP_USER = "evidence-cleanup";

	static final int BATCH = 100;

	private static final Logger LOGGER = LoggerFactory.getLogger(EvidenceCleanupService.class);

	private static final Set<EvidenceType> FILE_TYPES = Set.of(EvidenceType.VOICE_CLIP, EvidenceType.PATIENT_PHOTO,
			EvidenceType.SIGNED_FORM_PHOTO);

	private final ConsentEvidenceRepository repository;

	private final EvidenceStorage storage;

	private final AuditLogService auditLogService;

	private final TransactionTemplate transaction;

	private final Clock clock;

	private final Duration retention;

	public EvidenceCleanupService(ConsentEvidenceRepository repository, EvidenceStorage storage,
			AuditLogService auditLogService, PlatformTransactionManager transactionManager, Clock clock,
			ConsentProperties properties) {
		this.repository = repository;
		this.storage = storage;
		this.auditLogService = auditLogService;
		this.transaction = new TransactionTemplate(transactionManager);
		this.clock = clock;
		this.retention = Duration.ofHours(properties.evidence().unlinkedRetentionHours());
	}

	/** @return how many files this run removed */
	public int purgeExpired() {
		Instant cutoff = clock.instant().minus(retention);
		int purged = 0;
		List<ConsentEvidence> batch;
		do {
			batch = repository.findPurgeCandidates(cutoff, FILE_TYPES, PageRequest.of(0, BATCH));
			for (ConsentEvidence evidence : batch) {
				if (Boolean.TRUE.equals(transaction.execute(status -> purge(evidence)))) {
					purged++;
				}
			}
		} while (batch.size() == BATCH);
		return purged;
	}

	private boolean purge(ConsentEvidence evidence) {
		if (repository.markPurged(evidence.getEvidenceId(), CLEANUP_USER) != 1) {
			return false;
		}
		String summary = "evidence:" + evidence.getEvidenceId();
		auditLogService.append(AuditLogService.EVIDENCE_PURGED, null, null, summary, "PURGED", null, null);
		AfterCommit.run(() -> deleteFile(evidence));
		return true;
	}

	private void deleteFile(ConsentEvidence evidence) {
		try {
			storage.delete(evidence.getFileRef());
		} catch (RuntimeException e) {
			LOGGER.warn("Evidence file not deleted ({}), evidenceId={}", e.getClass().getSimpleName(),
					evidence.getEvidenceId());
		}
	}
}
