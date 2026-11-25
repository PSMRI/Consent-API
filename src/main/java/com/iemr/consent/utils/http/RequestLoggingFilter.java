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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.iemr.consent.utils.auth.AuthenticatedUser;
import com.iemr.consent.utils.exception.ErrorResponseWriter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Writes one summary line per request: method, path, status, error code, trace id and user id
 * (FR-020, research R13).
 *
 * <p>It wraps the origin, size and authentication filters, so their refusals are logged too.
 * It never logs the query string, headers, cookies or body, which may hold tokens or personal
 * data.</p>
 */
@Component
public class RequestLoggingFilter extends OncePerRequestFilter {

	private static final Logger LOGGER = LoggerFactory.getLogger(RequestLoggingFilter.class);

	private static final String NONE = "-";

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
			FilterChain filterChain) throws ServletException, IOException {
		try {
			filterChain.doFilter(request, response);
		} finally {
			Object code = request.getAttribute(ErrorResponseWriter.ERROR_CODE_ATTRIBUTE);
			Object user = request.getAttribute(AuthenticatedUser.ATTRIBUTE);
			Object userId = user instanceof AuthenticatedUser authenticated ? authenticated.userId() : NONE;
			LOGGER.info("{} {} status={} errorCode={} traceId={} userId={}", request.getMethod(),
					request.getRequestURI(), response.getStatus(), code != null ? code : NONE,
					TraceIdFilter.currentTraceId(request), userId);
		}
	}
}
