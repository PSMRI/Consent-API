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

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import com.iemr.consent.data.consent.Consent;
import com.iemr.consent.model.consent.SaveConsentRequest;
import com.iemr.consent.model.consent.SaveConsentResult;
import com.iemr.consent.repository.consent.ConsentPurposeRepository;
import com.iemr.consent.repository.consent.ConsentRepository;
import com.iemr.consent.utils.auth.AuthenticatedUser;
import com.iemr.consent.utils.exception.ConsentApiException;
import com.iemr.consent.utils.exception.ErrorCode;

/**
 * Saves a consent exactly once per request key (C18, research R4): a repeat with the same key and
 * the same request returns the original answer; a different request is {@code STATE_CONFLICT}.
 * When two identical requests race, the database's unique key lets one win and the other replays
 * it. Validation runs before the write; the write is one transaction ({@link ConsentWriter}).
 */
@Service
public class ConsentServiceImpl implements ConsentService {

	private final ConsentRepository consentRepository;

	private final ConsentPurposeRepository purposeRepository;

	private final ConsentValidator validator;

	private final ConsentWriter writer;

	public ConsentServiceImpl(ConsentRepository consentRepository, ConsentPurposeRepository purposeRepository,
			ConsentValidator validator, ConsentWriter writer) {
		this.consentRepository = consentRepository;
		this.purposeRepository = purposeRepository;
		this.validator = validator;
		this.writer = writer;
	}

	@Override
	public SaveConsentResult save(SaveConsentRequest request, String requestKey, String requestHash,
			AuthenticatedUser user, String sourceIp) {
		Consent existing = consentRepository.findByCapturedByAndRequestKey(user.userId(), requestKey)
				.orElse(null);
		if (existing != null) {
			return replay(existing, requestHash);
		}
		ValidatedConsent validated = validator.validate(request, user);
		try {
			return writer.write(request, validated, requestKey, requestHash, user, sourceIp);
		} catch (DataIntegrityViolationException e) {
			Consent winner = consentRepository.findByCapturedByAndRequestKey(user.userId(), requestKey)
					.orElseThrow(() -> new ConsentApiException(ErrorCode.STATE_CONFLICT));
			return replay(winner, requestHash);
		}
	}

	private SaveConsentResult replay(Consent consent, String requestHash) {
		if (!consent.getRequestHash().equals(requestHash)) {
			throw new ConsentApiException(ErrorCode.STATE_CONFLICT);
		}
		return ConsentResultMapper.fromStored(consent,
				purposeRepository.findByConsentIdOrderByConsentPurposeIdAsc(consent.getConsentId()));
	}
}
