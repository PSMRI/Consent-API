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

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

class ErrorCodeTest {

	@Test
	void hasExactlyTheContractCodesInOrderWithTheirStatuses() {
		Map<String, Integer> expected = new LinkedHashMap<>();
		expected.put("VALIDATION_FAILED", 400);
		expected.put("MALFORMED_REQUEST", 400);
		expected.put("UNAUTHENTICATED", 401);
		expected.put("SESSION_EXPIRED", 401);
		expected.put("ACCESS_DENIED", 403);
		expected.put("ORIGIN_NOT_ALLOWED", 403);
		expected.put("CONSENT_NOT_FOUND", 403);
		expected.put("CONSENT_DENIED", 403);
		expected.put("CONSENT_WITHDRAWN", 403);
		expected.put("CONSENT_EXPIRED", 403);
		expected.put("RECONSENT_REQUIRED", 403);
		expected.put("GUARDIAN_REQUIRED", 403);
		expected.put("NOT_FOUND", 404);
		expected.put("METHOD_NOT_ALLOWED", 405);
		expected.put("STALE_VERSION", 409);
		expected.put("STATE_CONFLICT", 409);
		expected.put("PAYLOAD_TOO_LARGE", 413);
		expected.put("UNSUPPORTED_MEDIA_TYPE", 415);
		expected.put("BUSINESS_RULE_VIOLATION", 422);
		expected.put("RATE_LIMITED", 429);
		expected.put("INTERNAL_ERROR", 500);
		expected.put("DEPENDENCY_UNAVAILABLE", 503);
		expected.put("OTP_EXPIRED", 422);
		expected.put("OTP_INVALID", 422);
		expected.put("EVIDENCE_HASH_MISMATCH", 422);
		expected.put("PURPOSE_NOT_WITHDRAWABLE", 422);

		Map<String, Integer> actual = new LinkedHashMap<>();
		for (ErrorCode code : ErrorCode.values()) {
			actual.put(code.name(), code.getStatus().value());
		}
		assertThat(actual).containsExactlyEntriesOf(expected);
	}

	@Test
	void consentReasonIsTrueForExactlyTheSixConsentCodes() {
		Set<ErrorCode> reasons = Arrays.stream(ErrorCode.values()).filter(ErrorCode::isConsentReason)
				.collect(Collectors.toSet());
		assertThat(reasons).containsExactlyInAnyOrder(ErrorCode.CONSENT_NOT_FOUND, ErrorCode.CONSENT_DENIED,
				ErrorCode.CONSENT_WITHDRAWN, ErrorCode.CONSENT_EXPIRED, ErrorCode.RECONSENT_REQUIRED,
				ErrorCode.GUARDIAN_REQUIRED);
	}

	@Test
	void noLegacyStatusAndEveryCodeHasASafeMessage() {
		for (ErrorCode code : ErrorCode.values()) {
			assertThat(code.getStatus().value()).isBetween(400, 599);
			assertThat(code.getDefaultMessage()).isNotBlank().doesNotContainIgnoringCase("exception");
		}
	}
}
