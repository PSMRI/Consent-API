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

import java.math.BigDecimal;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import com.iemr.consent.config.ConsentProperties;
import com.iemr.consent.data.evidence.ConsentEvidence;
import com.iemr.consent.data.evidence.EvidenceType;
import com.iemr.consent.model.evidence.EvidenceUploadResult;
import com.iemr.consent.repository.evidence.ConsentEvidenceRepository;
import com.iemr.consent.utils.auth.AuthenticatedUser;
import com.iemr.consent.utils.crypto.Sha256;
import com.iemr.consent.utils.exception.ConsentApiException;
import com.iemr.consent.utils.exception.ErrorCode;
import com.iemr.consent.utils.response.FieldErrorDetail;

/**
 * Stores proof files (research R2, R4).
 *
 * <ul>
 * <li>The server's SHA-256 of the bytes must equal the browser's ({@code EVIDENCE_HASH_MISMATCH});
 * nothing is stored otherwise.</li>
 * <li>Type, content type, size and voice-clip length (30–60 s, TODO-DPO) are checked.</li>
 * <li>The same request key and file replay the first result; a different file is
 * {@code STATE_CONFLICT}. If the row cannot be saved, the stored file is deleted.</li>
 * </ul>
 */
@Service
public class EvidenceServiceImpl implements EvidenceService {

	private final ConsentEvidenceRepository repository;

	private final EvidenceStorage storage;

	private final Clock clock;

	private final ConsentProperties.Evidence settings;

	public EvidenceServiceImpl(ConsentEvidenceRepository repository, EvidenceStorage storage, Clock clock,
			ConsentProperties properties) {
		this.repository = repository;
		this.storage = storage;
		this.clock = clock;
		this.settings = properties.evidence();
	}

	@Override
	public EvidenceUploadResult upload(EvidenceUpload upload, String requestKey, AuthenticatedUser user) {
		String serverSha = Sha256.hex(upload.content());
		String requestHash = Sha256.hex(upload.type() + "|" + upload.sha256() + "|" + upload.durationSec() + "|"
				+ serverSha);
		ConsentEvidence existing = repository.findByUploadedByAndRequestKey(user.userId(), requestKey)
				.orElse(null);
		if (existing != null) {
			return replay(existing, requestHash);
		}
		String contentType = check(upload);
		if (!serverSha.equals(upload.sha256())) {
			throw new ConsentApiException(ErrorCode.EVIDENCE_HASH_MISMATCH);
		}
		ConsentEvidence evidence = newEvidence(upload, contentType, serverSha, requestKey, requestHash, user);
		evidence.setFileRef(storage.store(evidence.getEvidenceId(), upload.content()));
		try {
			repository.saveAndFlush(evidence);
		} catch (DataIntegrityViolationException e) {
			storage.delete(evidence.getFileRef());
			ConsentEvidence winner = repository.findByUploadedByAndRequestKey(user.userId(), requestKey)
					.orElseThrow(() -> new ConsentApiException(ErrorCode.STATE_CONFLICT));
			return replay(winner, requestHash);
		} catch (RuntimeException e) {
			storage.delete(evidence.getFileRef());
			throw e;
		}
		return new EvidenceUploadResult(evidence.getEvidenceId(), serverSha);
	}

	@Override
	public List<ConsentEvidence> requireLinkable(List<UUID> evidenceIds, AuthenticatedUser user) {
		List<ConsentEvidence> result = new ArrayList<>();
		for (UUID id : evidenceIds.stream().distinct().toList()) {
			ConsentEvidence evidence = repository.findById(id.toString())
					.filter(e -> e.getType().isFile() && user.userId().equals(e.getUploadedBy()))
					.orElseThrow(() -> new ConsentApiException(ErrorCode.NOT_FOUND));
			if (evidence.getConsentId() != null) {
				throw new ConsentApiException(ErrorCode.STATE_CONFLICT);
			}
			result.add(evidence);
		}
		return result;
	}

	private static EvidenceUploadResult replay(ConsentEvidence existing, String requestHash) {
		if (!requestHash.equals(existing.getRequestHash())) {
			throw new ConsentApiException(ErrorCode.STATE_CONFLICT);
		}
		return new EvidenceUploadResult(existing.getEvidenceId(), existing.getSha256());
	}

	/** @return the normalised content type */
	private String check(EvidenceUpload upload) {
		if (upload.type() == null || !upload.type().isUploadable()) {
			throw invalid("type", "must be VOICE_CLIP or SIGNED_FORM_PHOTO");
		}
		if (upload.content().length == 0) {
			throw invalid("file", "must not be empty");
		}
		boolean audio = upload.type() == EvidenceType.VOICE_CLIP;
		if (upload.content().length > (audio ? settings.maxAudioBytes() : settings.maxPhotoBytes())) {
			throw new ConsentApiException(ErrorCode.PAYLOAD_TOO_LARGE);
		}
		String contentType = normalise(upload.contentType());
		String family = audio ? "audio/" : "image/";
		if (!settings.allowedContentTypes().contains(contentType) || !contentType.startsWith(family)) {
			throw new ConsentApiException(ErrorCode.UNSUPPORTED_MEDIA_TYPE);
		}
		if (audio && !validDuration(upload.durationSec())) {
			throw invalid("durationSec", "must be between " + settings.voiceMinSeconds() + " and "
					+ settings.voiceMaxSeconds() + " seconds");
		}
		return contentType;
	}

	private boolean validDuration(BigDecimal seconds) {
		return seconds != null && seconds.compareTo(BigDecimal.valueOf(settings.voiceMinSeconds())) >= 0
				&& seconds.compareTo(BigDecimal.valueOf(settings.voiceMaxSeconds())) <= 0;
	}

	private ConsentEvidence newEvidence(EvidenceUpload upload, String contentType, String sha, String requestKey,
			String requestHash, AuthenticatedUser user) {
		ConsentEvidence evidence = new ConsentEvidence();
		evidence.setEvidenceId(UUID.randomUUID().toString());
		evidence.setType(upload.type());
		evidence.setSha256(sha);
		evidence.setContentType(contentType);
		evidence.setSizeBytes((long) upload.content().length);
		evidence.setDurationSec(upload.durationSec());
		evidence.setUploadedBy(user.userId());
		evidence.setRequestKey(requestKey);
		evidence.setRequestHash(requestHash);
		evidence.setCreatedAt(clock.instant());
		evidence.setCreatedBy(user.auditName());
		return evidence;
	}

	private static String normalise(String contentType) {
		if (contentType == null) {
			return "";
		}
		int semicolon = contentType.indexOf(';');
		String base = semicolon >= 0 ? contentType.substring(0, semicolon) : contentType;
		return base.trim().toLowerCase(Locale.ROOT);
	}

	private static ConsentApiException invalid(String field, String message) {
		return new ConsentApiException(ErrorCode.VALIDATION_FAILED, null, null,
				List.of(new FieldErrorDetail(field, message)));
	}
}
