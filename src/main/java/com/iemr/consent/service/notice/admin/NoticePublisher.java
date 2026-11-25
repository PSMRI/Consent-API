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

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.iemr.consent.data.notice.Notice;
import com.iemr.consent.data.notice.NoticeContext;
import com.iemr.consent.data.notice.NoticeRequest;
import com.iemr.consent.data.notice.NoticeRequestAction;
import com.iemr.consent.data.notice.NoticeStatus;
import com.iemr.consent.data.notice.NoticeTranslation;
import com.iemr.consent.model.notice.NoticeLanguage;
import com.iemr.consent.model.notice.admin.MessageEntry;
import com.iemr.consent.model.notice.admin.PublishRequest;
import com.iemr.consent.repository.notice.NoticeRepository;
import com.iemr.consent.repository.notice.NoticeRequestRepository;
import com.iemr.consent.repository.notice.NoticeTranslationRepository;
import com.iemr.consent.service.audit.AuditLogService;
import com.iemr.consent.service.notice.NoticeService;
import com.iemr.consent.service.notice.translate.NoticeTranslator;
import com.iemr.consent.utils.auth.AuthenticatedUser;
import com.iemr.consent.utils.exception.ConsentApiException;
import com.iemr.consent.utils.exception.ErrorCode;

import jakarta.persistence.EntityManager;

/**
 * Publishes a project's English draft (A8, feature 006, FR-006 to FR-009, research R5, R8, R9). Every requested
 * language is translated <em>before</em> anything is written; then, in one transaction, the draft becomes the
 * published version (only if it is still a draft, FR-013), the translations are stored as machine-translated, the
 * previous version is retired, and the publish is audited and recorded for replay. Any refusal or failure changes
 * nothing. {@code requireReconsent} only marks the version material: re-consent is derived from it (C14); no consent
 * row is written.
 */
@Service
public class NoticePublisher {

	static final String NOT_CONFIGURED = "Translation is not configured; publish in English only";

	private final NoticeRepository notices;

	private final NoticeTranslationRepository translations;

	private final NoticeRequestRepository requests;

	private final NoticeService noticeService;

	private final NoticeTranslator translator;

	private final NoticeEntryMapper mapper;

	private final AuditLogService auditLog;

	private final EntityManager entityManager;

	private final Clock clock;

	public NoticePublisher(NoticeRepository notices, NoticeTranslationRepository translations,
			NoticeRequestRepository requests, NoticeService noticeService, NoticeTranslator translator,
			NoticeEntryMapper mapper, AuditLogService auditLog, EntityManager entityManager, Clock clock) {
		this.notices = notices;
		this.translations = translations;
		this.requests = requests;
		this.noticeService = noticeService;
		this.translator = translator;
		this.mapper = mapper;
		this.auditLog = auditLog;
		this.entityManager = entityManager;
		this.clock = clock;
	}

	@Transactional
	public MessageEntry publish(Integer projectId, NoticeContext context, PublishRequest request, String requestKey,
			String requestHash, AuthenticatedUser user, String sourceIp) {
		NoticeRequest earlier = requests.findByActorUserIdAndRequestKey(user.userId(), requestKey).orElse(null);
		if (earlier != null) {
			if (!earlier.getRequestHash().equals(requestHash)) {
				throw new ConsentApiException(ErrorCode.STATE_CONFLICT);
			}
			return mapper.english(projectId, context);
		}
		Notice draft = notices.findFirstByProjectIdAndContextAndStatusAndDeletedFalse(projectId, context,
				NoticeStatus.DRAFT).orElseThrow(() -> refused("Save the message before publishing"));
		NoticeTranslation english = translations.findByNoticeVersionIdAndLanguageCodeAndDeletedFalse(
				draft.getNoticeVersionId(), NoticeEntryMapper.ENGLISH)
				.orElseThrow(() -> refused("Save the message before publishing"));
		if (english.getDeclaration() == null || english.getDeclaration().isBlank()) {
			throw refused("Enter the patient declaration");
		}
		Set<String> languages = languages(request.translateTo());
		Map<String, String> names = centralLanguages(context);
		if (!languages.isEmpty() && !translator.isConfigured()) {
			throw refused(NOT_CONFIGURED);
		}
		for (String language : languages) {
			if (!names.containsKey(language)) {
				throw refused("Unknown language " + language);
			}
		}
		Map<String, Map<String, String>> translated = translateAll(fields(english), languages);

		boolean material = Boolean.TRUE.equals(request.requireReconsent());
		Instant now = clock.instant();
		if (notices.publishDraft(draft.getNoticeVersionId(), user.auditName(), now, material) == 0) {
			throw new ConsentApiException(ErrorCode.STALE_VERSION);
		}
		translated.forEach((language, text) -> translations
				.save(translation(draft, language, names.get(language), text, user)));
		notices.retireOthers(projectId, context, draft.getNoticeVersionId(), user.auditName());
		auditLog.append(AuditLogService.NOTICE_PUBLISHED, null, null, summary(draft, languages, material),
				"PUBLISHED", user.userId(), sourceIp);
		requests.save(NoticeAuthoringServiceImpl.record(user, requestKey, requestHash,
				NoticeRequestAction.PUBLISH, draft.getNoticeVersionId()));
		entityManager.flush();
		entityManager.refresh(draft);
		return mapper.english(projectId, context);
	}

	private static Set<String> languages(List<String> translateTo) {
		Set<String> languages = new LinkedHashSet<>();
		if (translateTo != null) {
			translateTo.stream().filter(l -> !NoticeEntryMapper.ENGLISH.equals(l)).forEach(languages::add);
		}
		return languages;
	}

	/** Language code to name from the central notice of the context (API 2's list). */
	private Map<String, String> centralLanguages(NoticeContext context) {
		Map<String, String> names = new LinkedHashMap<>();
		for (NoticeLanguage l : noticeService.languages(context, null)) {
			names.put(l.languageCode(), l.languageName());
		}
		return names;
	}

	/** Translates every language first; any failure, including a time-out, publishes nothing (FR-007). */
	private Map<String, Map<String, String>> translateAll(Map<String, String> english, Set<String> languages) {
		Map<String, Map<String, String>> result = new LinkedHashMap<>();
		for (String language : languages) {
			try {
				Map<String, String> text = translator.translate(english, NoticeEntryMapper.ENGLISH,
						language);
				result.put(language, text);
			} catch (RuntimeException e) {
				throw refused("Translation failed for " + language + "; nothing was published");
			}
		}
		return result;
	}

	/** The English text sections to translate; empty ones are left out and stay empty. */
	static Map<String, String> fields(NoticeTranslation t) {
		Map<String, String> fields = new LinkedHashMap<>();
		put(fields, "body", t.getBody());
		put(fields, "dataItems", t.getDataItems());
		put(fields, "retention", t.getRetention());
		put(fields, "recipients", t.getRecipients());
		put(fields, "withdrawalInfo", t.getWithdrawalInfo());
		put(fields, "grievanceInfo", t.getGrievanceInfo());
		put(fields, "declaration", t.getDeclaration());
		put(fields, "representativeDeclaration", t.getRepresentativeDeclaration());
		return fields;
	}

	private static void put(Map<String, String> fields, String name, String value) {
		if (value != null) {
			fields.put(name, value);
		}
	}

	private static NoticeTranslation translation(Notice draft, String language, String name,
			Map<String, String> text, AuthenticatedUser user) {
		NoticeTranslation t = new NoticeTranslation();
		t.setNoticeVersionId(draft.getNoticeVersionId());
		t.setLanguageCode(language);
		t.setLanguageName(name);
		t.setBody(text.get("body"));
		t.setDataItems(text.get("dataItems"));
		t.setRetention(text.get("retention"));
		t.setRecipients(text.get("recipients"));
		t.setWithdrawalInfo(text.get("withdrawalInfo"));
		t.setGrievanceInfo(text.get("grievanceInfo"));
		t.setDeclaration(text.get("declaration"));
		t.setRepresentativeDeclaration(text.get("representativeDeclaration"));
		t.setMachineTranslated(true);
		t.setSourceVersion(draft.getVersionNo());
		t.setTextHash(NoticeTextHash.of(t));
		t.setCreatedBy(user.auditName());
		return t;
	}

	private static String summary(Notice draft, Set<String> languages, boolean material) {
		StringBuilder langs = new StringBuilder(NoticeEntryMapper.ENGLISH);
		languages.forEach(l -> langs.append(',').append(l));
		return "notice:" + draft.getNoticeVersionId() + ";project:" + draft.getProjectId() + ";context:"
				+ draft.getContext() + ";v:" + draft.getVersionNo() + ";langs:" + langs
				+ ";material:" + material;
	}

	private static ConsentApiException refused(String message) {
		return new ConsentApiException(ErrorCode.BUSINESS_RULE_VIOLATION, message);
	}
}
