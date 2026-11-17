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

package com.iemr.consent.service.notify;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.io.IOException;
import java.net.SocketTimeoutException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.iemr.consent.config.CommonApiClientConfig;
import com.iemr.consent.support.LogCapture;
import com.iemr.consent.support.TestFixtures;
import com.iemr.consent.utils.http.ForwardedAuth;

/** Handing the consent SMS to Common-API's /sms/sendSMS (feature 004, R5). */
class CommonApiSmsClientTest {

	private static final String PHONE = "9876543210";

	private static final String TEXT = "Consent ID: c-1. Grievance: Officer 1800";

	private static final ForwardedAuth AUTH = new ForwardedAuth("jwt.value", "session-key", "Mozilla");

	@RegisterExtension
	final LogCapture logs = new LogCapture();

	private MockRestServiceServer server;

	private CommonApiSmsClient client;

	@BeforeEach
	void setUp() {
		RestClient.Builder builder = RestClient.builder().baseUrl("http://common");
		server = MockRestServiceServer.bindTo(builder).build();
		client = new CommonApiSmsClient(builder.build());
	}

	private static String legacy(int statusCode) {
		return "{\"statusCode\":" + statusCode + ",\"status\":\"x\",\"errorMessage\":\"x\",\"data\":null}";
	}

	@Test
	@DisplayName("C1: one free-text SMS to the given number, with the caller's login forwarded")
	void sendsOneFreeTextSms() {
		server.expect(requestTo("http://common/sms/sendSMS")).andExpect(method(HttpMethod.POST))
				.andExpect(header("JwtToken", "jwt.value")).andExpect(header("Authorization", "session-key"))
				.andExpect(content().json("[{\"smsText\":\"" + TEXT + "\",\"alternateNo\":\"9876543210\","
						+ "\"beneficiaryRegID\":1001,\"providerServiceMapID\":1,\"createdBy\":\"worker42\","
						+ "\"is1097\":false}]", true))
				.andRespond(withSuccess(legacy(200), MediaType.APPLICATION_JSON));

		assertThat(client.send(TEXT, PHONE, 1001L, 1, "worker42", AUTH)).isTrue();
		server.verify();
		logs.assertNoneContain(PHONE, TEXT, "jwt.value", "session-key");
	}

	@Test
	@DisplayName("C1: refusal, server error, empty answer or time-out all mean not queued")
	void failuresMeanNotQueued() {
		server.expect(requestTo("http://common/sms/sendSMS"))
				.andRespond(withSuccess(legacy(5000), MediaType.APPLICATION_JSON));
		server.expect(requestTo("http://common/sms/sendSMS")).andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
		server.expect(requestTo("http://common/sms/sendSMS")).andRespond(withSuccess("", MediaType.APPLICATION_JSON));
		server.expect(requestTo("http://common/sms/sendSMS")).andRespond(withServerError());
		server.expect(requestTo("http://common/sms/sendSMS")).andRespond(request -> {
			throw new SocketTimeoutException("Read timed out");
		});
		server.expect(requestTo("http://common/sms/sendSMS")).andRespond(request -> {
			throw new IOException("network");
		});

		for (int i = 0; i < 6; i++) {
			assertThat(client.send(TEXT, PHONE, null, null, "worker42", AUTH)).isFalse();
		}
		logs.assertNoneContain(PHONE, TEXT);
	}

	@Test
	void smsClientBeanUsesItsOwnTimeout() {
		assertThat(new CommonApiClientConfig().commonApiSmsRestClient(TestFixtures.consentProperties())).isNotNull();
	}
}
