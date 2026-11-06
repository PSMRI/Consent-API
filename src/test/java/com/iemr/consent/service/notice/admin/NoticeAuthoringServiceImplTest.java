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

import static com.iemr.consent.service.notice.admin.NoticeAdminFixtures.HASH;
import static com.iemr.consent.service.notice.admin.NoticeAdminFixtures.KEY;
import static com.iemr.consent.service.notice.admin.NoticeAdminFixtures.PROJECT;
import static com.iemr.consent.service.notice.admin.NoticeAdminFixtures.draft;
import static com.iemr.consent.service.notice.admin.NoticeAdminFixtures.notice;
import static com.iemr.consent.service.notice.admin.NoticeAdminFixtures.text;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;

import com.iemr.consent.data.notice.Notice;
import com.iemr.consent.data.notice.NoticeContext;
import com.iemr.consent.data.notice.NoticePurpose;
import com.iemr.consent.data.notice.NoticeRequest;
import com.iemr.consent.data.notice.NoticeRequestAction;
import com.iemr.consent.data.notice.NoticeStatus;
import com.iemr.consent.data.notice.NoticeTranslation;
import com.iemr.consent.model.notice.NoticeLanguage;
import com.iemr.consent.model.notice.admin.DraftRequest;
import com.iemr.consent.model.notice.admin.MessageEntry;
import com.iemr.consent.model.notice.admin.PublishRequest;
import com.iemr.consent.repository.notice.NoticePurposeRepository;
import com.iemr.consent.repository.notice.NoticeRepository;
import com.iemr.consent.repository.notice.NoticeRequestRepository;
import com.iemr.consent.repository.notice.NoticeTranslationRepository;
import com.iemr.consent.service.audit.AuditLogService;
import com.iemr.consent.service.notice.NoticeService;
import com.iemr.consent.support.ConsentFixtures;
import com.iemr.consent.utils.exception.ConsentApiException;
import com.iemr.consent.utils.exception.ErrorCode;

import jakarta.persistence.EntityManager;

/** Feature 006, US2 (A7): the English draft, its purposes, replay and audit (FR-004, FR-010 to FR-012). */
class NoticeAuthoringServiceImplTest {

	private NoticeRepository notices;

	private NoticeTranslationRepository translations;

	private NoticePurposeRepository purposes;

	private NoticeRequestRepository requests;

	private NoticeService noticeService;

	private NoticeEntryMapper mapper;

	private NoticePublisher publisher;

	private AuditLogService auditLog;

	private EntityManager entityManager;

	private NoticeAuthoringServiceImpl service;

	private final MessageEntry entry = new MessageEntry(PROJECT, NoticeContext.REGISTRATION, "en", null, null);

	@BeforeEach
	void setUp() {
		notices = mock(NoticeRepository.class);
		translations = mock(NoticeTranslationRepository.class);
		purposes = mock(NoticePurposeRepository.class);
		requests = mock(NoticeRequestRepository.class);
		noticeService = mock(NoticeService.class);
		mapper = mock(NoticeEntryMapper.class);
		publisher = mock(NoticePublisher.class);
		auditLog = mock(AuditLogService.class);
		entityManager = mock(EntityManager.class);
		service = new NoticeAuthoringServiceImpl(notices, translations, purposes, requests, noticeService, mapper,
				publisher, auditLog, entityManager);
		when(requests.findByActorUserIdAndRequestKey(any(), any())).thenReturn(Optional.empty());
		when(notices.findFirstByProjectIdAndContextAndStatusAndDeletedFalse(any(), any(), any()))
				.thenReturn(Optional.empty());
		when(notices.findFirstByProjectIdAndContextAndDeletedFalseOrderByVersionNoDesc(any(), any()))
				.thenReturn(Optional.empty());
		when(notices.saveAndFlush(any())).thenAnswer(i -> {
			Notice n = i.getArgument(0);
			if (n.getNoticeVersionId() == null) {
				n.setNoticeVersionId(77L);
			}
			return n;
		});
		when(noticeService.languages(any(), isNull()))
				.thenReturn(List.of(new NoticeLanguage("en", "English (India)", true)));
		when(mapper.english(PROJECT, NoticeContext.REGISTRATION)).thenReturn(entry);
	}

	private MessageEntry save(DraftRequest request) {
		return service.saveDraft(PROJECT, NoticeContext.REGISTRATION, request, KEY, HASH, ConsentFixtures.WORKER,
				"10.0.0.1");
	}

	private Notice savedNotice() {
		ArgumentCaptor<Notice> notice = ArgumentCaptor.forClass(Notice.class);
		verify(notices).saveAndFlush(notice.capture());
		return notice.getValue();
	}

	private NoticeTranslation savedText() {
		ArgumentCaptor<NoticeTranslation> text = ArgumentCaptor.forClass(NoticeTranslation.class);
		verify(translations).save(text.capture());
		return text.getValue();
	}

	@Test
	void listUsesTheMapper() {
		when(mapper.list(PROJECT)).thenReturn(List.of(entry));
		assertThat(service.list(PROJECT)).containsExactly(entry);
	}

	@Test
	void publishIsDelegated() {
		PublishRequest request = new PublishRequest(true, List.of());
		when(publisher.publish(PROJECT, NoticeContext.REVISIT, request, KEY, HASH, ConsentFixtures.WORKER, "ip"))
				.thenReturn(entry);
		assertThat(service.publish(PROJECT, NoticeContext.REVISIT, request, KEY, HASH, ConsentFixtures.WORKER, "ip"))
				.isSameAs(entry);
	}

	@Test
	void aFirstDraftGetsANewCodeVersionOneAndTheCentralPurposes() {
		Notice central = notice(11, 4, NoticeStatus.PUBLISHED);
		central.setProjectId(null);
		when(notices.findFirstByContextAndProjectIdIsNullAndStatusAndDeletedFalseOrderByVersionNoDesc(
				NoticeContext.REGISTRATION, NoticeStatus.PUBLISHED)).thenReturn(Optional.of(central));
		NoticePurpose reg = new NoticePurpose();
		reg.setPurposeCode("P-REG");
		reg.setDisplayRank(1);
		when(purposes.findByNoticeVersionIdAndDeletedFalseOrderByDisplayRankAsc(11L)).thenReturn(List.of(reg));

		assertThat(save(draft("I agree"))).isSameAs(entry);

		Notice notice = savedNotice();
		assertThat(notice.getNoticeCode()).isEqualTo("P3-REGISTRATION");
		assertThat(notice.getVersionNo()).isEqualTo(1);
		assertThat(notice.getStatus()).isEqualTo(NoticeStatus.DRAFT);
		assertThat(notice.getProjectId()).isEqualTo(PROJECT);
		assertThat(notice.isMaterial()).isFalse();
		assertThat(notice.getCreatedBy()).isEqualTo("worker42");
		assertThat(notice.getGrievanceEmail()).isEqualTo("g@example.org");
		assertThat(notice.getGrievanceOfficeHours()).isEqualTo("10-5");
		@SuppressWarnings("unchecked")
		ArgumentCaptor<List<NoticePurpose>> copies = ArgumentCaptor.forClass(List.class);
		verify(purposes).saveAll(copies.capture());
		assertThat(copies.getValue()).singleElement().satisfies(p -> {
			assertThat(p.getNoticeVersionId()).isEqualTo(77L);
			assertThat(p.getPurposeCode()).isEqualTo("P-REG");
			assertThat(p.getDisplayRank()).isEqualTo(1);
			assertThat(p.getCreatedBy()).isEqualTo("worker42");
		});
		NoticeTranslation text = savedText();
		assertThat(text.getNoticeVersionId()).isEqualTo(77L);
		assertThat(text.getLanguageCode()).isEqualTo("en");
		assertThat(text.getLanguageName()).isEqualTo("English (India)");
		assertThat(text.getBody()).isEqualTo("New body");
		assertThat(text.getDeclaration()).isEqualTo("I agree");
		assertThat(text.getRepresentativeDeclaration()).isEqualTo("On behalf");
		assertThat(text.isMachineTranslated()).isFalse();
		assertThat(text.getTextHash()).isEqualTo(NoticeTextHash.of(text)).hasSize(64);
		ArgumentCaptor<NoticeRequest> request = ArgumentCaptor.forClass(NoticeRequest.class);
		verify(requests).save(request.capture());
		assertThat(request.getValue().getAction()).isEqualTo(NoticeRequestAction.DRAFT);
		assertThat(request.getValue().getActorUserId()).isEqualTo(42L);
		assertThat(request.getValue().getRequestKey()).isEqualTo(KEY);
		assertThat(request.getValue().getRequestHash()).isEqualTo(HASH);
		assertThat(request.getValue().getNoticeVersionId()).isEqualTo(77L);
		verify(auditLog).append(AuditLogService.NOTICE_DRAFT_SAVED, null, null,
				"notice:77;project:3;context:REGISTRATION;v:1", "DRAFT", 42L, "10.0.0.1");
		verify(entityManager).refresh(notice);
	}

	@Test
	void aNextDraftKeepsTheProjectCodeAndCopiesItsPurposes() {
		when(notices.findFirstByProjectIdAndContextAndDeletedFalseOrderByVersionNoDesc(PROJECT,
				NoticeContext.REGISTRATION)).thenReturn(Optional.of(notice(41, 2, NoticeStatus.PUBLISHED)));
		when(purposes.findByNoticeVersionIdAndDeletedFalseOrderByDisplayRankAsc(41L)).thenReturn(List.of());
		when(noticeService.languages(any(), isNull())).thenReturn(List.of(new NoticeLanguage("hi", "Hindi", true)));

		save(new DraftRequest("en", "Body", null, null, null, null, null, null, null, null));

		Notice notice = savedNotice();
		assertThat(notice.getNoticeCode()).isEqualTo("MMU-REGISTRATION-P3");
		assertThat(notice.getVersionNo()).isEqualTo(3);
		assertThat(notice.getGrievanceOfficerName()).isNull();
		assertThat(notice.getGrievancePhone()).isNull();
		verify(purposes).findByNoticeVersionIdAndDeletedFalseOrderByDisplayRankAsc(41L);
		verify(notices, never()).findFirstByContextAndProjectIdIsNullAndStatusAndDeletedFalseOrderByVersionNoDesc(
				any(), any());
		assertThat(savedText().getLanguageName()).isEqualTo("English");
	}

	@Test
	void anExistingDraftIsUpdatedInPlace() {
		Notice existing = notice(50, 3, NoticeStatus.DRAFT);
		when(notices.findFirstByProjectIdAndContextAndStatusAndDeletedFalse(PROJECT, NoticeContext.REGISTRATION,
				NoticeStatus.DRAFT)).thenReturn(Optional.of(existing));
		NoticeTranslation english = text(50, "en", "Old");
		when(translations.findByNoticeVersionIdAndLanguageCodeAndDeletedFalse(50L, "en"))
				.thenReturn(Optional.of(english));

		save(draft("Changed"));

		assertThat(savedNotice()).isSameAs(existing);
		assertThat(existing.getVersionNo()).isEqualTo(3);
		assertThat(existing.getModifiedBy()).isEqualTo("worker42");
		NoticeTranslation text = savedText();
		assertThat(text).isSameAs(english);
		assertThat(text.getDeclaration()).isEqualTo("Changed");
		assertThat(text.getModifiedBy()).isEqualTo("worker42");
		verify(purposes, never()).saveAll(any());
		verify(auditLog).append(eq(AuditLogService.NOTICE_DRAFT_SAVED), isNull(), isNull(),
				eq("notice:50;project:3;context:REGISTRATION;v:3"), eq("DRAFT"), eq(42L), anyString());
	}

	@Test
	void anExistingDraftWithoutEnglishGetsANewText() {
		when(notices.findFirstByProjectIdAndContextAndStatusAndDeletedFalse(PROJECT, NoticeContext.REGISTRATION,
				NoticeStatus.DRAFT)).thenReturn(Optional.of(notice(50, 3, NoticeStatus.DRAFT)));
		when(translations.findByNoticeVersionIdAndLanguageCodeAndDeletedFalse(anyLong(), anyString()))
				.thenReturn(Optional.empty());

		save(draft("New"));

		assertThat(savedText().getCreatedBy()).isEqualTo("worker42");
	}

	@Test
	void aReplayAnswersFromTheRecordedRequest() {
		NoticeRequest earlier = NoticeAuthoringServiceImpl.record(ConsentFixtures.WORKER, KEY, HASH,
				NoticeRequestAction.DRAFT, 50L);
		when(requests.findByActorUserIdAndRequestKey(42L, KEY)).thenReturn(Optional.of(earlier));

		assertThat(save(draft("x"))).isSameAs(entry);
		verify(notices, never()).saveAndFlush(any());
		verifyNoInteractions(auditLog);

		earlier.setRequestHash("other");
		assertThatThrownBy(() -> save(draft("x"))).isInstanceOfSatisfying(ConsentApiException.class,
				e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.STATE_CONFLICT));
	}

	@Test
	void aFirstDraftNeedsACentralNoticeForItsPurposes() {
		when(notices.findFirstByContextAndProjectIdIsNullAndStatusAndDeletedFalseOrderByVersionNoDesc(any(), any()))
				.thenReturn(Optional.empty());

		assertThatThrownBy(() -> save(draft("x"))).isInstanceOfSatisfying(ConsentApiException.class, e -> {
			assertThat(e.getErrorCode()).isEqualTo(ErrorCode.BUSINESS_RULE_VIOLATION);
			assertThat(e.getMessage()).isEqualTo("No central notice to take purposes from");
		});
		verify(notices, never()).saveAndFlush(any());
	}

	@Test
	void aConcurrentFirstDraftIsStale() {
		when(notices.findFirstByProjectIdAndContextAndDeletedFalseOrderByVersionNoDesc(any(), any()))
				.thenReturn(Optional.of(notice(41, 2, NoticeStatus.PUBLISHED)));
		doThrow(new DataIntegrityViolationException("uk_cm_notice_code_version")).when(notices).saveAndFlush(any());

		assertThatThrownBy(() -> save(draft("x"))).isInstanceOfSatisfying(ConsentApiException.class,
				e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.STALE_VERSION));
		verifyNoInteractions(auditLog);
	}
}
