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

package com.iemr.consent.utils.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.iemr.consent.support.FullChainWebTest;
import com.iemr.consent.support.TestFixtures;
import com.iemr.consent.support.TestTokens;
import com.iemr.consent.utils.auth.TokenDenylist;

/** User Story 2 through the full filter chain: trace id, content type and body shape on every route. */
@FullChainWebTest
class ResponseContractTest {

	private static final String BASE = "/consent/v1/_test";

	private static final String TOKEN = TestTokens.valid("42", "asha", "jti-42");

	@Autowired
	private MockMvc mvc;

	@MockBean
	private TokenDenylist tokenDenylist;

	@MockBean
	private StringRedisTemplate stringRedisTemplate;

	private JsonNode assertContract(MockHttpServletRequestBuilder request, int status) throws Exception {
		MockHttpServletResponse response = mvc.perform(request.header("JwtToken", TOKEN))
				.andExpect(status().is(status)).andReturn().getResponse();
		assertThat(response.getContentType()).startsWith(MediaType.APPLICATION_JSON_VALUE);
		JsonNode body = TestFixtures.objectMapper().readTree(response.getContentAsString());
		assertThat(body.get("statusCode").asInt()).isEqualTo(status);
		assertThat(body.get("success").asBoolean()).isEqualTo(status < 300);
		assertThat(body.get("traceId").asText()).isEqualTo(response.getHeader("X-Trace-Id")).hasSize(16);
		return body;
	}

	@Test
	@DisplayName("US2-8: every route's body traceId equals the X-Trace-Id header")
	void everyRouteCarriesTheTraceId() throws Exception {
		assertContract(get(BASE + "/ok"), 200);
		assertContract(get(BASE + "/me"), 200);
		assertContract(post(BASE + "/created").contentType(MediaType.APPLICATION_JSON).content("{\"decision\":\"Y\"}"),
				201);
		assertContract(post(BASE + "/created").contentType(MediaType.APPLICATION_JSON).content("{}"), 400);
		for (String path : List.of("/consent/CONSENT_DENIED", "/fail/ACCESS_DENIED", "/fail/NOT_FOUND",
				"/fail/STATE_CONFLICT", "/fail/BUSINESS_RULE_VIOLATION", "/fail/RATE_LIMITED", "/boom", "/db-down",
				"/cache-down", "/param")) {
			JsonNode body = assertContract(get(BASE + path), statusOf(path));
			assertThat(body.get("error").get("code").isNull()).isFalse();
		}
	}

	private static int statusOf(String path) {
		return switch (path) {
			case "/consent/CONSENT_DENIED", "/fail/ACCESS_DENIED" -> 403;
			case "/fail/NOT_FOUND" -> 404;
			case "/fail/STATE_CONFLICT" -> 409;
			case "/fail/BUSINESS_RULE_VIOLATION" -> 422;
			case "/fail/RATE_LIMITED" -> 429;
			case "/boom" -> 500;
			case "/param" -> 400;
			default -> 503;
		};
	}

	@Test
	@DisplayName("Edge: authenticated unknown path is 404 NOT_FOUND")
	void authenticatedUnknownPathIsNotFound() throws Exception {
		JsonNode body = assertContract(get("/consent/v1/nope"), 404);
		assertThat(body.get("error").get("code").asText()).isEqualTo("NOT_FOUND");
	}

	@Test
	@DisplayName("Edge: wrong method on a known path is 405 in the standard body")
	void wrongMethodIsMethodNotAllowed() throws Exception {
		JsonNode body = assertContract(delete(BASE + "/ok"), 405);
		assertThat(body.get("error").get("code").asText()).isEqualTo("METHOD_NOT_ALLOWED");
	}

	@Test
	@DisplayName("Edge: body over the size limit is 413 PAYLOAD_TOO_LARGE")
	void oversizedBodyIsPayloadTooLarge() throws Exception {
		JsonNode body = assertContract(post(BASE + "/echo").contentType(MediaType.TEXT_PLAIN).content("x".repeat(65)),
				413);
		assertThat(body.get("error").get("code").asText()).isEqualTo("PAYLOAD_TOO_LARGE");
		assertThat(assertContract(post(BASE + "/echo").contentType(MediaType.TEXT_PLAIN).content("x".repeat(64)), 200)
				.get("data").asInt()).isEqualTo(64);
	}

	@Test
	@DisplayName("Edge: health without a database bean is not served here; refusals never use HTML")
	void refusalsAreNeverHtml() throws Exception {
		MockHttpServletResponse response = mvc.perform(get(BASE + "/ok").header("Origin", "https://evil.example"))
				.andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("ORIGIN_NOT_ALLOWED"))
				.andReturn().getResponse();
		assertThat(response.getContentAsString()).doesNotContain("<html");
	}
}
