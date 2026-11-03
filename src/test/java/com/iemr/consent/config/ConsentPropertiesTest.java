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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.validation.BindValidationException;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class ConsentPropertiesTest {

	@Configuration
	@EnableConfigurationProperties(ConsentProperties.class)
	static class Config {
	}

	private final ApplicationContextRunner runner = new ApplicationContextRunner()
			.withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class))
			.withUserConfiguration(Config.class)
			.withPropertyValues("consent.evidence.storage-dir=/tmp/evidence",
					"consent.common-api.base-url=http://common-api", "consent.encryption.key=a2V5");

	@Test
	void defaults() {
		runner.run(context -> {
			ConsentProperties properties = context.getBean(ConsentProperties.class);
			assertThat(properties.request().maxBodyBytes()).isEqualTo(1048576L);
			assertThat(properties.health().checkTimeoutMs()).isEqualTo(1500L);
			assertThat(properties.redis().timeoutMs()).isEqualTo(1500L);
			assertThat(properties.rateLimit().retryAfterSeconds()).isEqualTo(60L);
			assertThat(properties.evidence().maxBytes()).isEqualTo(10485760L);
			assertThat(properties.evidence().voiceMinSeconds()).isEqualTo(30);
			assertThat(properties.evidence().voiceMaxSeconds()).isEqualTo(60);
			assertThat(properties.evidence().allowedContentTypes()).contains("audio/webm", "image/png");
			assertThat(properties.capture().maxSkewMinutes()).isEqualTo(1440L);
			assertThat(properties.commonApi().timeoutMs()).isEqualTo(3000);
			assertThat(properties.notice().audioBaseUrl()).isEmpty();
			assertThat(properties.otp().ttlSeconds()).isEqualTo(300L);
			assertThat(properties.otp().retentionMinutes()).isEqualTo(30L);
			assertThat(properties.otp().maxAttempts()).isEqualTo(5);
			assertThat(properties.encryption().key()).isEqualTo("a2V5");
			assertThat(properties.accessGrant().maxDays()).isEqualTo(30);
			assertThat(properties.accessGrant().replayHours()).isEqualTo(24);
		});
	}

	@ParameterizedTest
	@ValueSource(strings = { "consent.evidence.storage-dir", "consent.common-api.base-url", "consent.encryption.key" })
	void requiredSettingsWithoutDefaultFailWhenBlank(String key) {
		runner.withPropertyValues(key + "=").run(context -> assertThat(context).hasFailed().getFailure()
				.hasRootCauseInstanceOf(BindValidationException.class));
	}

	@Test
	void bindsConfiguredValues() {
		runner.withPropertyValues("consent.request.max-body-bytes=10", "consent.health.check-timeout-ms=100",
				"consent.redis.timeout-ms=200", "consent.rate-limit.retry-after-seconds=5").run(context -> {
					ConsentProperties properties = context.getBean(ConsentProperties.class);
					assertThat(properties.request().maxBodyBytes()).isEqualTo(10L);
					assertThat(properties.health().checkTimeoutMs()).isEqualTo(100L);
					assertThat(properties.redis().timeoutMs()).isEqualTo(200L);
					assertThat(properties.rateLimit().retryAfterSeconds()).isEqualTo(5L);
				});
	}

	@ParameterizedTest
	@ValueSource(strings = { "consent.request.max-body-bytes=0", "consent.health.check-timeout-ms=99",
		"consent.health.check-timeout-ms=1901", "consent.redis.timeout-ms=0",
		"consent.rate-limit.retry-after-seconds=0", "consent.evidence.voice-min-seconds=0",
		"consent.otp.max-attempts=0", "consent.otp.ttl-seconds=10", "consent.capture.max-skew-minutes=0",
		"consent.access-grant.max-days=0", "consent.access-grant.replay-hours=0" })
	void rejectsOutOfRangeValues(String setting) {
		runner.withPropertyValues(setting).run(context -> assertThat(context).hasFailed().getFailure()
				.hasRootCauseInstanceOf(BindValidationException.class));
	}
}
