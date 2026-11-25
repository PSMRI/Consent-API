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

package com.iemr.consent.config;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Connects to the Redis instance shared with Common-API (login sessions and the logout
 * denylist).
 *
 * <p>The connection is built from Common-API's {@code spring.redis.*} keys, as in Common-API's
 * {@code IEMRApplBeans#connectionFactory}. Spring Boot 3.2 auto-configuration only reads
 * {@code spring.data.redis.*}, so without this bean {@code REDIS_HOST} would be silently
 * ignored.</p>
 *
 * <p>Two templates match how Common-API writes each kind of key:</p>
 * <ul>
 * <li>{@code stringRedisTemplate}: plain UTF-8 keys and values, for login sessions
 * (Common-API's {@code RedisStorage} uses {@code key.getBytes()}).</li>
 * <li>{@code redisTemplate}: Spring's default JDK key serialisation, for the
 * {@code denied_<jti>} denylist. Common-API's {@code RedisTemplate<String, Object>} sets no key
 * serializer, so its denylist keys are JDK-serialised strings; a plain-string lookup would
 * never find a logged-out token.</li>
 * </ul>
 */
@Configuration
public class RedisConfig {

	@Bean
	public LettuceConnectionFactory redisConnectionFactory(@Value("${spring.redis.host}") String host,
			@Value("${spring.redis.port}") int port, @Value("${spring.redis.password:}") String password,
			ConsentProperties properties) {
		RedisStandaloneConfiguration server = new RedisStandaloneConfiguration(host, port);
		if (!password.isBlank()) {
			server.setPassword(RedisPassword.of(password));
		}
		LettuceClientConfiguration client = LettuceClientConfiguration.builder()
				.commandTimeout(Duration.ofMillis(properties.redis().timeoutMs())).build();
		return new LettuceConnectionFactory(server, client);
	}

	@Bean
	public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
		return new StringRedisTemplate(connectionFactory);
	}

	@Bean
	public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
		RedisTemplate<String, Object> template = new RedisTemplate<>();
		template.setConnectionFactory(connectionFactory);
		return template;
	}
}
