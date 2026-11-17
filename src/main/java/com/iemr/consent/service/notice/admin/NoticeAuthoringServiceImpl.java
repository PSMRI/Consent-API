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

package com.iemr.consent.service.notice.admin;

import java.util.List;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.iemr.consent.data.notice.Notice;
import com.iemr.consent.data.notice.NoticeContext;
import com.iemr.consent.data.notice.NoticePurpose;
import com.iemr.consent.data.notice.NoticeRequest;
import com.iemr.consent.data.notice.NoticeRequestAction;
import com.iemr.consent.data.notice.NoticeStatus;
import com.iemr.consent.data.notice.NoticeTranslation;
import com.iemr.consent.model.notice.NoticeLanguage;
import com.iemr.consent.model.notice.admin.DraftRequest;
import com.iemr.consent.model.notice.admin.GrievanceInput;
import com.iemr.consent.model.notice.admin.MessageEntry;
import com.iemr.consent.model.notice.admin.PublishRequest;
import com.iemr.consent.repository.notice.NoticePurposeRepository;
import com.iemr.consent.repository.notice.NoticeRepository;
import com.iemr.consent.repository.notice.NoticeRequestRepository;
import com.iemr.consent.repository.notice.NoticeTranslationRepository;
import com.iemr.consent.service.audit.AuditLogService;
import com.iemr.consent.service.notice.NoticeService;
import com.iemr.consent.utils.auth.AuthenticatedUser;
import com.iemr.consent.utils.exception.ConsentApiException;
import com.iemr.consent.utils.exception.ErrorCode;

import jakarta.persistence.EntityManager;

/**
 * Lists a project's consent messages and saves its English draft (feature 006, US1 and US2, research R5 to R7,
 * R13); publishing is {@link NoticePublisher}. A draft is a {@code cm_notice} row in {@code DRAFT} with its English
 * translation, never served to patients. Each save is recorded for idempotent replay and audited in the same
 * transaction, without any notice text.
 */
@Service
public class NoticeAuthoringServiceImpl implements NoticeAuthoringService {

	private final NoticeRepository notices;

	private final NoticeTranslationRepository translations;

	private final NoticePurposeRepository purposes;

	private final NoticeRequestRepository requests;

	private final NoticeService noticeService;

	private final NoticeEntryMapper mapper;

	private final NoticePublisher publisher;

	private final AuditLogService auditLog;

	private final EntityManager entityManager;

	public NoticeAuthoringServiceImpl(NoticeRepository notices, NoticeTranslationRepository translations,
			NoticePurposeRepository purposes, NoticeRequestRepository requests, NoticeService noticeService,
			NoticeEntryMapper mapper, NoticePublisher publisher, AuditLogService auditLog,
			EntityManager entityManager) {
		this.notices = notices;
		this.translations = translations;
		this.purposes = purposes;
		this.requests = requests;
		this.noticeService = noticeService;
		this.mapper = mapper;
		this.publisher = publisher;
		this.auditLog = auditLog;
		this.entityManager = entityManager;
	}

	@Override
	@Transactional(readOnly = true)
	public List<MessageEntry> list(Integer projectId) {
		return mapper.list(projectId);
	}

	@Override
	@Transactional
	public MessageEntry saveDraft(Integer projectId, NoticeContext context, DraftRequest request, String requestKey,
			String requestHash, AuthenticatedUser user, String sourceIp) {
		NoticeRequest earlier = requests.findByActorUserIdAndRequestKey(user.userId(), requestKey).orElse(null);
		if (earlier != null) {
			if (!earlier.getRequestHash().equals(requestHash)) {
				throw new ConsentApiException(ErrorCode.STATE_CONFLICT);
			}
			return mapper.english(projectId, context);
		}
		Notice draft = notices.findFirstByProjectIdAndContextAndStatusAndDeletedFalse(projectId, context,
				NoticeStatus.DRAFT).orElse(null);
		boolean created = draft == null;
		Long purposeSource = null;
		if (created) {
			Notice latest = notices
					.findFirstByProjectIdAndContextAndDeletedFalseOrderByVersionNoDesc(
							projectId, context)
					.orElse(null);
			purposeSource = latest != null ? latest.getNoticeVersionId() : centralNoticeId(context);
			draft = newDraft(projectId, context, latest, user);
		} else {
			draft.setModifiedBy(user.auditName());
		}
		applyGrievance(draft, request.grievance());
		try {
			draft = notices.saveAndFlush(draft);
		} catch (DataIntegrityViolationException e) {
			throw new ConsentApiException(ErrorCode.STALE_VERSION);
		}
		if (created) {
			copyPurposes(purposeSource, draft.getNoticeVersionId(), user);
		}
		Long draftId = draft.getNoticeVersionId();
		NoticeTranslation english = created ? null
				: translations.findByNoticeVersionIdAndLanguageCodeAndDeletedFalse(draftId,
						NoticeEntryMapper.ENGLISH).orElse(null);
		translations.save(englishText(draft, english, request, context, user));
		requests.save(record(user, requestKey, requestHash, NoticeRequestAction.DRAFT, draftId));
		auditLog.append(AuditLogService.NOTICE_DRAFT_SAVED, null, null, summary(draft), "DRAFT", user.userId(),
				sourceIp);
		entityManager.flush();
		entityManager.refresh(draft);
		return mapper.english(projectId, context);
	}

	@Override
	public MessageEntry publish(Integer projectId, NoticeContext context, PublishRequest request, String requestKey,
			String requestHash, AuthenticatedUser user, String sourceIp) {
		return publisher.publish(projectId, context, request, requestKey, requestHash, user, sourceIp);
	}

	/** First draft of a project: the project's code and next version, or a new code at version 1 (research R5). */
	private static Notice newDraft(Integer projectId, NoticeContext context, Notice latest,
			AuthenticatedUser user) {
		Notice draft = new Notice();
		draft.setNoticeCode(latest != null ? latest.getNoticeCode() : "P" + projectId + "-" + context.name());
		draft.setVersionNo(latest != null ? latest.getVersionNo() + 1 : 1);
		draft.setContext(context);
		draft.setStatus(NoticeStatus.DRAFT);
		draft.setProjectId(projectId);
		draft.setCreatedBy(user.auditName());
		return draft;
	}

	private Long centralNoticeId(NoticeContext context) {
		return notices.findFirstByContextAndProjectIdIsNullAndStatusAndDeletedFalseOrderByVersionNoDesc(context,
				NoticeStatus.PUBLISHED).map(Notice::getNoticeVersionId)
				.orElseThrow(() -> new ConsentApiException(ErrorCode.BUSINESS_RULE_VIOLATION,
						"No central notice to take purposes from"));
	}

	/** The new version covers the same purposes, in the same order, as its source (research R7). */
	private void copyPurposes(Long fromId, Long toId, AuthenticatedUser user) {
		List<NoticePurpose> copies = purposes.findByNoticeVersionIdAndDeletedFalseOrderByDisplayRankAsc(fromId)
				.stream().map(p -> {
					NoticePurpose copy = new NoticePurpose();
					copy.setNoticeVersionId(toId);
					copy.setPurposeCode(p.getPurposeCode());
					copy.setDisplayRank(p.getDisplayRank());
					copy.setCreatedBy(user.auditName());
					return copy;
				}).toList();
		purposes.saveAll(copies);
	}

	private static void applyGrievance(Notice notice, GrievanceInput g) {
		notice.setGrievanceOfficerName(g == null ? null : g.officerName());
		notice.setGrievancePhone(g == null ? null : g.phone());
		notice.setGrievanceEmail(g == null ? null : g.email());
		notice.setGrievanceAddress(g == null ? null : g.address());
		notice.setGrievanceOfficeHours(g == null ? null : g.officeHours());
	}

	private NoticeTranslation englishText(Notice draft, NoticeTranslation existing, DraftRequest r,
			NoticeContext context, AuthenticatedUser user) {
		NoticeTranslation t = existing;
		if (t == null) {
			t = new NoticeTranslation();
			t.setNoticeVersionId(draft.getNoticeVersionId());
			t.setLanguageCode(NoticeEntryMapper.ENGLISH);
			t.setLanguageName(englishName(context));
			t.setCreatedBy(user.auditName());
		} else {
			t.setModifiedBy(user.auditName());
		}
		t.setBody(r.body());
		t.setDataItems(r.dataItems());
		t.setRetention(r.retention());
		t.setRecipients(r.recipients());
		t.setWithdrawalInfo(r.withdrawalInfo());
		t.setGrievanceInfo(r.grievanceInfo());
		t.setDeclaration(r.declaration());
		t.setRepresentativeDeclaration(r.representativeDeclaration());
		t.setTextHash(NoticeTextHash.of(t));
		return t;
	}

	private String englishName(NoticeContext context) {
		return noticeService.languages(context, null).stream()
				.filter(l -> NoticeEntryMapper.ENGLISH.equals(l.languageCode()))
				.map(NoticeLanguage::languageName).findFirst().orElse("English");
	}

	static NoticeRequest record(AuthenticatedUser user, String key, String hash, NoticeRequestAction action,
			Long noticeVersionId) {
		NoticeRequest row = new NoticeRequest();
		row.setActorUserId(user.userId());
		row.setRequestKey(key);
		row.setRequestHash(hash);
		row.setAction(action);
		row.setNoticeVersionId(noticeVersionId);
		row.setCreatedBy(user.auditName());
		return row;
	}

	private static String summary(Notice n) {
		return "notice:" + n.getNoticeVersionId() + ";project:" + n.getProjectId() + ";context:"
				+ n.getContext() + ";v:" + n.getVersionNo();
	}
}
