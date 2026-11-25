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

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.boot.context.properties.bind.validation.BindValidationException;
import org.springframework.boot.diagnostics.FailureAnalysis;
import org.springframework.boot.diagnostics.FailureAnalyzer;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;

/**
 * Turns a start-up failure caused by a missing or invalid setting into one line naming the
 * setting, never its value (FR-016, research R9). Handles:
 * <ul>
 * <li>{@link BindValidationException}: a {@code consent.*} value failed validation;</li>
 * <li>an unresolvable {@code ${NAME}} placeholder, e.g. an unset Docker variable;</li>
 * <li>{@code IllegalStateException("Missing required setting: …")}, e.g. a blank
 * {@code jwt.secret}.</li>
 * </ul>
 * Any other failure is left to Spring Boot.
 */
public class MissingSettingFailureAnalyzer implements FailureAnalyzer {

	public static final String PREFIX = "Missing required setting: ";

	static final String ACTION = "Set it in src/main/environment/common_<env>.properties (see "
			+ "common_example.properties), or as an environment variable for the Docker image.";

	private static final Pattern PLACEHOLDER = Pattern.compile("Could not resolve placeholder '([^']+)'");

	@Override
	public FailureAnalysis analyze(Throwable failure) {
		for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
			String setting = settingName(cause);
			if (setting != null) {
				return new FailureAnalysis(PREFIX + setting, ACTION, cause);
			}
		}
		return null;
	}

	private static String settingName(Throwable cause) {
		if (cause instanceof BindValidationException bind) {
			return bindName(bind);
		}
		String message = cause.getMessage();
		if (message == null) {
			return null;
		}
		if (cause instanceof IllegalStateException && message.startsWith(PREFIX)) {
			return message.substring(PREFIX.length());
		}
		Matcher matcher = PLACEHOLDER.matcher(message);
		return cause instanceof IllegalArgumentException && matcher.find() ? matcher.group(1) : null;
	}

	private static String bindName(BindValidationException bind) {
		String prefix = bind.getValidationErrors().getName().toString();
		ObjectError error = bind.getValidationErrors().getAllErrors().get(0);
		return error instanceof FieldError field ? prefix + "." + field.getField() : prefix;
	}
}
