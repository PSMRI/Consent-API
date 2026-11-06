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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

/**
 * Captures every log event during a test so it can assert that no token, secret or personal
 * value is ever logged (SC-008). Register with {@code @RegisterExtension}.
 */
public class LogCapture implements BeforeEachCallback, AfterEachCallback {

	private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

	@Override
	public void beforeEach(ExtensionContext context) {
		appender.list.clear();
		appender.start();
		root().addAppender(appender);
	}

	@Override
	public void afterEach(ExtensionContext context) {
		root().detachAppender(appender);
		appender.stop();
	}

	/** Formatted messages, plus any logged throwable text. */
	public List<String> messages() {
		return appender.list.stream().map(LogCapture::render).toList();
	}

	public void assertNoneContain(String... values) {
		for (String message : messages()) {
			for (String value : values) {
				assertThat(message).as("log line must not contain a sensitive value").doesNotContain(value);
			}
		}
	}

	private static String render(ILoggingEvent event) {
		String text = event.getFormattedMessage();
		if (event.getThrowableProxy() != null) {
			text += " " + event.getThrowableProxy().getClassName() + ": " + event.getThrowableProxy().getMessage();
		}
		return text;
	}

	private static Logger root() {
		return (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
	}
}
