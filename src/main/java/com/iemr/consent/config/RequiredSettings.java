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

import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Makes start-up fail, naming the setting, when a database or consent-capture setting is missing
 * (FR-016).
 *
 * <p>Spring Boot binds {@code spring.datasource.*} leniently: an unset Docker variable such as
 * {@code ${DATABASE_URL}} is kept as literal text and the service fails later with an unclear
 * driver error. This check runs as a {@link BeanFactoryPostProcessor}, before any bean (including
 * the datasource) is created, and reads the keys strictly: an unset variable or a blank URL /
 * user name stops start-up, and {@link MissingSettingFailureAnalyzer} names the setting. Values
 * are never logged.</p>
 */
@Component
public class RequiredSettings implements BeanFactoryPostProcessor, EnvironmentAware {

	/** Feature 002 settings with no default; Docker supplies them as environment variables. */
	static final String[] CONSENT_KEYS = {"consent.encryption.key", "consent.evidence.storage-dir",
		"consent.common-api.base-url"};

	private Environment environment;

	@Override
	public void setEnvironment(Environment environment) {
		this.environment = environment;
	}

	@Override
	public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) {
		check(environment);
	}

	/**
	 * @throws IllegalStateException    a blank URL or user name
	 * @throws IllegalArgumentException an unresolvable {@code ${VARIABLE}} in any datasource key
	 */
	static void check(Environment environment) {
		require(environment.getProperty("spring.datasource.url"), "spring.datasource.url");
		require(environment.getProperty("spring.datasource.username"), "spring.datasource.username");
		environment.getProperty("spring.datasource.password");
		for (String key : CONSENT_KEYS) {
			require(environment.getProperty(key), key);
		}
	}

	private static void require(String value, String key) {
		if (value == null || value.isBlank()) {
			throw new IllegalStateException(MissingSettingFailureAnalyzer.PREFIX + key);
		}
	}
}
