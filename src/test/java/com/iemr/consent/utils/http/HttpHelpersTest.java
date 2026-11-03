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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import jakarta.servlet.http.Cookie;

class HttpHelpersTest {

	@Test
	void clientIpPrefersForwardedFor() {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.setRemoteAddr("10.0.0.1");
		assertThat(ClientIp.of(request)).isEqualTo("10.0.0.1");
		request.addHeader("X-FORWARDED-FOR", "203.0.113.9");
		assertThat(ClientIp.of(request)).isEqualTo("203.0.113.9");
	}

	@Test
	void forwardedAuthPrefersTheCookieAndCopiesOnlyPresentValues() {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.setCookies(new Cookie("other", "x"), new Cookie("Jwttoken", "cookie.jwt"));
		request.addHeader("JwtToken", "header.jwt");
		request.addHeader("Authorization", "session");
		request.addHeader("User-Agent", "okhttp/4");

		ForwardedAuth auth = ForwardedAuth.from(request);
		HttpHeaders headers = new HttpHeaders();
		auth.applyTo(headers);

		assertThat(headers.getFirst("JwtToken")).isEqualTo("cookie.jwt");
		assertThat(headers.getFirst("Authorization")).isEqualTo("session");
		assertThat(headers.getFirst("User-Agent")).isEqualTo("okhttp/4");
		assertThat(auth.toString()).doesNotContain("cookie.jwt").doesNotContain("session");

		MockHttpServletRequest bare = new MockHttpServletRequest();
		bare.addHeader("JwtToken", "header.jwt");
		assertThat(ForwardedAuth.from(bare).jwt()).isEqualTo("header.jwt");
		HttpHeaders none = new HttpHeaders();
		ForwardedAuth.from(new MockHttpServletRequest()).applyTo(none);
		assertThat(none).isEmpty();
	}

	@Test
	void afterCommitRunsNowWithoutATransactionAndLaterWithOne() {
		AtomicInteger runs = new AtomicInteger();
		AfterCommit.run(runs::incrementAndGet);
		assertThat(runs).hasValue(1);

		TransactionSynchronizationManager.initSynchronization();
		try {
			AfterCommit.run(runs::incrementAndGet);
			assertThat(runs).hasValue(1);
			TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
		} finally {
			TransactionSynchronizationManager.clearSynchronization();
		}
		assertThat(runs).hasValue(2);
	}
}
