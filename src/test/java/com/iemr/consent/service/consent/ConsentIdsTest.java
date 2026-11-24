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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

/** Consent ids: India date plus 5 random digits, retried when taken, and the accepted client forms. */
class ConsentIdsTest {

	/** 20:00 UTC on 23 Nov is already 24 Nov in India. */
	private static final Clock LATE_EVENING = Clock.fixed(Instant.parse("2026-11-23T20:00:00Z"), ZoneOffset.UTC);

	@Test
	void idIsTheIndiaDateAndFiveDigits() {
		for (int i = 0; i < 1000; i++) {
			assertThat(ConsentIds.next(LATE_EVENING, id -> false)).hasSize(11).matches("^261124[0-9]{5}$");
		}
	}

	@Test
	void takenIdsAreDrawnAgain() {
		Set<String> seen = new HashSet<>();
		AtomicInteger calls = new AtomicInteger();
		String id = ConsentIds.next(LATE_EVENING, candidate -> {
			seen.add(candidate);
			return calls.incrementAndGet() < 3;
		});
		assertThat(calls).hasValue(3);
		assertThat(seen).contains(id);
	}

	@Test
	void givesUpWhenEveryAttemptIsTaken() {
		AtomicInteger calls = new AtomicInteger();
		assertThatThrownBy(() -> ConsentIds.next(LATE_EVENING, id -> calls.incrementAndGet() > 0))
				.isInstanceOf(IllegalStateException.class);
		assertThat(calls).hasValue(ConsentIds.MAX_ATTEMPTS);
	}

	@Test
	void elevenDigitIdsAndOldUuidsAreAccepted() {
		assertThat(ConsentIds.normalise("26112448271")).isEqualTo("26112448271");
		assertThat(ConsentIds.normalise("3F2504E0-4F89-11D3-9A0C-0305E82C3301"))
				.isEqualTo("3f2504e0-4f89-11d3-9a0c-0305e82c3301");
	}

	@Test
	void anythingElseIsRefused() {
		assertThat(ConsentIds.normalise(null)).isNull();
		assertThat(ConsentIds.normalise("2611244827")).isNull();
		assertThat(ConsentIds.normalise("261124482711")).isNull();
		assertThat(ConsentIds.normalise("26112448A71")).isNull();
		assertThat(ConsentIds.normalise("x")).isNull();
	}
}
