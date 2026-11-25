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

package com.iemr.consent.service.consent;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.iemr.consent.data.consent.Consent;
import com.iemr.consent.data.consent.ConsentPurpose;
import com.iemr.consent.data.consent.EventType;
import com.iemr.consent.data.consent.GuardianLink;
import com.iemr.consent.data.evidence.ConsentEvidence;
import com.iemr.consent.data.notice.Notice;
import com.iemr.consent.data.notice.NoticeTranslation;
import com.iemr.consent.data.notice.Purpose;
import com.iemr.consent.model.consent.ConsentDetail;
import com.iemr.consent.model.consent.ConsentDetail.Capture;
import com.iemr.consent.model.consent.ConsentDetail.Guardian;
import com.iemr.consent.model.consent.ConsentDetail.NoticeShown;
import com.iemr.consent.model.consent.ConsentDetail.Proof;
import com.iemr.consent.model.consent.ConsentDetail.PurposeDecision;
import com.iemr.consent.repository.consent.ConsentPurposeRepository;
import com.iemr.consent.repository.consent.ConsentRepository;
import com.iemr.consent.repository.consent.GuardianLinkRepository;
import com.iemr.consent.repository.evidence.ConsentEvidenceRepository;
import com.iemr.consent.repository.notice.NoticeRepository;
import com.iemr.consent.repository.notice.NoticeTranslationRepository;
import com.iemr.consent.repository.notice.PurposeRepository;
import com.iemr.consent.service.audit.AuditLogService;
import com.iemr.consent.service.notify.ConsentNotifier;
import com.iemr.consent.service.principal.ConsentViewMapper;
import com.iemr.consent.service.principal.EffectiveStatus;
import com.iemr.consent.utils.auth.AuthenticatedUser;
import com.iemr.consent.utils.crypto.FieldEncryptor;
import com.iemr.consent.utils.crypto.PhoneMasker;
import com.iemr.consent.utils.exception.ConsentApiException;
import com.iemr.consent.utils.exception.ErrorCode;

/**
 * Builds API 17 from the stored rows: the event, its purpose rows (named from the purpose master), the notice
 * and its translation in the language shown, the guardian link, the proof rows and the SMS outcome. Each view is
 * written to the audit log in the same transaction ({@code CONSENT_VIEWED}, summary {@code P-REG:GRANTED,…}).
 */
@Service
public class ConsentDetailServiceImpl implements ConsentDetailService {

	private final ConsentRepository consents;

	private final ConsentPurposeRepository decisions;

	private final PurposeRepository purposes;

	private final NoticeRepository notices;

	private final NoticeTranslationRepository translations;

	private final GuardianLinkRepository guardians;

	private final ConsentEvidenceRepository evidence;

	private final ConsentNotifier notifier;

	private final EffectiveStatus effectiveStatus;

	private final FieldEncryptor encryptor;

	private final AuditLogService auditLog;

	public ConsentDetailServiceImpl(ConsentRepository consents, ConsentPurposeRepository decisions,
			PurposeRepository purposes, NoticeRepository notices, NoticeTranslationRepository translations,
			GuardianLinkRepository guardians, ConsentEvidenceRepository evidence, ConsentNotifier notifier,
			EffectiveStatus effectiveStatus, FieldEncryptor encryptor, AuditLogService auditLog) {
		this.consents = consents;
		this.decisions = decisions;
		this.purposes = purposes;
		this.notices = notices;
		this.translations = translations;
		this.guardians = guardians;
		this.evidence = evidence;
		this.notifier = notifier;
		this.effectiveStatus = effectiveStatus;
		this.encryptor = encryptor;
		this.auditLog = auditLog;
	}

	@Override
	@Transactional
	public ConsentDetail detail(String consentId, AuthenticatedUser user, String sourceIp) {
		String id = ConsentIds.normalise(consentId);
		Consent consent = (id == null ? null : consents.findById(id).orElse(null));
		if (consent == null) {
			throw new ConsentApiException(ErrorCode.NOT_FOUND);
		}
		List<PurposeDecision> purposeDecisions = purposes(id);
		Guardian guardian = guardians.findFirstByConsentIdAndDeletedFalse(id).map(this::guardian).orElse(null);
		List<Proof> proofs = evidence.findByConsentIdAndDeletedFalseOrderByCreatedAtAsc(id).stream()
				.map(this::proof).toList();
		ConsentDetail detail = new ConsentDetail(id, consent.getBeneficiaryRegId(), consent.getEventType(),
				consent.getConsentingParty(), consent.getChannel(), consent.getMode(),
				consent.getLanguageCode(), consent.getTextHash(), capture(consent), notice(consent),
				purposeDecisions, guardian, proofs, notifier.stored(id), withdrawal(consent));
		auditLog.append(AuditLogService.CONSENT_VIEWED, id, consent.getBeneficiaryRegId(),
				purposeDecisions.stream().map(p -> p.purposeCode() + ":" + p.status())
						.collect(Collectors.joining(",")),
				"VIEWED", user.userId(), sourceIp);
		return detail;
	}

	private List<PurposeDecision> purposes(String consentId) {
		List<ConsentPurpose> rows = decisions.findByConsentIdOrderByConsentPurposeIdAsc(consentId);
		List<String> codes = rows.stream().map(ConsentPurpose::getPurposeCode).toList();
		Map<String, Purpose> byCode = ConsentViewMapper.latestByCode(
				purposes.findByPurposeCodeInAndActiveTrueAndDeletedFalse(codes));
		return rows.stream().sorted(Comparator.comparing(ConsentPurpose::getPurposeCode))
				.map(row -> decision(row, byCode.get(row.getPurposeCode()))).toList();
	}

	private PurposeDecision decision(ConsentPurpose row, Purpose purpose) {
		boolean known = purpose != null;
		return new PurposeDecision(row.getPurposeCode(), known ? purpose.getName() : null,
				known ? purpose.getDescription() : null, known ? purpose.getLegalBasis() : null,
				known && purpose.isMandatory(), known && purpose.isWithdrawable(), row.getDecision(),
				row.isCurrent() ? effectiveStatus.of(row) : row.getStatus(), row.getVersionNo(),
				row.isCurrent(), row.getValidUntil());
	}

	private static Capture capture(Consent c) {
		return new Capture(c.getCapturedBy(), c.getCapturedAt(), c.getServerReceivedAt(),
				c.isCaptureTimeFlagged(), c.getProviderServiceMapId(), c.getProjectId(),
				c.getFacilityId(), c.getServicePointId(), c.getServiceLine(), c.getDeviceId(),
				c.getCallId(), c.getSyncStatus());
	}

	private NoticeShown notice(Consent consent) {
		Notice notice = consent.getNoticeVersionId() == null ? null
				: notices.findById(consent.getNoticeVersionId()).orElse(null);
		if (notice == null) {
			return null;
		}
		NoticeTranslation text = translations.findByNoticeVersionIdAndLanguageCodeAndDeletedFalse(
				notice.getNoticeVersionId(), consent.getLanguageCode()).orElse(null);
		boolean shown = text != null;
		return new NoticeShown(notice.getNoticeVersionId(), notice.getNoticeCode(), notice.getVersionNo(),
				notice.getContext(), notice.getProjectId(), notice.getPublishedAt(),
				shown ? text.getLanguageName() : null, shown ? text.getBody() : null,
				shown ? text.getDataItems() : null, shown ? text.getRecipients() : null,
				shown ? text.getRetention() : null, shown ? text.getWithdrawalInfo() : null,
				shown ? text.getGrievanceInfo() : null);
	}

	private Guardian guardian(GuardianLink link) {
		return new Guardian(link.getGuardianType(), decrypt(link.getGuardianNameEncrypted()),
				link.getGuardianPhoneEncrypted() == null ? null
						: PhoneMasker.mask(encryptor.decrypt(link.getGuardianPhoneEncrypted())),
				link.getRelationship(), link.getVerificationMethod(), link.getVerifiedAt(),
				link.isFollowUpRequired(), link.getValidUntil());
	}

	private Proof proof(ConsentEvidence e) {
		boolean file = e.getType() != null && e.getType().isFile() && e.getFileRef() != null;
		return new Proof(e.getEvidenceId(), e.getType(), file, e.getContentType(), e.getSizeBytes(),
				e.getDurationSec(), decrypt(e.getWitnessNameEncrypted()), e.getWitnessRelationship(),
				e.getOtpStatus(), e.getMaskedPhone(), e.getDeclarationText(), e.getDeclarationWording(),
				e.getCreatedBy(), e.getCreatedAt());
	}

	/** @return the reason and remarks of a WITHDRAWAL event; null for other events */
	private ConsentDetail.Withdrawal withdrawal(Consent consent) {
		if (consent.getEventType() != EventType.WITHDRAWAL) {
			return null;
		}
		return new ConsentDetail.Withdrawal(consent.getWithdrawalReason(),
				decrypt(consent.getWithdrawalRemarksEncrypted()));
	}

	private String decrypt(String encrypted) {
		return encrypted == null ? null : encryptor.decrypt(encrypted);
	}
}
