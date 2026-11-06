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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.iemr.consent.data.consent.Consent;
import com.iemr.consent.data.consent.ConsentPurpose;
import com.iemr.consent.data.consent.EventType;
import com.iemr.consent.data.consent.PurposeStatus;
import com.iemr.consent.data.grant.AccessGrant;
import com.iemr.consent.data.grant.GrantStatus;
import com.iemr.consent.data.notice.Purpose;
import com.iemr.consent.model.principal.WithdrawalRequest;
import com.iemr.consent.repository.consent.ConsentPurposeRepository;
import com.iemr.consent.repository.grant.AccessGrantRepository;
import com.iemr.consent.repository.notice.PurposeRepository;
import com.iemr.consent.service.audit.AuditLogService;
import com.iemr.consent.service.grant.AccessGrantService;
import com.iemr.consent.utils.auth.AuthenticatedUser;
import com.iemr.consent.utils.exception.ConsentApiException;
import com.iemr.consent.utils.exception.ErrorCode;

/**
 * Writes one withdrawal (front-end API 11; FR-009 – FR-012) in a single transaction: lock and check the
 * named decisions, record a WITHDRAWAL event with each purpose WITHDRAWN at the next version, end every
 * pending or active access grant covering a withdrawn purpose, and audit it.
 *
 * <p>Checks run in this order (research R3): no current decision (422), stale version (409), purpose
 * cannot be withdrawn (422 {@code PURPOSE_NOT_WITHDRAWABLE}), purpose not currently granted (409). The
 * consent-check cache, once it exists, is cleared after commit here (consent-check feature).</p>
 */
@Component
public class WithdrawalWriter {

	private final ConsentPurposeRepository purposeRepository;

	private final PurposeRepository purposes;

	private final AccessGrantRepository grantRepository;

	private final AccessGrantService grantService;

	private final PrincipalEventFactory events;

	private final EffectiveStatus effectiveStatus;

	private final AuditLogService auditLogService;

	public WithdrawalWriter(ConsentPurposeRepository purposeRepository, PurposeRepository purposes,
			AccessGrantRepository grantRepository, AccessGrantService grantService,
			PrincipalEventFactory events,
			EffectiveStatus effectiveStatus, AuditLogService auditLogService) {
		this.purposeRepository = purposeRepository;
		this.purposes = purposes;
		this.grantRepository = grantRepository;
		this.grantService = grantService;
		this.events = events;
		this.effectiveStatus = effectiveStatus;
		this.auditLogService = auditLogService;
	}

	@Transactional
	public void write(Long benRegId, WithdrawalRequest request, String requestKey, String requestHash,
			AuthenticatedUser user, String sourceIp) {
		Set<String> codes = request.purposeCodes().all() ? withdrawableGranted(benRegId)
				: new TreeSet<>(request.purposeCodes().codes());
		Map<String, ConsentPurpose> rows = events.lockCurrent(benRegId, codes, request.versions());
		Map<String, Purpose> byCode = latest(codes);
		for (String code : codes) {
			if (!withdrawable(byCode.get(code))) {
				throw new ConsentApiException(ErrorCode.PURPOSE_NOT_WITHDRAWABLE, null, code, null);
			}
		}
		for (ConsentPurpose row : rows.values()) {
			if (effectiveStatus.of(row) != PurposeStatus.GRANTED) {
				throw new ConsentApiException(ErrorCode.STATE_CONFLICT,
						"This purpose is not currently granted",
						row.getPurposeCode(), null);
			}
		}
		Consent source = events.newestSource(rows.values());
		Consent event = events.saveEvent(benRegId, EventType.WITHDRAWAL, null, source, events.partyFor(source),
				request, requestKey, requestHash, user);
		rows.values().forEach(row -> events.replace(row, event, PurposeStatus.WITHDRAWN, null));
		List<AccessGrant> open = grantRepository.findLockedByBeneficiaryRegIdAndStatusIn(benRegId,
				List.of(GrantStatus.PENDING, GrantStatus.ACTIVE));
		grantService.revokeForWithdrawal(open, codes, user, sourceIp);
		String summary = codes.stream().map(c -> c + ":" + PurposeStatus.WITHDRAWN)
				.collect(Collectors.joining(","));
		auditLogService.append(AuditLogService.CONSENT_WITHDRAWN, event.getConsentId(), benRegId, summary,
				PurposeStatus.WITHDRAWN.name(), user.userId(), sourceIp);
	}

	/** {@code ALL}: the patient's currently granted purposes that can be withdrawn. */
	private Set<String> withdrawableGranted(Long benRegId) {
		List<ConsentPurpose> granted = purposeRepository.findByBeneficiaryRegIdAndCurrentTrue(benRegId).stream()
				.filter(row -> effectiveStatus.of(row) == PurposeStatus.GRANTED).toList();
		Map<String, Purpose> byCode = latest(granted.stream().map(ConsentPurpose::getPurposeCode).toList());
		Set<String> codes = granted.stream().map(ConsentPurpose::getPurposeCode)
				.filter(code -> withdrawable(byCode.get(code)))
				.collect(Collectors.toCollection(TreeSet::new));
		if (codes.isEmpty()) {
			throw new ConsentApiException(ErrorCode.STATE_CONFLICT, "Nothing can be withdrawn", null, null);
		}
		return codes;
	}

	private Map<String, Purpose> latest(Collection<String> codes) {
		return ConsentViewMapper.latestByCode(purposes.findByPurposeCodeInAndActiveTrueAndDeletedFalse(codes));
	}

	private static boolean withdrawable(Purpose purpose) {
		return purpose != null && purpose.isWithdrawable();
	}
}
