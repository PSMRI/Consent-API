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
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import com.iemr.consent.support.LogCapture;
import com.iemr.consent.support.SampleController;
import com.iemr.consent.support.TestFixtures;
import com.iemr.consent.utils.auth.CurrentUserArgumentResolver;
import com.iemr.consent.utils.http.PayloadTooLargeException;
import com.iemr.consent.utils.http.TraceIdFilter;
import com.iemr.consent.utils.response.ApiResponse;

/** User Story 2: every outcome maps to its status and code in the standard body. */
class GlobalExceptionHandlerTest {

	private static final String BASE = "/consent/v1/_test";

	@RegisterExtension
	final LogCapture logs = new LogCapture();

	private final GlobalExceptionHandler handler = new GlobalExceptionHandler(
			new ErrorResponseWriter(TestFixtures.objectMapper(), TestFixtures.CLOCK, TestFixtures.consentProperties()));

	private MockMvc mvc;

	@BeforeEach
	void setUp() {
		mvc = MockMvcBuilders.standaloneSetup(new SampleController(TestFixtures.CLOCK)).setControllerAdvice(handler)
				.setCustomArgumentResolvers(new CurrentUserArgumentResolver()).addFilters(new TraceIdFilter()).build();
	}

	private ResultActions expectError(MockHttpServletRequestBuilder request, int status, String code)
			throws Exception {
		ResultActions result = mvc.perform(request).andExpect(status().is(status))
				.andExpect(jsonPath("$.statusCode").value(status)).andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.error.code").value(code)).andExpect(jsonPath("$.data").value(nullValue()))
				.andExpect(jsonPath("$.traceId").value(not(nullValue())));
		String body = result.andReturn().getResponse().getContentAsString();
		assertThat(body).doesNotContain("Exception").doesNotContainPattern("\"statusCode\":50[0-9]{2}");
		return result;
	}

	@Test
	@DisplayName("US2-1: successful read is 200 with data and no error")
	void okRead() throws Exception {
		mvc.perform(get(BASE + "/ok")).andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.statusCode").value(200)).andExpect(jsonPath("$.data.value").value("ok"))
				.andExpect(jsonPath("$.error").value(nullValue()))
				.andExpect(jsonPath("$.timestamp").value("2026-10-29T10:00:00Z"));
	}

	@Test
	@DisplayName("US2-2: creation is 201 with the new record")
	void created() throws Exception {
		mvc.perform(post(BASE + "/created").contentType(MediaType.APPLICATION_JSON).content("{\"decision\":\"YES\"}"))
				.andExpect(status().isCreated()).andExpect(jsonPath("$.statusCode").value(201))
				.andExpect(jsonPath("$.data.decision").value("YES"));
	}

	@Test
	@DisplayName("US2-3: invalid fields are 400 VALIDATION_FAILED listing each field, never the value")
	void invalidBody() throws Exception {
		MvcResult result = expectError(post(BASE + "/created").contentType(MediaType.APPLICATION_JSON)
				.content("{\"note\":\"xxxxxxxxxxxxxxx\"}"), 400, "VALIDATION_FAILED")
				.andExpect(jsonPath("$.error.details[*].field").value(containsInAnyOrder("decision", "note")))
				.andReturn();
		assertThat(result.getResponse().getContentAsString()).doesNotContain("xxxxxxxxxxxxxxx");
	}

	@Test
	void constraintViolationAndMethodValidationAreValidationFailed() throws Exception {
		expectError(get(BASE + "/constraint"), 400, "VALIDATION_FAILED")
				.andExpect(jsonPath("$.error.details[0].field").value("decision"));
		expectError(get(BASE + "/limited").param("size", "99"), 400, "VALIDATION_FAILED")
				.andExpect(jsonPath("$.error.details[0].field").value("size"));
	}

	@ParameterizedTest
	@EnumSource(value = ErrorCode.class, names = { "CONSENT_NOT_FOUND", "CONSENT_DENIED", "CONSENT_WITHDRAWN",
		"CONSENT_EXPIRED", "RECONSENT_REQUIRED", "GUARDIAN_REQUIRED" })
	@DisplayName("US2-4: consent refusals are 403 with the reason and purpose code")
	void consentRefusal(ErrorCode code) throws Exception {
		expectError(get(BASE + "/consent/" + code.name()), 403, code.name())
				.andExpect(jsonPath("$.error.purposeCode").value("P-SHR"));
	}

	@ParameterizedTest
	@CsvSource({ "ACCESS_DENIED,403", "NOT_FOUND,404", "STALE_VERSION,409", "STATE_CONFLICT,409",
		"BUSINESS_RULE_VIOLATION,422", "UNAUTHENTICATED,401", "SESSION_EXPIRED,401" })
	@DisplayName("US2-5/6: typed failures keep their status; ACCESS_DENIED has no purpose code")
	void typedFailures(String code, int status) throws Exception {
		expectError(get(BASE + "/fail/" + code), status, code)
				.andExpect(jsonPath("$.error.purposeCode").value(nullValue()));
	}

	@Test
	void rateLimitedSetsRetryAfter() throws Exception {
		expectError(get(BASE + "/fail/RATE_LIMITED"), 429, "RATE_LIMITED")
				.andExpect(header().string("Retry-After", "60"));
	}

	@Test
	@DisplayName("US2-7: unexpected error is 500 with a generic message and no internals")
	void unexpectedError() throws Exception {
		MvcResult result = expectError(get(BASE + "/boom"), 500, "INTERNAL_ERROR")
				.andExpect(jsonPath("$.message").value("An unexpected error occurred")).andReturn();
		assertThat(result.getResponse().getContentAsString()).doesNotContain("IllegalStateException")
				.doesNotContain("password").doesNotContain("secret");
		logs.assertNoneContain("password=secret");
	}

	@Test
	void malformedRequests() throws Exception {
		expectError(post(BASE + "/created").contentType(MediaType.APPLICATION_JSON).content("{bad json"), 400,
				"MALFORMED_REQUEST");
		expectError(get(BASE + "/param"), 400, "MALFORMED_REQUEST");
		expectError(get(BASE + "/param").param("count", "abc"), 400, "MALFORMED_REQUEST");
	}

	@Test
	void wrongMethodMediaTypeAndUnknownPath() throws Exception {
		expectError(delete(BASE + "/ok"), 405, "METHOD_NOT_ALLOWED");
		expectError(post(BASE + "/created").contentType(MediaType.TEXT_PLAIN).content("x"), 415,
				"UNSUPPORTED_MEDIA_TYPE");
		expectError(get(BASE + "/nope"), 404, "NOT_FOUND");
		expectError(get(BASE + "/upload"), 413, "PAYLOAD_TOO_LARGE");
	}

	@Test
	void dependencyDownIs503WithoutConnectionDetails() throws Exception {
		for (String path : List.of("/db-down", "/data-down", "/cache-down")) {
			MvcResult result = expectError(get(BASE + path), 503, "DEPENDENCY_UNAVAILABLE").andReturn();
			assertThat(result.getResponse().getContentAsString()).doesNotContain("host");
		}
		logs.assertNoneContain("db-host", "redis-host");
	}

	@Test
	void missingCurrentUserIsUnauthenticated() throws Exception {
		expectError(get(BASE + "/me"), 401, "UNAUTHENTICATED");
	}

	@Test
	void noResourceFoundIsNotFound() {
		ResponseEntity<ApiResponse<Void>> response = handler
				.handleNotFound(new NoResourceFoundException(HttpMethod.GET, "x"), new MockHttpServletRequest());

		assertThat(response.getStatusCode().value()).isEqualTo(404);
		assertThat(response.getBody().getError().code()).isEqualTo(ErrorCode.NOT_FOUND);
	}

	@Test
	void unreadableBodyCutOffBySizeLimitIsPayloadTooLarge() {
		HttpMessageNotReadableException e = new HttpMessageNotReadableException("I/O error",
				new PayloadTooLargeException(), new MockHttpInputMessage(new byte[0]));

		assertThat(handler.handleUnreadable(e, new MockHttpServletRequest()).getStatusCode().value()).isEqualTo(413);
	}

	@Test
	void multipartErrors() {
		ResponseEntity<ApiResponse<Void>> missing = handler.handleMissingPart(
				new org.springframework.web.multipart.support.MissingServletRequestPartException("file"),
				new MockHttpServletRequest());
		assertThat(missing.getStatusCode().value()).isEqualTo(400);
		assertThat(missing.getBody().getError().details().get(0).field()).isEqualTo("file");
		assertThat(handler.handleMultipart(new org.springframework.web.multipart.MultipartException("x"),
				new MockHttpServletRequest()).getBody().getError().code()).isEqualTo(ErrorCode.MALFORMED_REQUEST);
	}

	@Test
	void globalValidationErrorsUseTheObjectName() throws Exception {
		org.springframework.validation.BeanPropertyBindingResult result =
				new org.springframework.validation.BeanPropertyBindingResult(new Object(), "sampleBody");
		result.reject("invalid", "object is invalid");
		org.springframework.web.bind.MethodArgumentNotValidException e =
				new org.springframework.web.bind.MethodArgumentNotValidException(
						new org.springframework.core.MethodParameter(Object.class.getMethod("toString"), -1), result);

		ResponseEntity<ApiResponse<Void>> response = handler.handleInvalidBody(e, new MockHttpServletRequest());

		assertThat(response.getBody().getError().details().get(0).field()).isEqualTo("sampleBody");
		assertThat(TraceIdFilter.currentTraceId(new MockHttpServletRequest())).isNull();
	}
}
