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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import com.iemr.consent.config.SecurityProperties;
import com.iemr.consent.support.TestFixtures;
import com.iemr.consent.utils.exception.ErrorCode;

class SessionValidatorTest {

	private static final String KEY = "a1b2c3";

	private static final String SESSION = "{\"userID\":17,\"userName\":\"asha\",\"loginIPAddress\":\"10.0.0.1\"}";

	private StringRedisTemplate redis;

	private ValueOperations<String, String> values;

	@BeforeEach
	@SuppressWarnings("unchecked")
	void setUp() {
		redis = mock(StringRedisTemplate.class);
		values = mock(ValueOperations.class);
		when(redis.opsForValue()).thenReturn(values);
	}

	private SessionValidator validator(boolean ipValidation, boolean extend) {
		SecurityProperties properties = TestFixtures.securityProperties("", ipValidation, extend);
		return new SessionValidator(redis, properties, TestFixtures.objectMapper());
	}

	private static void assertRefused(Runnable call, ErrorCode code) {
		assertThatThrownBy(call::run).isInstanceOf(AuthenticationException.class)
				.extracting(e -> ((AuthenticationException) e).getErrorCode()).isEqualTo(code);
	}

	@Test
	void liveSessionGivesUserAndExtendsExpiry() {
		when(values.get(KEY)).thenReturn(SESSION);

		AuthenticatedUser user = validator(false, true).validate("Bearer " + KEY, "10.9.9.9", ClientType.MOBILE);

		assertThat(user).isEqualTo(new AuthenticatedUser(17L, "asha", ClientType.MOBILE));
		verify(redis).expire(KEY, Duration.ofSeconds(7200));
	}

	@Test
	void keyWithoutBearerPrefixIsUsedAsIs() {
		when(values.get(KEY)).thenReturn(SESSION);

		assertThat(validator(false, false).validate(KEY, "ip", ClientType.WEB).userId()).isEqualTo(17L);
		verify(redis, never()).expire(anyString(), any(Duration.class));
	}

	@Test
	void sessionWithoutUserNameGivesNullName() {
		when(values.get(KEY)).thenReturn("{\"userID\":\"5\"}", "{\"userID\":\"5\",\"userName\":null}");

		assertThat(validator(false, false).validate(KEY, "ip", ClientType.MOBILE).userName()).isNull();
		assertThat(validator(false, false).validate(KEY, "ip", ClientType.MOBILE).userName()).isNull();
	}

	@ParameterizedTest
	@ValueSource(strings = { "", "   ", "Bearer ", "Bearer   " })
	void blankKeyIsSessionExpired(String authorization) {
		assertRefused(() -> validator(false, true).validate(authorization, "ip", ClientType.MOBILE),
				ErrorCode.SESSION_EXPIRED);
	}

	@Test
	void absentSessionIsSessionExpired() {
		assertRefused(() -> validator(false, true).validate(KEY, "ip", ClientType.MOBILE), ErrorCode.SESSION_EXPIRED);
		when(values.get(KEY)).thenReturn(" ");
		assertRefused(() -> validator(false, true).validate(KEY, "ip", ClientType.MOBILE), ErrorCode.SESSION_EXPIRED);
	}

	@ParameterizedTest
	@ValueSource(strings = { "{not json", "[1,2]", "\"text\"" })
	void unparsableSessionIsSessionExpired(String json) {
		when(values.get(KEY)).thenReturn(json);
		assertRefused(() -> validator(false, true).validate(KEY, "ip", ClientType.MOBILE), ErrorCode.SESSION_EXPIRED);
	}

	@Test
	void sessionWithoutUserIdIsUnauthenticated() {
		when(values.get(KEY)).thenReturn("{\"userName\":\"asha\"}");
		assertRefused(() -> validator(false, false).validate(KEY, "ip", ClientType.MOBILE), ErrorCode.UNAUTHENTICATED);
	}

	@Test
	void ipValidationRefusesAnotherIpAndAcceptsTheLoginIp() {
		when(values.get(KEY)).thenReturn(SESSION);

		assertRefused(() -> validator(true, true).validate(KEY, "10.9.9.9", ClientType.MOBILE),
				ErrorCode.SESSION_EXPIRED);
		assertThat(validator(true, true).validate(KEY, "10.0.0.1", ClientType.MOBILE).userId()).isEqualTo(17L);
	}

	@Test
	void ipValidationWithoutStoredIpIsSessionExpired() {
		when(values.get(KEY)).thenReturn("{\"userID\":17}");
		assertRefused(() -> validator(true, true).validate(KEY, "10.0.0.1", ClientType.MOBILE),
				ErrorCode.SESSION_EXPIRED);
	}
}
