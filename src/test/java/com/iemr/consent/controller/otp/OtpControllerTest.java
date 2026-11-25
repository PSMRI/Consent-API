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

package com.iemr.consent.controller.otp;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.iemr.consent.model.otp.OtpChallengeView;
import com.iemr.consent.model.otp.OtpVerifyResult;
import com.iemr.consent.service.otp.OtpService;
import com.iemr.consent.support.ConsentFixtures;
import com.iemr.consent.support.TestFixtures;
import com.iemr.consent.utils.auth.AuthenticatedUser;
import com.iemr.consent.utils.auth.CurrentUserArgumentResolver;
import com.iemr.consent.utils.exception.ConsentApiException;
import com.iemr.consent.utils.exception.ErrorCode;
import com.iemr.consent.utils.exception.ErrorResponseWriter;
import com.iemr.consent.utils.exception.GlobalExceptionHandler;
import com.iemr.consent.utils.http.TraceIdFilter;
import com.iemr.consent.utils.idempotency.RequestHasher;

class OtpControllerTest {

	private static final String KEY = "3f2504e0-4f89-11d3-9a0c-0305e82c3301";

	private static final String CHALLENGE = "5f2504e0-4f89-11d3-9a0c-0305e82c3301";

	private OtpService service;

	private MockMvc mvc;

	@BeforeEach
	void setUp() {
		service = mock(OtpService.class);
		mvc = MockMvcBuilders
				.standaloneSetup(new OtpController(service, new RequestHasher(TestFixtures.objectMapper()),
						TestFixtures.CLOCK))
				.setControllerAdvice(new GlobalExceptionHandler(new ErrorResponseWriter(TestFixtures.objectMapper(),
						TestFixtures.CLOCK, TestFixtures.consentProperties())))
				.setCustomArgumentResolvers(new CurrentUserArgumentResolver()).addFilters(new TraceIdFilter()).build();
	}

	private static MockHttpServletRequestBuilder send(String body) {
		return post("/consent/v1/otp/challenges").requestAttr(AuthenticatedUser.ATTRIBUTE, ConsentFixtures.WORKER)
				.contentType(MediaType.APPLICATION_JSON).content(body);
	}

	@Test
	void sendIs201WithMaskedPhone() throws Exception {
		when(service.send(any(), eq(KEY), anyString(), eq(ConsentFixtures.WORKER), any()))
				.thenReturn(new OtpChallengeView("c1", "******3210", TestFixtures.NOW));

		mvc.perform(send("{\"purpose\":\"CONSENT\",\"phone\":\"9876543210\"}").header("Idempotency-Key", KEY))
				.andExpect(status().isCreated()).andExpect(jsonPath("$.statusCode").value(201))
				.andExpect(jsonPath("$.data.maskedPhone").value("******3210"));
	}

	@Test
	void sendValidation() throws Exception {
		mvc.perform(send("{\"purpose\":\"CONSENT\",\"phone\":\"12345\"}").header("Idempotency-Key", KEY))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
		mvc.perform(send("{\"purpose\":\"CONSENT\",\"phone\":\"9876543210\"}")).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.details[0].field").value("Idempotency-Key"));
	}

	@Test
	void sendRateLimitedHasRetryAfter() throws Exception {
		when(service.send(any(), anyString(), anyString(), any(), any()))
				.thenThrow(new ConsentApiException(ErrorCode.RATE_LIMITED));

		mvc.perform(send("{\"purpose\":\"GUARDIAN\",\"phone\":\"9876543210\"}").header("Idempotency-Key", KEY))
				.andExpect(status().isTooManyRequests()).andExpect(header().string("Retry-After", "60"));
	}

	@Test
	void verifyTrueFalseAndExpired() throws Exception {
		when(service.verify(eq(CHALLENGE), eq("123456"), eq(ConsentFixtures.WORKER), any()))
				.thenReturn(new OtpVerifyResult(true));
		when(service.verify(eq(CHALLENGE), eq("000000"), eq(ConsentFixtures.WORKER), any()))
				.thenThrow(new ConsentApiException(ErrorCode.OTP_EXPIRED));

		mvc.perform(post("/consent/v1/otp/challenges/" + CHALLENGE + "/verify")
				.requestAttr(AuthenticatedUser.ATTRIBUTE, ConsentFixtures.WORKER).contentType(MediaType.APPLICATION_JSON)
				.content("{\"otp\":\"123456\"}")).andExpect(status().isOk())
				.andExpect(jsonPath("$.data.verified").value(true));
		mvc.perform(post("/consent/v1/otp/challenges/" + CHALLENGE + "/verify")
				.requestAttr(AuthenticatedUser.ATTRIBUTE, ConsentFixtures.WORKER).contentType(MediaType.APPLICATION_JSON)
				.content("{\"otp\":\"000000\"}")).andExpect(status().isUnprocessableEntity())
				.andExpect(jsonPath("$.error.code").value("OTP_EXPIRED"));
		mvc.perform(post("/consent/v1/otp/challenges/" + CHALLENGE + "/verify")
				.requestAttr(AuthenticatedUser.ATTRIBUTE, ConsentFixtures.WORKER).contentType(MediaType.APPLICATION_JSON)
				.content("{\"otp\":\"12\"}")).andExpect(status().isBadRequest());
		mvc.perform(post("/consent/v1/otp/challenges/not-a-uuid/verify")
				.requestAttr(AuthenticatedUser.ATTRIBUTE, ConsentFixtures.WORKER).contentType(MediaType.APPLICATION_JSON)
				.content("{\"otp\":\"123456\"}")).andExpect(status().isBadRequest());
	}
}
