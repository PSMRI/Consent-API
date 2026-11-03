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

package com.iemr.consent.utils.exception;

import org.springframework.http.HttpStatus;

import lombok.Getter;

/**
 * Central list of machine-readable error codes returned in {@code error.code}.
 *
 * <p>Central list, additive only — never rename or reuse a code (FR-008). Clients act on
 * the code, never on the message text. Each code fixes its HTTP status, so the body
 * {@code statusCode} always equals the transport status (constitution API Response
 * Standard).</p>
 */
@Getter
public enum ErrorCode {

	VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "One or more fields are invalid", false),
	MALFORMED_REQUEST(HttpStatus.BAD_REQUEST, "The request could not be read", false),
	UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "Login required", false),
	SESSION_EXPIRED(HttpStatus.UNAUTHORIZED, "Session expired. Please login again", false),
	ACCESS_DENIED(HttpStatus.FORBIDDEN, "You are not permitted to perform this action", false),
	ORIGIN_NOT_ALLOWED(HttpStatus.FORBIDDEN, "Origin not allowed", false),
	CONSENT_NOT_FOUND(HttpStatus.FORBIDDEN, "No consent is recorded for this purpose", true),
	CONSENT_DENIED(HttpStatus.FORBIDDEN, "Consent was refused for this purpose", true),
	CONSENT_WITHDRAWN(HttpStatus.FORBIDDEN, "Consent was withdrawn for this purpose", true),
	CONSENT_EXPIRED(HttpStatus.FORBIDDEN, "Consent has expired for this purpose", true),
	RECONSENT_REQUIRED(HttpStatus.FORBIDDEN, "Consent must be taken again for this purpose", true),
	GUARDIAN_REQUIRED(HttpStatus.FORBIDDEN, "Guardian consent is required for this purpose", true),
	NOT_FOUND(HttpStatus.NOT_FOUND, "The requested resource was not found", false),
	METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "HTTP method not allowed for this resource", false),
	STALE_VERSION(HttpStatus.CONFLICT, "The record was changed by someone else. Reload and try again", false),
	STATE_CONFLICT(HttpStatus.CONFLICT, "The request conflicts with the current state of the record", false),
	PAYLOAD_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, "The request body is too large", false),
	UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Unsupported content type", false),
	BUSINESS_RULE_VIOLATION(HttpStatus.UNPROCESSABLE_ENTITY, "The request breaks a business rule", false),
	RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "Too many requests. Try again later", false),
	INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred", false),
	DEPENDENCY_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "Service unavailable", false),
	OTP_EXPIRED(HttpStatus.UNPROCESSABLE_ENTITY, "The OTP has expired. Send a new one", false),
	OTP_INVALID(HttpStatus.UNPROCESSABLE_ENTITY, "The OTP was not verified for this consent", false),
	EVIDENCE_HASH_MISMATCH(HttpStatus.UNPROCESSABLE_ENTITY, "The file does not match its fingerprint", false),
	PURPOSE_NOT_WITHDRAWABLE(HttpStatus.UNPROCESSABLE_ENTITY, "This purpose cannot be withdrawn", false);

	private final HttpStatus status;

	private final String defaultMessage;

	private final boolean consentReason;

	ErrorCode(HttpStatus status, String defaultMessage, boolean consentReason) {
		this.status = status;
		this.defaultMessage = defaultMessage;
		this.consentReason = consentReason;
	}
}
