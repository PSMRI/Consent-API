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

import java.time.Duration;

import org.springframework.stereotype.Component;

import com.iemr.consent.data.consent.Consent;
import com.iemr.consent.data.consent.ConsentPurpose;
import com.iemr.consent.data.consent.Decision;
import com.iemr.consent.data.consent.PurposeStatus;
import com.iemr.consent.data.notice.Purpose;
import com.iemr.consent.model.consent.PurposeResult;
import com.iemr.consent.repository.consent.ConsentPurposeRepository;

/**
 * Writes one purpose decision of a saved consent event (research R6 of feature 008), shared by a save (API 7,
 * {@link ConsentWriter}) and the legacy import. For a known patient the current decision for the purpose is marked
 * {@code SUPERSEDED} and the new one gets the next version (the current row is locked by {@code findCurrent}); a
 * granted purpose with validity days is valid until the capture time plus those days. Append-only otherwise.
 */
@Component
public class ConsentDecisionWriter {

	private final ConsentPurposeRepository purposeRepository;

	public ConsentDecisionWriter(ConsentPurposeRepository purposeRepository) {
		this.purposeRepository = purposeRepository;
	}

	public PurposeResult write(Consent consent, String purposeCode, Decision decision, Purpose purpose) {
		int version = 1;
		Long patient = consent.getBeneficiaryRegId();
		if (patient != null) {
			ConsentPurpose previous = purposeRepository.findCurrent(patient, purposeCode).orElse(null);
			if (previous != null) {
				previous.setCurrent(false);
				previous.setStatus(PurposeStatus.SUPERSEDED);
				purposeRepository.saveAndFlush(previous);
				version = previous.getVersionNo() + 1;
			}
		}
		PurposeStatus status = ConsentResultMapper.statusOf(decision);
		ConsentPurpose row = new ConsentPurpose();
		row.setConsentId(consent.getConsentId());
		row.setBeneficiaryRegId(patient);
		row.setPurposeCode(purposeCode);
		row.setDecision(decision);
		row.setStatus(status);
		row.setVersionNo(version);
		row.setCurrent(true);
		if (status == PurposeStatus.GRANTED && purpose.getValidityDays() != null) {
			row.setValidUntil(consent.getCapturedAt().plus(Duration.ofDays(purpose.getValidityDays())));
		}
		purposeRepository.save(row);
		return new PurposeResult(purposeCode, status, version);
	}
}
