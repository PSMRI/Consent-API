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

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;

class PublicPathsTest {

	private static MockHttpServletRequest request(String contextPath, String path) {
		MockHttpServletRequest request = new MockHttpServletRequest("GET", contextPath + path);
		request.setContextPath(contextPath);
		return request;
	}

	@ParameterizedTest
	@ValueSource(strings = { "", "/consentapi" })
	void healthAndVersionArePublicWithAndWithoutContextPath(String contextPath) {
		PublicPaths paths = new PublicPaths(false);

		assertThat(paths.isPublic(request(contextPath, "/health"))).isTrue();
		assertThat(paths.isPublic(request(contextPath, "/version"))).isTrue();
	}

	@ParameterizedTest
	@ValueSource(strings = { "/v3/api-docs", "/v3/api-docs/swagger-config", "/swagger-ui.html",
		"/swagger-ui/index.html" })
	void docsArePublicOnlyWhenEnabled(String path) {
		assertThat(new PublicPaths(true).isPublic(request("", path))).isTrue();
		assertThat(new PublicPaths(false).isPublic(request("", path))).isFalse();
	}

	@ParameterizedTest
	@ValueSource(strings = { "/consent/v1/anything", "/healthz", "/version/x", "/error", "/v3/api-docsx",
		"/swagger-uix" })
	void everythingElseIsProtected(String path) {
		assertThat(new PublicPaths(true).isPublic(request("", path))).isFalse();
	}
}
