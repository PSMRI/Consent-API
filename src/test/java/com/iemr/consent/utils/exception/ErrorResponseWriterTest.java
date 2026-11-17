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

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.iemr.consent.support.TestFixtures;
import com.iemr.consent.utils.http.TraceIdFilter;
import com.iemr.consent.utils.response.ApiResponse;
import com.iemr.consent.utils.response.FieldErrorDetail;

class ErrorResponseWriterTest {

	private final ObjectMapper mapper = TestFixtures.objectMapper();

	private final ErrorResponseWriter writer = new ErrorResponseWriter(mapper, TestFixtures.CLOCK,
			TestFixtures.consentProperties());

	@Test
	void writesStandardBodyWithStatusAndTraceId() throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.setAttribute(TraceIdFilter.ATTRIBUTE, "0123456789abcdef");
		MockHttpServletResponse response = new MockHttpServletResponse();

		writer.write(request, response, ErrorCode.UNAUTHENTICATED, null);

		assertThat(response.getStatus()).isEqualTo(401);
		assertThat(response.getContentType()).startsWith("application/json");
		assertThat(response.getHeader("Retry-After")).isNull();
		String expected = mapper.writeValueAsString(ApiResponse.error(ErrorCode.UNAUTHENTICATED, null, null, null,
				"0123456789abcdef", TestFixtures.CLOCK));
		assertThat(response.getContentAsString()).isEqualTo(expected);
		assertThat(request.getAttribute(ErrorResponseWriter.ERROR_CODE_ATTRIBUTE)).isEqualTo(ErrorCode.UNAUTHENTICATED);
	}

	@Test
	void passesPurposeCodeThrough() throws Exception {
		MockHttpServletResponse response = new MockHttpServletResponse();

		writer.write(new MockHttpServletRequest(), response, ErrorCode.CONSENT_DENIED, "P-SHR");

		JsonNode body = mapper.readTree(response.getContentAsString());
		assertThat(body.get("statusCode").asInt()).isEqualTo(403);
		assertThat(body.get("success").asBoolean()).isFalse();
		assertThat(body.get("error").get("purposeCode").asText()).isEqualTo("P-SHR");
		assertThat(body.get("traceId").isNull()).isTrue();
	}

	@Test
	void retryAfterOnlyForRateLimited() throws Exception {
		MockHttpServletResponse response = new MockHttpServletResponse();

		writer.write(new MockHttpServletRequest(), response, ErrorCode.RATE_LIMITED, null);

		assertThat(response.getStatus()).isEqualTo(429);
		assertThat(response.getHeader("Retry-After")).isEqualTo("60");
	}

	@Test
	void responseEntityCarriesDetailsAndMessage() {
		List<FieldErrorDetail> details = List.of(new FieldErrorDetail("f", "m"));
		ResponseEntity<ApiResponse<Void>> entity = writer.toResponseEntity(new MockHttpServletRequest(),
				ErrorCode.VALIDATION_FAILED, "safe", null, details);

		assertThat(entity.getStatusCode().value()).isEqualTo(400);
		assertThat(entity.getBody().getMessage()).isEqualTo("safe");
		assertThat(entity.getBody().getError().details()).isEqualTo(details);
	}
}
