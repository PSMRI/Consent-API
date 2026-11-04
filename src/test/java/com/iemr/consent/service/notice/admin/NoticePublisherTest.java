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
import static com.iemr.consent.service.notice.admin.NoticeAdminFixtures.notice;
import static com.iemr.consent.service.notice.admin.NoticeAdminFixtures.text;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

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
import com.iemr.consent.service.notice.translate.TranslationException;
import com.iemr.consent.support.ConsentFixtures;
import com.iemr.consent.support.TestFixtures;
import com.iemr.consent.utils.exception.ConsentApiException;
import com.iemr.consent.utils.exception.ErrorCode;

import jakarta.persistence.EntityManager;

/** Feature 006, US3 and US4 (A8): one-step publish that changes nothing on any refusal or failure. */
class NoticePublisherTest {

	private NoticeRepository notices;

	private NoticeTranslationRepository translations;

	private NoticeRequestRepository requests;

	private NoticeService noticeService;

	private NoticeTranslator translator;

	private NoticeEntryMapper mapper;

	private AuditLogService auditLog;

	private EntityManager entityManager;

	private NoticePublisher publisher;

	private Notice draft;

	private NoticeTranslation english;

	private final MessageEntry entry = new MessageEntry(PROJECT, NoticeContext.REGISTRATION, "en", null, null);

	@BeforeEach
	void setUp() {
		notices = mock(NoticeRepository.class);
		translations = mock(NoticeTranslationRepository.class);
		requests = mock(NoticeRequestRepository.class);
		noticeService = mock(NoticeService.class);
		translator = mock(NoticeTranslator.class);
		mapper = mock(NoticeEntryMapper.class);
		auditLog = mock(AuditLogService.class);
		entityManager = mock(EntityManager.class);
		publisher = new NoticePublisher(notices, translations, requests, noticeService, translator, mapper, auditLog,
				entityManager, TestFixtures.CLOCK);
		draft = notice(50, 3, NoticeStatus.DRAFT);
		english = text(50, "en", "I agree");
		english.setGrievanceInfo("Officer");
		when(requests.findByActorUserIdAndRequestKey(any(), any())).thenReturn(Optional.empty());
		when(notices.findFirstByProjectIdAndContextAndStatusAndDeletedFalse(PROJECT, NoticeContext.REGISTRATION,
				NoticeStatus.DRAFT)).thenReturn(Optional.of(draft));
		when(translations.findByNoticeVersionIdAndLanguageCodeAndDeletedFalse(50L, "en")).thenReturn(Optional.of(english));
		when(noticeService.languages(NoticeContext.REGISTRATION, null)).thenReturn(List.of(
				new NoticeLanguage("en", "English", true), new NoticeLanguage("hi", "Hindi", true),
				new NoticeLanguage("kn", "Kannada", false)));
		when(translator.isConfigured()).thenReturn(true);
		when(translator.translate(any(), eq("en"), anyString())).thenAnswer(i -> {
			Map<String, String> out = new HashMap<>();
			Map<String, String> in = i.getArgument(0);
			in.forEach((k, v) -> out.put(k, v + " [" + i.getArgument(2) + "]"));
			return out;
		});
		when(notices.publishDraft(anyLong(), anyString(), any(), anyBoolean())).thenReturn(1);
		when(mapper.english(PROJECT, NoticeContext.REGISTRATION)).thenReturn(entry);
	}

	private MessageEntry publish(PublishRequest request) {
		return publisher.publish(PROJECT, NoticeContext.REGISTRATION, request, KEY, HASH, ConsentFixtures.WORKER,
				"10.0.0.1");
	}

	private void assertRefused(PublishRequest request, String message) {
		assertThatThrownBy(() -> publish(request)).isInstanceOfSatisfying(ConsentApiException.class, e -> {
			assertThat(e.getErrorCode()).isEqualTo(ErrorCode.BUSINESS_RULE_VIOLATION);
			assertThat(e.getMessage()).isEqualTo(message);
		});
		assertNothingWritten();
	}

	private void assertNothingWritten() {
		verify(notices, never()).publishDraft(anyLong(), anyString(), any(), anyBoolean());
		verify(translations, never()).save(any());
		verify(notices, never()).retireOthers(any(), any(), any(), any());
		verify(requests, never()).save(any());
		verifyNoInteractions(auditLog);
	}

	@Test
	void publishesTranslatesRetiresAuditsAndRecords() {
		assertThat(publish(new PublishRequest(false, List.of("hi", "en", "kn", "hi")))).isSameAs(entry);

		verify(notices).publishDraft(50L, "worker42", TestFixtures.NOW, false);
		ArgumentCaptor<NoticeTranslation> saved = ArgumentCaptor.forClass(NoticeTranslation.class);
		verify(translations, org.mockito.Mockito.times(2)).save(saved.capture());
		NoticeTranslation hindi = saved.getAllValues().get(0);
		assertThat(hindi.getLanguageCode()).isEqualTo("hi");
		assertThat(hindi.getLanguageName()).isEqualTo("Hindi");
		assertThat(hindi.getNoticeVersionId()).isEqualTo(50L);
		assertThat(hindi.getBody()).isEqualTo("Body en [hi]");
		assertThat(hindi.getDeclaration()).isEqualTo("I agree [hi]");
		assertThat(hindi.getGrievanceInfo()).isEqualTo("Officer [hi]");
		assertThat(hindi.getRetention()).isNull();
		assertThat(hindi.isMachineTranslated()).isTrue();
		assertThat(hindi.getSourceVersion()).isEqualTo(3);
		assertThat(hindi.getAudioRef()).isNull();
		assertThat(hindi.getTextHash()).isEqualTo(NoticeTextHash.of(hindi));
		assertThat(hindi.getCreatedBy()).isEqualTo("worker42");
		assertThat(saved.getAllValues().get(1).getLanguageCode()).isEqualTo("kn");
		verify(notices).retireOthers(PROJECT, NoticeContext.REGISTRATION, 50L, "worker42");
		verify(auditLog).append(AuditLogService.NOTICE_PUBLISHED, null, null,
				"notice:50;project:3;context:REGISTRATION;v:3;langs:en,hi,kn;material:false", "PUBLISHED", 42L,
				"10.0.0.1");
		ArgumentCaptor<NoticeRequest> request = ArgumentCaptor.forClass(NoticeRequest.class);
		verify(requests).save(request.capture());
		assertThat(request.getValue().getAction()).isEqualTo(NoticeRequestAction.PUBLISH);
		assertThat(request.getValue().getNoticeVersionId()).isEqualTo(50L);
		verify(entityManager).refresh(draft);
	}

	@Test
	void englishOnlyNeedsNoTranslator() {
		when(translator.isConfigured()).thenReturn(false);

		publish(new PublishRequest(null, null));

		verify(notices).publishDraft(50L, "worker42", TestFixtures.NOW, false);
		verify(translations, never()).save(any());
		verify(auditLog).append(eq(AuditLogService.NOTICE_PUBLISHED), isNull(), isNull(),
				eq("notice:50;project:3;context:REGISTRATION;v:3;langs:en;material:false"), eq("PUBLISHED"),
				eq(42L), anyString());
		verify(translator, never()).translate(any(), any(), any());
	}

	@Test
	void requireReconsentPublishesAMaterialVersionWithoutTouchingConsents() {
		publish(new PublishRequest(true, List.of()));

		verify(notices).publishDraft(50L, "worker42", TestFixtures.NOW, true);
		verify(auditLog).append(eq(AuditLogService.NOTICE_PUBLISHED), isNull(), isNull(),
				eq("notice:50;project:3;context:REGISTRATION;v:3;langs:en;material:true"), eq("PUBLISHED"),
				eq(42L), anyString());
	}

	@Test
	void withoutADraftNothingIsPublished() {
		when(notices.findFirstByProjectIdAndContextAndStatusAndDeletedFalse(any(), any(), any()))
				.thenReturn(Optional.empty());
		assertRefused(new PublishRequest(false, List.of()), "Save the message before publishing");
	}

	@Test
	void aDraftWithoutEnglishTextIsNotPublished() {
		when(translations.findByNoticeVersionIdAndLanguageCodeAndDeletedFalse(50L, "en")).thenReturn(Optional.empty());
		assertRefused(new PublishRequest(false, List.of()), "Save the message before publishing");
	}

	@Test
	void aDeclarationIsRequired() {
		english.setDeclaration(null);
		assertRefused(new PublishRequest(false, List.of()), "Enter the patient declaration");
		english.setDeclaration("  ");
		assertRefused(new PublishRequest(false, List.of()), "Enter the patient declaration");
	}

	@Test
	void translationsNeedAConfiguredService() {
		when(translator.isConfigured()).thenReturn(false);
		assertRefused(new PublishRequest(false, List.of("hi")), NoticePublisher.NOT_CONFIGURED);
	}

	@Test
	void unknownLanguagesAreRefused() {
		assertRefused(new PublishRequest(false, Arrays.asList("hi", "xx")), "Unknown language xx");
	}

	@Test
	void aTranslationFailureOrTimeOutPublishesNothing() {
		when(translator.translate(any(), eq("en"), eq("kn"))).thenThrow(new TranslationException("timed out"));
		assertRefused(new PublishRequest(false, List.of("hi", "kn")), "Translation failed for kn; nothing was published");
	}

	@Test
	void aConcurrentPublishIsStale() {
		when(notices.publishDraft(anyLong(), anyString(), any(), anyBoolean())).thenReturn(0);

		assertThatThrownBy(() -> publish(new PublishRequest(false, List.of("hi"))))
				.isInstanceOfSatisfying(ConsentApiException.class,
						e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.STALE_VERSION));
		verify(translations, never()).save(any());
		verifyNoInteractions(auditLog);
	}

	@Test
	void aReplayAnswersFromTheRecordedRequest() {
		NoticeRequest earlier = NoticeAuthoringServiceImpl.record(ConsentFixtures.WORKER, KEY, HASH,
				NoticeRequestAction.PUBLISH, 50L);
		when(requests.findByActorUserIdAndRequestKey(42L, KEY)).thenReturn(Optional.of(earlier));

		assertThat(publish(new PublishRequest(false, List.of()))).isSameAs(entry);
		assertNothingWritten();

		earlier.setRequestHash("other");
		assertThatThrownBy(() -> publish(new PublishRequest(false, List.of())))
				.isInstanceOfSatisfying(ConsentApiException.class,
						e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.STATE_CONFLICT));
	}

	@Test
	void everyTextSectionIsTranslated() {
		NoticeTranslation full = text(50, "en", "D");
		full.setDataItems("a");
		full.setRetention("b");
		full.setRecipients("c");
		full.setWithdrawalInfo("d");
		full.setGrievanceInfo("e");
		full.setRepresentativeDeclaration("f");
		assertThat(NoticePublisher.fields(full)).containsOnlyKeys("body", "dataItems", "retention", "recipients",
				"withdrawalInfo", "grievanceInfo", "declaration", "representativeDeclaration");
	}
}
