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

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Component;

import com.iemr.consent.config.ConsentProperties;

/**
 * The constitution's "one shared, configurable encryption component" (research R3): AES-256-GCM
 * with a random 12-byte IV per value and a 128-bit tag.
 *
 * <p>Used for personal fields in {@code cm_} tables (guardian and witness names and phones), the
 * phone held in an OTP challenge, and evidence files. Strings are stored as Base64 of
 * IV‖ciphertext; bytes as IV‖ciphertext. The key is {@code consent.encryption.key} (Base64,
 * 32 bytes); a missing or wrong-length key stops start-up. No value ever appears in an
 * exception message.</p>
 */
@Component
public class FieldEncryptor {

	static final String KEY_SETTING = "Missing required setting: consent.encryption.key";

	private static final String TRANSFORMATION = "AES/GCM/NoPadding";

	private static final int IV_BYTES = 12;

	private static final int TAG_BITS = 128;

	private static final int KEY_BYTES = 32;

	private final SecretKey key;

	private final SecureRandom random = new SecureRandom();

	public FieldEncryptor(ConsentProperties properties) {
		this.key = new SecretKeySpec(decodeKey(properties.encryption().key()), "AES");
	}

	public String encrypt(String plain) {
		return Base64.getEncoder().encodeToString(encrypt(plain.getBytes(StandardCharsets.UTF_8)));
	}

	public String decrypt(String encrypted) {
		return new String(decrypt(Base64.getDecoder().decode(encrypted)), StandardCharsets.UTF_8);
	}

	public byte[] encrypt(byte[] plain) {
		byte[] iv = new byte[IV_BYTES];
		random.nextBytes(iv);
		try {
			Cipher cipher = Cipher.getInstance(TRANSFORMATION);
			cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
			byte[] cipherText = cipher.doFinal(plain);
			return ByteBuffer.allocate(iv.length + cipherText.length).put(iv).put(cipherText).array();
		} catch (GeneralSecurityException e) {
			throw new IllegalStateException("Encryption failed", e);
		}
	}

	public byte[] decrypt(byte[] encrypted) {
		try {
			Cipher cipher = Cipher.getInstance(TRANSFORMATION);
			cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, encrypted, 0, IV_BYTES));
			return cipher.doFinal(encrypted, IV_BYTES, encrypted.length - IV_BYTES);
		} catch (GeneralSecurityException | IllegalArgumentException e) {
			throw new IllegalStateException("Decryption failed");
		}
	}

	private static byte[] decodeKey(String encoded) {
		byte[] bytes;
		try {
			bytes = Base64.getDecoder().decode(encoded);
		} catch (IllegalArgumentException e) {
			throw new IllegalStateException(KEY_SETTING);
		}
		if (bytes.length != KEY_BYTES) {
			throw new IllegalStateException(KEY_SETTING);
		}
		return bytes;
	}
}
