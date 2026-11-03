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

import java.time.Duration;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.iemr.consent.config.SecurityProperties;
import com.iemr.consent.utils.exception.ErrorCode;

/**
 * Checks the Redis login session named by the {@code Authorization} header, as Common-API's
 * {@code HTTPRequestInterceptor} / {@code Validator#checkKeyExists} do.
 *
 * <p>The key is the header value (a {@code Bearer } prefix is removed). A missing or unreadable
 * session, or a login from another IP when {@code enableIPValidation=true}, is
 * {@code SESSION_EXPIRED} (research R1). A found session's lifetime is extended by
 * {@code iemr.session.expiry.time} when {@code iemr.extend.expiry.time=true}.</p>
 */
@Component
public class SessionValidator {

	private static final String BEARER = "Bearer ";

	private final StringRedisTemplate redisTemplate;

	private final SecurityProperties securityProperties;

	private final ObjectMapper objectMapper;

	public SessionValidator(StringRedisTemplate redisTemplate, SecurityProperties securityProperties,
			ObjectMapper objectMapper) {
		this.redisTemplate = redisTemplate;
		this.securityProperties = securityProperties;
		this.objectMapper = objectMapper;
	}

	/**
	 * @param authorization value of the {@code Authorization} header
	 * @param clientIp      {@code X-FORWARDED-FOR}, else the remote address
	 * @param clientType    client type for the returned user
	 * @return the user stored in the session ({@code userID}, {@code userName})
	 */
	public AuthenticatedUser validate(String authorization, String clientIp, ClientType clientType) {
		String key = authorization.startsWith(BEARER) ? authorization.substring(BEARER.length())
				: authorization;
		key = key.trim();
		if (key.isEmpty()) {
			throw new AuthenticationException(ErrorCode.SESSION_EXPIRED);
		}
		String sessionJson = redisTemplate.opsForValue().get(key);
		if (sessionJson == null || sessionJson.isBlank()) {
			throw new AuthenticationException(ErrorCode.SESSION_EXPIRED);
		}
		JsonNode session = parse(sessionJson);
		if (securityProperties.isIpValidationEnabled()
				&& !session.path("loginIPAddress").asText("").equals(clientIp)) {
			throw new AuthenticationException(ErrorCode.SESSION_EXPIRED);
		}
		if (securityProperties.isExtendSessionExpiry()) {
			redisTemplate.expire(key, Duration.ofSeconds(securityProperties.getSessionExpirySeconds()));
		}
		JsonNode userName = session.get("userName");
		return AuthenticatedUser.of(session.path("userID").asText(""),
				userName == null || userName.isNull() ? null : userName.asText(), clientType);
	}

	private JsonNode parse(String sessionJson) {
		try {
			JsonNode node = objectMapper.readTree(sessionJson);
			if (!node.isObject()) {
				throw new AuthenticationException(ErrorCode.SESSION_EXPIRED);
			}
			return node;
		} catch (JsonProcessingException e) {
			throw new AuthenticationException(ErrorCode.SESSION_EXPIRED);
		}
	}
}
