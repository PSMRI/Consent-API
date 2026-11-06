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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.MDC;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import com.fasterxml.jackson.databind.JsonNode;
import com.iemr.consent.support.LogCapture;
import com.iemr.consent.support.TestFixtures;
import com.iemr.consent.utils.exception.ErrorCode;
import com.iemr.consent.utils.exception.ErrorResponseWriter;
import com.iemr.consent.utils.http.TraceIdFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;

class AuthenticationFilterTest {

	private static final String COOKIE_JWT = "cookie.jwt.value";

	private static final String HEADER_JWT = "header.jwt.value";

	private static final String SESSION_KEY = "session-key-123";

	private static final AuthenticatedUser COOKIE_USER = new AuthenticatedUser(1L, "cookie-user", ClientType.WEB);

	private static final AuthenticatedUser HEADER_USER = new AuthenticatedUser(2L, "header-user", ClientType.WEB);

	private static final AuthenticatedUser MOBILE_USER = new AuthenticatedUser(3L, "mobile", ClientType.MOBILE);

	@RegisterExtension
	final LogCapture logs = new LogCapture();

	private JwtValidator jwtValidator;

	private SessionValidator sessionValidator;

	private AuthenticationFilter filter;

	private FilterChain chain;

	private MockHttpServletResponse response;

	@BeforeEach
	void setUp() {
		jwtValidator = mock(JwtValidator.class);
		sessionValidator = mock(SessionValidator.class);
		filter = new AuthenticationFilter(new PublicPaths(false), jwtValidator, sessionValidator,
				new ErrorResponseWriter(TestFixtures.objectMapper(), TestFixtures.CLOCK,
						TestFixtures.consentProperties()));
		chain = mock(FilterChain.class);
		response = new MockHttpServletResponse();
		when(jwtValidator.validate(COOKIE_JWT)).thenReturn(COOKIE_USER);
		when(jwtValidator.validate(HEADER_JWT)).thenReturn(HEADER_USER);
	}

	private static MockHttpServletRequest request(String path) {
		MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
		request.setAttribute(TraceIdFilter.ATTRIBUTE, "0123456789abcdef");
		request.setRemoteAddr("10.0.0.9");
		return request;
	}

	private static MockHttpServletRequest protectedRequest() {
		return request("/consent/v1/_test/me");
	}

	private void assertRefused(ErrorCode code) throws Exception {
		assertThat(response.getStatus()).isEqualTo(code.getStatus().value());
		JsonNode body = TestFixtures.objectMapper().readTree(response.getContentAsString());
		assertThat(body.get("statusCode").asInt()).isEqualTo(code.getStatus().value());
		assertThat(body.get("success").asBoolean()).isFalse();
		assertThat(body.get("error").get("code").asText()).isEqualTo(code.name());
		assertThat(body.get("traceId").asText()).isEqualTo("0123456789abcdef");
		verify(chain, never()).doFilter(any(), any());
		logs.assertNoneContain(COOKIE_JWT, HEADER_JWT, SESSION_KEY);
	}

	private AuthenticatedUser userPassedOn(MockHttpServletRequest request) throws Exception {
		verify(chain).doFilter(request, response);
		return (AuthenticatedUser) request.getAttribute(AuthenticatedUser.ATTRIBUTE);
	}

	@Test
	void cookieWinsOverHeaderForAnotherUser() throws Exception {
		MockHttpServletRequest request = protectedRequest();
		request.setCookies(new Cookie("Jwttoken", COOKIE_JWT));
		request.addHeader("JwtToken", HEADER_JWT);

		filter.doFilter(request, response, chain);

		assertThat(userPassedOn(request)).isEqualTo(COOKIE_USER);
		verify(jwtValidator, never()).validate(HEADER_JWT);
	}

	@Test
	void headerIsUsedWhenNoJwtCookie() throws Exception {
		MockHttpServletRequest request = protectedRequest();
		request.setCookies(new Cookie("other", "x"));
		request.addHeader("JwtToken", HEADER_JWT);

		filter.doFilter(request, response, chain);

		assertThat(userPassedOn(request)).isEqualTo(HEADER_USER);
		verifyNoInteractions(sessionValidator);
	}

	@Test
	void mdcHoldsUserIdDuringChainOnly() throws Exception {
		MockHttpServletRequest request = protectedRequest();
		request.addHeader("JwtToken", HEADER_JWT);
		AtomicReference<String> mdc = new AtomicReference<>();
		filter.doFilter(request, response, (req, res) -> mdc.set(MDC.get("userId")));

		assertThat(mdc.get()).isEqualTo("2");
		assertThat(MDC.get("userId")).isNull();
	}

	@Test
	void webJwtWithAuthorizationAlsoNeedsALiveSession() throws Exception {
		MockHttpServletRequest request = protectedRequest();
		request.addHeader("JwtToken", HEADER_JWT);
		request.addHeader("Authorization", SESSION_KEY);
		request.addHeader("X-FORWARDED-FOR", "203.0.113.5");

		filter.doFilter(request, response, chain);

		assertThat(userPassedOn(request)).isEqualTo(HEADER_USER);
		verify(sessionValidator).validate(SESSION_KEY, "203.0.113.5", ClientType.WEB);
	}

	@Test
	void webJwtWithMissingSessionIsSessionExpired() throws Exception {
		MockHttpServletRequest request = protectedRequest();
		request.addHeader("JwtToken", HEADER_JWT);
		request.addHeader("Authorization", SESSION_KEY);
		when(sessionValidator.validate(SESSION_KEY, "10.0.0.9", ClientType.WEB))
				.thenThrow(new AuthenticationException(ErrorCode.SESSION_EXPIRED));

		filter.doFilter(request, response, chain);

		assertRefused(ErrorCode.SESSION_EXPIRED);
	}

	@Test
	void mobileAgentWithAuthorizationUsesTheSession() throws Exception {
		for (String agent : new String[] { "okhttp/4.9.0", "Java/17.0.2" }) {
			MockHttpServletRequest request = protectedRequest();
			request.addHeader("User-Agent", agent);
			request.addHeader("Authorization", SESSION_KEY);
			MockHttpServletResponse mobileResponse = new MockHttpServletResponse();
			when(sessionValidator.validate(SESSION_KEY, "10.0.0.9", ClientType.MOBILE)).thenReturn(MOBILE_USER);

			filter.doFilter(request, mobileResponse, chain);

			verify(chain).doFilter(request, mobileResponse);
			assertThat(request.getAttribute(AuthenticatedUser.ATTRIBUTE)).isEqualTo(MOBILE_USER);
		}
	}

	@Test
	void nonMobileAgentWithOnlyAuthorizationIsUnauthenticated() throws Exception {
		MockHttpServletRequest request = protectedRequest();
		request.addHeader("User-Agent", "Mozilla/5.0");
		request.addHeader("Authorization", SESSION_KEY);

		filter.doFilter(request, response, chain);

		assertRefused(ErrorCode.UNAUTHENTICATED);
	}

	@Test
	void authorizationWithoutUserAgentIsUnauthenticated() throws Exception {
		MockHttpServletRequest request = protectedRequest();
		request.addHeader("Authorization", SESSION_KEY);

		filter.doFilter(request, response, chain);

		assertRefused(ErrorCode.UNAUTHENTICATED);
	}

	@Test
	void mobileAgentWithoutAuthorizationIsUnauthenticated() throws Exception {
		MockHttpServletRequest request = protectedRequest();
		request.addHeader("User-Agent", "okhttp/4.9.0");

		filter.doFilter(request, response, chain);

		assertRefused(ErrorCode.UNAUTHENTICATED);
	}

	@Test
	void noCredentialIsUnauthenticated() throws Exception {
		filter.doFilter(protectedRequest(), response, chain);
		assertRefused(ErrorCode.UNAUTHENTICATED);
	}

	@Test
	void invalidOrExpiredTokenKeepsItsCode() throws Exception {
		MockHttpServletRequest request = protectedRequest();
		request.addHeader("JwtToken", "expired.jwt");
		when(jwtValidator.validate("expired.jwt")).thenThrow(new AuthenticationException(ErrorCode.SESSION_EXPIRED));

		filter.doFilter(request, response, chain);

		assertRefused(ErrorCode.SESSION_EXPIRED);
	}

	@Test
	void redisDownWhileCheckingIsDependencyUnavailable() throws Exception {
		MockHttpServletRequest request = protectedRequest();
		request.addHeader("JwtToken", "jwt.redis.down");
		when(jwtValidator.validate("jwt.redis.down")).thenThrow(new RedisConnectionFailureException("redis-host"));

		filter.doFilter(request, response, chain);

		assertRefused(ErrorCode.DEPENDENCY_UNAVAILABLE);
		logs.assertNoneContain("redis-host");
	}

	@Test
	void sessionStoreDownIsDependencyUnavailable() throws Exception {
		MockHttpServletRequest request = protectedRequest();
		request.addHeader("User-Agent", "okhttp/4");
		request.addHeader("Authorization", SESSION_KEY);
		when(sessionValidator.validate(anyString(), anyString(), eq(ClientType.MOBILE)))
				.thenThrow(new RedisConnectionFailureException("down"));

		filter.doFilter(request, response, chain);

		assertRefused(ErrorCode.DEPENDENCY_UNAVAILABLE);
	}

	@Test
	void unexpectedFailureIsInternalErrorWithoutLeakingTheMessage() throws Exception {
		MockHttpServletRequest request = protectedRequest();
		request.addHeader("JwtToken", "jwt.boom");
		when(jwtValidator.validate("jwt.boom")).thenThrow(new IllegalStateException("db secret=xyz"));

		filter.doFilter(request, response, chain);

		assertRefused(ErrorCode.INTERNAL_ERROR);
		assertThat(response.getContentAsString()).doesNotContain("secret");
		logs.assertNoneContain("secret=xyz");
	}

	@Test
	void exceptionFromTheChainItselfPropagates() throws Exception {
		MockHttpServletRequest request = protectedRequest();
		request.addHeader("JwtToken", HEADER_JWT);
		doThrow(new ServletException("controller failed")).when(chain).doFilter(any(), any());

		assertThatThrownBy(() -> filter.doFilter(request, response, chain)).isInstanceOf(ServletException.class);
		assertThat(MDC.get("userId")).isNull();
	}

	@Test
	void publicPathAndPreflightSkipValidation() throws Exception {
		MockHttpServletRequest health = request("/health");
		filter.doFilter(health, response, chain);
		verify(chain).doFilter(health, response);

		MockHttpServletRequest preflight = new MockHttpServletRequest("OPTIONS", "/consent/v1/x");
		filter.doFilter(preflight, response, chain);
		verify(chain).doFilter(preflight, response);

		verifyNoInteractions(jwtValidator, sessionValidator);
	}
}
