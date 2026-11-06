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

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;

/**
 * The single list of paths reachable without login (FR-003): {@code /health},
 * {@code /version}, and the API docs only when {@code springdoc.api-docs.enabled=true}.
 * Everything else, including unknown paths and {@code /error}, needs a login (research R12).
 */
@Component
public class PublicPaths {

	private final boolean docsEnabled;

	public PublicPaths(@Value("${springdoc.api-docs.enabled:false}") boolean docsEnabled) {
		this.docsEnabled = docsEnabled;
	}

	/** {@code true} if the request path (without the context path) is public. */
	public boolean isPublic(HttpServletRequest request) {
		String path = request.getRequestURI().substring(request.getContextPath().length());
		if ("/health".equals(path) || "/version".equals(path)) {
			return true;
		}
		return docsEnabled && isDocsPath(path);
	}

	private static boolean isDocsPath(String path) {
		return "/v3/api-docs".equals(path) || path.startsWith("/v3/api-docs/")
				|| "/swagger-ui.html".equals(path) || path.startsWith("/swagger-ui/");
	}
}
