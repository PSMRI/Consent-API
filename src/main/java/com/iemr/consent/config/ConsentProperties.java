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
		@Valid @DefaultValue AccessGrant accessGrant, @Valid @DefaultValue Sms sms,
		@Valid @DefaultValue Admin admin, @Valid @DefaultValue Translation translation,
		@Valid @DefaultValue Legacy legacy) {

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
	 * Proof files (TODO-DPO limits; reviewed list of 3 Nov 2026, feature 004).
	 *
	 * @param storageDir             directory of the encrypted evidence store
	 * @param maxBytes               largest accepted upload request (size filter and multipart ceiling)
	 * @param voiceMinSeconds        shortest recording
	 * @param voiceMaxSeconds        longest recording (READ_ALOUD limit at save)
	 * @param allowedContentTypes    accepted file content types
	 * @param maxAudioBytes          largest recording
	 * @param maxPhotoBytes          largest signed-form photo
	 * @param selfReadMaxSeconds     longest recording linked to a SELF_READ consent
	 * @param unlinkedRetentionHours unlinked evidence older than this is removed by the clean-up job
	 * @param cleanupCron            Quartz cron of the clean-up job
	 */
	public record Evidence(@NotBlank String storageDir, @DefaultValue("10485760") @Positive long maxBytes,
			@DefaultValue("2") @Min(1) int voiceMinSeconds,
			@DefaultValue("120") @Min(1) int voiceMaxSeconds,
			@DefaultValue({ "audio/webm", "audio/ogg", "audio/mpeg", "audio/wav", "audio/mp4",
				"audio/x-m4a", "audio/x-wav", "audio/wave", "image/jpeg", "image/png" })
			@NotEmpty List<String> allowedContentTypes,
			@DefaultValue("5242880") @Positive long maxAudioBytes,
			@DefaultValue("2097152") @Positive long maxPhotoBytes,
			@DefaultValue("60") @Min(1) int selfReadMaxSeconds,
			@DefaultValue("24") @Positive int unlinkedRetentionHours,
			@DefaultValue("0 0 * * * ?") @NotBlank String cleanupCron) {
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

	/** Default consent SMS text (TODO-LEGAL). */
	public static final String DEFAULT_SMS_TEMPLATE = "Your consent is recorded. Consent ID: {consentId}. "
			+ "Grievance: {grievance}";

	/**
	 * Consent SMS through Common-API (feature 004).
	 *
	 * @param template  SMS text with {@code {consentId}} and {@code {grievance}} (TODO-LEGAL wording; must not
	 *                  name a programme or health condition)
	 * @param timeoutMs connect and read timeout of the SMS call; when reached the status is FAILED (TODO-DPO)
	 */
	public record Sms(
			@DefaultValue(DEFAULT_SMS_TEMPLATE) @NotBlank String template,
			@DefaultValue("2000") @Positive int timeoutMs) {
	}

	/**
	 * Admin notice endpoints (feature 006, research R3).
	 *
	 * @param serviceKey shared secret Common-API sends as {@code X-Consent-Service-Key}; blank disables
	 *                   {@code /consent/v1/admin/**}. Set from the environment only; never logged.
	 */
	public record Admin(@DefaultValue("") String serviceKey) {

		@Override
		public String toString() {
			boolean unset = serviceKey == null || serviceKey.isBlank();
			return "Admin[serviceKey=" + (unset ? "<unset>" : "******") + "]";
		}
	}

	/**
	 * Machine translation of published notices (feature 006, research R9).
	 *
	 * @param provider  translation service; blank (the default) = not configured, so only English is published.
	 *                  The service is an open decision (TODO-LEGAL / tech lead)
	 * @param timeoutMs longest wait for one language; a timeout publishes nothing
	 */
	public record Translation(@DefaultValue("") String provider, @DefaultValue("10000") @Positive int timeoutMs) {
	}

	/**
	 * Legacy consent upload (feature 008, research R9).
	 *
	 * @param batchExpiryDays a checked file can be imported for this many days
	 */
	public record Legacy(@DefaultValue("30") @Positive int batchExpiryDays) {
	}
}
