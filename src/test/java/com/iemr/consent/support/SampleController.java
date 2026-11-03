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
import java.util.Map;
import java.util.Set;

import org.springframework.boot.test.context.TestComponent;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import com.iemr.consent.utils.auth.AuthenticatedUser;
import com.iemr.consent.utils.auth.CurrentUser;
import com.iemr.consent.utils.exception.ConsentApiException;
import com.iemr.consent.utils.exception.ConsentDeniedException;
import com.iemr.consent.utils.exception.ErrorCode;
import com.iemr.consent.utils.http.TraceIdFilter;
import com.iemr.consent.utils.response.ApiResponse;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Valid;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * TEST-ONLY sample endpoint under {@code /consent/v1/_test} (clarification 1). It lives only in
 * {@code src/test}, is excluded from component scanning ({@link TestComponent}) and is imported
 * explicitly by the tests that need it, so it is never part of the deployed service.
 */
@TestComponent
@RestController
@RequestMapping("/consent/v1/_test")
public class SampleController {

	private final Clock clock;

	public SampleController(Clock clock) {
		this.clock = clock;
	}

	/** Validated request body. */
	public record SampleBody(@NotNull String decision, @Size(max = 10) String note) {
	}

	@GetMapping("/ok")
	public ApiResponse<Map<String, String>> ok(HttpServletRequest request) {
		return ApiResponse.ok(Map.of("value", "ok"), TraceIdFilter.currentTraceId(request), clock);
	}

	@PostMapping(value = "/created", consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<ApiResponse<SampleBody>> created(@Valid @RequestBody SampleBody body,
			HttpServletRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(ApiResponse.created(body, TraceIdFilter.currentTraceId(request), clock));
	}

	@PostMapping(value = "/echo", consumes = MediaType.TEXT_PLAIN_VALUE)
	public ApiResponse<Integer> echo(@RequestBody String body, HttpServletRequest request) {
		return ApiResponse.ok(body.length(), TraceIdFilter.currentTraceId(request), clock);
	}

	@GetMapping("/consent/{code}")
	public void consent(@PathVariable String code) {
		throw new ConsentDeniedException(ErrorCode.valueOf(code), "P-SHR");
	}

	@GetMapping("/fail/{code}")
	public void fail(@PathVariable String code) {
		throw new ConsentApiException(ErrorCode.valueOf(code));
	}

	@GetMapping("/param")
	public ApiResponse<Integer> param(@RequestParam int count, HttpServletRequest request) {
		return ApiResponse.ok(count, TraceIdFilter.currentTraceId(request), clock);
	}

	@GetMapping("/limited")
	public ApiResponse<Integer> limited(@RequestParam @Max(5) int size, HttpServletRequest request) {
		return ApiResponse.ok(size, TraceIdFilter.currentTraceId(request), clock);
	}

	@GetMapping("/constraint")
	public void constraint() {
		try (var factory = Validation.buildDefaultValidatorFactory()) {
			Validator validator = factory.getValidator();
			Set<ConstraintViolation<SampleBody>> violations = validator.validate(new SampleBody(null, "x"));
			throw new ConstraintViolationException(violations);
		}
	}

	@GetMapping("/upload")
	public void upload() {
		throw new MaxUploadSizeExceededException(1);
	}

	@GetMapping("/boom")
	public void boom() {
		throw new IllegalStateException("db password=secret");
	}

	@GetMapping("/db-down")
	public void dbDown() {
		throw new CannotGetJdbcConnectionException("jdbc:mysql://db-host:3306 down");
	}

	@GetMapping("/data-down")
	public void dataDown() {
		throw new DataAccessResourceFailureException("db-host unreachable");
	}

	@GetMapping("/cache-down")
	public void cacheDown() {
		throw new RedisConnectionFailureException("redis-host:6379 down");
	}

	@GetMapping("/me")
	public ApiResponse<AuthenticatedUser> me(@CurrentUser AuthenticatedUser user, HttpServletRequest request) {
		return ApiResponse.ok(user, TraceIdFilter.currentTraceId(request), clock);
	}
}
