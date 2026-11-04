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

package com.iemr.consent.service.principal;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.iemr.consent.data.consent.Consent;
import com.iemr.consent.data.consent.ConsentPurpose;
import com.iemr.consent.data.consent.ConsentingParty;
import com.iemr.consent.data.consent.EventType;
import com.iemr.consent.data.consent.GuardianLink;
import com.iemr.consent.data.consent.PurposeStatus;
import com.iemr.consent.data.notice.Notice;
import com.iemr.consent.data.notice.NoticeContext;
import com.iemr.consent.data.notice.NoticePurpose;
import com.iemr.consent.data.notice.NoticeStatus;
import com.iemr.consent.model.principal.PendingAction;
import com.iemr.consent.model.principal.PendingReason;
import com.iemr.consent.repository.consent.ConsentPurposeRepository;
import com.iemr.consent.repository.consent.ConsentRepository;
import com.iemr.consent.repository.consent.GuardianLinkRepository;
import com.iemr.consent.repository.notice.NoticePurposeRepository;
import com.iemr.consent.repository.notice.NoticeRepository;

/**
 * Derives a patient's pending consent actions on each call (research R5); nothing is stored, so the answer
 * is right before the expiry, guardian and notice jobs exist.
 * <ul>
 * <li>CONSENT_MISSING: purposes of the active notice with no current decision (C4).</li>
 * <li>LEGACY: decisions from a pre-DPDP (legacy) event (C5).</li>
 * <li>RECONSENT_REQUIRED: decisions marked so, past their end date (C16), or taken on a notice that has a
 * newer published material version (C14).</li>
 * <li>GUARDIAN_TO_SELF: decisions a guardian gave whose guardian link has ended (C9).</li>
 * </ul>
 * WITHDRAWN and DENIED are valid decisions and need nothing. {@code dueSince} is the earliest date among an
 * action's purposes.
 */
@Service
public class PendingActionServiceImpl implements PendingActionService {

	private final ConsentPurposeRepository purposeRepository;

	private final ConsentRepository consentRepository;

	private final NoticeRepository noticeRepository;

	private final NoticePurposeRepository noticePurposeRepository;

	private final GuardianLinkRepository guardianLinkRepository;

	private final EffectiveStatus effectiveStatus;

	private final Clock clock;

	public PendingActionServiceImpl(ConsentPurposeRepository purposeRepository,
			ConsentRepository consentRepository, NoticeRepository noticeRepository,
			NoticePurposeRepository noticePurposeRepository, GuardianLinkRepository guardianLinkRepository,
			EffectiveStatus effectiveStatus, Clock clock) {
		this.purposeRepository = purposeRepository;
		this.consentRepository = consentRepository;
		this.noticeRepository = noticeRepository;
		this.noticePurposeRepository = noticePurposeRepository;
		this.guardianLinkRepository = guardianLinkRepository;
		this.effectiveStatus = effectiveStatus;
		this.clock = clock;
	}

	@Override
	@Transactional(readOnly = true)
	public List<PendingAction> pending(Long benRegId, NoticeContext context) {
		List<ConsentPurpose> current = purposeRepository.findByBeneficiaryRegIdAndCurrentTrue(benRegId);
		return evaluate(current, sources(current), context, new Lookups());
	}

	/**
	 * API 15: every patient's current decisions and their source events are read in two queries, and the notice
	 * and guardian look-ups are shared across the page; each patient is then evaluated exactly as by API 9.
	 */
	@Override
	@Transactional(readOnly = true)
	public Map<Long, List<PendingAction>> pendingFor(Collection<Long> benRegIds, NoticeContext context) {
		List<ConsentPurpose> all = purposeRepository.findByBeneficiaryRegIdInAndCurrentTrue(benRegIds);
		Map<Long, List<ConsentPurpose>> byPatient = all.stream()
				.collect(Collectors.groupingBy(ConsentPurpose::getBeneficiaryRegId));
		Map<String, Consent> sources = sources(all);
		Lookups lookups = new Lookups();
		Map<Long, List<PendingAction>> result = new LinkedHashMap<>();
		for (Long benRegId : benRegIds) {
			List<ConsentPurpose> rows = byPatient.getOrDefault(benRegId, List.of());
			result.put(benRegId, evaluate(rows, sources, context, lookups));
		}
		return result;
	}

	/** Look-ups that do not depend on the patient, shared by every patient of one request. */
	private static final class Lookups {

		private final Map<NoticeContext, Optional<List<String>>> noticePurposes =
				new EnumMap<>(NoticeContext.class);

		private final Map<Long, Optional<Instant>> newerNotice = new HashMap<>();

		private final Map<String, Optional<Instant>> linkEnds = new HashMap<>();
	}

	/** The events the current decisions came from; no query when there are none. */
	private Map<String, Consent> sources(List<ConsentPurpose> current) {
		if (current.isEmpty()) {
			return Map.of();
		}
		List<String> eventIds = current.stream().map(ConsentPurpose::getConsentId).distinct().toList();
		return consentRepository.findByConsentIdIn(eventIds).stream()
				.collect(Collectors.toMap(Consent::getConsentId, Function.identity()));
	}

	/** One patient's pending actions (API 9 rules). */
	private List<PendingAction> evaluate(List<ConsentPurpose> current, Map<String, Consent> sources,
			NoticeContext context, Lookups lookups) {
		List<PendingAction> actions = new ArrayList<>();
		missing(context, current, lookups).ifPresent(actions::add);
		Map<String, Instant> legacy = new TreeMap<>();
		Map<String, Instant> reconsent = new TreeMap<>();
		Map<String, Instant> guardianEnded = new TreeMap<>();
		for (ConsentPurpose row : current) {
			Consent source = sources.get(row.getConsentId());
			if (source.getEventType() == EventType.LEGACY) {
				legacy.put(row.getPurposeCode(), source.getCapturedAt());
			}
			reconsentDue(row, source, lookups.newerNotice)
					.ifPresent(due -> reconsent.put(row.getPurposeCode(), due));
			lookups.linkEnds.computeIfAbsent(source.getConsentId(), id -> guardianEnd(source))
					.ifPresent(end -> guardianEnded.put(row.getPurposeCode(), end));
		}
		action(PendingReason.LEGACY, legacy).ifPresent(actions::add);
		action(PendingReason.RECONSENT_REQUIRED, reconsent).ifPresent(actions::add);
		action(PendingReason.GUARDIAN_TO_SELF, guardianEnded).ifPresent(actions::add);
		return actions;
	}

	/** CONSENT_MISSING for the active notice's purposes that have no current decision. */
	private Optional<PendingAction> missing(NoticeContext context, List<ConsentPurpose> current, Lookups lookups) {
		Optional<List<String>> noticeCodes = lookups.noticePurposes.computeIfAbsent(context,
				this::activeNoticePurposes);
		if (noticeCodes.isEmpty()) {
			return Optional.empty();
		}
		Set<String> decided = current.stream().map(ConsentPurpose::getPurposeCode).collect(Collectors.toSet());
		List<String> codes = noticeCodes.get().stream().filter(code -> !decided.contains(code)).sorted()
				.toList();
		return codes.isEmpty() ? Optional.empty()
				: Optional.of(new PendingAction(PendingReason.CONSENT_MISSING, codes, null));
	}

	/** The purposes of the active central notice of a context, or empty when there is none. */
	private Optional<List<String>> activeNoticePurposes(NoticeContext context) {
		Optional<Notice> notice = noticeRepository
				.findFirstByContextAndProjectIdIsNullAndStatusAndDeletedFalseOrderByVersionNoDesc(
						context, NoticeStatus.PUBLISHED);
		return notice.map(n -> codesOf(n.getNoticeVersionId()));
	}

	private List<String> codesOf(Long noticeVersionId) {
		return noticePurposeRepository
				.findByNoticeVersionIdAndDeletedFalseOrderByDisplayRankAsc(noticeVersionId).stream()
				.map(NoticePurpose::getPurposeCode).toList();
	}

	/** The date re-consent became due for a granted or denied decision, if it is due. */
	private Optional<Instant> reconsentDue(ConsentPurpose row, Consent source,
			Map<Long, Optional<Instant>> newerNotice) {
		PurposeStatus status = effectiveStatus.of(row);
		if (status == PurposeStatus.EXPIRED) {
			return Optional.of(row.getValidUntil());
		}
		if (status == PurposeStatus.RECONSENT_REQUIRED) {
			return Optional.of(source.getCapturedAt());
		}
		if (status == PurposeStatus.WITHDRAWN) {
			return Optional.empty();
		}
		return newerNotice.computeIfAbsent(source.getNoticeVersionId(), this::newerMaterialPublishedAt);
	}

	private Optional<Instant> newerMaterialPublishedAt(Long noticeVersionId) {
		return noticeRepository.findById(noticeVersionId)
				.flatMap(n -> noticeRepository.findNewerMaterial(n.getNoticeCode(), n.getVersionNo())
				.stream()
						.findFirst())
				.map(Notice::getPublishedAt);
	}

	/** The end of a guardian's link, when the decision is a guardian's and the link has ended (C9). */
	private Optional<Instant> guardianEnd(Consent source) {
		if (source.getConsentingParty() != ConsentingParty.GUARDIAN) {
			return Optional.empty();
		}
		Instant now = clock.instant();
		return guardianLinkRepository.findFirstByConsentIdAndDeletedFalse(source.getConsentId())
				.map(GuardianLink::getValidUntil).filter(end -> end.isBefore(now));
	}

	private static Optional<PendingAction> action(PendingReason reason, Map<String, Instant> dueByCode) {
		if (dueByCode.isEmpty()) {
			return Optional.empty();
		}
		Instant earliest = earliest(dueByCode.values());
		return Optional.of(new PendingAction(reason, List.copyOf(dueByCode.keySet()), earliest));
	}

	private static Instant earliest(Collection<Instant> dates) {
		return dates.stream().filter(Objects::nonNull).min(Comparator.naturalOrder()).orElse(null);
	}
}
