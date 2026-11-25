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

import org.springframework.http.HttpHeaders;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;

/**
 * The caller's own AMRIT login, forwarded on server-to-server calls to Common-API so Common-API
 * applies its usual login checks (research R11). Never logged.
 *
 * @param jwt           {@code Jwttoken} cookie or {@code JwtToken} header, or {@code null}
 * @param authorization {@code Authorization} header, or {@code null}
 * @param userAgent     {@code User-Agent}, needed by Common-API for mobile logins
 */
public record ForwardedAuth(String jwt, String authorization, String userAgent) {

	public static ForwardedAuth from(HttpServletRequest request) {
		String jwt = null;
		Cookie[] cookies = request.getCookies();
		if (cookies != null) {
			for (Cookie cookie : cookies) {
				if ("Jwttoken".equalsIgnoreCase(cookie.getName())) {
					jwt = cookie.getValue();
				}
			}
		}
		if (jwt == null) {
			jwt = request.getHeader("JwtToken");
		}
		return new ForwardedAuth(jwt, request.getHeader(HttpHeaders.AUTHORIZATION),
				request.getHeader(HttpHeaders.USER_AGENT));
	}

	/** Copies the non-null values onto outgoing headers. */
	public void applyTo(HttpHeaders headers) {
		if (jwt != null) {
			headers.set("JwtToken", jwt);
		}
		if (authorization != null) {
			headers.set(HttpHeaders.AUTHORIZATION, authorization);
		}
		if (userAgent != null) {
			headers.set(HttpHeaders.USER_AGENT, userAgent);
		}
	}

	@Override
	public String toString() {
		return "ForwardedAuth[hidden]";
	}
}
