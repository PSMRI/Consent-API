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

import java.util.List;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.iemr.consent.config.SecurityProperties;

/**
 * Decides whether a browser origin is on {@code cors.allowed-origins} (FR-004, research R3).
 *
 * <p>Mirrors Common-API's {@code JwtUserIdValidationFilter#isOriginAllowed}: comma-separated
 * patterns, {@code *} as a wildcard, and an empty list allows nothing (fail closed). Unlike
 * Common-API, every literal part is quoted with {@link Pattern#quote}, so characters such as
 * {@code +}, {@code ?} or {@code (} in a configured origin cannot change the match.</p>
 */
@Component
public class OriginPolicy {

	private final List<Pattern> patterns;

	public OriginPolicy(SecurityProperties securityProperties) {
		this.patterns = securityProperties.getAllowedOrigins().stream().map(OriginPolicy::toPattern).toList();
	}

	/** {@code true} only if {@code origin} is non-null and matches a configured pattern. */
	public boolean isAllowed(String origin) {
		if (origin == null) {
			return false;
		}
		return patterns.stream().anyMatch(pattern -> pattern.matcher(origin).matches());
	}

	private static Pattern toPattern(String configured) {
		String[] literals = configured.split("\\*", -1);
		StringBuilder regex = new StringBuilder();
		for (int i = 0; i < literals.length; i++) {
			if (i > 0) {
				regex.append(".*");
			}
			regex.append(Pattern.quote(literals[i]));
		}
		return Pattern.compile(regex.toString());
	}
}
