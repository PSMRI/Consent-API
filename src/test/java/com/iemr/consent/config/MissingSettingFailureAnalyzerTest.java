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
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.boot.context.properties.bind.validation.BindValidationException;
import org.springframework.boot.context.properties.bind.validation.ValidationErrors;
import org.springframework.boot.context.properties.source.ConfigurationPropertyName;
import org.springframework.boot.diagnostics.FailureAnalysis;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;

class MissingSettingFailureAnalyzerTest {

	private static final String SYNTHETIC_VALUE = "synthetic-secret-value-123";

	private final MissingSettingFailureAnalyzer analyzer = new MissingSettingFailureAnalyzer();

	private static BindValidationException bindFailure(ObjectError error) {
		ValidationErrors errors = mock(ValidationErrors.class);
		when(errors.getName()).thenReturn(ConfigurationPropertyName.of("consent"));
		when(errors.getAllErrors()).thenReturn(List.of(error));
		BindValidationException bind = mock(BindValidationException.class);
		when(bind.getValidationErrors()).thenReturn(errors);
		return bind;
	}

	@Test
	void bindValidationNamesTheSettingWithoutItsValue() {
		FieldError error = new FieldError("consent", "health.checkTimeoutMs", SYNTHETIC_VALUE, false, null, null,
				"must be less than or equal to 1900");
		BindValidationException bind = bindFailure(error);

		IllegalStateException wrapper = new IllegalStateException("wrapper");
		wrapper.initCause(bind);
		FailureAnalysis analysis = analyzer.analyze(wrapper);

		assertThat(analysis.getDescription()).isEqualTo("Missing required setting: consent.health.checkTimeoutMs");
		assertThat(analysis.getDescription() + analysis.getAction()).doesNotContain(SYNTHETIC_VALUE);
	}

	@Test
	void bindValidationWithObjectErrorNamesThePrefix() {
		ObjectError error = new ObjectError("consent", "invalid");
		BindValidationException bind = bindFailure(error);

		assertThat(analyzer.analyze(bind).getDescription()).isEqualTo("Missing required setting: consent");
	}

	@Test
	void unresolvedPlaceholderNamesTheVariable() {
		IllegalArgumentException cause = new IllegalArgumentException(
				"Could not resolve placeholder 'JWT_SECRET_KEY' in value \"${JWT_SECRET_KEY}\"");

		FailureAnalysis analysis = analyzer.analyze(new BeanCreationException("securityProperties", cause));

		assertThat(analysis.getDescription()).isEqualTo("Missing required setting: JWT_SECRET_KEY");
		assertThat(analysis.getAction()).contains("common_example.properties");
	}

	@Test
	void missingRequiredSettingMessageIsPassedThrough() {
		IllegalStateException cause = new IllegalStateException("Missing required setting: jwt.secret");

		FailureAnalysis analysis = analyzer.analyze(new BeanCreationException("securityProperties", cause));

		assertThat(analysis.getDescription()).isEqualTo("Missing required setting: jwt.secret");
	}

	@Test
	void unrelatedFailuresAreLeftToSpringBoot() {
		assertThat(analyzer.analyze(new IllegalStateException("something else"))).isNull();
		assertThat(analyzer.analyze(new IllegalArgumentException("Could not do it"))).isNull();
		assertThat(analyzer.analyze(new RuntimeException("Missing required setting: x"))).isNull();
		assertThat(analyzer.analyze(new RuntimeException("Could not resolve placeholder 'X'"))).isNull();
		assertThat(analyzer.analyze(new RuntimeException((String) null))).isNull();
	}
}
