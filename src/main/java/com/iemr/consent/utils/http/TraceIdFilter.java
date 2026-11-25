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

import java.io.IOException;
import java.security.SecureRandom;
import java.util.HexFormat;

import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * First filter: gives every request a new trace id (FR-012, research R5).
 *
 * <p>The id is a random 16-hex-character value, set as the {@code X-Trace-Id} response header
 * <em>before</em> the chain runs (so refusals carry it too), as a request attribute and as
 * the MDC key {@code traceId} for the logs. An incoming {@code X-Trace-Id} is never trusted:
 * it could inject text into the logs.</p>
 */
@Component
public class TraceIdFilter extends OncePerRequestFilter {

	/** Request attribute and MDC key holding the trace id. */
	public static final String ATTRIBUTE = "traceId";

	/** Response header carrying the trace id. */
	public static final String HEADER = "X-Trace-Id";

	private static final int ID_BYTES = 8;

	private final SecureRandom random = new SecureRandom();

	/** Returns the current request's trace id, or {@code null} if none was assigned. */
	public static String currentTraceId(HttpServletRequest request) {
		Object value = request.getAttribute(ATTRIBUTE);
		return value instanceof String id ? id : null;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
			FilterChain filterChain) throws ServletException, IOException {
		byte[] bytes = new byte[ID_BYTES];
		random.nextBytes(bytes);
		String traceId = HexFormat.of().formatHex(bytes);
		request.setAttribute(ATTRIBUTE, traceId);
		response.setHeader(HEADER, traceId);
		MDC.put(ATTRIBUTE, traceId);
		try {
			filterChain.doFilter(request, response);
		} finally {
			MDC.remove(ATTRIBUTE);
		}
	}
}
