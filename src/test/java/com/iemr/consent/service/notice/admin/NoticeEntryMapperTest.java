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

import static com.iemr.consent.service.notice.admin.NoticeAdminFixtures.PROJECT;
import static com.iemr.consent.service.notice.admin.NoticeAdminFixtures.notice;
import static com.iemr.consent.service.notice.admin.NoticeAdminFixtures.text;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.iemr.consent.data.notice.Notice;
import com.iemr.consent.data.notice.NoticeContext;
import com.iemr.consent.data.notice.NoticeStatus;
import com.iemr.consent.data.notice.NoticeTranslation;
import com.iemr.consent.model.notice.admin.MessageEntry;
import com.iemr.consent.repository.notice.NoticeRepository;
import com.iemr.consent.repository.notice.NoticeTranslationRepository;

/** Feature 006, US1 (A6): one entry per context and language, project notices only. */
class NoticeEntryMapperTest {

	private NoticeRepository notices;

	private NoticeTranslationRepository translations;

	private NoticeEntryMapper mapper;

	@BeforeEach
	void setUp() {
		notices = mock(NoticeRepository.class);
		translations = mock(NoticeTranslationRepository.class);
		mapper = new NoticeEntryMapper(notices, translations);
		when(notices.findFirstByProjectIdAndContextAndStatusAndDeletedFalse(any(), any(), any()))
				.thenReturn(Optional.empty());
		when(translations.findByNoticeVersionIdAndLanguageCodeAndDeletedFalse(anyLong(), anyString()))
				.thenReturn(Optional.empty());
		when(translations.findByNoticeVersionIdAndDeletedFalseOrderByLanguageCodeAsc(anyLong())).thenReturn(List.of());
	}

	private void registration(NoticeStatus status, Notice notice) {
		when(notices.findFirstByProjectIdAndContextAndStatusAndDeletedFalse(PROJECT, NoticeContext.REGISTRATION,
				status)).thenReturn(Optional.of(notice));
	}

	@Test
	void nothingSavedGivesOneEmptyEnglishEntryPerContext() {
		List<MessageEntry> entries = mapper.list(PROJECT);

		assertThat(entries).extracting(MessageEntry::context, MessageEntry::languageCode)
				.containsExactly(org.assertj.core.groups.Tuple.tuple(NoticeContext.REGISTRATION, "en"),
						org.assertj.core.groups.Tuple.tuple(NoticeContext.REVISIT, "en"));
		assertThat(entries).allSatisfy(e -> {
			assertThat(e.draft()).isNull();
			assertThat(e.published()).isNull();
			assertThat(e.projectId()).isEqualTo(PROJECT);
		});
		verify(notices, never()).findFirstByContextAndProjectIdIsNullAndStatusAndDeletedFalseOrderByVersionNoDesc(
				any(), any());
	}

	@Test
	void publishedLanguagesAndTheEnglishDraftAreListed() {
		Notice published = notice(41, 2, NoticeStatus.PUBLISHED);
		published.setMaterial(true);
		Notice draft = notice(50, 3, NoticeStatus.DRAFT);
		draft.setModifiedBy("second.admin");
		draft.setLastModDate(Instant.parse("2026-11-03T10:15:00Z"));
		draft.setGrievanceOfficerName(null);
		draft.setGrievancePhone(null);
		registration(NoticeStatus.PUBLISHED, published);
		registration(NoticeStatus.DRAFT, draft);
		NoticeTranslation hindi = text(41, "hi", "Hindi declaration");
		hindi.setMachineTranslated(true);
		hindi.setSourceVersion(2);
		when(translations.findByNoticeVersionIdAndDeletedFalseOrderByLanguageCodeAsc(41L))
				.thenReturn(List.of(text(41, "en", "English declaration"), hindi));
		when(translations.findByNoticeVersionIdAndLanguageCodeAndDeletedFalse(50L, "en"))
				.thenReturn(Optional.of(text(50, "en", "New declaration")));

		List<MessageEntry> registration = mapper.list(PROJECT).stream()
				.filter(e -> e.context() == NoticeContext.REGISTRATION).toList();

		assertThat(registration).extracting(MessageEntry::languageCode).containsExactly("en", "hi");
		MessageEntry english = registration.get(0);
		assertThat(english.draft().declaration()).isEqualTo("New declaration");
		assertThat(english.draft().savedBy()).isEqualTo("second.admin");
		assertThat(english.draft().savedAt()).isEqualTo(Instant.parse("2026-11-03T10:15:00Z"));
		assertThat(english.draft().grievance()).isNull();
		assertThat(english.published().noticeVersionId()).isEqualTo(41L);
		assertThat(english.published().version()).isEqualTo(2);
		assertThat(english.published().publishedBy()).isEqualTo("publisher");
		assertThat(english.published().machineTranslated()).isFalse();
		assertThat(english.published().material()).isTrue();
		assertThat(english.published().grievance().officerName()).isEqualTo("A. Kumar");
		MessageEntry hindiEntry = registration.get(1);
		assertThat(hindiEntry.draft()).isNull();
		assertThat(hindiEntry.published().machineTranslated()).isTrue();
		assertThat(hindiEntry.published().sourceVersion()).isEqualTo(2);
		assertThat(hindiEntry.published().noticeVersionId()).isEqualTo(41L);
	}

	@Test
	void aDraftWithoutPublishedVersionAndSavedByFallsBackToTheCreator() {
		Notice draft = notice(50, 1, NoticeStatus.DRAFT);
		registration(NoticeStatus.DRAFT, draft);
		when(translations.findByNoticeVersionIdAndLanguageCodeAndDeletedFalse(50L, "en"))
				.thenReturn(Optional.of(text(50, "en", "D")));

		MessageEntry english = mapper.english(PROJECT, NoticeContext.REGISTRATION);

		assertThat(english.draft().savedBy()).isEqualTo("first.admin");
		assertThat(english.published()).isNull();
	}

	@Test
	void aPublishedVersionWithoutEnglishStillListsItsLanguages() {
		registration(NoticeStatus.PUBLISHED, notice(41, 1, NoticeStatus.PUBLISHED));
		when(translations.findByNoticeVersionIdAndDeletedFalseOrderByLanguageCodeAsc(41L))
				.thenReturn(List.of(text(41, "hi", "H")));
		registration(NoticeStatus.DRAFT, notice(50, 2, NoticeStatus.DRAFT));

		List<MessageEntry> registration = mapper.list(PROJECT).stream()
				.filter(e -> e.context() == NoticeContext.REGISTRATION).toList();

		assertThat(registration).extracting(MessageEntry::languageCode).containsExactly("hi");
		verify(translations).findByNoticeVersionIdAndLanguageCodeAndDeletedFalse(eq(50L), eq("en"));
	}
}
