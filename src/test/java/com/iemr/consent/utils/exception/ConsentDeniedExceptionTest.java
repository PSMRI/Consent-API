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

package com.iemr.consent.utils.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.Arrays;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class ConsentDeniedExceptionTest {

	@ParameterizedTest
	@EnumSource(value = ErrorCode.class, names = { "CONSENT_NOT_FOUND", "CONSENT_DENIED", "CONSENT_WITHDRAWN",
		"CONSENT_EXPIRED", "RECONSENT_REQUIRED", "GUARDIAN_REQUIRED" })
	void acceptsEachConsentReason(ErrorCode reason) {
		ConsentDeniedException e = new ConsentDeniedException(reason, "P-SHR");

		assertThat(e.getErrorCode()).isEqualTo(reason);
		assertThat(e.getPurposeCode()).isEqualTo("P-SHR");
		assertThat(e.getErrorCode().getStatus().value()).isEqualTo(403);
	}

	@Test
	void rejectsEveryNonConsentCode() {
		Arrays.stream(ErrorCode.values()).filter(code -> !code.isConsentReason())
				.forEach(code -> assertThatIllegalArgumentException()
						.isThrownBy(() -> new ConsentDeniedException(code, "P-SHR")));
	}

	@Test
	void rejectsNullReason() {
		assertThatIllegalArgumentException().isThrownBy(() -> new ConsentDeniedException(null, "P-SHR"));
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = { "  " })
	void rejectsMissingPurposeCode(String purposeCode) {
		assertThatIllegalArgumentException()
				.isThrownBy(() -> new ConsentDeniedException(ErrorCode.CONSENT_DENIED, purposeCode));
	}
}
