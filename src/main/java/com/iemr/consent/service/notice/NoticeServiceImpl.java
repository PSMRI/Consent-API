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

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.iemr.consent.config.ConsentProperties;
import com.iemr.consent.data.notice.Notice;
import com.iemr.consent.data.notice.NoticeContext;
import com.iemr.consent.data.notice.NoticePurpose;
import com.iemr.consent.data.notice.NoticeStatus;
import com.iemr.consent.data.notice.NoticeTranslation;
import com.iemr.consent.data.notice.Purpose;
import com.iemr.consent.model.notice.ActiveNotice;
import com.iemr.consent.model.notice.GrievanceContact;
import com.iemr.consent.model.notice.NoticeLanguage;
import com.iemr.consent.model.notice.NoticePurposeView;
import com.iemr.consent.repository.notice.NoticePurposeRepository;
import com.iemr.consent.repository.notice.NoticeRepository;
import com.iemr.consent.repository.notice.NoticeTranslationRepository;
import com.iemr.consent.repository.notice.PurposeRepository;
import com.iemr.consent.utils.exception.ConsentApiException;
import com.iemr.consent.utils.exception.ErrorCode;

/**
 * Serves the active (highest PUBLISHED) notice of a context, in the language the patient chose,
 * with the purposes it covers: mandatory first, then display order (proposal §3.1 rule 4,
 * research R5). Draft and retired versions are never served (FR-003).
 */
@Service
@Transactional(readOnly = true)
public class NoticeServiceImpl implements NoticeService {

	private final NoticeRepository noticeRepository;

	private final NoticeTranslationRepository translationRepository;

	private final NoticePurposeRepository noticePurposeRepository;

	private final PurposeRepository purposeRepository;

	private final String audioBaseUrl;

	public NoticeServiceImpl(NoticeRepository noticeRepository, NoticeTranslationRepository translationRepository,
			NoticePurposeRepository noticePurposeRepository, PurposeRepository purposeRepository,
			ConsentProperties properties) {
		this.noticeRepository = noticeRepository;
		this.translationRepository = translationRepository;
		this.noticePurposeRepository = noticePurposeRepository;
		this.purposeRepository = purposeRepository;
		this.audioBaseUrl = properties.notice().audioBaseUrl();
	}

	@Override
	public List<NoticeLanguage> languages(NoticeContext context, Integer projectId) {
		Map<String, NoticeLanguage> byCode = new TreeMap<>();
		central(context).ifPresent(n -> addLanguages(byCode, n));
		project(context, projectId).ifPresent(n -> addLanguages(byCode, n));
		return List.copyOf(byCode.values());
	}

	@Override
	public ActiveNotice activeNotice(NoticeContext context, String languageCode, Integer projectId) {
		Optional<ActiveNotice> fromProject = project(context, projectId)
				.flatMap(n -> translation(n.getNoticeVersionId(), languageCode).map(t -> view(n, t)));
		if (fromProject.isPresent()) {
			return fromProject.get();
		}
		Notice notice = central(context).orElseThrow(() -> new ConsentApiException(ErrorCode.NOT_FOUND));
		NoticeTranslation t = translation(notice.getNoticeVersionId(), languageCode)
				.orElseThrow(() -> new ConsentApiException(ErrorCode.NOT_FOUND));
		return view(notice, t);
	}

	private ActiveNotice view(Notice notice, NoticeTranslation t) {
		List<NoticePurposeView> purposes = purposesOf(notice).stream()
				.map(p -> new NoticePurposeView(p.getPurposeCode(), p.getName(), p.getDescription(),
						p.isMandatory(), p.isWithdrawable(), p.getValidityDays()))
				.toList();
		return new ActiveNotice(notice.getNoticeVersionId(), t.getLanguageCode(), notice.getContext().name(),
				t.getBody(), t.getTextHash(), audioUrl(t.getAudioRef()), t.getDataItems(),
				t.getRetention(), t.getRecipients(), t.getWithdrawalInfo(), t.getGrievanceInfo(),
				purposes, t.getDeclaration(), t.getRepresentativeDeclaration(), grievance(notice));
	}

	private static GrievanceContact grievance(Notice n) {
		GrievanceContact contact = new GrievanceContact(n.getGrievanceOfficerName(), n.getGrievancePhone(),
				n.getGrievanceEmail(), n.getGrievanceAddress(), n.getGrievanceOfficeHours());
		return contact.equals(new GrievanceContact(null, null, null, null, null)) ? null : contact;
	}

	private void addLanguages(Map<String, NoticeLanguage> byCode, Notice notice) {
		Long id = notice.getNoticeVersionId();
		translationRepository.findByNoticeVersionIdAndDeletedFalseOrderByLanguageCodeAsc(id)
				.forEach(t -> byCode.put(t.getLanguageCode(), language(t)));
	}

	@Override
	public Optional<PublishedNotice> findPublished(Long noticeVersionId, String languageCode) {
		return noticeRepository.findById(noticeVersionId)
				.filter(n -> n.getStatus() == NoticeStatus.PUBLISHED && !n.isDeleted())
				.flatMap(n -> translation(noticeVersionId, languageCode)
						.map(t -> new PublishedNotice(n, t, purposesOf(n))));
	}

	private Optional<NoticeTranslation> translation(Long noticeVersionId, String languageCode) {
		return translationRepository.findByNoticeVersionIdAndLanguageCodeAndDeletedFalse(noticeVersionId,
				languageCode);
	}

	private static NoticeLanguage language(NoticeTranslation t) {
		return new NoticeLanguage(t.getLanguageCode(), t.getLanguageName(), t.getAudioRef() != null);
	}

	private Optional<Notice> central(NoticeContext context) {
		return noticeRepository
				.findFirstByContextAndProjectIdIsNullAndStatusAndDeletedFalseOrderByVersionNoDesc(
						context, NoticeStatus.PUBLISHED);
	}

	private Optional<Notice> project(NoticeContext context, Integer projectId) {
		if (projectId == null) {
			return Optional.empty();
		}
		return noticeRepository.findFirstByContextAndProjectIdAndStatusAndDeletedFalseOrderByVersionNoDesc(
				context, projectId, NoticeStatus.PUBLISHED);
	}

	/** The notice's purposes (latest active version of each), mandatory first, then display order. */
	private List<Purpose> purposesOf(Notice notice) {
		List<NoticePurpose> links = noticePurposeRepository
				.findByNoticeVersionIdAndDeletedFalseOrderByDisplayRankAsc(notice.getNoticeVersionId());
		Map<String, Integer> rank = links.stream().collect(
				Collectors.toMap(NoticePurpose::getPurposeCode, NoticePurpose::getDisplayRank));
		Map<String, Purpose> latest = purposeRepository
				.findByPurposeCodeInAndActiveTrueAndDeletedFalse(rank.keySet()).stream()
				.collect(Collectors.toMap(Purpose::getPurposeCode, Function.identity(),
						(a, b) -> a.getVersion() >= b.getVersion() ? a : b));
		return latest.values().stream()
				.sorted(Comparator.comparing((Purpose p) -> !p.isMandatory())
						.thenComparing(p -> rank.get(p.getPurposeCode())))
				.toList();
	}

	private String audioUrl(String audioRef) {
		return audioRef == null || audioBaseUrl.isEmpty() ? null : audioBaseUrl + audioRef;
	}
}
