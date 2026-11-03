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
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.iemr.consent.config.ConsentProperties;
import com.iemr.consent.support.TestFixtures;

class FieldEncryptorTest {

	private final FieldEncryptor encryptor = new FieldEncryptor(TestFixtures.consentProperties());

	private static ConsentProperties withKey(String key) {
		ConsentProperties base = TestFixtures.consentProperties();
		return new ConsentProperties(base.request(), base.health(), base.redis(), base.rateLimit(), base.evidence(),
				base.capture(), base.commonApi(), base.notice(), base.otp(), new ConsentProperties.Encryption(key),
				base.accessGrant());
	}

	@Test
	void stringRoundTripsAndNeverRepeatsCiphertext() {
		String first = encryptor.encrypt("Asha Devi");
		String second = encryptor.encrypt("Asha Devi");

		assertThat(first).isNotEqualTo(second).doesNotContain("Asha");
		assertThat(encryptor.decrypt(first)).isEqualTo("Asha Devi");
		assertThat(encryptor.decrypt(second)).isEqualTo("Asha Devi");
	}

	@Test
	void bytesRoundTrip() {
		byte[] plain = "voice-clip-bytes".getBytes(StandardCharsets.UTF_8);
		byte[] encrypted = encryptor.encrypt(plain);

		assertThat(new String(encrypted, StandardCharsets.ISO_8859_1)).doesNotContain("voice-clip");
		assertThat(encryptor.decrypt(encrypted)).isEqualTo(plain);
	}

	@Test
	void tamperedOrForeignCiphertextIsRejectedWithoutTheValue() {
		byte[] encrypted = encryptor.encrypt("9876543210".getBytes(StandardCharsets.UTF_8));
		encrypted[encrypted.length - 1] ^= 1;

		assertThatIllegalStateException().isThrownBy(() -> encryptor.decrypt(encrypted))
				.withMessage("Decryption failed");
		String other = new FieldEncryptor(withKey(Base64.getEncoder().encodeToString(new byte[32])))
				.encrypt("9876543210");
		assertThatIllegalStateException().isThrownBy(() -> encryptor.decrypt(other));
		assertThatIllegalStateException().isThrownBy(() -> encryptor.decrypt(new byte[4]));
	}

	@ParameterizedTest
	@ValueSource(strings = { "not base64 !", "c2hvcnQ=" })
	void badKeyStopsStartUpNamingTheSetting(String key) {
		assertThatIllegalStateException().isThrownBy(() -> new FieldEncryptor(withKey(key)))
				.withMessage("Missing required setting: consent.encryption.key");
	}
}
