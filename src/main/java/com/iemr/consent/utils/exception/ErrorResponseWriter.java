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

import java.io.IOException;
import java.time.Clock;
import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iemr.consent.config.ConsentProperties;
import com.iemr.consent.utils.http.TraceIdFilter;
import com.iemr.consent.utils.response.ApiResponse;
import com.iemr.consent.utils.response.FieldErrorDetail;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Builds every error response, so filters, {@link GlobalExceptionHandler} and
 * {@link ConsentErrorController} all produce the same standard body (FR-006).
 *
 * <p>Filters run before Spring MVC and cannot reach {@code @RestControllerAdvice}; they call
 * {@link #write}. It serialises with the application's {@link ObjectMapper} and never uses
 * {@code sendError}, which would produce a container error page.</p>
 */
@Component
public class ErrorResponseWriter {

	/** Request attribute holding the error code of the response, read by the request log. */
	public static final String ERROR_CODE_ATTRIBUTE = "consent.errorCode";

	private final ObjectMapper objectMapper;

	private final Clock clock;

	private final ConsentProperties properties;

	public ErrorResponseWriter(ObjectMapper objectMapper, Clock clock, ConsentProperties properties) {
		this.objectMapper = objectMapper;
		this.clock = clock;
		this.properties = properties;
	}

	/** Writes a refusal from a filter, with the code's default message. */
	public void write(HttpServletRequest request, HttpServletResponse response, ErrorCode code,
			String purposeCode) throws IOException {
		ResponseEntity<ApiResponse<Void>> entity = toResponseEntity(request, code, null, purposeCode, null);
		response.setStatus(entity.getStatusCode().value());
		entity.getHeaders().forEach((name, values) -> values.forEach(value -> response.addHeader(name, value)));
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.setCharacterEncoding("UTF-8");
		objectMapper.writeValue(response.getOutputStream(), entity.getBody());
	}

	/** Builds the standard error response for a handler or controller. */
	public ResponseEntity<ApiResponse<Void>> toResponseEntity(HttpServletRequest request, ErrorCode code,
			String message, String purposeCode, List<FieldErrorDetail> details) {
		request.setAttribute(ERROR_CODE_ATTRIBUTE, code);
		ApiResponse<Void> body = ApiResponse.error(code, message, purposeCode, details,
				TraceIdFilter.currentTraceId(request), clock);
		ResponseEntity.BodyBuilder builder = ResponseEntity.status(code.getStatus())
				.contentType(MediaType.APPLICATION_JSON);
		if (code == ErrorCode.RATE_LIMITED) {
			long retryAfter = properties.rateLimit().retryAfterSeconds();
			builder.header(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfter));
		}
		return builder.body(body);
	}
}
