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

package com.iemr.consent.controller.otp;

import java.time.Clock;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.iemr.consent.model.otp.OtpChallengeView;
import com.iemr.consent.model.otp.OtpSendRequest;
import com.iemr.consent.model.otp.OtpVerifyRequest;
import com.iemr.consent.model.otp.OtpVerifyResult;
import com.iemr.consent.service.otp.OtpService;
import com.iemr.consent.utils.auth.AuthenticatedUser;
import com.iemr.consent.utils.auth.CurrentUser;
import com.iemr.consent.utils.http.ForwardedAuth;
import com.iemr.consent.utils.http.TraceIdFilter;
import com.iemr.consent.utils.idempotency.IdempotencyKey;
import com.iemr.consent.utils.idempotency.RequestHasher;
import com.iemr.consent.utils.response.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;

/** OTP proof for the consent dialog (front-end APIs 4 and 5; FR-004 – FR-006). */
@RestController
@RequestMapping(value = "/consent/v1/otp/challenges", produces = MediaType.APPLICATION_JSON_VALUE)
public class OtpController {

	private static final String UUID = "^[0-9a-fA-F-]{36}$";

	private final OtpService otpService;

	private final RequestHasher hasher;

	private final Clock clock;

	public OtpController(OtpService otpService, RequestHasher hasher, Clock clock) {
		this.otpService = otpService;
		this.hasher = hasher;
		this.clock = clock;
	}

	@Operation(summary = "Send an OTP (needs Idempotency-Key)")
	@PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<ApiResponse<OtpChallengeView>> send(@Valid @RequestBody OtpSendRequest body,
			@CurrentUser AuthenticatedUser user, HttpServletRequest request) {
		String key = IdempotencyKey.require(request);
		ForwardedAuth auth = ForwardedAuth.from(request);
		OtpChallengeView view = otpService.send(body, key, hasher.hash(body), user, auth);
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(ApiResponse.created(view, TraceIdFilter.currentTraceId(request), clock));
	}

	@Operation(summary = "Verify an OTP")
	@PostMapping(value = "/{challengeId}/verify", consumes = MediaType.APPLICATION_JSON_VALUE)
	public ApiResponse<OtpVerifyResult> verify(@PathVariable @Pattern(regexp = UUID) String challengeId,
			@Valid @RequestBody OtpVerifyRequest body, @CurrentUser AuthenticatedUser user,
			HttpServletRequest request) {
		return ApiResponse.ok(otpService.verify(challengeId, body.otp(), user, ForwardedAuth.from(request)),
				TraceIdFilter.currentTraceId(request), clock);
	}
}
