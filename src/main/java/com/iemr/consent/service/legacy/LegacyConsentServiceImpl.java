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

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.iemr.consent.config.ConsentProperties;
import com.iemr.consent.data.legacy.LegacyBatch;
import com.iemr.consent.data.legacy.LegacyBatchStatus;
import com.iemr.consent.data.legacy.LegacyRow;
import com.iemr.consent.data.legacy.LegacyRowStatus;
import com.iemr.consent.data.notice.Notice;
import com.iemr.consent.data.notice.NoticeContext;
import com.iemr.consent.data.notice.NoticeStatus;
import com.iemr.consent.data.notice.NoticeTranslation;
import com.iemr.consent.model.legacy.LegacyBatchView;
import com.iemr.consent.model.legacy.LegacyCheckRequest;
import com.iemr.consent.model.legacy.LegacyRowInput;
import com.iemr.consent.model.legacy.LegacyRowView;
import com.iemr.consent.repository.legacy.LegacyBatchRepository;
import com.iemr.consent.repository.legacy.LegacyRowRepository;
import com.iemr.consent.repository.notice.NoticeRepository;
import com.iemr.consent.repository.notice.NoticeTranslationRepository;
import com.iemr.consent.service.audit.AuditLogService;
import com.iemr.consent.utils.auth.AuthenticatedUser;
import com.iemr.consent.utils.crypto.FieldEncryptor;
import com.iemr.consent.utils.exception.ConsentApiException;
import com.iemr.consent.utils.exception.ErrorCode;
import com.iemr.consent.utils.response.FieldErrorDetail;

/**
 * Admin legacy consent upload (A9–A10, feature 008, research R4, R5). A check stores the batch and its rows, with
 * the representative's name and the remarks encrypted, and writes no consent; an import claims the batch with a
 * conditional update, so it runs once, then {@link LegacyImporter} stores the valid rows. Both are audited in their
 * transaction and replayed from the stored batch for the same {@code Idempotency-Key}.
 */
@Service
public class LegacyConsentServiceImpl implements LegacyConsentService {

	static final String ALREADY_IMPORTED = "Batch already imported";

	static final String EXPIRED = "Batch expired";

	static final String NO_CENTRAL_NOTICE = "No central notice to record legacy consents against";

	static final String ENGLISH = "en";

	private final LegacyBatchRepository batches;

	private final LegacyRowRepository rows;

	private final LegacyRowChecker checker;

	private final LegacyImporter importer;

	private final NoticeRepository notices;

	private final NoticeTranslationRepository translations;

	private final FieldEncryptor encryptor;

	private final AuditLogService auditLog;

	private final Clock clock;

	private final Duration expiry;

	public LegacyConsentServiceImpl(LegacyBatchRepository batches, LegacyRowRepository rows,
			LegacyRowChecker checker, LegacyImporter importer, NoticeRepository notices,
			NoticeTranslationRepository translations, FieldEncryptor encryptor, AuditLogService auditLog,
			Clock clock, ConsentProperties properties) {
		this.batches = batches;
		this.rows = rows;
		this.checker = checker;
		this.importer = importer;
		this.notices = notices;
		this.translations = translations;
		this.encryptor = encryptor;
		this.auditLog = auditLog;
		this.clock = clock;
		this.expiry = Duration.ofDays(properties.legacy().batchExpiryDays());
	}

	@Override
	@Transactional
	public LegacyBatchView check(LegacyCheckRequest request, String requestKey, String requestHash,
			AuthenticatedUser user, String sourceIp) {
		LegacyBatch earlier = batches.findByUploadedByUserIdAndRequestKey(user.userId(), requestKey)
				.orElse(null);
		if (earlier != null) {
			if (!earlier.getRequestHash().equals(requestHash)) {
				throw new ConsentApiException(ErrorCode.STATE_CONFLICT);
			}
			return view(earlier, rows.findByBatchIdOrderByRowNoAsc(earlier.getBatchId()));
		}
		requireUniqueRowNumbers(request.rows());
		List<CheckedRow> checked = checker.check(request.rows());
		Instant now = clock.instant();
		LegacyBatch batch = new LegacyBatch();
		batch.setBatchId(UUID.randomUUID().toString());
		batch.setFileName(fileName(request.fileName()));
		batch.setServiceProviderId(request.serviceProviderId());
		batch.setStatus(LegacyBatchStatus.CHECKED);
		batch.setTotal(checked.size());
		batch.setValid((int) checked.stream().filter(r -> r.status() == LegacyRowStatus.VALID).count());
		batch.setFailed(batch.getTotal() - batch.getValid());
		batch.setUploadedByUserId(user.userId());
		batch.setUploadedBy(user.auditName());
		batch.setUploadedAt(now);
		batch.setExpiresAt(now.plus(expiry));
		batch.setRequestKey(requestKey);
		batch.setRequestHash(requestHash);
		batch.setCreatedBy(user.auditName());
		try {
			batch = batches.saveAndFlush(batch);
		} catch (DataIntegrityViolationException e) {
			throw new ConsentApiException(ErrorCode.STATE_CONFLICT);
		}
		String batchId = batch.getBatchId();
		List<LegacyRow> stored = rows.saveAll(checked.stream().map(r -> row(batchId, r, user)).toList());
		auditLog.append(AuditLogService.LEGACY_CHECKED, null, null,
				"batch:" + batchId + ";rows:" + batch.getTotal() + ";valid:" + batch.getValid(),
				LegacyBatchStatus.CHECKED.name(), user.userId(), sourceIp);
		return view(batch, stored);
	}

	@Override
	@Transactional
	public LegacyBatchView importBatch(String batchId, String requestKey, String requestHash,
			AuthenticatedUser user,
			String sourceIp) {
		LegacyBatch earlier = batches.findById(batchId).orElse(null);
		if (earlier != null && requestKey.equals(earlier.getImportRequestKey())) {
			if (!requestHash.equals(earlier.getImportRequestHash())) {
				throw new ConsentApiException(ErrorCode.STATE_CONFLICT);
			}
			return view(earlier, rows.findByBatchIdOrderByRowNoAsc(batchId));
		}
		Notice notice = notices
				.findFirstByContextAndProjectIdIsNullAndStatusAndDeletedFalseOrderByVersionNoDesc(
						NoticeContext.REGISTRATION, NoticeStatus.PUBLISHED)
				.orElse(null);
		NoticeTranslation english = notice == null ? null
				: translations.findByNoticeVersionIdAndLanguageCodeAndDeletedFalse(
						notice.getNoticeVersionId(), ENGLISH).orElse(null);
		if (english == null) {
			throw new ConsentApiException(ErrorCode.BUSINESS_RULE_VIOLATION, NO_CENTRAL_NOTICE);
		}
		Instant now = clock.instant();
		if (batches.claimForImport(batchId, user.auditName(), now, requestKey, requestHash, now) == 0) {
			throw whyNotImportable(batchId);
		}
		LegacyBatch batch = batches.findById(batchId).orElseThrow();
		List<LegacyRow> result = importer.importRows(batch, notice, english, user, sourceIp);
		batch.setImported(count(result, LegacyRowStatus.IMPORTED));
		batch.setSkipped(count(result, LegacyRowStatus.SKIPPED));
		return view(batches.saveAndFlush(batch), result);
	}

	private ConsentApiException whyNotImportable(String batchId) {
		LegacyBatch batch = batches.findById(batchId).filter(b -> !b.isDeleted()).orElse(null);
		if (batch == null) {
			return new ConsentApiException(ErrorCode.NOT_FOUND);
		}
		if (batch.getStatus() == LegacyBatchStatus.IMPORTED) {
			return new ConsentApiException(ErrorCode.STATE_CONFLICT, ALREADY_IMPORTED);
		}
		return new ConsentApiException(ErrorCode.BUSINESS_RULE_VIOLATION, EXPIRED);
	}

	private static void requireUniqueRowNumbers(List<LegacyRowInput> input) {
		Set<Integer> seen = new HashSet<>();
		for (int i = 0; i < input.size(); i++) {
			if (!seen.add(input.get(i).rowNo())) {
				throw new ConsentApiException(ErrorCode.VALIDATION_FAILED, null, null,
						List.of(new FieldErrorDetail("rows[" + i + "].rowNo", "is repeated")));
			}
		}
	}

	private LegacyRow row(String batchId, CheckedRow checked, AuthenticatedUser user) {
		LegacyRowInput input = checked.input();
		LegacyRow row = new LegacyRow();
		row.setBatchId(batchId);
		row.setRowNo(input.rowNo());
		row.setBeneficiaryId(trimmed(input.beneficiaryId()));
		row.setBeneficiaryRegId(input.beneficiaryRegId());
		row.setConsentDate(checked.consentDate());
		row.setPurposes(checked.purposes());
		row.setLanguageCode(checked.language());
		row.setMode(checked.mode());
		row.setRepresentativeNameEncrypted(encrypted(input.representativeName()));
		row.setRepresentativeRelationship(trimmed(input.representativeRelationship()));
		row.setRemarksEncrypted(encrypted(input.remarks()));
		row.setStatus(checked.status());
		row.setReasons(checked.reasons().isEmpty() ? null : String.join(",", checked.reasons()));
		row.setCreatedBy(user.auditName());
		return row;
	}

	private String encrypted(String value) {
		String text = trimmed(value);
		return text == null ? null : encryptor.encrypt(text);
	}

	static LegacyBatchView view(LegacyBatch batch, List<LegacyRow> batchRows) {
		List<LegacyRowView> views = batchRows.stream().map(r -> new LegacyRowView(r.getRowNo(),
				r.getBeneficiaryId(), r.getStatus(), reasons(r.getReasons()))).toList();
		return new LegacyBatchView(batch.getBatchId(), batch.getFileName(), batch.getUploadedBy(),
				batch.getUploadedAt(), batch.getStatus(), batch.getTotal(), batch.getValid(),
				batch.getImported(),
				batch.getSkipped(), batch.getFailed(), views);
	}

	private static List<String> reasons(String stored) {
		return stored == null ? List.of() : Arrays.asList(stored.split(","));
	}

	private static int count(List<LegacyRow> batchRows, LegacyRowStatus status) {
		return (int) batchRows.stream().filter(r -> r.getStatus() == status).count();
	}

	/** The file name without control characters or surrounding spaces; {@code null} when nothing is left. */
	static String fileName(String name) {
		if (name == null) {
			return null;
		}
		String clean = name.replaceAll("\\p{Cntrl}", "").trim();
		return clean.isEmpty() ? null : clean;
	}

	private static String trimmed(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}
}
