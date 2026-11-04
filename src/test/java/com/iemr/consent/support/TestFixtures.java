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

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iemr.consent.config.ConsentProperties;
import com.iemr.consent.config.SecurityProperties;

/** Shared synthetic test values: a fixed clock and default settings. */
public final class TestFixtures {

	public static final Instant NOW = Instant.parse("2026-10-29T10:00:00Z");

	public static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

	public static final String ALLOWED_ORIGINS = "http://localhost:4200,https://*.piramalswasthya.org";

	/** Synthetic Base64 AES-256 key, test-only. */
	public static final String ENCRYPTION_KEY = "dGVzdC1vbmx5LWVuY3J5cHRpb24ta2V5LTMyYnl0ZXM=";

	public static final String STORAGE_DIR = "target/test-evidence";

	public static final String COMMON_API_URL = "http://common-api.test";

	private TestFixtures() {
	}

	public static ConsentProperties consentProperties() {
		return consentProperties(1048576L);
	}

	/** Test limits: recordings up to 1024 bytes, signed-form photos up to 512 bytes. */
	public static final long AUDIO_MAX_BYTES = 1024L;

	public static final long PHOTO_MAX_BYTES = 512L;

	public static final String SMS_TEMPLATE = "Consent ID: {consentId}. Grievance: {grievance}";

	/** Test value of the admin service key (feature 006). */
	public static final String SERVICE_KEY = "test-service-key-0123456789";

	public static ConsentProperties consentProperties(long maxBodyBytes) {
		return consentProperties(maxBodyBytes, STORAGE_DIR, "");
	}

	public static ConsentProperties consentProperties(long maxBodyBytes, String storageDir, String audioBaseUrl) {
		return new ConsentProperties(new ConsentProperties.Request(maxBodyBytes),
				new ConsentProperties.Health(1500L), new ConsentProperties.Redis(1500L),
				new ConsentProperties.RateLimit(60L),
				new ConsentProperties.Evidence(storageDir, 1024L, 2, 120,
						List.of("audio/webm", "audio/ogg", "audio/mpeg", "audio/wav", "audio/mp4", "audio/x-m4a",
								"image/jpeg", "image/png"),
						AUDIO_MAX_BYTES, PHOTO_MAX_BYTES, 60, 24, "0 0 * * * ?"),
				new ConsentProperties.Capture(1440L), new ConsentProperties.CommonApi(COMMON_API_URL, 3000),
				new ConsentProperties.Notice(audioBaseUrl), new ConsentProperties.Otp(300L, 30L, 5),
				new ConsentProperties.Encryption(ENCRYPTION_KEY), new ConsentProperties.AccessGrant(30, 24),
				new ConsentProperties.Sms(SMS_TEMPLATE, 2000), new ConsentProperties.Admin(SERVICE_KEY),
				new ConsentProperties.Translation("", 10000));
	}

	public static SecurityProperties securityProperties() {
		return securityProperties(ALLOWED_ORIGINS, false, true);
	}

	public static SecurityProperties securityProperties(String origins, boolean ipValidation, boolean extend) {
		return new SecurityProperties(TestTokens.SECRET, origins, 7200, extend, ipValidation);
	}

	public static ObjectMapper objectMapper() {
		return new ObjectMapper().findAndRegisterModules();
	}
}
