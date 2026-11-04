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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import com.iemr.consent.config.ConsentProperties;
import com.iemr.consent.support.TestFixtures;
import com.iemr.consent.utils.exception.ConsentApiException;
import com.iemr.consent.utils.exception.ErrorCode;

/** Feature 006, research R3: admin endpoints need Common-API's service key. */
class ServiceKeyInterceptorTest {

	private static ServiceKeyInterceptor interceptor(String key) {
		ConsentProperties base = TestFixtures.consentProperties();
		return new ServiceKeyInterceptor(new ConsentProperties(base.request(), base.health(), base.redis(),
				base.rateLimit(), base.evidence(), base.capture(), base.commonApi(), base.notice(), base.otp(),
				base.encryption(), base.accessGrant(), base.sms(), new ConsentProperties.Admin(key),
				base.translation()));
	}

	private static MockHttpServletRequest request(String header) {
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/consent/v1/admin/projects/3/notices");
		if (header != null) {
			request.addHeader(ServiceKeyInterceptor.HEADER, header);
		}
		return request;
	}

	private static void assertDenied(ServiceKeyInterceptor interceptor, String header) {
		assertThatThrownBy(() -> interceptor.preHandle(request(header), new MockHttpServletResponse(), new Object()))
				.isInstanceOfSatisfying(ConsentApiException.class,
						e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.ACCESS_DENIED));
	}

	@Test
	void theRightKeyPasses() {
		assertThat(interceptor(TestFixtures.SERVICE_KEY).preHandle(request(TestFixtures.SERVICE_KEY),
				new MockHttpServletResponse(), new Object())).isTrue();
	}

	@Test
	void aMissingOrWrongKeyIsDenied() {
		ServiceKeyInterceptor interceptor = interceptor(TestFixtures.SERVICE_KEY);
		assertDenied(interceptor, null);
		assertDenied(interceptor, "wrong");
		assertDenied(interceptor, TestFixtures.SERVICE_KEY + "x");
	}

	@Test
	void aBlankOrMissingSettingDisablesTheEndpoints() {
		assertDenied(interceptor(""), "");
		assertDenied(interceptor("  "), "  ");
		assertDenied(interceptor(null), "anything");
	}
}
