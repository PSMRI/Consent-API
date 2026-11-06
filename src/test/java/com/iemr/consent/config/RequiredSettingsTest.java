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
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mock.env.MockEnvironment;

class RequiredSettingsTest {

	private static MockEnvironment environment(String url, String username) {
		MockEnvironment environment = new MockEnvironment();
		if (url != null) {
			environment.setProperty("spring.datasource.url", url);
		}
		if (username != null) {
			environment.setProperty("spring.datasource.username", username);
		}
		environment.setProperty("consent.encryption.key", "a2V5");
		environment.setProperty("consent.evidence.storage-dir", "/tmp/e");
		environment.setProperty("consent.common-api.base-url", "http://c");
		return environment;
	}

	@Test
	void acceptsConfiguredDatabaseSettings() {
		RequiredSettings settings = new RequiredSettings();
		settings.setEnvironment(environment("jdbc:mysql://h/db_identity", "user"));

		assertThatNoException().isThrownBy(() -> settings.postProcessBeanFactory(
				mock(ConfigurableListableBeanFactory.class)));
	}

	@Test
	void missingOrBlankUrlOrUserStopsStartUpNamingTheKey() {
		assertThatIllegalStateException().isThrownBy(() -> RequiredSettings.check(environment(null, "u")))
				.withMessage("Missing required setting: spring.datasource.url");
		assertThatIllegalStateException().isThrownBy(() -> RequiredSettings.check(environment(" ", "u")))
				.withMessage("Missing required setting: spring.datasource.url");
		assertThatIllegalStateException().isThrownBy(() -> RequiredSettings.check(environment("jdbc:x", "")))
				.withMessage("Missing required setting: spring.datasource.username");
	}

	@Test
	void missingConsentSettingIsRefused() {
		MockEnvironment environment = environment("jdbc:x", "u");
		environment.setProperty("consent.evidence.storage-dir", "${EVIDENCE_STORAGE_DIR}");
		assertThatIllegalArgumentException().isThrownBy(() -> RequiredSettings.check(environment))
				.withMessageContaining("EVIDENCE_STORAGE_DIR");
		environment.setProperty("consent.evidence.storage-dir", " ");
		assertThatIllegalStateException().isThrownBy(() -> RequiredSettings.check(environment))
				.withMessage("Missing required setting: consent.evidence.storage-dir");
	}

	@Test
	void unsetDockerVariableInPasswordIsRefused() {
		MockEnvironment environment = environment("jdbc:x", "u");
		environment.setProperty("spring.datasource.password", "${DATABASE_PASSWORD}");

		assertThatIllegalArgumentException().isThrownBy(() -> RequiredSettings.check(environment))
				.withMessageContaining("Could not resolve placeholder 'DATABASE_PASSWORD'");
	}

	@Test
	void unsetDockerVariableStopsStartUpBeforeAnyBeanIsCreated() {
		new ApplicationContextRunner().withBean(RequiredSettings.class)
				.withPropertyValues("spring.datasource.url=${DATABASE_URL}", "spring.datasource.username=u")
				.run(context -> assertThat(context).hasFailed().getFailure()
						.hasMessageContaining("Could not resolve placeholder 'DATABASE_URL'"));
	}
}
