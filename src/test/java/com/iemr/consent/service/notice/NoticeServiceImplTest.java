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

package com.iemr.consent.service.notice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.iemr.consent.data.notice.Notice;
import com.iemr.consent.data.notice.NoticeContext;
import com.iemr.consent.data.notice.NoticePurpose;
import com.iemr.consent.data.notice.NoticeStatus;
import com.iemr.consent.model.notice.ActiveNotice;
import com.iemr.consent.model.notice.NoticeLanguage;
import com.iemr.consent.repository.notice.NoticePurposeRepository;
import com.iemr.consent.repository.notice.NoticeRepository;
import com.iemr.consent.repository.notice.NoticeTranslationRepository;
import com.iemr.consent.repository.notice.PurposeRepository;
import com.iemr.consent.support.ConsentFixtures;
import com.iemr.consent.support.TestFixtures;
import com.iemr.consent.utils.exception.ConsentApiException;
import com.iemr.consent.utils.exception.ErrorCode;

class NoticeServiceImplTest {

	private static final long ID = ConsentFixtures.NOTICE_ID;

	private NoticeRepository notices;

	private NoticeTranslationRepository translations;

	private NoticePurposeRepository links;

	private PurposeRepository purposes;

	private NoticeServiceImpl service;

	@BeforeEach
	void setUp() {
		notices = mock(NoticeRepository.class);
		translations = mock(NoticeTranslationRepository.class);
		links = mock(NoticePurposeRepository.class);
		purposes = mock(PurposeRepository.class);
		service = service("https://audio/");
		Notice notice = ConsentFixtures.notice(NoticeStatus.PUBLISHED);
		when(notices.findFirstByContextAndStatusAndDeletedFalseOrderByVersionNoDesc(NoticeContext.REGISTRATION,
				NoticeStatus.PUBLISHED)).thenReturn(Optional.of(notice));
		when(notices.findById(ID)).thenReturn(Optional.of(notice));
		when(links.findByNoticeVersionIdAndDeletedFalseOrderByDisplayRankAsc(ID))
				.thenReturn(List.of(link("P-REM", 1), link("P-RES", 2), link("P-REG", 3)));
		when(purposes.findByPurposeCodeInAndActiveTrueAndDeletedFalse(any())).thenReturn(List.of(
				ConsentFixtures.purpose("P-REM", false, 365, 1), ConsentFixtures.purpose("P-REM", false, 180, 2),
				ConsentFixtures.purpose("P-REM", false, 90, 1),
				ConsentFixtures.purpose("P-RES", false, null, 1), ConsentFixtures.purpose("P-REG", true, null, 1)));
	}

	private NoticeServiceImpl service(String audioBase) {
		return new NoticeServiceImpl(notices, translations, links, purposes,
				TestFixtures.consentProperties(1048576L, TestFixtures.STORAGE_DIR, audioBase));
	}

	private static NoticePurpose link(String code, int rank) {
		NoticePurpose link = new NoticePurpose();
		link.setNoticeVersionId(ID);
		link.setPurposeCode(code);
		link.setDisplayRank(rank);
		return link;
	}

	@Test
	void languagesOfTheActiveNoticeWithAudioFlag() {
		when(translations.findByNoticeVersionIdAndDeletedFalseOrderByLanguageCodeAsc(ID)).thenReturn(
				List.of(ConsentFixtures.translation("en", "a.mp3"), ConsentFixtures.translation("hi", null)));

		assertThat(service.languages(NoticeContext.REGISTRATION)).containsExactly(
				new NoticeLanguage("en", "name-en", true), new NoticeLanguage("hi", "name-hi", false));
	}

	@Test
	void noPublishedNoticeMeansNoLanguagesAndNotFound() {
		assertThat(service.languages(NoticeContext.REVISIT)).isEmpty();
		assertThatThrownBy(() -> service.activeNotice(NoticeContext.REVISIT, "hi"))
				.isInstanceOf(ConsentApiException.class).extracting("errorCode").isEqualTo(ErrorCode.NOT_FOUND);
	}

	@Test
	void activeNoticeHasEverySectionAndMandatoryPurposesFirst() {
		when(translations.findByNoticeVersionIdAndLanguageCodeAndDeletedFalse(ID, "en"))
				.thenReturn(Optional.of(ConsentFixtures.translation("en", "a.mp3")));

		ActiveNotice notice = service.activeNotice(NoticeContext.REGISTRATION, "en");

		assertThat(notice.noticeVersionId()).isEqualTo(ID);
		assertThat(notice.context()).isEqualTo("REGISTRATION");
		assertThat(notice.textHash()).isEqualTo(ConsentFixtures.TEXT_HASH);
		assertThat(notice.audioUrl()).isEqualTo("https://audio/a.mp3");
		assertThat(List.of(notice.dataItems(), notice.retention(), notice.recipients(), notice.withdrawalInfo(),
				notice.grievanceInfo())).containsExactly("items", "retention", "recipients", "withdraw", "grievance");
		assertThat(notice.purposes()).extracting("purposeCode").containsExactly("P-REG", "P-REM", "P-RES");
		assertThat(notice.purposes().get(1).validityDays()).as("latest purpose version").isEqualTo(180);
		assertThat(notice.toString()).doesNotContain("notice body");
	}

	@Test
	void audioUrlIsNullWithoutRefOrBaseUrl() {
		when(translations.findByNoticeVersionIdAndLanguageCodeAndDeletedFalse(ID, "hi"))
				.thenReturn(Optional.of(ConsentFixtures.translation("hi", null)));
		when(translations.findByNoticeVersionIdAndLanguageCodeAndDeletedFalse(ID, "en"))
				.thenReturn(Optional.of(ConsentFixtures.translation("en", "a.mp3")));

		assertThat(service.activeNotice(NoticeContext.REGISTRATION, "hi").audioUrl()).isNull();
		assertThat(service("").activeNotice(NoticeContext.REGISTRATION, "en").audioUrl()).isNull();
	}

	@Test
	void unknownLanguageIsNotFound() {
		assertThatThrownBy(() -> service.activeNotice(NoticeContext.REGISTRATION, "xx"))
				.isInstanceOf(ConsentApiException.class).extracting("errorCode").isEqualTo(ErrorCode.NOT_FOUND);
	}

	@Test
	void findPublishedOnlyForPublishedNoticesInThatLanguage() {
		when(translations.findByNoticeVersionIdAndLanguageCodeAndDeletedFalse(ID, "hi"))
				.thenReturn(Optional.of(ConsentFixtures.translation("hi", null)));

		PublishedNotice found = service.findPublished(ID, "hi").orElseThrow();
		assertThat(found.purposes()).extracting("purposeCode").containsExactly("P-REG", "P-REM", "P-RES");
		assertThat(service.findPublished(ID, "xx")).isEmpty();
		assertThat(service.findPublished(99L, "hi")).isEmpty();

		Notice retired = ConsentFixtures.notice(NoticeStatus.RETIRED);
		Notice deleted = ConsentFixtures.notice(NoticeStatus.PUBLISHED);
		deleted.setDeleted(true);
		when(notices.findById(ID)).thenReturn(Optional.of(retired), Optional.of(deleted));
		assertThat(service.findPublished(ID, "hi")).isEmpty();
		assertThat(service.findPublished(ID, "hi")).isEmpty();
		assertThat(Set.of(NoticeStatus.values())).contains(NoticeStatus.DRAFT);
	}
}
