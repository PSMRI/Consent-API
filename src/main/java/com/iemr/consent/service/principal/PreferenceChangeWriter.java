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

package com.iemr.consent.service.principal;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.iemr.consent.data.consent.Consent;
import com.iemr.consent.data.consent.ConsentMode;
import com.iemr.consent.data.consent.ConsentPurpose;
import com.iemr.consent.data.consent.ConsentingParty;
import com.iemr.consent.data.consent.Decision;
import com.iemr.consent.data.consent.EventType;
import com.iemr.consent.data.consent.GuardianLink;
import com.iemr.consent.data.consent.PurposeStatus;
import com.iemr.consent.data.evidence.ConsentEvidence;
import com.iemr.consent.data.evidence.EvidenceType;
import com.iemr.consent.data.notice.Notice;
import com.iemr.consent.data.notice.Purpose;
import com.iemr.consent.model.otp.OtpReason;
import com.iemr.consent.model.principal.PreferenceRequest;
import com.iemr.consent.repository.consent.ConsentRepository;
import com.iemr.consent.repository.evidence.ConsentEvidenceRepository;
import com.iemr.consent.repository.notice.NoticeRepository;
import com.iemr.consent.repository.notice.PurposeRepository;
import com.iemr.consent.service.audit.AuditLogService;
import com.iemr.consent.service.consent.ProofRows;
import com.iemr.consent.service.otp.OtpChallenge;
import com.iemr.consent.service.otp.OtpService;
import com.iemr.consent.utils.auth.AuthenticatedUser;
import com.iemr.consent.utils.exception.ConsentApiException;
import com.iemr.consent.utils.exception.ErrorCode;
import com.iemr.consent.utils.http.AfterCommit;

/**
 * Writes one preference change (front-end API 10; FR-005 – FR-008) in a single transaction: lock and check
 * the named decisions, record an UPDATE event with each purpose GRANTED or DENIED at the next version, keep
 * the OTP proof of a GRANT, and audit it.
 *
 * <p>A GRANT needs a verified OTP challenge bound to the patient ({@code refId} = benRegId), from the
 * guardian's phone while a guardian's consent is current (research R6), and is refused when the purpose
 * needs the full consent screen again (re-consent required, expired, or a newer material notice). A DENY of
 * a granted purpose that cannot be withdrawn is refused. The consent-check cache, once it exists, is cleared
 * after commit here (consent-check feature).</p>
 */
@Component
public class PreferenceChangeWriter {

	static final String VERIFIED = "VERIFIED";

	private final PrincipalEventFactory events;

	private final PurposeRepository purposes;

	private final ConsentRepository consentRepository;

	private final NoticeRepository noticeRepository;

	private final OtpService otpService;

	private final ConsentEvidenceRepository evidenceRepository;

	private final EffectiveStatus effectiveStatus;

	private final AuditLogService auditLogService;

	private final Clock clock;

	public PreferenceChangeWriter(PrincipalEventFactory events, PurposeRepository purposes,
			ConsentRepository consentRepository, NoticeRepository noticeRepository, OtpService otpService,
			ConsentEvidenceRepository evidenceRepository, EffectiveStatus effectiveStatus,
			AuditLogService auditLogService, Clock clock) {
		this.events = events;
		this.purposes = purposes;
		this.consentRepository = consentRepository;
		this.noticeRepository = noticeRepository;
		this.otpService = otpService;
		this.evidenceRepository = evidenceRepository;
		this.effectiveStatus = effectiveStatus;
		this.auditLogService = auditLogService;
		this.clock = clock;
	}

	@Transactional
	public void write(Long benRegId, PreferenceRequest request, String requestKey, String requestHash,
			AuthenticatedUser user, String sourceIp) {
		boolean grant = request.decision() == Decision.GRANT;
		Map<String, ConsentPurpose> rows = events.lockCurrent(benRegId, request.purposeCodes(),
				request.versions());
		Map<String, Purpose> byCode = ConsentViewMapper
				.latestByCode(purposes.findByPurposeCodeInAndActiveTrueAndDeletedFalse(rows.keySet()));
		if (grant) {
			checkGrantable(rows);
		} else {
			checkDeniable(rows, byCode);
		}
		Consent source = events.newestSource(rows.values());
		ConsentingParty party = events.partyFor(source);
		OtpChallenge challenge = grant ? otp(request, benRegId, source, party, user) : null;
		Consent event = events.saveEvent(benRegId, EventType.UPDATE, grant ? ConsentMode.OTP : null, source,
				party, request, requestKey, requestHash, user);
		PurposeStatus status = grant ? PurposeStatus.GRANTED : PurposeStatus.DENIED;
		rows.values().forEach(row -> events.replace(row, event, status,
				grant ? validUntil(byCode.get(row.getPurposeCode())) : null));
		if (challenge != null) {
			saveOtpProof(event, challenge, user);
		}
		String summary = rows.keySet().stream().map(c -> c + ":" + status).collect(Collectors.joining(","));
		auditLogService.append(AuditLogService.PREFERENCE_CHANGED, event.getConsentId(), benRegId, summary,
				status.name(), user.userId(), sourceIp);
	}

	/** GRANT: not already granted, and not a purpose that needs the full consent screen (C14, C16). */
	private void checkGrantable(Map<String, ConsentPurpose> rows) {
		Map<String, Consent> sources = consentRepository
				.findByConsentIdIn(rows.values().stream().map(ConsentPurpose::getConsentId).toList())
				.stream()
				.collect(Collectors.toMap(Consent::getConsentId, c -> c));
		Map<Long, Boolean> superseded = new HashMap<>();
		for (ConsentPurpose row : rows.values()) {
			PurposeStatus status = effectiveStatus.of(row);
			if (status == PurposeStatus.GRANTED) {
				throw new ConsentApiException(ErrorCode.STATE_CONFLICT,
						"This purpose is already granted",
						row.getPurposeCode(), null);
			}
			Long noticeId = sources.get(row.getConsentId()).getNoticeVersionId();
			boolean newerNotice = superseded.computeIfAbsent(noticeId, this::hasNewerMaterialNotice);
			if (status == PurposeStatus.RECONSENT_REQUIRED || status == PurposeStatus.EXPIRED
					|| newerNotice) {
				throw new ConsentApiException(ErrorCode.BUSINESS_RULE_VIOLATION,
						"Take consent again through the consent screen", row.getPurposeCode(),
						null);
			}
		}
	}

	/** DENY: not already denied, and a granted purpose must be one that can be withdrawn. */
	private void checkDeniable(Map<String, ConsentPurpose> rows, Map<String, Purpose> byCode) {
		for (ConsentPurpose row : rows.values()) {
			PurposeStatus status = effectiveStatus.of(row);
			if (status == PurposeStatus.DENIED) {
				throw new ConsentApiException(ErrorCode.STATE_CONFLICT,
						"This purpose is already denied",
						row.getPurposeCode(), null);
			}
			Purpose purpose = byCode.get(row.getPurposeCode());
			if (status == PurposeStatus.GRANTED && (purpose == null || !purpose.isWithdrawable())) {
				throw new ConsentApiException(ErrorCode.PURPOSE_NOT_WITHDRAWABLE, null,
						row.getPurposeCode(), null);
			}
		}
	}

	private boolean hasNewerMaterialNotice(Long noticeVersionId) {
		Optional<Notice> notice = noticeRepository.findById(noticeVersionId);
		return notice.isPresent() && !noticeRepository
				.findNewerMaterial(notice.get().getNoticeCode(), notice.get().getVersionNo()).isEmpty();
	}

	private OtpChallenge otp(PreferenceRequest request, Long benRegId, Consent source, ConsentingParty party,
			AuthenticatedUser user) {
		String phoneHash = null;
		Set<OtpReason> reasons = Set.of(OtpReason.CONSENT);
		if (party == ConsentingParty.GUARDIAN) {
			GuardianLink link = events.activeGuardian(source).orElseThrow();
			phoneHash = events.guardianPhoneHash(link);
			reasons = Set.of(OtpReason.GUARDIAN);
		}
		return otpService.requireUsableFor(request.otpChallengeId().toString(), reasons,
				String.valueOf(benRegId), user, phoneHash);
	}

	private Instant validUntil(Purpose purpose) {
		if (purpose == null || purpose.getValidityDays() == null) {
			return null;
		}
		return clock.instant().plus(Duration.ofDays(purpose.getValidityDays()));
	}

	private void saveOtpProof(Consent event, OtpChallenge challenge, AuthenticatedUser user) {
		ConsentEvidence row = ProofRows.row(event, EvidenceType.OTP, user, clock);
		row.setOtpStatus(VERIFIED);
		row.setMaskedPhone(challenge.maskedPhone());
		evidenceRepository.save(row);
		AfterCommit.run(() -> otpService.markUsed(challenge.challengeId()));
	}
}
