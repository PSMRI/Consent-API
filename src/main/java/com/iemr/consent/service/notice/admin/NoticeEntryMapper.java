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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.iemr.consent.data.notice.Notice;
import com.iemr.consent.data.notice.NoticeContext;
import com.iemr.consent.data.notice.NoticeStatus;
import com.iemr.consent.data.notice.NoticeTranslation;
import com.iemr.consent.model.notice.GrievanceContact;
import com.iemr.consent.model.notice.admin.DraftView;
import com.iemr.consent.model.notice.admin.MessageEntry;
import com.iemr.consent.model.notice.admin.PublishedView;
import com.iemr.consent.repository.notice.NoticeRepository;
import com.iemr.consent.repository.notice.NoticeTranslationRepository;

/**
 * Builds the Admin "Consent message" entries of a project (A6, feature 006, data-model "Message entry"): per context,
 * one entry per language of the published version plus English when there is a draft, or one empty English entry.
 * Only the project's own notices are used, never the central notice; retired versions are not listed.
 */
@Component
public class NoticeEntryMapper {

	static final String ENGLISH = "en";

	private final NoticeRepository notices;

	private final NoticeTranslationRepository translations;

	public NoticeEntryMapper(NoticeRepository notices, NoticeTranslationRepository translations) {
		this.notices = notices;
		this.translations = translations;
	}

	/** Every entry of the project, REGISTRATION first. */
	public List<MessageEntry> list(Integer projectId) {
		List<MessageEntry> entries = new ArrayList<>();
		for (NoticeContext context : NoticeContext.values()) {
			entries.addAll(entries(projectId, context));
		}
		return entries;
	}

	/** The English entry of one context (the answer of a draft save or publish). */
	public MessageEntry english(Integer projectId, NoticeContext context) {
		return entries(projectId, context).stream().filter(e -> ENGLISH.equals(e.languageCode())).findFirst()
				.orElseThrow();
	}

	private List<MessageEntry> entries(Integer projectId, NoticeContext context) {
		Optional<Notice> draft = notices.findFirstByProjectIdAndContextAndStatusAndDeletedFalse(projectId,
				context, NoticeStatus.DRAFT);
		Optional<Notice> published = notices.findFirstByProjectIdAndContextAndStatusAndDeletedFalse(projectId,
				context, NoticeStatus.PUBLISHED);
		Optional<NoticeTranslation> draftText = draft.flatMap(d -> translations
				.findByNoticeVersionIdAndLanguageCodeAndDeletedFalse(d.getNoticeVersionId(), ENGLISH));
		List<NoticeTranslation> texts = published.map(p -> translations
				.findByNoticeVersionIdAndDeletedFalseOrderByLanguageCodeAsc(p.getNoticeVersionId()))
				.orElse(List.of());
		Map<String, NoticeTranslation> publishedTexts = texts.stream().collect(Collectors.toMap(
				NoticeTranslation::getLanguageCode, Function.identity(), (a, b) -> a, TreeMap::new));
		Map<String, MessageEntry> byLanguage = new TreeMap<>();
		publishedTexts.forEach((language, text) -> byLanguage.put(language, new MessageEntry(projectId, context,
				language, null, publishedView(published.orElseThrow(), text))));
		DraftView draftView = draftText.map(t -> draftView(draft.orElseThrow(), t)).orElse(null);
		PublishedView englishPublished = Optional.ofNullable(publishedTexts.get(ENGLISH))
				.map(t -> publishedView(published.orElseThrow(), t)).orElse(null);
		if (draftView != null || byLanguage.isEmpty()) {
			MessageEntry english = new MessageEntry(projectId, context, ENGLISH, draftView,
					englishPublished);
			byLanguage.put(ENGLISH, english);
		}
		return new ArrayList<>(byLanguage.values());
	}

	private static DraftView draftView(Notice n, NoticeTranslation t) {
		String savedBy = n.getModifiedBy() != null ? n.getModifiedBy() : n.getCreatedBy();
		return new DraftView(t.getBody(), t.getDataItems(), t.getRetention(), t.getRecipients(),
				t.getWithdrawalInfo(), t.getGrievanceInfo(), t.getDeclaration(),
				t.getRepresentativeDeclaration(), GrievanceContact.from(n), savedBy,
				n.getLastModDate());
	}

	private static PublishedView publishedView(Notice n, NoticeTranslation t) {
		return new PublishedView(t.getBody(), t.getDataItems(), t.getRetention(), t.getRecipients(),
				t.getWithdrawalInfo(), t.getGrievanceInfo(), t.getDeclaration(),
				t.getRepresentativeDeclaration(), GrievanceContact.from(n), n.getNoticeVersionId(),
				n.getVersionNo(), n.getApprovedBy(), n.getPublishedAt(), t.isMachineTranslated(),
				t.getSourceVersion(), n.isMaterial());
	}
}
