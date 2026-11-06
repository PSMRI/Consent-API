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

package com.iemr.consent.service.otp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.io.IOException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.iemr.consent.config.CommonApiClientConfig;
import com.iemr.consent.support.LogCapture;
import com.iemr.consent.support.TestFixtures;
import com.iemr.consent.utils.exception.ConsentApiException;
import com.iemr.consent.utils.exception.ErrorCode;
import com.iemr.consent.utils.http.ForwardedAuth;

class CommonApiOtpClientTest {

	private static final String PHONE = "9876543210";

	private static final ForwardedAuth AUTH = new ForwardedAuth("jwt.value", "session-key", "okhttp/4");

	@RegisterExtension
	final LogCapture logs = new LogCapture();

	private MockRestServiceServer server;

	private CommonApiOtpClient client;

	@BeforeEach
	void setUp() {
		RestClient.Builder builder = RestClient.builder().baseUrl("http://common");
		server = MockRestServiceServer.bindTo(builder).build();
		client = new CommonApiOtpClient(builder.build());
	}

	private static String legacy(int statusCode) {
		return "{\"data\":{},\"statusCode\":" + statusCode + ",\"errorMessage\":\"x\",\"status\":\"y\"}";
	}

	@Test
	void sendForwardsTheCallersLogin() {
		server.expect(requestTo("http://common/beneficiaryConsent/sendConsent")).andExpect(method(HttpMethod.POST))
				.andExpect(header("JwtToken", "jwt.value")).andExpect(header("Authorization", "session-key"))
				.andExpect(header("User-Agent", "okhttp/4"))
				.andExpect(content().json("{\"mobNo\":\"9876543210\"}"))
				.andRespond(withSuccess(legacy(200), MediaType.APPLICATION_JSON));

		assertThatNoException().isThrownBy(() -> client.send(PHONE, AUTH));
		server.verify();
		logs.assertNoneContain(PHONE, "jwt.value", "session-key");
	}

	@Test
	void sendRateLimitedOrFailed() {
		server.expect(requestTo("http://common/beneficiaryConsent/sendConsent"))
				.andRespond(withSuccess(legacy(429), MediaType.APPLICATION_JSON));
		server.expect(requestTo("http://common/beneficiaryConsent/sendConsent"))
				.andRespond(withSuccess(legacy(500), MediaType.APPLICATION_JSON));
		server.expect(requestTo("http://common/beneficiaryConsent/sendConsent")).andRespond(withServerError());
		server.expect(requestTo("http://common/beneficiaryConsent/sendConsent"))
				.andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

		assertCode(() -> client.send(PHONE, AUTH), ErrorCode.RATE_LIMITED);
		assertCode(() -> client.send(PHONE, AUTH), ErrorCode.DEPENDENCY_UNAVAILABLE);
		assertCode(() -> client.send(PHONE, AUTH), ErrorCode.DEPENDENCY_UNAVAILABLE);
		assertCode(() -> client.send(PHONE, AUTH), ErrorCode.DEPENDENCY_UNAVAILABLE);
		logs.assertNoneContain(PHONE);
	}

	@Test
	void validateRightWrongAndError() {
		server.expect(requestTo("http://common/beneficiaryConsent/validateConsent"))
				.andExpect(content().json("{\"mobNo\":\"9876543210\",\"otp\":123456}"))
				.andRespond(withSuccess(legacy(200), MediaType.APPLICATION_JSON));
		server.expect(requestTo("http://common/beneficiaryConsent/validateConsent"))
				.andRespond(withSuccess(legacy(500), MediaType.APPLICATION_JSON));
		server.expect(requestTo("http://common/beneficiaryConsent/validateConsent"))
				.andRespond(withSuccess("", MediaType.APPLICATION_JSON));
		server.expect(requestTo("http://common/beneficiaryConsent/validateConsent"))
				.andRespond(request -> {
					throw new IOException("network");
				});

		assertThat(client.validate(PHONE, "123456", new ForwardedAuth(null, null, null))).isTrue();
		assertThat(client.validate(PHONE, "000000", AUTH)).isFalse();
		assertThat(client.validate(PHONE, "000000", AUTH)).isFalse();
		assertCode(() -> client.validate(PHONE, "123456", AUTH), ErrorCode.DEPENDENCY_UNAVAILABLE);
		logs.assertNoneContain(PHONE, "123456");
	}

	@Test
	void clientConfigUsesTheConfiguredBaseUrl() {
		assertThat(new CommonApiClientConfig().commonApiRestClient(TestFixtures.consentProperties())).isNotNull();
	}

	private static void assertCode(Runnable call, ErrorCode code) {
		assertThatThrownBy(call::run).isInstanceOf(ConsentApiException.class).extracting("errorCode")
				.isEqualTo(code);
	}
}
