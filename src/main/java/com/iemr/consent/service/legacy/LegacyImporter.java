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

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.iemr.consent.data.consent.Channel;
import com.iemr.consent.data.consent.Consent;
import com.iemr.consent.data.consent.ConsentMode;
import com.iemr.consent.data.consent.ConsentingParty;
import com.iemr.consent.data.consent.Decision;
import com.iemr.consent.data.consent.EventType;
import com.iemr.consent.data.consent.SyncStatus;
import com.iemr.consent.data.evidence.ConsentEvidence;
import com.iemr.consent.data.evidence.EvidenceType;
import com.iemr.consent.data.legacy.LegacyBatch;
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
import com.iemr.consent.service.consent.ProofRows;
import com.iemr.consent.utils.auth.AuthenticatedUser;
import com.iemr.consent.utils.crypto.FieldEncryptor;
import com.iemr.consent.utils.crypto.Sha256;

/**
 * Turns the valid rows of a claimed legacy batch into LEGACY consent events (A10, feature 008, research R5 step 3),
 * inside the caller's transaction. Rows are taken by consent date, so a patient's newest legacy consent ends up
 * current. A row is skipped when the patient already has a consent taken in the app or a newer legacy one
 * ({@code NEWER_CONSENT_EXISTS}), when the same patient and date were imported meanwhile ({@code ALREADY_IMPORTED}),
 * or when one of its purposes is no longer active ({@code UNKNOWN_PURPOSE}). Each stored consent records the central
 * REGISTRATION notice and the text of the row's language (English when that language is no longer in the notice),
 * one GRANTED decision per purpose, the representative as WITNESS evidence when given, and one audit entry.
 */
@Component
public class LegacyImporter {

	static final String DEVICE_ID = "legacy-import";

	static final int SUMMARY_MAX = 255;

	private final ConsentRepository consents;

	private final ConsentDecisionWriter decisionWriter;

	private final ConsentEvidenceRepository evidence;

	private final LegacyRowRepository rows;

	private final NoticeTranslationRepository translations;

	private final PurposeRepository purposes;

	private final FieldEncryptor encryptor;

	private final AuditLogService auditLog;

	private final Clock clock;

	public LegacyImporter(ConsentRepository consents, ConsentDecisionWriter decisionWriter,
			ConsentEvidenceRepository evidence, LegacyRowRepository rows,
			NoticeTranslationRepository translations, PurposeRepository purposes, FieldEncryptor encryptor,
			AuditLogService auditLog, Clock clock) {
		this.consents = consents;
		this.decisionWriter = decisionWriter;
		this.evidence = evidence;
		this.rows = rows;
		this.translations = translations;
		this.purposes = purposes;
		this.encryptor = encryptor;
		this.auditLog = auditLog;
		this.clock = clock;
	}

	/** The batch's rows after the import (ERROR rows unchanged), ordered by row number. */
	public List<LegacyRow> importRows(LegacyBatch batch, Notice notice, NoticeTranslation english,
			AuthenticatedUser user, String sourceIp) {
		List<LegacyRow> all = rows.findByBatchIdOrderByRowNoAsc(batch.getBatchId());
		List<LegacyRow> valid = all.stream().filter(r -> r.getStatus() == LegacyRowStatus.VALID)
				.sorted(Comparator.comparing(LegacyRow::getConsentDate)
				.thenComparing(LegacyRow::getRowNo))
				.toList();
		Map<String, String> hashes = translations
				.findByNoticeVersionIdAndDeletedFalseOrderByLanguageCodeAsc(notice.getNoticeVersionId())
				.stream()
				.collect(Collectors.toMap(NoticeTranslation::getLanguageCode,
						NoticeTranslation::getTextHash,
						(a, b) -> a));
		Map<String, Purpose> masters = masters(valid);
		Instant now = clock.instant();
		for (LegacyRow row : valid) {
			String skip = skipReason(row, masters);
			if (skip != null) {
				row.setStatus(LegacyRowStatus.SKIPPED);
				row.setReasons(skip);
			} else {
				String text = hashes.getOrDefault(row.getLanguageCode(), english.getTextHash());
				row.setConsentId(store(batch, row, notice, text, masters, user, sourceIp, now));
				row.setStatus(LegacyRowStatus.IMPORTED);
			}
			row.setModifiedBy(user.auditName());
			rows.saveAndFlush(row);
		}
		return all;
	}

	private Map<String, Purpose> masters(List<LegacyRow> valid) {
		Set<String> codes = valid.stream().flatMap(r -> LegacyRowChecker.codes(r.getPurposes()).stream())
				.collect(Collectors.toSet());
		if (codes.isEmpty()) {
			return Map.of();
		}
		return purposes.findByPurposeCodeInAndActiveTrueAndDeletedFalse(codes).stream()
				.collect(Collectors.toMap(Purpose::getPurposeCode, Function.identity(), (a, b) -> a));
	}

	private String skipReason(LegacyRow row, Map<String, Purpose> masters) {
		Long patient = row.getBeneficiaryRegId();
		if (consents.existsByBeneficiaryRegIdAndEventTypeNot(patient, EventType.LEGACY)) {
			return "NEWER_CONSENT_EXISTS";
		}
		Instant capturedAt = capturedAt(row);
		boolean newerLegacy = consents.findFirstByBeneficiaryRegIdAndEventTypeOrderByCapturedAtDesc(patient,
				EventType.LEGACY).filter(c -> c.getCapturedAt().isAfter(capturedAt)).isPresent();
		if (newerLegacy) {
			return "NEWER_CONSENT_EXISTS";
		}
		if (rows.existsByBeneficiaryRegIdAndConsentDateAndStatus(patient, row.getConsentDate(),
				LegacyRowStatus.IMPORTED)) {
			return "ALREADY_IMPORTED";
		}
		if (!masters.keySet().containsAll(LegacyRowChecker.codes(row.getPurposes()))) {
			return "UNKNOWN_PURPOSE";
		}
		return null;
	}

	private String store(LegacyBatch batch, LegacyRow row, Notice notice, String textHash,
			Map<String, Purpose> masters, AuthenticatedUser user, String sourceIp, Instant now) {
		Consent consent = consents.saveAndFlush(consent(batch, row, notice, textHash, user, now));
		List<PurposeResult> results = new ArrayList<>();
		for (String code : LegacyRowChecker.codes(row.getPurposes())) {
			results.add(decisionWriter.write(consent, code, Decision.GRANT, masters.get(code)));
		}
		if (row.getRepresentativeNameEncrypted() != null) {
			ConsentEvidence witness = ProofRows.row(consent, EvidenceType.WITNESS, user, clock);
			String name = encryptor.decrypt(row.getRepresentativeNameEncrypted());
			witness.setWitnessNameEncrypted(encryptor.encrypt(name));
			witness.setWitnessRelationship(row.getRepresentativeRelationship());
			evidence.save(witness);
		}
		auditLog.append(AuditLogService.LEGACY_IMPORTED, consent.getConsentId(), row.getBeneficiaryRegId(),
				summary(results, batch.getBatchId(), row.getRowNo()), "GRANTED", user.userId(),
				sourceIp);
		return consent.getConsentId();
	}

	private static Consent consent(LegacyBatch batch, LegacyRow row, Notice notice, String textHash,
			AuthenticatedUser user, Instant now) {
		String reference = batch.getBatchId() + ":" + row.getRowNo();
		Consent consent = new Consent();
		consent.setConsentId(UUID.randomUUID().toString());
		consent.setBeneficiaryRegId(row.getBeneficiaryRegId());
		consent.setEventType(EventType.LEGACY);
		consent.setConsentingParty(ConsentingParty.SELF);
		consent.setNoticeVersionId(notice.getNoticeVersionId());
		consent.setLanguageCode(row.getLanguageCode());
		consent.setTextHash(textHash);
		consent.setChannel(Channel.FACILITY);
		consent.setMode(ConsentMode.valueOf(row.getMode()));
		consent.setCapturedBy(user.userId());
		consent.setDeviceId(DEVICE_ID);
		consent.setCapturedAt(capturedAt(row));
		consent.setServerReceivedAt(now);
		consent.setCaptureTimeFlagged(false);
		consent.setSyncStatus(SyncStatus.SYNCED);
		consent.setRequestKey(UUID.nameUUIDFromBytes(reference.getBytes(StandardCharsets.UTF_8)).toString());
		consent.setRequestHash(Sha256.hex(reference + "|" + row.getBeneficiaryRegId() + "|"
				+ row.getConsentDate()
				+ "|" + row.getPurposes() + "|" + row.getLanguageCode() + "|" + row.getMode()));
		return consent;
	}

	/** The consent date at 00:00 India time. */
	static Instant capturedAt(LegacyRow row) {
		return row.getConsentDate().atStartOfDay(LegacyRowChecker.INDIA).toInstant();
	}

	/** {@code P-REG:GRANTED,…;batch:<id>;row:<n>}, the statuses shortened if needed to fit the audit column. */
	static String summary(List<PurposeResult> results, String batchId, Integer rowNo) {
		String suffix = ";batch:" + batchId + ";row:" + rowNo;
		String statuses = results.stream().map(r -> r.purposeCode() + ":" + r.status())
				.collect(Collectors.joining(","));
		int room = SUMMARY_MAX - suffix.length();
		return (statuses.length() > room ? statuses.substring(0, room) : statuses) + suffix;
	}
}
