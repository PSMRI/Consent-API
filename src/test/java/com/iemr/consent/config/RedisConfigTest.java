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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.JdkSerializationRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import com.iemr.consent.support.TestFixtures;

class RedisConfigTest {

	private final RedisConfig config = new RedisConfig();

	@Test
	void connectsToConfiguredHostPortPasswordAndTimeout() {
		LettuceConnectionFactory factory = config.redisConnectionFactory("redis.internal", 6380, "pw",
				TestFixtures.consentProperties());

		assertThat(factory.getHostName()).isEqualTo("redis.internal");
		assertThat(factory.getPort()).isEqualTo(6380);
		assertThat(factory.getStandaloneConfiguration().getPassword().get()).isEqualTo("pw".toCharArray());
		assertThat(factory.getClientConfiguration().getCommandTimeout()).isEqualTo(Duration.ofMillis(1500));
	}

	@Test
	void blankPasswordMeansNoPassword() {
		LettuceConnectionFactory factory = config.redisConnectionFactory("localhost", 6379, "",
				TestFixtures.consentProperties());

		assertThat(factory.getStandaloneConfiguration().getPassword().isPresent()).isFalse();
	}

	@Test
	void sessionTemplateUsesPlainStringKeys() {
		StringRedisTemplate template = config.stringRedisTemplate(mock(RedisConnectionFactory.class));

		assertThat(template.getKeySerializer()).isInstanceOf(StringRedisSerializer.class);
		assertThat(template.getValueSerializer()).isInstanceOf(StringRedisSerializer.class);
	}

	@Test
	void denylistTemplateUsesCommonApiDefaultJdkKeySerialisation() {
		RedisTemplate<String, Object> template = config.redisTemplate(mock(RedisConnectionFactory.class));
		template.afterPropertiesSet();

		assertThat(template.getKeySerializer()).isInstanceOf(JdkSerializationRedisSerializer.class);
	}
}
