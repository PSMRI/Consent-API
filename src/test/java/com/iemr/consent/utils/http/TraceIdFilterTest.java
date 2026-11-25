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

import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;

class TraceIdFilterTest {

	private final TraceIdFilter filter = new TraceIdFilter();

	@Test
	void assignsNewHexIdBeforeChainAndIgnoresIncomingHeader() throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.addHeader(TraceIdFilter.HEADER, "injected\nvalue");
		MockHttpServletResponse response = new MockHttpServletResponse();
		AtomicReference<String> headerInChain = new AtomicReference<>();
		AtomicReference<String> mdcInChain = new AtomicReference<>();
		FilterChain chain = (req, res) -> {
			headerInChain.set(response.getHeader(TraceIdFilter.HEADER));
			mdcInChain.set(MDC.get(TraceIdFilter.ATTRIBUTE));
		};

		filter.doFilter(request, response, chain);

		String id = response.getHeader(TraceIdFilter.HEADER);
		assertThat(id).matches("^[0-9a-f]{16}$");
		assertThat(headerInChain.get()).isEqualTo(id);
		assertThat(mdcInChain.get()).isEqualTo(id);
		assertThat(TraceIdFilter.currentTraceId(request)).isEqualTo(id);
		assertThat(MDC.get(TraceIdFilter.ATTRIBUTE)).isNull();
	}

	@Test
	void everyRequestGetsADifferentId() throws Exception {
		MockHttpServletResponse first = new MockHttpServletResponse();
		MockHttpServletResponse second = new MockHttpServletResponse();
		filter.doFilter(new MockHttpServletRequest(), first, (req, res) -> { });
		filter.doFilter(new MockHttpServletRequest(), second, (req, res) -> { });

		assertThat(first.getHeader(TraceIdFilter.HEADER)).isNotEqualTo(second.getHeader(TraceIdFilter.HEADER));
	}

	@Test
	void clearsMdcEvenWhenChainThrows() {
		FilterChain chain = (req, res) -> {
			throw new ServletException("boom");
		};

		assertThatThrownBy(() -> filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), chain))
				.isInstanceOf(ServletException.class);
		assertThat(MDC.get(TraceIdFilter.ATTRIBUTE)).isNull();
	}

	@Test
	void currentTraceIdIsNullWhenNotAssigned() {
		MockHttpServletRequest request = new MockHttpServletRequest();
		assertThat(TraceIdFilter.currentTraceId(request)).isNull();
		request.setAttribute(TraceIdFilter.ATTRIBUTE, 42);
		assertThat(TraceIdFilter.currentTraceId(request)).isNull();
	}
}
