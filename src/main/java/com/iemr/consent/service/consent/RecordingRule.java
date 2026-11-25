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

import java.math.BigDecimal;
import java.util.List;

import org.springframework.stereotype.Component;

import com.iemr.consent.config.ConsentProperties;
import com.iemr.consent.data.evidence.ConsentEvidence;
import com.iemr.consent.data.evidence.EvidenceType;

/**
 * Length of the optional recording of the patient's agreement (feature 004, clarification Q1): no recording is
 * fine; when one is linked it must be at most 60 s for SELF_READ and 120 s for READ_ALOUD (TODO-DPO settings). The
 * "at most one recording" check is in {@link ConsentValidator} for every mode.
 */
@Component
public class RecordingRule {

	private final int selfReadMaxSeconds;

	private final int readAloudMaxSeconds;

	public RecordingRule(ConsentProperties properties) {
		this.selfReadMaxSeconds = properties.evidence().selfReadMaxSeconds();
		this.readAloudMaxSeconds = properties.evidence().voiceMaxSeconds();
	}

	public void checkSelfRead(List<ConsentEvidence> evidence) {
		check(evidence, selfReadMaxSeconds);
	}

	public void checkReadAloud(List<ConsentEvidence> evidence) {
		check(evidence, readAloudMaxSeconds);
	}

	private static void check(List<ConsentEvidence> evidence, int maxSeconds) {
		BigDecimal limit = BigDecimal.valueOf(maxSeconds);
		boolean tooLong = evidence.stream().filter(e -> e.getType() == EvidenceType.VOICE_CLIP)
				.anyMatch(e -> e.getDurationSec() != null && e.getDurationSec().compareTo(limit) > 0);
		if (tooLong) {
			throw ConsentRules.invalid("evidenceIds", "recording must be at most " + maxSeconds + " s");
		}
	}
}
