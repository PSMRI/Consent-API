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

package com.iemr.consent.utils.response;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.iemr.consent.support.TestFixtures;
import com.iemr.consent.utils.exception.ErrorCode;

class ApiResponseTest {

	@Test
	void okIsSuccessWithDataAndNoError() {
		ApiResponse<String> response = ApiResponse.ok("value", "abc", TestFixtures.CLOCK);

		assertThat(response.isSuccess()).isTrue();
		assertThat(response.getStatusCode()).isEqualTo(200);
		assertThat(response.getMessage()).isEqualTo("OK");
		assertThat(response.getData()).isEqualTo("value");
		assertThat(response.getError()).isNull();
		assertThat(response.getTimestamp()).isEqualTo("2026-10-29T10:00:00Z");
		assertThat(response.getTraceId()).isEqualTo("abc");
	}

	@Test
	void createdIs201() {
		ApiResponse<String> response = ApiResponse.created("new", "abc", TestFixtures.CLOCK);

		assertThat(response.isSuccess()).isTrue();
		assertThat(response.getStatusCode()).isEqualTo(201);
		assertThat(response.getMessage()).isEqualTo("Created");
	}

	@Test
	void errorTakesStatusFromCodeAndHasNoData() {
		List<FieldErrorDetail> details = List.of(new FieldErrorDetail("decision", "must not be null"));
		ApiResponse<Void> response = ApiResponse.error(ErrorCode.VALIDATION_FAILED, null, null, details, "abc",
				TestFixtures.CLOCK);

		assertThat(response.isSuccess()).isFalse();
		assertThat(response.getStatusCode()).isEqualTo(400);
		assertThat(response.getMessage()).isEqualTo(ErrorCode.VALIDATION_FAILED.getDefaultMessage());
		assertThat(response.getData()).isNull();
		assertThat(response.getError().code()).isEqualTo(ErrorCode.VALIDATION_FAILED);
		assertThat(response.getError().details()).isEqualTo(details);
	}

	@Test
	void errorKeepsSafeMessageOverrideAndPurposeCode() {
		ApiResponse<Void> response = ApiResponse.error(ErrorCode.CONSENT_DENIED, "custom", "P-SHR", null, "abc",
				TestFixtures.CLOCK);

		assertThat(response.getStatusCode()).isEqualTo(403);
		assertThat(response.getMessage()).isEqualTo("custom");
		assertThat(response.getError().purposeCode()).isEqualTo("P-SHR");
	}

	@Test
	void statusCodeAlwaysEqualsTheCodesHttpStatus() {
		for (ErrorCode code : ErrorCode.values()) {
			ApiResponse<Void> response = ApiResponse.error(code, null, null, null, "t", TestFixtures.CLOCK);
			assertThat(response.getStatusCode()).isEqualTo(code.getStatus().value());
			assertThat(response.isSuccess()).isFalse();
		}
	}

	@Test
	void serialisesAllSevenFieldsIncludingNulls() throws Exception {
		String json = TestFixtures.objectMapper()
				.writeValueAsString(ApiResponse.ok(Map.of("a", 1), "abc", TestFixtures.CLOCK));
		JsonNode node = TestFixtures.objectMapper().readTree(json);

		assertThat(node.fieldNames()).toIterable().containsExactly("success", "statusCode", "message", "data",
				"error", "timestamp", "traceId");
		assertThat(node.get("error").isNull()).isTrue();

		String errorJson = TestFixtures.objectMapper().writeValueAsString(
				ApiResponse.error(ErrorCode.NOT_FOUND, null, null, null, "abc", TestFixtures.CLOCK));
		JsonNode error = TestFixtures.objectMapper().readTree(errorJson).get("error");
		assertThat(error.fieldNames()).toIterable().containsExactly("code", "purposeCode", "details");
		assertThat(error.get("code").asText()).isEqualTo("NOT_FOUND");
	}
}
