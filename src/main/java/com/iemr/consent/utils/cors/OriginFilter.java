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

import java.io.IOException;

import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.iemr.consent.utils.exception.ErrorCode;
import com.iemr.consent.utils.exception.ErrorResponseWriter;
import com.iemr.consent.utils.http.TraceIdFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Refuses browser requests from origins not on the allow-list and answers allowed
 * pre-flights (FR-004).
 *
 * <p>Mirrors steps 1–4 of Common-API's {@code JwtUserIdValidationFilter}:</p>
 * <ul>
 * <li>{@code OPTIONS} needs an allowed {@code Origin}; otherwise 403.</li>
 * <li>Any other request carrying a disallowed {@code Origin} gets 403.</li>
 * <li>Requests without {@code Origin} (servers, mobile apps) pass.</li>
 * </ul>
 * <p>It runs before the public-path bypass, so {@code /health} is origin-checked too. Refusals
 * use the standard body ({@code ORIGIN_NOT_ALLOWED}) instead of {@code sendError}.</p>
 */
@Component
public class OriginFilter extends OncePerRequestFilter {

	static final String ALLOW_METHODS = "GET, POST, PUT, PATCH, DELETE, OPTIONS";

	static final String ALLOW_HEADERS = "Authorization, Content-Type, Accept, Jwttoken, serverAuthorization,"
			+ " ServerAuthorization, serverauthorization, Serverauthorization, Idempotency-Key";

	private static final String OPTIONS = "OPTIONS";

	private final OriginPolicy originPolicy;

	private final ErrorResponseWriter errorResponseWriter;

	public OriginFilter(OriginPolicy originPolicy, ErrorResponseWriter errorResponseWriter) {
		this.originPolicy = originPolicy;
		this.errorResponseWriter = errorResponseWriter;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
			FilterChain filterChain) throws ServletException, IOException {
		String origin = request.getHeader(HttpHeaders.ORIGIN);
		boolean preflight = OPTIONS.equalsIgnoreCase(request.getMethod());
		if ((preflight || origin != null) && !originPolicy.isAllowed(origin)) {
			errorResponseWriter.write(request, response, ErrorCode.ORIGIN_NOT_ALLOWED, null);
			return;
		}
		if (origin != null) {
			response.setHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, origin);
			response.setHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, ALLOW_METHODS);
			response.setHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS, ALLOW_HEADERS);
			response.setHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true");
			response.setHeader(HttpHeaders.ACCESS_CONTROL_MAX_AGE, "3600");
			response.setHeader(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, TraceIdFilter.HEADER);
		}
		if (preflight) {
			response.setStatus(HttpServletResponse.SC_OK);
			return;
		}
		filterChain.doFilter(request, response);
	}
}
