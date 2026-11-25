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

package com.iemr.consent.service.consent;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Consent event ids: 11 digits, the consent date in India as {@code yyMMdd} followed by 5 random digits, for
 * example {@code 26112448271} (24 Nov 2026). Only the 5 random digits need remembering. A new id is checked
 * against the stored ones and drawn again if taken; the {@code cm_consent} primary key is the final guard.
 * Consents saved before this format keep their 36-character UUID ids, so {@link #normalise} accepts both forms.
 */
public final class ConsentIds {

	/** How many times a taken id is drawn again before giving up (about 1 in 10^20 at 1,000 consents a day). */
	static final int MAX_ATTEMPTS = 5;

	private static final ZoneId INDIA = ZoneId.of("Asia/Kolkata");

	private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyMMdd");

	private static final Pattern SHORT = Pattern.compile("^[0-9]{11}$");

	private static final SecureRandom RANDOM = new SecureRandom();

	private ConsentIds() {
	}

	/**
	 * A new consent id that is not yet taken.
	 *
	 * @param clock the injected clock (the date part is today in India)
	 * @param taken whether an id is already stored
	 * @return the new id
	 * @throws IllegalStateException if every attempt was taken (in practice never)
	 */
	public static String next(Clock clock, Predicate<String> taken) {
		String date = LocalDate.now(clock.withZone(INDIA)).format(DATE);
		for (int i = 0; i < MAX_ATTEMPTS; i++) {
			String id = date + String.format("%05d", RANDOM.nextInt(100_000));
			if (!taken.test(id)) {
				return id;
			}
		}
		throw new IllegalStateException("No free consent id");
	}

	/**
	 * The stored form of a consent id sent by a client: an 11-digit id as is, or an old UUID in lower case.
	 *
	 * @param id the id as sent
	 * @return the id as stored, or {@code null} when it can be neither form
	 */
	public static String normalise(String id) {
		if (id == null) {
			return null;
		}
		if (SHORT.matcher(id).matches()) {
			return id;
		}
		try {
			return UUID.fromString(id).toString();
		} catch (IllegalArgumentException e) {
			return null;
		}
	}
}
