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

package com.iemr.consent.service.grant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.iemr.consent.service.grant.GrantReplayStore.Replay;
import com.iemr.consent.support.TestFixtures;

class GrantReplayStoreTest {

	private ValueOperations<String, String> values;

	private StringRedisTemplate redis;

	@BeforeEach
	@SuppressWarnings("unchecked")
	void setUp() {
		redis = mock(StringRedisTemplate.class);
		values = mock(ValueOperations.class);
		when(redis.opsForValue()).thenReturn(values);
	}

	private GrantReplayStore store(ObjectMapper mapper) {
		return new GrantReplayStore(redis, mapper, TestFixtures.consentProperties());
	}

	@Test
	void savesForTheConfiguredHoursAndReadsBack() {
		GrantReplayStore store = store(TestFixtures.objectMapper());
		store.save(42L, "k", new Replay("hash-1", "g-1"));
		verify(values).set("consent:idem:grant:42:k", "{\"requestHash\":\"hash-1\",\"grantId\":\"g-1\"}",
				Duration.ofHours(24));

		when(values.get("consent:idem:grant:42:k")).thenReturn("{\"requestHash\":\"hash-1\",\"grantId\":\"g-1\"}");
		assertThat(store.find(42L, "k")).contains(new Replay("hash-1", "g-1"));
		assertThat(store.find(42L, "other")).isEmpty();
	}

	@Test
	void unreadableOrUnwritableRecordsFailLoudly() throws Exception {
		when(values.get(anyString())).thenReturn("not json");
		assertThatThrownBy(() -> store(TestFixtures.objectMapper()).find(42L, "k"))
				.isInstanceOf(IllegalStateException.class);

		ObjectMapper broken = mock(ObjectMapper.class);
		when(broken.writeValueAsString(any())).thenThrow(new JsonProcessingException("boom") {
			private static final long serialVersionUID = 1L;
		});
		assertThatThrownBy(() -> store(broken).save(42L, "k", new Replay("h", "g")))
				.isInstanceOf(IllegalStateException.class);
	}
}
