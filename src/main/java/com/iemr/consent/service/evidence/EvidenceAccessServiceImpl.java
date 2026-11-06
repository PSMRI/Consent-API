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

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.iemr.consent.data.consent.Consent;
import com.iemr.consent.data.evidence.ConsentEvidence;
import com.iemr.consent.model.evidence.EvidenceView;
import com.iemr.consent.repository.consent.ConsentRepository;
import com.iemr.consent.repository.evidence.ConsentEvidenceRepository;
import com.iemr.consent.service.audit.AuditLogService;
import com.iemr.consent.utils.auth.AuthenticatedUser;
import com.iemr.consent.utils.exception.ConsentApiException;
import com.iemr.consent.utils.exception.ErrorCode;

/**
 * Reads proof files back for any logged-in user (decision 5 Nov 2026: no role restriction yet). Files are
 * decrypted and checked against their SHA-256 by {@link EvidenceStorage}; a file that fails the check is never
 * sent. Each retrieval is written to the audit log as {@code evidence:<id>;type:<type>} with status VIEWED or
 * DOWNLOADED; no personal data.
 */
@Service
public class EvidenceAccessServiceImpl implements EvidenceAccessService {

	static final String VIEWED = "VIEWED";

	static final String DOWNLOADED = "DOWNLOADED";

	private static final Logger LOGGER = LoggerFactory.getLogger(EvidenceAccessServiceImpl.class);

	private static final Map<String, String> EXTENSIONS = Map.of("audio/webm", "webm", "audio/ogg", "ogg",
			"audio/mpeg", "mp3", "audio/wav", "wav", "audio/x-wav", "wav", "audio/wave", "wav", "audio/mp4", "m4a",
			"audio/x-m4a", "m4a", "image/jpeg", "jpg", "image/png", "png");

	private final ConsentEvidenceRepository evidence;

	private final ConsentRepository consents;

	private final EvidenceStorage storage;

	private final AuditLogService auditLog;

	public EvidenceAccessServiceImpl(ConsentEvidenceRepository evidence, ConsentRepository consents,
			EvidenceStorage storage, AuditLogService auditLog) {
		this.evidence = evidence;
		this.consents = consents;
		this.storage = storage;
		this.auditLog = auditLog;
	}

	@Override
	@Transactional(readOnly = true)
	public List<EvidenceView> listForConsent(String consentId) {
		if (!consents.existsById(consentId)) {
			throw new ConsentApiException(ErrorCode.NOT_FOUND);
		}
		return evidence.findByConsentIdAndDeletedFalseOrderByCreatedAtAsc(consentId).stream()
				.filter(EvidenceAccessServiceImpl::isStoredFile)
				.map(e -> new EvidenceView(e.getEvidenceId(), e.getType(), e.getContentType(), e.getSizeBytes(),
						e.getDurationSec(), e.getCreatedBy(), e.getCreatedAt()))
				.toList();
	}

	@Override
	@Transactional
	public EvidenceFile open(String evidenceId, boolean download, AuthenticatedUser user, String sourceIp) {
		ConsentEvidence file = evidence.findById(evidenceId)
				.filter(e -> !e.isDeleted() && e.getConsentId() != null && isStoredFile(e))
				.orElseThrow(() -> new ConsentApiException(ErrorCode.NOT_FOUND));
		Long beneficiary = consents.findById(file.getConsentId()).map(Consent::getBeneficiaryRegId).orElse(null);
		byte[] content;
		try {
			content = storage.read(file.getFileRef(), file.getSha256());
		} catch (IllegalStateException e) {
			LOGGER.error("Evidence {} failed its integrity check and was not sent", evidenceId);
			throw new ConsentApiException(ErrorCode.INTERNAL_ERROR);
		}
		auditLog.append(AuditLogService.EVIDENCE_ACCESSED, file.getConsentId(), beneficiary,
				"evidence:" + evidenceId + ";type:" + file.getType(), download ? DOWNLOADED : VIEWED,
				user.userId(), sourceIp);
		return new EvidenceFile(fileName(file), file.getContentType(), content);
	}

	private static boolean isStoredFile(ConsentEvidence e) {
		return e.getType() != null && e.getType().isFile() && e.getFileRef() != null;
	}

	private static String fileName(ConsentEvidence e) {
		String extension = EXTENSIONS.getOrDefault(e.getContentType(), "bin");
		return "consent-evidence-" + e.getEvidenceId() + "." + extension;
	}
}
