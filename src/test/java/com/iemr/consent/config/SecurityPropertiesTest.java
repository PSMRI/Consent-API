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
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import com.iemr.consent.support.TestTokens;

class SecurityPropertiesTest {

	private final ApplicationContextRunner runner = new ApplicationContextRunner()
			.withBean(SecurityProperties.class);

	@Test
	void bindsCommonApiKeys() {
		runner.withPropertyValues("jwt.secret=" + TestTokens.SECRET,
				"cors.allowed-origins= http://localhost:4200 , https://*.example.org ,",
				"iemr.session.expiry.time=600", "iemr.extend.expiry.time=false", "enableIPValidation=true")
				.run(context -> {
					SecurityProperties properties = context.getBean(SecurityProperties.class);
					assertThat(properties.getJwtSecret()).isEqualTo(TestTokens.SECRET);
					assertThat(properties.getAllowedOrigins())
							.containsExactly("http://localhost:4200", "https://*.example.org");
					assertThat(properties.getSessionExpirySeconds()).isEqualTo(600);
					assertThat(properties.isExtendSessionExpiry()).isFalse();
					assertThat(properties.isIpValidationEnabled()).isTrue();
					assertThat(properties.toString()).doesNotContain(TestTokens.SECRET);
				});
	}

	@Test
	void defaultsMatchCommonApiAndMissingOriginsGiveEmptyList() {
		runner.withPropertyValues("jwt.secret=" + TestTokens.SECRET).run(context -> {
			SecurityProperties properties = context.getBean(SecurityProperties.class);
			assertThat(properties.getAllowedOrigins()).isEmpty();
			assertThat(properties.getSessionExpirySeconds()).isEqualTo(7200);
			assertThat(properties.isExtendSessionExpiry()).isTrue();
			assertThat(properties.isIpValidationEnabled()).isFalse();
		});
	}

	@Test
	void emptyOriginsGiveEmptyList() {
		runner.withPropertyValues("jwt.secret=" + TestTokens.SECRET, "cors.allowed-origins=")
				.run(context -> assertThat(context.getBean(SecurityProperties.class).getAllowedOrigins()).isEmpty());
	}

	@Test
	void missingSecretStopsStartUpNamingOnlyTheKey() {
		runner.run(context -> assertThat(context).hasFailed().getFailure()
				.rootCause().hasMessage("Missing required setting: jwt.secret"));
	}

	@Test
	void blankOrNullSecretIsRejectedAndNullOriginsAreEmpty() {
		assertThatIllegalStateException().isThrownBy(() -> new SecurityProperties("  ", "", 1, true, false))
				.withMessage("Missing required setting: jwt.secret");
		assertThatIllegalStateException().isThrownBy(() -> new SecurityProperties(null, "", 1, true, false));
		assertThat(new SecurityProperties("s", null, 1, true, false).getAllowedOrigins()).isEmpty();
	}
}
