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

package com.iemr.consent.utils.cors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import com.fasterxml.jackson.databind.JsonNode;
import com.iemr.consent.support.TestFixtures;
import com.iemr.consent.utils.exception.ErrorResponseWriter;
import com.iemr.consent.utils.http.TraceIdFilter;

import jakarta.servlet.FilterChain;

class OriginFilterTest {

	private static final String ALLOWED = "http://localhost:4200";

	private static final String DISALLOWED = "https://evil.example";

	private final OriginFilter filter = new OriginFilter(new OriginPolicy(TestFixtures.securityProperties()),
			new ErrorResponseWriter(TestFixtures.objectMapper(), TestFixtures.CLOCK, TestFixtures.consentProperties()));

	private FilterChain chain;

	private MockHttpServletResponse response;

	@BeforeEach
	void setUp() {
		chain = mock(FilterChain.class);
		response = new MockHttpServletResponse();
	}

	private MockHttpServletRequest request(String method, String origin, String path) {
		MockHttpServletRequest request = new MockHttpServletRequest(method, path);
		request.setAttribute(TraceIdFilter.ATTRIBUTE, "0123456789abcdef");
		if (origin != null) {
			request.addHeader("Origin", origin);
		}
		return request;
	}

	private void assertRefused() throws Exception {
		assertThat(response.getStatus()).isEqualTo(403);
		JsonNode body = TestFixtures.objectMapper().readTree(response.getContentAsString());
		assertThat(body.get("statusCode").asInt()).isEqualTo(403);
		assertThat(body.get("success").asBoolean()).isFalse();
		assertThat(body.get("error").get("code").asText()).isEqualTo("ORIGIN_NOT_ALLOWED");
		assertThat(body.get("traceId").asText()).isEqualTo("0123456789abcdef");
		verify(chain, never()).doFilter(any(), any());
	}

	@Test
	void preflightWithoutOriginIsRefused() throws Exception {
		filter.doFilter(request("OPTIONS", null, "/consent/v1/x"), response, chain);
		assertRefused();
	}

	@Test
	void preflightFromDisallowedOriginIsRefused() throws Exception {
		filter.doFilter(request("OPTIONS", DISALLOWED, "/consent/v1/x"), response, chain);
		assertRefused();
	}

	@Test
	void preflightFromAllowedOriginIsAnsweredWithAmritHeaders() throws Exception {
		filter.doFilter(request("OPTIONS", ALLOWED, "/consent/v1/x"), response, chain);

		assertThat(response.getStatus()).isEqualTo(200);
		assertThat(response.getHeader("Access-Control-Allow-Origin")).isEqualTo(ALLOWED);
		assertThat(response.getHeader("Access-Control-Allow-Methods"))
				.isEqualTo("GET, POST, PUT, PATCH, DELETE, OPTIONS");
		assertThat(response.getHeader("Access-Control-Allow-Headers")).isEqualTo("Authorization, Content-Type, Accept,"
				+ " Jwttoken, serverAuthorization, ServerAuthorization, serverauthorization, Serverauthorization,"
				+ " Idempotency-Key");
		assertThat(response.getHeader("Access-Control-Allow-Credentials")).isEqualTo("true");
		assertThat(response.getHeader("Access-Control-Max-Age")).isEqualTo("3600");
		assertThat(response.getHeader("Access-Control-Expose-Headers")).isEqualTo("X-Trace-Id");
		verify(chain, never()).doFilter(any(), any());
	}

	@Test
	void requestFromDisallowedOriginIsRefused() throws Exception {
		filter.doFilter(request("GET", DISALLOWED, "/consent/v1/x"), response, chain);
		assertRefused();
	}

	@Test
	void requestWithoutOriginPassesWithoutCorsHeaders() throws Exception {
		MockHttpServletRequest request = request("GET", null, "/consent/v1/x");
		filter.doFilter(request, response, chain);

		verify(chain).doFilter(request, response);
		assertThat(response.getHeader("Access-Control-Allow-Origin")).isNull();
	}

	@Test
	void requestFromAllowedOriginGetsCorsHeadersAndPasses() throws Exception {
		MockHttpServletRequest request = request("POST", ALLOWED, "/consent/v1/x");
		filter.doFilter(request, response, chain);

		verify(chain).doFilter(request, response);
		assertThat(response.getHeader("Access-Control-Allow-Origin")).isEqualTo(ALLOWED);
	}

	@Test
	void healthIsOriginCheckedToo() throws Exception {
		filter.doFilter(request("GET", DISALLOWED, "/health"), response, chain);
		assertRefused();
	}
}
