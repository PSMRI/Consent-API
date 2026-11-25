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

package com.iemr.consent.utils.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

class Sha256Test {

	@Test
	void knownVectors() {
		assertThat(Sha256.hex("")).isEqualTo("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
		assertThat(Sha256.hex("abc")).isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
		assertThat(Sha256.hex("abc".getBytes(StandardCharsets.UTF_8))).isEqualTo(Sha256.hex("abc"));
	}

	@Test
	void phoneMaskShowsOnlyTheLastFourDigits() {
		assertThat(PhoneMasker.mask("9876543210")).isEqualTo("******3210");
	}

	@Test
	void phoneMaskRejectsShortOrMissingValuesWithoutEchoingThem() {
		assertThatIllegalArgumentException().isThrownBy(() -> PhoneMasker.mask("1234"))
				.withMessageNotContaining("1234");
		assertThatIllegalArgumentException().isThrownBy(() -> PhoneMasker.mask(null));
	}
}
