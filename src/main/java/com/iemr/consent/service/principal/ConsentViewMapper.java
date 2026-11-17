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

import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.iemr.consent.data.consent.Consent;
import com.iemr.consent.data.consent.ConsentPurpose;
import com.iemr.consent.data.notice.Purpose;
import com.iemr.consent.model.principal.ConsentEventView;
import com.iemr.consent.model.principal.ConsentEventView.PurposeOutcome;
import com.iemr.consent.model.principal.CurrentDecisionView;

/** Maps stored decisions and events to the API 8 views (contract {@code PatientConsents}). */
@Component
public class ConsentViewMapper {

	private final EffectiveStatus effectiveStatus;

	public ConsentViewMapper(EffectiveStatus effectiveStatus) {
		this.effectiveStatus = effectiveStatus;
	}

	/**
	 * Keeps the highest version of each purpose code.
	 *
	 * @return purposes by code
	 */
	public static Map<String, Purpose> latestByCode(Collection<Purpose> purposes) {
		BinaryOperator<Purpose> newer = (a, b) -> a.getVersion() >= b.getVersion() ? a : b;
		return purposes.stream().collect(Collectors.toMap(Purpose::getPurposeCode, Function.identity(), newer));
	}

	/**
	 * @param rows     current decisions
	 * @param purposes purposes by code; a missing purpose shows no name and is neither mandatory nor
	 *                 withdrawable
	 * @return one view per decision, sorted by purpose code
	 */
	public List<CurrentDecisionView> current(List<ConsentPurpose> rows, Map<String, Purpose> purposes) {
		return rows.stream().sorted(Comparator.comparing(ConsentPurpose::getPurposeCode))
				.map(row -> view(row, purposes.get(row.getPurposeCode()))).toList();
	}

	/**
	 * @param events events in the order to show them
	 * @param rows   the events' purpose rows, in row-id order
	 */
	public List<ConsentEventView> history(List<Consent> events, List<ConsentPurpose> rows) {
		Map<String, List<PurposeOutcome>> byEvent = rows.stream().collect(Collectors.groupingBy(
				ConsentPurpose::getConsentId, LinkedHashMap::new,
				Collectors.mapping(ConsentViewMapper::outcome, Collectors.toList())));
		return events.stream().map(e -> event(e, byEvent.getOrDefault(e.getConsentId(), List.of())))
				.toList();
	}

	private static PurposeOutcome outcome(ConsentPurpose row) {
		return new PurposeOutcome(row.getPurposeCode(), row.getStatus(), row.getVersionNo());
	}

	private static ConsentEventView event(Consent e, List<PurposeOutcome> purposes) {
		return new ConsentEventView(e.getConsentId(), e.getEventType(), e.getConsentingParty(),
				e.getNoticeVersionId(), e.getLanguageCode(), e.getMode(), e.getCapturedBy(),
				e.getCapturedAt(), e.getFacilityId(), e.getServiceLine(), e.getDeviceId(),
				e.getSyncStatus(), purposes);
	}

	private CurrentDecisionView view(ConsentPurpose row, Purpose purpose) {
		return new CurrentDecisionView(row.getPurposeCode(), purpose == null ? null : purpose.getName(),
				purpose != null && purpose.isMandatory(), purpose != null && purpose.isWithdrawable(),
				effectiveStatus.of(row), row.getValidUntil(), row.getVersionNo());
	}
}
