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

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.iemr.consent.data.evidence.ConsentEvidence;
import com.iemr.consent.data.evidence.EvidenceType;
import com.iemr.consent.support.TestFixtures;
import com.iemr.consent.utils.exception.ConsentApiException;
import com.iemr.consent.utils.exception.ErrorCode;

/** Optional recording length per mode (feature 004, clarification Q1). */
class RecordingRuleTest {

	private final RecordingRule rule = new RecordingRule(TestFixtures.consentProperties());

	private static ConsentEvidence evidence(EvidenceType type, String seconds) {
		ConsentEvidence e = new ConsentEvidence();
		e.setType(type);
		e.setDurationSec(seconds == null ? null : new BigDecimal(seconds));
		return e;
	}

	private static void assertTooLong(Runnable call) {
		assertThatThrownBy(call::run).isInstanceOf(ConsentApiException.class).extracting("errorCode")
				.isEqualTo(ErrorCode.VALIDATION_FAILED);
	}

	@Test
	@DisplayName("M3: no recording is fine in both modes")
	void noRecordingIsFine() {
		assertThatNoException().isThrownBy(() -> rule.checkSelfRead(List.of()));
		assertThatNoException().isThrownBy(() -> rule.checkReadAloud(
				List.of(evidence(EvidenceType.SIGNED_FORM_PHOTO, "500"), evidence(EvidenceType.VOICE_CLIP, null))));
	}

	@Test
	@DisplayName("M3: SELF_READ allows up to 60 s")
	void selfReadBoundary() {
		assertThatNoException().isThrownBy(() -> rule.checkSelfRead(List.of(evidence(EvidenceType.VOICE_CLIP, "60"))));
		assertTooLong(() -> rule.checkSelfRead(List.of(evidence(EvidenceType.VOICE_CLIP, "60.1"))));
	}

	@Test
	@DisplayName("M3: READ_ALOUD allows up to 120 s")
	void readAloudBoundary() {
		assertThatNoException()
				.isThrownBy(() -> rule.checkReadAloud(List.of(evidence(EvidenceType.VOICE_CLIP, "120"))));
		assertTooLong(() -> rule.checkReadAloud(List.of(evidence(EvidenceType.VOICE_CLIP, "121"))));
	}
}
