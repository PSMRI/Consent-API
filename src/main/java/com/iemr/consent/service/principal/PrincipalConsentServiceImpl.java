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

import java.util.List;
import java.util.Map;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.iemr.consent.data.consent.Consent;
import com.iemr.consent.data.consent.ConsentPurpose;
import com.iemr.consent.data.consent.PurposeStatus;
import com.iemr.consent.data.notice.Purpose;
import com.iemr.consent.model.principal.CurrentDecisionView;
import com.iemr.consent.model.principal.PatientConsents;
import com.iemr.consent.model.principal.PreferenceRequest;
import com.iemr.consent.model.principal.WithdrawalRequest;
import com.iemr.consent.model.principal.WithdrawalResult;
import com.iemr.consent.repository.consent.ConsentPurposeRepository;
import com.iemr.consent.repository.consent.ConsentRepository;
import com.iemr.consent.repository.notice.PurposeRepository;
import com.iemr.consent.utils.auth.AuthenticatedUser;
import com.iemr.consent.utils.exception.ConsentApiException;
import com.iemr.consent.utils.exception.ErrorCode;

/**
 * Reads a patient's consents (API 8) and applies preference changes and withdrawals (APIs 10, 11) exactly
 * once per request key, as API 7 does (research R2): a repeat with the same key and request changes nothing
 * and returns the current consents; the same key with another request, or for another patient, is
 * {@code STATE_CONFLICT}. When two identical requests race, the database's unique key lets one win.
 */
@Service
public class PrincipalConsentServiceImpl implements PrincipalConsentService {

	private final ConsentRepository consentRepository;

	private final ConsentPurposeRepository purposeRepository;

	private final PurposeRepository purposes;

	private final ConsentViewMapper mapper;

	private final PreferenceChangeWriter preferenceWriter;

	private final WithdrawalWriter withdrawalWriter;

	public PrincipalConsentServiceImpl(ConsentRepository consentRepository,
			ConsentPurposeRepository purposeRepository, PurposeRepository purposes,
			ConsentViewMapper mapper,
			PreferenceChangeWriter preferenceWriter, WithdrawalWriter withdrawalWriter) {
		this.consentRepository = consentRepository;
		this.purposeRepository = purposeRepository;
		this.purposes = purposes;
		this.mapper = mapper;
		this.preferenceWriter = preferenceWriter;
		this.withdrawalWriter = withdrawalWriter;
	}

	@Override
	@Transactional(readOnly = true)
	public PatientConsents consents(Long benRegId) {
		List<Consent> events = consentRepository.findByBeneficiaryRegIdOrderByServerReceivedAtDesc(benRegId);
		if (events.isEmpty()) {
			return new PatientConsents(List.of(), List.of());
		}
		List<ConsentPurpose> rows = purposeRepository
				.findByConsentIdInOrderByConsentPurposeIdAsc(events.stream().map(Consent::getConsentId)
				.toList());
		List<ConsentPurpose> current = rows.stream().filter(ConsentPurpose::isCurrent).toList();
		Map<String, Purpose> byCode = ConsentViewMapper.latestByCode(purposes
				.findByPurposeCodeInAndActiveTrueAndDeletedFalse(
						current.stream().map(ConsentPurpose::getPurposeCode).toList()));
		return new PatientConsents(mapper.current(current, byCode), mapper.history(events, rows));
	}

	@Override
	public PatientConsents changePreference(Long benRegId, PreferenceRequest request, String requestKey,
			String requestHash, AuthenticatedUser user, String sourceIp) {
		writeOnce(user, requestKey, requestHash,
				() -> preferenceWriter.write(benRegId, request, requestKey, requestHash, user,
				sourceIp));
		return consents(benRegId);
	}

	@Override
	public WithdrawalResult withdraw(Long benRegId, WithdrawalRequest request, String requestKey,
			String requestHash, AuthenticatedUser user, String sourceIp) {
		writeOnce(user, requestKey, requestHash,
				() -> withdrawalWriter.write(benRegId, request, requestKey, requestHash, user,
						sourceIp));
		PatientConsents consents = consents(benRegId);
		List<String> notWithdrawn = request.purposeCodes().all() ? notWithdrawable(consents.current())
				: List.of();
		return new WithdrawalResult(consents.current(), consents.history(), notWithdrawn);
	}

	/** Runs the write unless this user already used the key; a repeat must be the same request. */
	private void writeOnce(AuthenticatedUser user, String requestKey, String requestHash, Runnable write) {
		Consent existing = consentRepository.findByCapturedByAndRequestKey(user.userId(), requestKey)
				.orElse(null);
		if (existing != null) {
			requireSameRequest(existing, requestHash);
			return;
		}
		try {
			write.run();
		} catch (DataIntegrityViolationException e) {
			Consent winner = consentRepository.findByCapturedByAndRequestKey(user.userId(), requestKey)
					.orElseThrow(() -> new ConsentApiException(ErrorCode.STATE_CONFLICT));
			requireSameRequest(winner, requestHash);
		}
	}

	private static void requireSameRequest(Consent consent, String requestHash) {
		if (!consent.getRequestHash().equals(requestHash)) {
			throw new ConsentApiException(ErrorCode.STATE_CONFLICT);
		}
	}

	/** Granted purposes that cannot be withdrawn: what {@code ALL} leaves unchanged. */
	private static List<String> notWithdrawable(List<CurrentDecisionView> current) {
		return current.stream().filter(d -> d.status() == PurposeStatus.GRANTED && !d.isWithdrawable())
				.map(CurrentDecisionView::purposeCode).toList();
	}
}
