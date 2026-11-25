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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.iemr.consent.utils.response.ApiResponse;

import io.swagger.v3.oas.annotations.Hidden;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Replaces Spring Boot's {@code BasicErrorController} so that container-level errors, including
 * exceptions that escape a filter (which {@link GlobalExceptionHandler} never sees), still return
 * the standard body (FR-006).
 *
 * <p>It needs no login check: the filters are registered for {@code REQUEST} dispatch only, so
 * the container's forward to {@code /error} is not filtered, and {@code /error} is not a public
 * path. A direct request to {@code /error} is refused with 401 by the authentication filter.</p>
 */
@Hidden
@RestController
public class ConsentErrorController implements ErrorController {

	private static final Logger LOGGER = LoggerFactory.getLogger(ConsentErrorController.class);

	private final ErrorResponseWriter writer;

	public ConsentErrorController(ErrorResponseWriter writer) {
		this.writer = writer;
	}

	@RequestMapping("/error")
	public ResponseEntity<ApiResponse<Void>> error(HttpServletRequest request) {
		Object status = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
		ErrorCode code = toErrorCode(status instanceof Integer value ? value : 500);
		Object exception = request.getAttribute(RequestDispatcher.ERROR_EXCEPTION);
		if (exception != null) {
			LOGGER.error("Unhandled error ({})", exception.getClass().getName());
		}
		return writer.toResponseEntity(request, code, null, null, null);
	}

	static ErrorCode toErrorCode(int status) {
		return switch (status) {
			case 404 -> ErrorCode.NOT_FOUND;
			case 405 -> ErrorCode.METHOD_NOT_ALLOWED;
			case 413 -> ErrorCode.PAYLOAD_TOO_LARGE;
			case 415 -> ErrorCode.UNSUPPORTED_MEDIA_TYPE;
			default -> isClientError(status) ? ErrorCode.MALFORMED_REQUEST : ErrorCode.INTERNAL_ERROR;
		};
	}

	private static boolean isClientError(int status) {
		return status >= 400 && status < 500;
	}
}
