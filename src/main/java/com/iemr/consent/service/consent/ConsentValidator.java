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

package com.iemr.consent.service.consent;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.iemr.consent.data.consent.ConsentMode;
import com.iemr.consent.data.consent.ConsentingParty;
import com.iemr.consent.data.consent.Decision;
import com.iemr.consent.data.consent.EventType;
import com.iemr.consent.data.evidence.ConsentEvidence;
import com.iemr.consent.data.notice.Purpose;
import com.iemr.consent.model.consent.DecisionItem;
import com.iemr.consent.model.consent.SaveConsentRequest;
import com.iemr.consent.service.evidence.EvidenceService;
import com.iemr.consent.service.notice.NoticeService;
import com.iemr.consent.service.notice.PublishedNotice;
import com.iemr.consent.utils.auth.AuthenticatedUser;
import com.iemr.consent.utils.exception.ConsentApiException;
import com.iemr.consent.utils.exception.ErrorCode;

/**
 * Checks a save before anything is written (FR-010, FR-012, research R6).
 *
 * <ul>
 * <li>The notice version is still published and the fingerprint matches the language shown;
 * otherwise {@code STALE_VERSION}.</li>
 * <li>Decisions cover exactly the notice's purposes, once each.</li>
 * <li>A GRANT needs a mode and that mode's proof ({@link ModeProofRule}); SELF_READ needs none.
 * A decline with no mode is allowed only when every decision is DENY.</li>
 * <li>Every evidence id can be linked; a guardian's details pass {@link GuardianRule}.</li>
 * </ul>
 */
@Component
public class ConsentValidator {

	/** Event types the consent screen may save; WITHDRAWAL and LEGACY come from other paths. */
	private static final Set<EventType> SCREEN_EVENTS = Set.of(EventType.NEW, EventType.UPDATE, EventType.RENEWAL);

	private final NoticeService noticeService;

	private final EvidenceService evidenceService;

	private final GuardianRule guardianRule;

	private final Map<ConsentMode, ModeProofRule> rules = new EnumMap<>(ConsentMode.class);

	public ConsentValidator(NoticeService noticeService, EvidenceService evidenceService,
			GuardianRule guardianRule, List<ModeProofRule> rules) {
		this.noticeService = noticeService;
		this.evidenceService = evidenceService;
		this.guardianRule = guardianRule;
		rules.forEach(rule -> this.rules.put(rule.mode(), rule));
	}

	public ValidatedConsent validate(SaveConsentRequest request, AuthenticatedUser user) {
		if (!SCREEN_EVENTS.contains(request.eventType())) {
			throw ConsentRules.invalid("eventType", "must be NEW, UPDATE or RENEWAL");
		}
		PublishedNotice notice = noticeService.findPublished(request.noticeVersionId(), request.languageCode())
				.filter(n -> n.translation().getTextHash().equals(request.textHash()))
				.orElseThrow(() -> new ConsentApiException(ErrorCode.STALE_VERSION));
		checkDecisions(request.decisions(), notice.purposes());
		boolean anyGrant = request.decisions().stream().anyMatch(d -> d.decision() == Decision.GRANT);
		if (anyGrant && request.mode() == null) {
			throw ConsentRules.invalid("mode", "is required when any purpose is granted");
		}
		List<ConsentEvidence> evidence = evidenceService.requireLinkable(orEmpty(request.evidenceIds()), user);
		if (anyGrant && request.mode() != ConsentMode.SELF_READ) {
			ModeProofRule rule = rules.get(request.mode());
			if (rule == null) {
				throw ConsentRules.invalid("mode", "is not supported");
			}
			rule.check(request, user, evidence);
		}
		List<ConsentEvidence> all = new ArrayList<>(evidence);
		if (request.consentingParty() == ConsentingParty.GUARDIAN) {
			guardianRule.check(request, user);
			all.addAll(evidenceService.requireLinkable(orEmpty(request.guardian().evidenceIds()), user));
		}
		return new ValidatedConsent(notice, all);
	}

	private static void checkDecisions(List<DecisionItem> decisions, List<Purpose> purposes) {
		Set<String> expected = purposes.stream().map(Purpose::getPurposeCode).collect(Collectors.toSet());
		Set<String> seen = new HashSet<>();
		for (DecisionItem decision : decisions) {
			if (!seen.add(decision.purposeCode())) {
				throw ConsentRules.invalid("decisions", "has a purpose more than once");
			}
		}
		if (!seen.equals(expected)) {
			throw ConsentRules.invalid("decisions", "must cover exactly the purposes of the notice");
		}
	}

	private static List<UUID> orEmpty(List<UUID> ids) {
		return ids == null ? List.of() : ids;
	}
}
