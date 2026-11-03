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

package com.iemr.consent.utils.auth;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.iemr.consent.support.FullChainWebTest;
import com.iemr.consent.support.LogCapture;
import com.iemr.consent.support.TestTokens;

import jakarta.servlet.http.Cookie;

/** User Story 1 acceptance scenarios end to end through the real filter chain. */
@FullChainWebTest
class AuthenticationFlowTest {

	private static final String ME = "/consent/v1/_test/me";

	private static final String SESSION_KEY = "f00dfeedsessionkey";

	@RegisterExtension
	final LogCapture logs = new LogCapture();

	@Autowired
	private MockMvc mvc;

	@MockBean
	private TokenDenylist tokenDenylist;

	@MockBean
	private StringRedisTemplate stringRedisTemplate;

	private ValueOperations<String, String> sessions;

	@BeforeEach
	@SuppressWarnings("unchecked")
	void setUp() {
		sessions = org.mockito.Mockito.mock(ValueOperations.class);
		when(stringRedisTemplate.opsForValue()).thenReturn(sessions);
	}

	private ResultActions refused(MockHttpServletRequestBuilder request, int status, String code) throws Exception {
		return mvc.perform(request).andExpect(status().is(status))
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$.success").value(false)).andExpect(jsonPath("$.statusCode").value(status))
				.andExpect(jsonPath("$.error.code").value(code)).andExpect(header().exists("X-Trace-Id"));
	}

	@Test
	@DisplayName("US1-1: web UI login token is accepted and the user is available")
	void webLoginIsAccepted() throws Exception {
		String token = TestTokens.valid("42", "asha", "jti-42");
		mvc.perform(get(ME).header("JwtToken", token)).andExpect(status().isOk())
				.andExpect(jsonPath("$.statusCode").value(200)).andExpect(jsonPath("$.data.userId").value(42))
				.andExpect(jsonPath("$.data.clientType").value("WEB"));

		mvc.perform(get(ME).cookie(new Cookie("Jwttoken", token))).andExpect(status().isOk());
		logs.assertNoneContain(token);
	}

	@Test
	@DisplayName("US1-2: mobile app login is accepted the same way")
	void mobileLoginIsAccepted() throws Exception {
		when(sessions.get(SESSION_KEY)).thenReturn("{\"userID\":\"77\",\"userName\":\"mobile\"}");

		mvc.perform(get(ME).header("User-Agent", "okhttp/4.9.0").header("Authorization", SESSION_KEY))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.userId").value(77))
				.andExpect(jsonPath("$.data.clientType").value("MOBILE"));
		logs.assertNoneContain(SESSION_KEY);
	}

	@Test
	@DisplayName("US1-3: missing, malformed, bad-signature and logged-out tokens are UNAUTHENTICATED")
	void invalidLoginsAreUnauthenticated() throws Exception {
		when(tokenDenylist.isDenylisted("jti-out")).thenReturn(true);

		refused(get(ME), 401, "UNAUTHENTICATED");
		refused(get(ME).header("JwtToken", TestTokens.MALFORMED), 401, "UNAUTHENTICATED");
		refused(get(ME).header("JwtToken", TestTokens.badSignature("42")), 401, "UNAUTHENTICATED");
		refused(get(ME).header("JwtToken", TestTokens.valid("42", "asha", "jti-out")), 401, "UNAUTHENTICATED");
	}

	@Test
	@DisplayName("US1-4: expired token or timed-out session is SESSION_EXPIRED")
	void expiredLoginsAreSessionExpired() throws Exception {
		refused(get(ME).header("JwtToken", TestTokens.expired("42")), 401, "SESSION_EXPIRED");
		refused(get(ME).header("User-Agent", "okhttp/4.9.0").header("Authorization", SESSION_KEY), 401,
				"SESSION_EXPIRED");
		refused(get(ME).header("JwtToken", TestTokens.valid("42", "a", "j")).header("Authorization", SESSION_KEY),
				401, "SESSION_EXPIRED");
	}

	@Test
	@DisplayName("US1-5: disallowed origin is refused, including pre-flight")
	void disallowedOriginIsRefused() throws Exception {
		String token = TestTokens.valid("42", "asha", "jti-42");
		refused(get(ME).header("Origin", "https://evil.example").header("JwtToken", token), 403,
				"ORIGIN_NOT_ALLOWED");
		refused(options(ME).header("Origin", "https://evil.example").header("Access-Control-Request-Method", "GET"),
				403, "ORIGIN_NOT_ALLOWED");
		refused(get("/health").header("Origin", "https://evil.example"), 403, "ORIGIN_NOT_ALLOWED");
	}

	@Test
	@DisplayName("US1-6: allowed pre-flight succeeds and lists the AMRIT headers")
	void allowedPreflightSucceeds() throws Exception {
		mvc.perform(options(ME).header("Origin", "https://amrit.piramalswasthya.org")
				.header("Access-Control-Request-Method", "POST")).andExpect(status().isOk())
				.andExpect(header().string("Access-Control-Allow-Origin", "https://amrit.piramalswasthya.org"))
				.andExpect(header().string("Access-Control-Allow-Headers", containsString("Jwttoken")))
				.andExpect(header().string("Access-Control-Allow-Headers", containsString("Authorization")));
	}

	@Test
	@DisplayName("Edge: unknown path without login is 401, never 404, and never an empty or HTML body")
	void unknownPathWithoutLoginIsUnauthorized() throws Exception {
		refused(get("/consent/v1/nope"), 401, "UNAUTHENTICATED")
				.andExpect(content().string(containsString("\"traceId\"")));
		refused(get("/error"), 401, "UNAUTHENTICATED");
	}
}
