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

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.iemr.consent.config.ConsentProperties;
import com.iemr.consent.model.otp.OtpChallengeView;

/**
 * Redis storage of OTP challenges and of OTP-send replays (research R1, R4). Records live for
 * {@code consent.otp.retention-minutes}, longer than the code's expiry, so a save shortly after a
 * late verify still finds its challenge.
 */
@Component
public class OtpChallengeStore {

	static final String CHALLENGE_PREFIX = "consent:otp:challenge:";

	static final String REPLAY_PREFIX = "consent:idem:otp:";

	private final StringRedisTemplate redis;

	private final ObjectMapper objectMapper;

	private final Duration retention;

	public OtpChallengeStore(StringRedisTemplate redis, ObjectMapper objectMapper, ConsentProperties properties) {
		this.redis = redis;
		this.objectMapper = objectMapper;
		this.retention = Duration.ofMinutes(properties.otp().retentionMinutes());
	}

	/** A stored OTP send, replayed for a repeat with the same request key. */
	public record SendReplay(String requestHash, OtpChallengeView response) {
	}

	public void create(OtpChallenge challenge) {
		redis.opsForValue().set(CHALLENGE_PREFIX + challenge.challengeId(), write(challenge), retention);
	}

	public Optional<OtpChallenge> find(String challengeId) {
		return read(redis.opsForValue().get(CHALLENGE_PREFIX + challengeId), OtpChallenge.class);
	}

	/** Updates a challenge, keeping its remaining lifetime. */
	public void update(OtpChallenge challenge) {
		String key = CHALLENGE_PREFIX + challenge.challengeId();
		Long seconds = redis.getExpire(key, TimeUnit.SECONDS);
		Duration ttl = seconds != null && seconds > 0 ? Duration.ofSeconds(seconds) : retention;
		redis.opsForValue().set(key, write(challenge), ttl);
	}

	public Optional<SendReplay> findSendReplay(Long userId, String requestKey) {
		return read(redis.opsForValue().get(replayKey(userId, requestKey)), SendReplay.class);
	}

	public void saveSendReplay(Long userId, String requestKey, SendReplay replay) {
		redis.opsForValue().set(replayKey(userId, requestKey), write(replay), retention);
	}

	private static String replayKey(Long userId, String requestKey) {
		return REPLAY_PREFIX + userId + ":" + requestKey;
	}

	private String write(Object value) {
		try {
			return objectMapper.writeValueAsString(value);
		} catch (JsonProcessingException e) {
			throw new IllegalStateException("OTP record could not be written", e);
		}
	}

	private <T> Optional<T> read(String json, Class<T> type) {
		if (json == null) {
			return Optional.empty();
		}
		try {
			return Optional.of(objectMapper.readValue(json, type));
		} catch (JsonProcessingException e) {
			throw new IllegalStateException("OTP record could not be read", e);
		}
	}
}
