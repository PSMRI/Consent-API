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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import com.iemr.consent.config.ConsentProperties;
import com.iemr.consent.utils.exception.ConsentApiException;
import com.iemr.consent.utils.exception.ErrorCode;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Guards {@code /consent/v1/admin/**} (feature 006, research R3): besides the user's normal login, the call must
 * carry {@value #HEADER} equal to {@code consent.admin.service-key}, which only Common-API holds, so its publisher
 * role check cannot be bypassed. The comparison is constant-time and the header is never logged. A blank setting
 * disables the endpoints.
 */
@Component
public class ServiceKeyInterceptor implements HandlerInterceptor {

	public static final String HEADER = "X-Consent-Service-Key";

	private final byte[] serviceKey;

	public ServiceKeyInterceptor(ConsentProperties properties) {
		String key = properties.admin().serviceKey();
		this.serviceKey = key == null || key.isBlank() ? null : key.getBytes(StandardCharsets.UTF_8);
	}

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
		String given = request.getHeader(HEADER);
		if (serviceKey == null || given == null
				|| !MessageDigest.isEqual(serviceKey, given.getBytes(StandardCharsets.UTF_8))) {
			throw new ConsentApiException(ErrorCode.ACCESS_DENIED);
		}
		return true;
	}
}
