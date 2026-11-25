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

import java.io.IOException;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.iemr.consent.utils.exception.ErrorCode;
import com.iemr.consent.utils.exception.ErrorResponseWriter;
import com.iemr.consent.utils.http.ClientIp;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Accepts only requests carrying a valid AMRIT login, before any controller runs (FR-001,
 * FR-002).
 *
 * <p>The credential is chosen in Common-API's {@code JwtUserIdValidationFilter} order, and only
 * the first one found is used (clarification 2):</p>
 * <ol>
 * <li>{@code Jwttoken} cookie, 2. {@code JwtToken} header (web; an {@code Authorization} header
 * sent with them must also be a live session, as in Common-API's interceptor);</li>
 * <li>mobile: {@code User-Agent} containing {@code okhttp} or {@code java/} plus an
 * {@code Authorization} session key.</li>
 * </ol>
 * <p>Refusals are written in the standard body. A Redis or database failure while checking is
 * 503 {@code DEPENDENCY_UNAVAILABLE}; any other failure is 500 {@code INTERNAL_ERROR}. Tokens,
 * cookies, the {@code Authorization} value and exception messages are never logged.</p>
 */
@Component
public class AuthenticationFilter extends OncePerRequestFilter {

	static final String JWT_COOKIE = "Jwttoken";

	static final String JWT_HEADER = "JwtToken";

	static final String USER_ID_MDC = "userId";

	private static final Logger LOGGER = LoggerFactory.getLogger(AuthenticationFilter.class);

	private final PublicPaths publicPaths;

	private final JwtValidator jwtValidator;

	private final SessionValidator sessionValidator;

	private final ErrorResponseWriter errorResponseWriter;

	public AuthenticationFilter(PublicPaths publicPaths, JwtValidator jwtValidator,
			SessionValidator sessionValidator, ErrorResponseWriter errorResponseWriter) {
		this.publicPaths = publicPaths;
		this.jwtValidator = jwtValidator;
		this.sessionValidator = sessionValidator;
		this.errorResponseWriter = errorResponseWriter;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
			FilterChain filterChain) throws ServletException, IOException {
		if ("OPTIONS".equalsIgnoreCase(request.getMethod()) || publicPaths.isPublic(request)) {
			filterChain.doFilter(request, response);
			return;
		}
		AuthenticatedUser user;
		try {
			user = authenticate(request);
		} catch (AuthenticationException e) {
			errorResponseWriter.write(request, response, e.getErrorCode(), null);
			return;
		} catch (DataAccessException e) {
			LOGGER.error("Login check failed: dependency unavailable ({})", e.getClass().getSimpleName());
			errorResponseWriter.write(request, response, ErrorCode.DEPENDENCY_UNAVAILABLE, null);
			return;
		} catch (RuntimeException e) {
			LOGGER.error("Login check failed unexpectedly ({})", e.getClass().getSimpleName());
			errorResponseWriter.write(request, response, ErrorCode.INTERNAL_ERROR, null);
			return;
		}
		request.setAttribute(AuthenticatedUser.ATTRIBUTE, user);
		MDC.put(USER_ID_MDC, String.valueOf(user.userId()));
		try {
			filterChain.doFilter(request, response);
		} finally {
			MDC.remove(USER_ID_MDC);
		}
	}

	private AuthenticatedUser authenticate(HttpServletRequest request) {
		String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
		String jwt = jwtFromCookie(request);
		if (jwt == null) {
			jwt = request.getHeader(JWT_HEADER);
		}
		if (jwt != null) {
			AuthenticatedUser user = jwtValidator.validate(jwt);
			if (authorization != null) {
				sessionValidator.validate(authorization, clientIp(request), ClientType.WEB);
			}
			return user;
		}
		if (authorization != null && isMobileClient(request.getHeader(HttpHeaders.USER_AGENT))) {
			return sessionValidator.validate(authorization, clientIp(request), ClientType.MOBILE);
		}
		throw new AuthenticationException(ErrorCode.UNAUTHENTICATED);
	}

	private static String jwtFromCookie(HttpServletRequest request) {
		Cookie[] cookies = request.getCookies();
		if (cookies == null) {
			return null;
		}
		for (Cookie cookie : cookies) {
			if (JWT_COOKIE.equalsIgnoreCase(cookie.getName())) {
				return cookie.getValue();
			}
		}
		return null;
	}

	private static boolean isMobileClient(String userAgent) {
		if (userAgent == null) {
			return false;
		}
		String agent = userAgent.toLowerCase(Locale.ROOT);
		return agent.contains("okhttp") || agent.contains("java/");
	}

	private static String clientIp(HttpServletRequest request) {
		return ClientIp.of(request);
	}
}
