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
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.iemr.consent.model.otp.OtpChallengeView;
import com.iemr.consent.model.otp.OtpReason;
import com.iemr.consent.service.otp.OtpChallengeStore.SendReplay;
import com.iemr.consent.support.TestFixtures;

class OtpChallengeStoreTest {

	private static final OtpChallenge CHALLENGE = new OtpChallenge("c1", OtpReason.CONSENT, "enc-phone", "phone-hash",
			"******3210", null, 42L, Instant.parse("2026-10-29T10:05:00Z"), null, false, 0);

	private StringRedisTemplate redis;

	private ValueOperations<String, String> values;

	private OtpChallengeStore store;

	@BeforeEach
	@SuppressWarnings("unchecked")
	void setUp() {
		redis = mock(StringRedisTemplate.class);
		values = mock(ValueOperations.class);
		when(redis.opsForValue()).thenReturn(values);
		store = new OtpChallengeStore(redis, TestFixtures.objectMapper(), TestFixtures.consentProperties());
	}

	@Test
	void createKeepsTheChallengeForTheRetentionPeriodWithoutThePlainPhone() {
		store.create(CHALLENGE);

		ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
		verify(values).set(eq("consent:otp:challenge:c1"), json.capture(), eq(Duration.ofMinutes(30)));
		assertThat(json.getValue()).contains("enc-phone").contains("phone-hash").doesNotContain("9876543210");
	}

	@Test
	void findRoundTripsAndMissingIsEmpty() {
		ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
		store.create(CHALLENGE);
		verify(values).set(anyString(), json.capture(), eq(Duration.ofMinutes(30)));
		when(values.get("consent:otp:challenge:c1")).thenReturn(json.getValue());

		assertThat(store.find("c1")).contains(CHALLENGE);
		assertThat(store.find("missing")).isEmpty();
	}

	@Test
	void updateKeepsTheRemainingLifetime() {
		when(redis.getExpire("consent:otp:challenge:c1", TimeUnit.SECONDS)).thenReturn(120L, -1L, null);

		store.update(CHALLENGE.verified(TestFixtures.NOW));
		verify(values).set(eq("consent:otp:challenge:c1"), anyString(), eq(Duration.ofSeconds(120)));
		store.update(CHALLENGE.markedUsed());
		store.update(CHALLENGE.failedAttempt());
		verify(values, org.mockito.Mockito.times(2)).set(eq("consent:otp:challenge:c1"), anyString(),
				eq(Duration.ofMinutes(30)));
	}

	@Test
	void sendReplayRoundTrips() {
		SendReplay replay = new SendReplay("h", new OtpChallengeView("c1", "******3210", TestFixtures.NOW));
		ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
		store.saveSendReplay(42L, "key", replay);
		verify(values).set(eq("consent:idem:otp:42:key"), json.capture(), eq(Duration.ofMinutes(30)));
		when(values.get("consent:idem:otp:42:key")).thenReturn(json.getValue());

		assertThat(store.findSendReplay(42L, "key")).contains(replay);
	}

	@Test
	void corruptOrUnwritableRecordsFailClearly() throws Exception {
		when(values.get("consent:otp:challenge:bad")).thenReturn("{not json");
		assertThatIllegalStateException().isThrownBy(() -> store.find("bad"));

		ObjectMapper broken = mock(ObjectMapper.class);
		when(broken.writeValueAsString(org.mockito.ArgumentMatchers.any()))
				.thenThrow(new JsonProcessingException("x") {
					private static final long serialVersionUID = 1L;
				});
		OtpChallengeStore failing = new OtpChallengeStore(redis, broken, TestFixtures.consentProperties());
		assertThatIllegalStateException().isThrownBy(() -> failing.create(CHALLENGE));
	}

	@Test
	void challengeStringHidesThePhone() {
		assertThat(CHALLENGE.toString()).doesNotContain("enc-phone").doesNotContain("phone-hash");
		assertThat(CHALLENGE.failedAttempt().attempts()).isEqualTo(1);
	}
}
