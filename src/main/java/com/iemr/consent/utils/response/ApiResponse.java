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

package com.iemr.consent.utils.response;

import java.time.Clock;
import java.util.List;

import org.springframework.http.HttpStatus;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.iemr.consent.utils.exception.ErrorCode;

import lombok.Getter;

/**
 * The single body shape of every Consent-API response (constitution API Response Standard).
 *
 * <p>Extends Common-API's {@code utils/response/ApiResponse} ({@code success},
 * {@code statusCode}, {@code message}, {@code data}) with {@code error}, {@code timestamp} and
 * {@code traceId}. {@code statusCode} is always derived from the HTTP status used, so it can
 * never differ from the transport status.</p>
 *
 * @param <T> type of {@code data}
 */
@Getter
@JsonInclude(JsonInclude.Include.ALWAYS)
@JsonPropertyOrder({ "success", "statusCode", "message", "data", "error", "timestamp", "traceId" })
public final class ApiResponse<T> {

	private final boolean success;

	private final int statusCode;

	private final String message;

	private final T data;

	private final ErrorBody error;

	private final String timestamp;

	private final String traceId;

	private ApiResponse(HttpStatus status, String message, T data, ErrorBody error, String traceId,
			Clock clock) {
		this.success = status.is2xxSuccessful();
		this.statusCode = status.value();
		this.message = message;
		this.data = data;
		this.error = error;
		this.timestamp = clock.instant().toString();
		this.traceId = traceId;
	}

	/** 200 OK with {@code data}. */
	public static <T> ApiResponse<T> ok(T data, String traceId, Clock clock) {
		return new ApiResponse<>(HttpStatus.OK, "OK", data, null, traceId, clock);
	}

	/** 201 Created with the new record in {@code data}. */
	public static <T> ApiResponse<T> created(T data, String traceId, Clock clock) {
		return new ApiResponse<>(HttpStatus.CREATED, "Created", data, null, traceId, clock);
	}

	/**
	 * Error response; status and code come from {@code code}.
	 *
	 * @param message safe override, or {@code null} for the code's default message
	 */
	public static ApiResponse<Void> error(ErrorCode code, String message, String purposeCode,
			List<FieldErrorDetail> details, String traceId, Clock clock) {
		String text = message != null ? message : code.getDefaultMessage();
		return new ApiResponse<>(code.getStatus(), text, null, new ErrorBody(code, purposeCode, details),
				traceId, clock);
	}
}
