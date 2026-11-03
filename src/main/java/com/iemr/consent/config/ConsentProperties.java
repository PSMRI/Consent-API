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

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;

/**
 * Consent-API's own settings, prefix {@code consent} (research R2–R11 of features 001 and 002).
 *
 * <p>Values marked TODO-DPO are placeholders until the DPO signs them off (constitution X):
 * evidence size and voice-clip length, capture-time skew, OTP retention and wrong-code limit.
 * {@code evidence.storageDir}, {@code commonApi.baseUrl} and {@code encryption.key} have no
 * default; start-up fails, naming the key, when they are missing.</p>
 */
@Validated
@ConfigurationProperties(prefix = "consent")
public record ConsentProperties(@Valid @DefaultValue Request request, @Valid @DefaultValue Health health,
		@Valid @DefaultValue Redis redis, @Valid @DefaultValue RateLimit rateLimit,
		@Valid @DefaultValue Evidence evidence, @Valid @DefaultValue Capture capture,
		@Valid @DefaultValue CommonApi commonApi, @Valid @DefaultValue Notice notice,
		@Valid @DefaultValue Otp otp, @Valid @DefaultValue Encryption encryption,
		@Valid @DefaultValue AccessGrant accessGrant) {

	/** @param maxBodyBytes largest accepted request body, in bytes */
	public record Request(@DefaultValue("1048576") @Positive long maxBodyBytes) {
	}

	/** @param checkTimeoutMs per-dependency health-check timeout; keeps {@code /health} under 2 s */
	public record Health(@DefaultValue("1500") @Min(100) @Max(1900) long checkTimeoutMs) {
	}

	/** @param timeoutMs Redis command timeout */
	public record Redis(@DefaultValue("1500") @Positive long timeoutMs) {
	}

	/** @param retryAfterSeconds value of the {@code Retry-After} header on {@code RATE_LIMITED} */
	public record RateLimit(@DefaultValue("60") @Positive long retryAfterSeconds) {
	}

	/**
	 * Proof files (TODO-DPO limits; voice length per proposal §3.1, 30–60 s).
	 *
	 * @param storageDir          directory of the encrypted evidence store
	 * @param maxBytes            largest accepted file
	 * @param voiceMinSeconds     shortest voice clip
	 * @param voiceMaxSeconds     longest voice clip
	 * @param allowedContentTypes accepted file content types
	 */
	public record Evidence(@NotBlank String storageDir, @DefaultValue("10485760") @Positive long maxBytes,
			@DefaultValue("30") @Min(1) int voiceMinSeconds,
			@DefaultValue("60") @Min(1) int voiceMaxSeconds,
			@DefaultValue({ "audio/webm", "audio/ogg", "audio/mpeg", "audio/wav", "image/jpeg",
				"image/png" }) @NotEmpty List<String> allowedContentTypes) {
	}

	/** @param maxSkewMinutes device capture time further than this from server time is flagged (TODO-DPO) */
	public record Capture(@DefaultValue("1440") @Positive long maxSkewMinutes) {
	}

	/**
	 * @param baseUrl   Common-API base URL, e.g. {@code http://localhost:8083/commonapi-v3.0.0}
	 * @param timeoutMs connect and read timeout for Common-API calls
	 */
	public record CommonApi(@NotBlank String baseUrl, @DefaultValue("3000") @Positive int timeoutMs) {
	}

	/** @param audioBaseUrl prefix for a notice translation's {@code audio_ref}; empty = no audio link */
	public record Notice(@DefaultValue("") String audioBaseUrl) {
	}

	/**
	 * OTP challenges (TODO-DPO values).
	 *
	 * @param ttlSeconds       code validity, as Common-API's 5 minutes
	 * @param retentionMinutes how long the challenge record is kept, so a save after a late verify works
	 * @param maxAttempts      wrong codes allowed before the challenge is locked
	 */
	public record Otp(@DefaultValue("300") @Min(30) long ttlSeconds,
			@DefaultValue("30") @Positive long retentionMinutes,
			@DefaultValue("5") @Min(1) int maxAttempts) {
	}

	/** @param key Base64 AES-256 key of the shared field and file encryption (a secret) */
	public record Encryption(@NotBlank String key) {
	}

	/**
	 * Doctor access grants (feature 003).
	 *
	 * @param maxDays     longest access a doctor may request, in days (TODO-LEGAL)
	 * @param replayHours how long an approve or revoke request key is remembered for replay
	 */
	public record AccessGrant(@DefaultValue("30") @Min(1) int maxDays,
			@DefaultValue("24") @Min(1) int replayHours) {
	}
}
