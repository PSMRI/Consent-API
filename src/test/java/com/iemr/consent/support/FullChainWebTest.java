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

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.time.Clock;

import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import com.iemr.consent.config.ConsentProperties;
import com.iemr.consent.config.SecurityProperties;
import com.iemr.consent.utils.auth.JwtValidator;
import com.iemr.consent.utils.auth.PublicPaths;
import com.iemr.consent.utils.auth.SessionValidator;
import com.iemr.consent.utils.cors.OriginPolicy;
import com.iemr.consent.utils.exception.ErrorResponseWriter;

/**
 * {@code @WebMvcTest} of the test-only {@link SampleController} behind the real filter chain
 * ({@code WebConfig} order: trace id, request log, origin, size, authentication), the real
 * {@code GlobalExceptionHandler} and a fixed clock. Redis is mocked by the test class.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@WebMvcTest(controllers = SampleController.class, properties = { "jwt.secret=" + TestTokens.SECRET,
	"cors.allowed-origins=" + TestFixtures.ALLOWED_ORIGINS, "consent.request.max-body-bytes=64",
	"consent.evidence.storage-dir=" + TestFixtures.STORAGE_DIR,
	"consent.common-api.base-url=" + TestFixtures.COMMON_API_URL,
	"consent.encryption.key=" + TestFixtures.ENCRYPTION_KEY })
@Import({ SampleController.class, ErrorResponseWriter.class, OriginPolicy.class, PublicPaths.class,
	JwtValidator.class, SessionValidator.class, SecurityProperties.class, FullChainWebTest.SliceConfig.class })
public @interface FullChainWebTest {

	/** Fixed clock and the {@code consent.*} settings. */
	@TestConfiguration
	@EnableConfigurationProperties(ConsentProperties.class)
	class SliceConfig {

		@Bean
		Clock clock() {
			return TestFixtures.CLOCK;
		}
	}
}
