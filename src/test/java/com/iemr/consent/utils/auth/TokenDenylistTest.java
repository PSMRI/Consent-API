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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.RedisTemplate;

class TokenDenylistTest {

	@SuppressWarnings("unchecked")
	private final RedisTemplate<String, Object> redis = mock(RedisTemplate.class);

	private final TokenDenylist denylist = new TokenDenylist(redis);

	@Test
	void checksTheCommonApiKey() {
		when(redis.hasKey("denied_jti-1")).thenReturn(true);
		when(redis.hasKey("denied_jti-2")).thenReturn(false);

		assertThat(denylist.isDenylisted("jti-1")).isTrue();
		assertThat(denylist.isDenylisted("jti-2")).isFalse();
		assertThat(denylist.isDenylisted("jti-3")).isFalse();
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = " ")
	void blankJtiIsNeverDenylisted(String jti) {
		assertThat(denylist.isDenylisted(jti)).isFalse();
		verifyNoInteractions(redis);
	}

	/**
	 * Common-API fails open here (treats the token as not logged out). Consent-API must not:
	 * accepting a logged-out token would be a security gap, so the error propagates and the
	 * filter answers 503 DEPENDENCY_UNAVAILABLE.
	 */
	@Test
	void redisFailurePropagatesInsteadOfFailingOpen() {
		when(redis.hasKey("denied_jti-1")).thenThrow(new RedisConnectionFailureException("down"));

		assertThatThrownBy(() -> denylist.isDenylisted("jti-1")).isInstanceOf(RedisConnectionFailureException.class);
	}
}
