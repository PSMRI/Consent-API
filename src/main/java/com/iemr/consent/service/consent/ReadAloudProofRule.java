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

import java.util.List;

import org.springframework.stereotype.Component;

import com.iemr.consent.data.consent.ConsentMode;
import com.iemr.consent.data.evidence.ConsentEvidence;
import com.iemr.consent.model.consent.SaveConsentRequest;
import com.iemr.consent.utils.auth.AuthenticatedUser;

/**
 * READ_ALOUD mode ("Read aloud + recorded agreement"): the worker confirms the notice was read out or its audio played
 * (H2); an optional recording of the patient's agreement is at most 120 s (feature 004).
 */
@Component
public class ReadAloudProofRule implements ModeProofRule {

	private final RecordingRule recordingRule;

	public ReadAloudProofRule(RecordingRule recordingRule) {
		this.recordingRule = recordingRule;
	}

	@Override
	public ConsentMode mode() {
		return ConsentMode.READ_ALOUD;
	}

	@Override
	public void check(SaveConsentRequest request, AuthenticatedUser user, List<ConsentEvidence> evidence) {
		if (request.proof() == null || !Boolean.TRUE.equals(request.proof().workerConfirmed())) {
			throw ConsentRules.invalid("proof.workerConfirmed", "worker must confirm the reading");
		}
		recordingRule.checkReadAloud(evidence);
	}
}
