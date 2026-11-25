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

package com.iemr.consent.controller.grant;

import java.time.Clock;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.iemr.consent.data.grant.GrantStatus;
import com.iemr.consent.model.grant.AccessGrantView;
import com.iemr.consent.model.grant.AccessRequest;
import com.iemr.consent.model.grant.ApproveRequest;
import com.iemr.consent.service.grant.AccessGrantService;
import com.iemr.consent.utils.auth.AuthenticatedUser;
import com.iemr.consent.utils.auth.CurrentUser;
import com.iemr.consent.utils.exception.ConsentApiException;
import com.iemr.consent.utils.exception.ErrorCode;
import com.iemr.consent.utils.http.ClientIp;
import com.iemr.consent.utils.http.TraceIdFilter;
import com.iemr.consent.utils.idempotency.IdempotencyKey;
import com.iemr.consent.utils.idempotency.RequestHasher;
import com.iemr.consent.utils.response.ApiResponse;
import com.iemr.consent.utils.response.FieldErrorDetail;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

/**
 * Doctor access grants: the doctor's request, and the patient's list, approval and cancellation (front-end
 * APIs 12 – 14). Writes need an {@code Idempotency-Key}; approve and revoke hash the grant id with the body
 * (research R2).
 */
@RestController
public class AccessGrantController {

	private static final String UUID = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}"
			+ "-[0-9a-fA-F]{12}$";

	private final AccessGrantService service;

	private final RequestHasher hasher;

	private final Clock clock;

	public AccessGrantController(AccessGrantService service, RequestHasher hasher, Clock clock) {
		this.service = service;
		this.hasher = hasher;
		this.clock = clock;
	}

	@Operation(summary = "Doctor requests time-limited access to a patient (needs Idempotency-Key)")
	@PostMapping(value = "/consent/v1/access-grants", consumes = MediaType.APPLICATION_JSON_VALUE,
			produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<ApiResponse<AccessGrantView>> request(@Valid @RequestBody AccessRequest body,
			@CurrentUser AuthenticatedUser user, HttpServletRequest request) {
		String key = IdempotencyKey.require(request);
		AccessGrantView grant = service.request(body, key, hasher.hash(body), user, ClientIp.of(request));
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(ApiResponse.created(grant, TraceIdFilter.currentTraceId(request), clock));
	}

	@Operation(summary = "Patient's access grants, optionally by status (front-end API 12)")
	@GetMapping(value = "/consent/v1/principals/{benRegId}/access-grants",
			produces = MediaType.APPLICATION_JSON_VALUE)
	public ApiResponse<List<AccessGrantView>> list(@PathVariable @Positive Long benRegId,
			@RequestParam(required = false) String status, HttpServletRequest request) {
		return ApiResponse.ok(service.list(benRegId, statuses(status)), TraceIdFilter.currentTraceId(request),
				clock);
	}

	@Operation(summary = "Approve a pending grant by OTP (front-end API 13; needs Idempotency-Key)")
	@PostMapping(value = "/consent/v1/access-grants/{grantId}/approve", consumes = MediaType.APPLICATION_JSON_VALUE,
			produces = MediaType.APPLICATION_JSON_VALUE)
	public ApiResponse<AccessGrantView> approve(@PathVariable @Pattern(regexp = UUID) String grantId,
			@Valid @RequestBody ApproveRequest body, @CurrentUser AuthenticatedUser user,
			HttpServletRequest request) {
		String key = IdempotencyKey.require(request);
		AccessGrantView grant = service.approve(grantId, body, key, hash(grantId, body), user,
				ClientIp.of(request));
		return ApiResponse.ok(grant, TraceIdFilter.currentTraceId(request), clock);
	}

	@Operation(summary = "Cancel a pending or active grant (front-end API 14; needs Idempotency-Key)")
	@PostMapping(value = "/consent/v1/access-grants/{grantId}/revoke", consumes = MediaType.APPLICATION_JSON_VALUE,
			produces = MediaType.APPLICATION_JSON_VALUE)
	public ApiResponse<AccessGrantView> revoke(@PathVariable @Pattern(regexp = UUID) String grantId,
			@RequestBody Map<String, Object> body, @CurrentUser AuthenticatedUser user,
			HttpServletRequest request) {
		String key = IdempotencyKey.require(request);
		AccessGrantView grant = service.revoke(grantId, key, hash(grantId, body), user, ClientIp.of(request));
		return ApiResponse.ok(grant, TraceIdFilter.currentTraceId(request), clock);
	}

	private String hash(String grantId, Object body) {
		return hasher.hash(Map.of("target", grantId.toLowerCase(Locale.ROOT), "body", body));
	}

	/** Parses {@code ACTIVE,PENDING}; blank or missing = all statuses. */
	private static Set<GrantStatus> statuses(String status) {
		Set<GrantStatus> statuses = EnumSet.noneOf(GrantStatus.class);
		if (status == null || status.isBlank()) {
			return statuses;
		}
		for (String value : Arrays.stream(status.split(",")).map(String::trim).toList()) {
			try {
				statuses.add(GrantStatus.valueOf(value));
			} catch (IllegalArgumentException e) {
				throw new ConsentApiException(ErrorCode.VALIDATION_FAILED, null, null,
						List.of(new FieldErrorDetail("status",
						"must be PENDING, ACTIVE, REVOKED or EXPIRED")));
			}
		}
		return statuses;
	}
}
