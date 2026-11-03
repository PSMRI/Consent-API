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

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Reads the logout denylist that Common-API writes at logout ({@code denied_<jti>}).
 *
 * <p>Mirrors Common-API's {@code TokenDenylist#isTokenDenylisted}, using the same key format and
 * the same {@code RedisTemplate} key serialisation (see {@code RedisConfig}). Unlike Common-API
 * it does <strong>not</strong> fail open: a Redis error propagates, and the authentication
 * filter answers 503 {@code DEPENDENCY_UNAVAILABLE}. Treating the token as "not logged out" would
 * let a logged-out token reach consent records (plan deviation 4).</p>
 */
@Component
public class TokenDenylist {

	static final String PREFIX = "denied_";

	private final RedisTemplate<String, Object> redisTemplate;

	public TokenDenylist(RedisTemplate<String, Object> redisTemplate) {
		this.redisTemplate = redisTemplate;
	}

	/** {@code true} if the token with this {@code jti} was logged out; a blank jti is never denylisted. */
	public boolean isDenylisted(String jti) {
		if (jti == null || jti.isBlank()) {
			return false;
		}
		return Boolean.TRUE.equals(redisTemplate.hasKey(PREFIX + jti));
	}
}
