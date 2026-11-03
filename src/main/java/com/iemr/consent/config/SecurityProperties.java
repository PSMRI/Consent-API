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

package com.iemr.consent.config;

import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import lombok.Getter;

/**
 * Login and origin settings shared with Common-API.
 *
 * <p>These are root-level keys inherited from Common-API ({@code jwt.secret},
 * {@code cors.allowed-origins}, {@code iemr.*}, {@code enableIPValidation}), not one prefix, so
 * they are read with {@code @Value} instead of {@code @ConfigurationProperties}. A blank
 * {@code jwt.secret} stops start-up with a message naming the key, never its value
 * (FR-016).</p>
 */
@Getter
@Component
public class SecurityProperties {

	private final String jwtSecret;

	private final List<String> allowedOrigins;

	private final int sessionExpirySeconds;

	private final boolean extendSessionExpiry;

	private final boolean ipValidationEnabled;

	public SecurityProperties(@Value("${jwt.secret:}") String jwtSecret,
			@Value("${cors.allowed-origins:}") String allowedOrigins,
			@Value("${iemr.session.expiry.time:7200}") int sessionExpirySeconds,
			@Value("${iemr.extend.expiry.time:true}") boolean extendSessionExpiry,
			@Value("${enableIPValidation:false}") boolean ipValidationEnabled) {
		if (jwtSecret == null || jwtSecret.isBlank()) {
			throw new IllegalStateException("Missing required setting: jwt.secret");
		}
		this.jwtSecret = jwtSecret;
		this.allowedOrigins = parseOrigins(allowedOrigins);
		this.sessionExpirySeconds = sessionExpirySeconds;
		this.extendSessionExpiry = extendSessionExpiry;
		this.ipValidationEnabled = ipValidationEnabled;
	}

	private static List<String> parseOrigins(String origins) {
		if (origins == null) {
			return List.of();
		}
		return Arrays.stream(origins.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
	}

	@Override
	public String toString() {
		return "SecurityProperties[allowedOrigins=" + allowedOrigins + "]";
	}
}
