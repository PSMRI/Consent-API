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

package com.iemr.consent.support;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;

import javax.crypto.SecretKey;

import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/** Synthetic AMRIT-style login tokens, built like Common-API's {@code JwtUtil}. */
public final class TestTokens {

	/** Synthetic HS256 secret used only by tests. */
	public static final String SECRET = "test-only-synthetic-jwt-secret-0123456789abcdef";

	public static final String OTHER_SECRET = "a-different-synthetic-jwt-secret-9876543210fedcba";

	public static final String MALFORMED = "not-a-jwt";

	private TestTokens() {
	}

	/** A valid web token for {@code userId}, issued now, expiring in one hour. */
	public static String valid(String userId, String userName, String jti) {
		return builder(SECRET, userId, userName, jti, Duration.ofHours(1)).compact();
	}

	public static String expired(String userId) {
		return builder(SECRET, userId, "u" + userId, "jti-expired", Duration.ofHours(-1)).compact();
	}

	public static String badSignature(String userId) {
		return builder(OTHER_SECRET, userId, "u" + userId, "jti-bad", Duration.ofHours(1)).compact();
	}

	/** Token without a {@code userId} claim. */
	public static String withoutUserId() {
		return Jwts.builder().subject("someone").id("jti-none").issuedAt(Date.from(TestFixtures.NOW))
				.expiration(Date.from(TestFixtures.NOW.plus(Duration.ofHours(1)))).signWith(key(SECRET)).compact();
	}

	private static JwtBuilder builder(String secret, String userId, String userName, String jti, Duration validity) {
		JwtBuilder builder = Jwts.builder().subject(userName).claim("userId", userId)
				.claim("token_type", "access").issuedAt(Date.from(TestFixtures.NOW.minus(Duration.ofHours(2))))
				.expiration(Date.from(TestFixtures.NOW.plus(validity))).signWith(key(secret));
		return jti != null ? builder.id(jti) : builder;
	}

	private static SecretKey key(String secret) {
		return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
	}
}
