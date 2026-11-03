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

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Date;

import org.springframework.stereotype.Component;

import com.iemr.consent.config.SecurityProperties;
import com.iemr.consent.utils.exception.ErrorCode;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/**
 * Validates an AMRIT web login token exactly as Common-API's {@code JwtUtil#validateToken}:
 * HS signature with the shared {@code jwt.secret}, expiry, and the logout denylist.
 *
 * <p>Unlike Common-API it tells an expired token ({@code SESSION_EXPIRED}) apart from an
 * invalid one ({@code UNAUTHENTICATED}) (clarification 3). The token and parser exception
 * messages are never logged (research R13).</p>
 */
@Component
public class JwtValidator {

	static final String USER_ID_CLAIM = "userId";

	private final JwtParser parser;

	private final TokenDenylist tokenDenylist;

	public JwtValidator(SecurityProperties securityProperties, TokenDenylist tokenDenylist, Clock clock) {
		byte[] secret = securityProperties.getJwtSecret().getBytes(StandardCharsets.UTF_8);
		this.parser = Jwts.parser().verifyWith(Keys.hmacShaKeyFor(secret))
				.clock(() -> Date.from(clock.instant())).build();
		this.tokenDenylist = tokenDenylist;
	}

	/**
	 * @return the web user named by the token
	 * @throws AuthenticationException {@code SESSION_EXPIRED} if expired, otherwise
	 *                                 {@code UNAUTHENTICATED}
	 */
	public AuthenticatedUser validate(String token) {
		Claims claims;
		try {
			claims = parser.parseSignedClaims(token).getPayload();
		} catch (ExpiredJwtException e) {
			throw new AuthenticationException(ErrorCode.SESSION_EXPIRED);
		} catch (JwtException | IllegalArgumentException e) {
			throw new AuthenticationException(ErrorCode.UNAUTHENTICATED);
		}
		String jti = claims.getId();
		if (jti != null && tokenDenylist.isDenylisted(jti)) {
			throw new AuthenticationException(ErrorCode.UNAUTHENTICATED);
		}
		return AuthenticatedUser.of(claims.get(USER_ID_CLAIM), claims.getSubject(), ClientType.WEB);
	}
}
