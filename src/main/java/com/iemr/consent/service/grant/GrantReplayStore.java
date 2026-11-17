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

import java.time.Duration;
import java.util.Optional;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.iemr.consent.config.ConsentProperties;

/**
 * Redis record of approve and revoke request keys (research R2), kept for
 * {@code consent.access-grant.replay-hours}. Written after commit; if it is lost, a retry meets the grant's
 * new state and gets {@code STATE_CONFLICT}, so the grant still changes only once.
 */
@Component
public class GrantReplayStore {

	static final String PREFIX = "consent:idem:grant:";

	private final StringRedisTemplate redis;

	private final ObjectMapper objectMapper;

	private final Duration ttl;

	public GrantReplayStore(StringRedisTemplate redis, ObjectMapper objectMapper, ConsentProperties properties) {
		this.redis = redis;
		this.objectMapper = objectMapper;
		this.ttl = Duration.ofHours(properties.accessGrant().replayHours());
	}

	/** An approve or revoke already done with a request key. */
	public record Replay(String requestHash, String grantId) {
	}

	public Optional<Replay> find(Long userId, String requestKey) {
		String json = redis.opsForValue().get(key(userId, requestKey));
		if (json == null) {
			return Optional.empty();
		}
		try {
			return Optional.of(objectMapper.readValue(json, Replay.class));
		} catch (JsonProcessingException e) {
			throw new IllegalStateException("Grant replay record could not be read", e);
		}
	}

	public void save(Long userId, String requestKey, Replay replay) {
		try {
			redis.opsForValue().set(key(userId, requestKey), objectMapper.writeValueAsString(replay), ttl);
		} catch (JsonProcessingException e) {
			throw new IllegalStateException("Grant replay record could not be written", e);
		}
	}

	private static String key(Long userId, String requestKey) {
		return PREFIX + userId + ":" + requestKey;
	}
}
