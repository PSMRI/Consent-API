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

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import com.iemr.consent.utils.http.PayloadTooLargeException;
import com.iemr.consent.utils.response.ApiResponse;
import com.iemr.consent.utils.response.FieldErrorDetail;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;

/**
 * Turns every exception from a controller into the standard error body with the right HTTP
 * status and {@link ErrorCode} (FR-006 – FR-011, research R4).
 *
 * <p>Mirrors Common-API's {@code utils/exception/GlobalExceptionHandler}, extended to the full
 * status table of the constitution's API Response Standard. Rejected values, exception messages
 * and stack traces are never returned; unexpected errors are logged with the exception class and
 * trace id only (the message may hold personal data).</p>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

	private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	private final ErrorResponseWriter writer;

	public GlobalExceptionHandler(ErrorResponseWriter writer) {
		this.writer = writer;
	}

	@ExceptionHandler(ConsentApiException.class)
	public ResponseEntity<ApiResponse<Void>> handleConsentApi(ConsentApiException e, HttpServletRequest request) {
		return writer.toResponseEntity(request, e.getErrorCode(), e.getMessage(), e.getPurposeCode(),
				e.getDetails());
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ApiResponse<Void>> handleInvalidBody(MethodArgumentNotValidException e,
			HttpServletRequest request) {
		List<FieldErrorDetail> details = new ArrayList<>();
		e.getBindingResult().getFieldErrors().forEach(
				error -> details.add(detail(error.getField(), error.getDefaultMessage())));
		e.getBindingResult().getGlobalErrors().forEach(
				error -> details.add(detail(error.getObjectName(), error.getDefaultMessage())));
		return validationFailed(request, details);
	}

	@ExceptionHandler(ConstraintViolationException.class)
	public ResponseEntity<ApiResponse<Void>> handleConstraintViolation(ConstraintViolationException e,
			HttpServletRequest request) {
		List<FieldErrorDetail> details = e.getConstraintViolations().stream()
				.map(v -> new FieldErrorDetail(v.getPropertyPath().toString(), v.getMessage()))
				.toList();
		return validationFailed(request, details);
	}

	@ExceptionHandler(HandlerMethodValidationException.class)
	public ResponseEntity<ApiResponse<Void>> handleMethodValidation(HandlerMethodValidationException e,
			HttpServletRequest request) {
		List<FieldErrorDetail> details = new ArrayList<>();
		e.getAllValidationResults().forEach(result -> {
			String parameter = result.getMethodParameter().getParameterName();
			result.getResolvableErrors()
					.forEach(error -> details.add(detail(parameter, error.getDefaultMessage())));
		});
		return validationFailed(request, details);
	}

	@ExceptionHandler({ MissingServletRequestParameterException.class,
			MethodArgumentTypeMismatchException.class })
	public ResponseEntity<ApiResponse<Void>> handleMalformed(Exception e, HttpServletRequest request) {
		return writer.toResponseEntity(request, ErrorCode.MALFORMED_REQUEST, null, null, null);
	}

	/** Unreadable body; a body cut off by the size limit is 413, not 400. */
	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<ApiResponse<Void>> handleUnreadable(HttpMessageNotReadableException e,
			HttpServletRequest request) {
		ErrorCode code = hasCause(e, PayloadTooLargeException.class) ? ErrorCode.PAYLOAD_TOO_LARGE
				: ErrorCode.MALFORMED_REQUEST;
		return writer.toResponseEntity(request, code, null, null, null);
	}

	@ExceptionHandler(HttpRequestMethodNotSupportedException.class)
	public ResponseEntity<ApiResponse<Void>> handleMethod(HttpRequestMethodNotSupportedException e,
			HttpServletRequest request) {
		return writer.toResponseEntity(request, ErrorCode.METHOD_NOT_ALLOWED, null, null, null);
	}

	@ExceptionHandler(HttpMediaTypeNotSupportedException.class)
	public ResponseEntity<ApiResponse<Void>> handleMediaType(HttpMediaTypeNotSupportedException e,
			HttpServletRequest request) {
		return writer.toResponseEntity(request, ErrorCode.UNSUPPORTED_MEDIA_TYPE, null, null, null);
	}

	@ExceptionHandler(MissingServletRequestPartException.class)
	public ResponseEntity<ApiResponse<Void>> handleMissingPart(MissingServletRequestPartException e,
			HttpServletRequest request) {
		return validationFailed(request, List.of(detail(e.getRequestPartName(), "is required")));
	}

	@ExceptionHandler(MultipartException.class)
	public ResponseEntity<ApiResponse<Void>> handleMultipart(MultipartException e, HttpServletRequest request) {
		return writer.toResponseEntity(request, ErrorCode.MALFORMED_REQUEST, null, null, null);
	}

	@ExceptionHandler(MaxUploadSizeExceededException.class)
	public ResponseEntity<ApiResponse<Void>> handleUploadSize(MaxUploadSizeExceededException e,
			HttpServletRequest request) {
		return writer.toResponseEntity(request, ErrorCode.PAYLOAD_TOO_LARGE, null, null, null);
	}

	@ExceptionHandler({ NoResourceFoundException.class, NoHandlerFoundException.class })
	public ResponseEntity<ApiResponse<Void>> handleNotFound(Exception e, HttpServletRequest request) {
		return writer.toResponseEntity(request, ErrorCode.NOT_FOUND, null, null, null);
	}

	@ExceptionHandler({ DataAccessResourceFailureException.class, CannotGetJdbcConnectionException.class,
			RedisConnectionFailureException.class })
	public ResponseEntity<ApiResponse<Void>> handleDependency(Exception e, HttpServletRequest request) {
		LOGGER.warn("Dependency unavailable ({})", e.getClass().getSimpleName());
		return writer.toResponseEntity(request, ErrorCode.DEPENDENCY_UNAVAILABLE, null, null, null);
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception e, HttpServletRequest request) {
		LOGGER.error("Unexpected error ({})", e.getClass().getName());
		return writer.toResponseEntity(request, ErrorCode.INTERNAL_ERROR, null, null, null);
	}

	private ResponseEntity<ApiResponse<Void>> validationFailed(HttpServletRequest request,
			List<FieldErrorDetail> details) {
		return writer.toResponseEntity(request, ErrorCode.VALIDATION_FAILED, null, null, details);
	}

	private static FieldErrorDetail detail(String field, String message) {
		return new FieldErrorDetail(field, message);
	}

	private static boolean hasCause(Throwable error, Class<? extends Throwable> type) {
		for (Throwable cause = error; cause != null; cause = cause.getCause()) {
			if (type.isInstance(cause)) {
				return true;
			}
		}
		return false;
	}
}
