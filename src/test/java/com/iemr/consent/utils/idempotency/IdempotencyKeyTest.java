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

package com.iemr.consent.utils.idempotency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.iemr.consent.support.TestFixtures;
import com.iemr.consent.utils.exception.ConsentApiException;
import com.iemr.consent.utils.exception.ErrorCode;

class IdempotencyKeyTest {

	private static MockHttpServletRequest withKey(String key) {
		MockHttpServletRequest request = new MockHttpServletRequest();
		if (key != null) {
			request.addHeader(IdempotencyKey.HEADER, key);
		}
		return request;
	}

	@Test
	void validKeyIsReturnedLowerCase() {
		assertThat(IdempotencyKey.require(withKey(" 3F2504E0-4F89-11D3-9A0C-0305E82C3301 ")))
				.isEqualTo("3f2504e0-4f89-11d3-9a0c-0305e82c3301");
	}

	@ParameterizedTest
	@ValueSource(strings = { "", "  ", "not-a-uuid", "3f2504e0-4f89-11d3-9a0c-0305e82c330" })
	void invalidKeyIsValidationFailed(String key) {
		assertThatThrownBy(() -> IdempotencyKey.require(withKey(key))).isInstanceOf(ConsentApiException.class)
				.satisfies(e -> {
					ConsentApiException error = (ConsentApiException) e;
					assertThat(error.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED);
					assertThat(error.getDetails().get(0).field()).isEqualTo("Idempotency-Key");
				});
	}

	@Test
	void missingKeyIsValidationFailed() {
		assertThatThrownBy(() -> IdempotencyKey.require(withKey(null))).isInstanceOf(ConsentApiException.class);
	}

	record Sample(String b, String a, Map<String, Integer> m) {
	}

	@Test
	void hashIsCanonicalAndChangesWithContent() {
		RequestHasher hasher = new RequestHasher(TestFixtures.objectMapper());
		String first = hasher.hash(new Sample("1", "2", new java.util.LinkedHashMap<>(Map.of("y", 1))));
		java.util.LinkedHashMap<String, Integer> ordered = new java.util.LinkedHashMap<>();
		ordered.put("z", 2);
		ordered.put("a", 1);
		java.util.LinkedHashMap<String, Integer> reversed = new java.util.LinkedHashMap<>();
		reversed.put("a", 1);
		reversed.put("z", 2);

		assertThat(hasher.hash(new Sample("1", "2", ordered))).isEqualTo(hasher.hash(new Sample("1", "2", reversed)));
		assertThat(first).hasSize(64).isNotEqualTo(hasher.hash(new Sample("1", "3", Map.of("y", 1))));
	}

	static class Unserialisable {

		@JsonProperty
		String getValue() {
			throw new IllegalStateException("boom");
		}
	}

	@Test
	void unserialisableRequestFailsClearly() {
		RequestHasher hasher = new RequestHasher(TestFixtures.objectMapper());
		assertThatThrownBy(() -> hasher.hash(new Unserialisable())).isInstanceOf(IllegalStateException.class)
				.hasMessage("Request could not be hashed");
	}
}
