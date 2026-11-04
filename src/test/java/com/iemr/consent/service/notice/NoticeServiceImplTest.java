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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.iemr.consent.data.notice.Notice;
import com.iemr.consent.data.notice.NoticeContext;
import com.iemr.consent.data.notice.NoticePurpose;
import com.iemr.consent.data.notice.NoticeStatus;
import com.iemr.consent.data.notice.NoticeTranslation;
import com.iemr.consent.model.notice.ActiveNotice;
import com.iemr.consent.model.notice.GrievanceContact;
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
		when(notices.findFirstByContextAndProjectIdIsNullAndStatusAndDeletedFalseOrderByVersionNoDesc(NoticeContext.REGISTRATION,
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

		assertThat(service.languages(NoticeContext.REGISTRATION, null)).containsExactly(
				new NoticeLanguage("en", "name-en", true), new NoticeLanguage("hi", "name-hi", false));
	}

	@Test
	void noPublishedNoticeMeansNoLanguagesAndNotFound() {
		assertThat(service.languages(NoticeContext.REVISIT, null)).isEmpty();
		assertThatThrownBy(() -> service.activeNotice(NoticeContext.REVISIT, "hi", null))
				.isInstanceOf(ConsentApiException.class).extracting("errorCode").isEqualTo(ErrorCode.NOT_FOUND);
	}

	@Test
	void activeNoticeHasEverySectionAndMandatoryPurposesFirst() {
		when(translations.findByNoticeVersionIdAndLanguageCodeAndDeletedFalse(ID, "en"))
				.thenReturn(Optional.of(ConsentFixtures.translation("en", "a.mp3")));

		ActiveNotice notice = service.activeNotice(NoticeContext.REGISTRATION, "en", null);

		assertThat(notice.noticeVersionId()).isEqualTo(ID);
		assertThat(notice.context()).isEqualTo("REGISTRATION");
		assertThat(notice.textHash()).isEqualTo(ConsentFixtures.TEXT_HASH);
		assertThat(notice.audioUrl()).isEqualTo("https://audio/a.mp3");
		assertThat(List.of(notice.dataItems(), notice.retention(), notice.recipients(), notice.withdrawalInfo(),
				notice.grievanceInfo())).containsExactly("items", "retention", "recipients", "withdraw", "grievance");
		assertThat(notice.purposes()).extracting("purposeCode").containsExactly("P-REG", "P-REM", "P-RES");
		assertThat(notice.purposes().get(1).validityDays()).as("latest purpose version").isEqualTo(180);
		assertThat(notice.toString()).doesNotContain("notice body");
		assertThat(notice.declaration()).isNull();
		assertThat(notice.representativeDeclaration()).isNull();
		assertThat(notice.grievance()).as("no grievance_* column set").isNull();
	}

	@Test
	void audioUrlIsNullWithoutRefOrBaseUrl() {
		when(translations.findByNoticeVersionIdAndLanguageCodeAndDeletedFalse(ID, "hi"))
				.thenReturn(Optional.of(ConsentFixtures.translation("hi", null)));
		when(translations.findByNoticeVersionIdAndLanguageCodeAndDeletedFalse(ID, "en"))
				.thenReturn(Optional.of(ConsentFixtures.translation("en", "a.mp3")));

		assertThat(service.activeNotice(NoticeContext.REGISTRATION, "hi", null).audioUrl()).isNull();
		assertThat(service("").activeNotice(NoticeContext.REGISTRATION, "en", null).audioUrl()).isNull();
	}

	@Test
	void unknownLanguageIsNotFound() {
		assertThatThrownBy(() -> service.activeNotice(NoticeContext.REGISTRATION, "xx", null))
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

	private static final long PROJECT_NOTICE_ID = 77L;

	private Notice projectNotice() {
		Notice notice = ConsentFixtures.notice(NoticeStatus.PUBLISHED);
		notice.setNoticeVersionId(PROJECT_NOTICE_ID);
		notice.setNoticeCode("MMU-REGISTRATION-P3");
		notice.setProjectId(3);
		notice.setGrievanceOfficerName("Officer");
		when(notices.findFirstByContextAndProjectIdAndStatusAndDeletedFalseOrderByVersionNoDesc(
				NoticeContext.REGISTRATION, 3, NoticeStatus.PUBLISHED)).thenReturn(Optional.of(notice));
		when(links.findByNoticeVersionIdAndDeletedFalseOrderByDisplayRankAsc(PROJECT_NOTICE_ID))
				.thenReturn(List.of(link("P-REG", 1)));
		when(purposes.findByPurposeCodeInAndActiveTrueAndDeletedFalse(Set.of("P-REG")))
				.thenReturn(List.of(ConsentFixtures.purpose("P-REG", true, null, 1)));
		return notice;
	}

	private static NoticeTranslation projectTranslation(String lang, String audioRef) {
		NoticeTranslation t = ConsentFixtures.translation(lang, audioRef);
		t.setNoticeVersionId(PROJECT_NOTICE_ID);
		t.setLanguageName("project-" + lang);
		t.setDeclaration("I agree (project)");
		t.setRepresentativeDeclaration("We agree for the patient (project)");
		return t;
	}

	@Test
	@DisplayName("US1: the project's notice in that language, with its declarations and grievance contact")
	void projectNoticeWhenPublishedInTheLanguage() {
		Notice project = projectNotice();
		project.setGrievancePhone("1800");
		project.setGrievanceEmail("g@example.org");
		project.setGrievanceAddress("Office");
		project.setGrievanceOfficeHours("10-5");
		when(translations.findByNoticeVersionIdAndLanguageCodeAndDeletedFalse(PROJECT_NOTICE_ID, "hi"))
				.thenReturn(Optional.of(projectTranslation("hi", null)));

		ActiveNotice notice = service.activeNotice(NoticeContext.REGISTRATION, "hi", 3);

		assertThat(notice.noticeVersionId()).isEqualTo(PROJECT_NOTICE_ID);
		assertThat(notice.purposes()).extracting("purposeCode").containsExactly("P-REG");
		assertThat(notice.declaration()).isEqualTo("I agree (project)");
		assertThat(notice.representativeDeclaration()).isEqualTo("We agree for the patient (project)");
		assertThat(notice.grievance()).isEqualTo(new GrievanceContact("Officer", "1800", "g@example.org", "Office",
				"10-5"));
	}

	@Test
	@DisplayName("US1: central notice when the project has no notice in that language, or no notice at all")
	void centralFallback() {
		projectNotice();
		when(translations.findByNoticeVersionIdAndLanguageCodeAndDeletedFalse(ID, "kn"))
				.thenReturn(Optional.of(ConsentFixtures.translation("kn", null)));

		assertThat(service.activeNotice(NoticeContext.REGISTRATION, "kn", 3).noticeVersionId()).isEqualTo(ID);
		assertThat(service.activeNotice(NoticeContext.REGISTRATION, "kn", 5).noticeVersionId()).isEqualTo(ID);
		assertThatThrownBy(() -> service.activeNotice(NoticeContext.REGISTRATION, "xx", 3))
				.isInstanceOf(ConsentApiException.class).extracting("errorCode").isEqualTo(ErrorCode.NOT_FOUND);
	}

	@Test
	@DisplayName("US1: grievance contact is present when any one of its fields is set")
	void grievanceWithOnlySomeFields() {
		Notice notice = ConsentFixtures.notice(NoticeStatus.PUBLISHED);
		notice.setGrievanceEmail("g@example.org");
		when(notices.findFirstByContextAndProjectIdIsNullAndStatusAndDeletedFalseOrderByVersionNoDesc(
				NoticeContext.REVISIT, NoticeStatus.PUBLISHED)).thenReturn(Optional.of(notice));
		when(translations.findByNoticeVersionIdAndLanguageCodeAndDeletedFalse(ID, "hi"))
				.thenReturn(Optional.of(ConsentFixtures.translation("hi", null)));

		assertThat(service.activeNotice(NoticeContext.REVISIT, "hi", null).grievance())
				.isEqualTo(new GrievanceContact(null, null, "g@example.org", null, null));
	}

	@Test
	@DisplayName("US1: languages are central plus project, each code once, project entry winning")
	void languageUnion() {
		projectNotice();
		when(translations.findByNoticeVersionIdAndDeletedFalseOrderByLanguageCodeAsc(ID)).thenReturn(
				List.of(ConsentFixtures.translation("en", null), ConsentFixtures.translation("kn", null)));
		when(translations.findByNoticeVersionIdAndDeletedFalseOrderByLanguageCodeAsc(PROJECT_NOTICE_ID)).thenReturn(
				List.of(projectTranslation("en", "p.mp3"), projectTranslation("hi", null)));

		assertThat(service.languages(NoticeContext.REGISTRATION, 3)).containsExactly(
				new NoticeLanguage("en", "project-en", true), new NoticeLanguage("hi", "project-hi", false),
				new NoticeLanguage("kn", "name-kn", false));
		assertThat(service.languages(NoticeContext.REGISTRATION, 5)).extracting("languageCode")
				.containsExactly("en", "kn");
	}

	@Test
	@DisplayName("US1: a project with a notice but no central notice still lists its languages")
	void projectLanguagesWithoutCentralNotice() {
		Notice project = projectNotice();
		project.setContext(NoticeContext.REVISIT);
		when(notices.findFirstByContextAndProjectIdAndStatusAndDeletedFalseOrderByVersionNoDesc(
				NoticeContext.REVISIT, 3, NoticeStatus.PUBLISHED)).thenReturn(Optional.of(project));
		when(translations.findByNoticeVersionIdAndDeletedFalseOrderByLanguageCodeAsc(PROJECT_NOTICE_ID))
				.thenReturn(List.of(projectTranslation("hi", null)));

		assertThat(service.languages(NoticeContext.REVISIT, 3)).extracting("languageCode").containsExactly("hi");
	}
}
