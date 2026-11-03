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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import com.iemr.consent.support.LogCapture;
import com.iemr.consent.utils.auth.AuthenticatedUser;
import com.iemr.consent.utils.auth.ClientType;
import com.iemr.consent.utils.exception.ErrorCode;
import com.iemr.consent.utils.exception.ErrorResponseWriter;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;

class RequestLoggingFilterTest {

	private static final String JWT = "eyJhbGciOiJIUzI1NiJ9.synthetic.token";

	private static final String SESSION_KEY = "synthetic-session-key";

	private static final String PHONE = "9876543210";

	@RegisterExtension
	final LogCapture logs = new LogCapture();

	private final RequestLoggingFilter filter = new RequestLoggingFilter();

	private MockHttpServletRequest sensitiveRequest() {
		MockHttpServletRequest request = new MockHttpServletRequest("POST", "/consent/v1/x");
		request.setQueryString("phone=" + PHONE);
		request.addHeader("JwtToken", JWT);
		request.addHeader("Authorization", SESSION_KEY);
		request.setCookies(new Cookie("Jwttoken", JWT));
		request.setContent(("{\"phone\":\"" + PHONE + "\"}").getBytes());
		request.setAttribute(TraceIdFilter.ATTRIBUTE, "0123456789abcdef");
		return request;
	}

	private List<String> summaryLines() {
		return logs.messages().stream().filter(line -> line.startsWith("POST ") || line.startsWith("GET ")).toList();
	}

	@Test
	void logsOneSummaryLineForAnAuthenticatedRequest() throws Exception {
		MockHttpServletRequest request = sensitiveRequest();
		MockHttpServletResponse response = new MockHttpServletResponse();

		filter.doFilter(request, response, (req, res) -> {
			req.setAttribute(AuthenticatedUser.ATTRIBUTE, new AuthenticatedUser(42L, "asha", ClientType.WEB));
			((HttpServletResponse) res).setStatus(201);
		});

		assertThat(summaryLines()).containsExactly(
				"POST /consent/v1/x status=201 errorCode=- traceId=0123456789abcdef userId=42");
		logs.assertNoneContain(JWT, SESSION_KEY, PHONE, "asha");
	}

	@Test
	void logsRefusalsWrittenByInnerFilters() throws Exception {
		MockHttpServletRequest request = sensitiveRequest();

		filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> {
			req.setAttribute(ErrorResponseWriter.ERROR_CODE_ATTRIBUTE, ErrorCode.UNAUTHENTICATED);
			((HttpServletResponse) res).setStatus(401);
		});

		assertThat(summaryLines()).containsExactly(
				"POST /consent/v1/x status=401 errorCode=UNAUTHENTICATED traceId=0123456789abcdef userId=-");
		logs.assertNoneContain(JWT, SESSION_KEY, PHONE);
	}

	@Test
	void stillLogsWhenTheChainThrows() {
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/health");

		assertThatThrownBy(() -> filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> {
			throw new ServletException("boom");
		})).isInstanceOf(ServletException.class);
		assertThat(summaryLines()).hasSize(1);
	}
}
