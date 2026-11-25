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

package com.iemr.consent.controller.admin;

import java.time.Clock;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.iemr.consent.model.legacy.LegacyBatchView;
import com.iemr.consent.model.legacy.LegacyCheckRequest;
import com.iemr.consent.service.legacy.LegacyConsentService;
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

/**
 * Admin legacy consent upload (A9–A10, feature 008). Called only by Common-API, which looks up the Beneficiary IDs:
 * besides the user's login, every call needs {@code X-Consent-Service-Key} ({@code ServiceKeyInterceptor}). Both
 * writes need an {@code Idempotency-Key}; the uploader and importer are always the logged-in user. Problems with a
 * row are reported inside the batch, not as request errors.
 */
@RestController
@RequestMapping(value = "/consent/v1/admin/legacy/batches", produces = MediaType.APPLICATION_JSON_VALUE)
public class LegacyConsentController {

	private static final Pattern UUID_TEXT = Pattern
			.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

	private final LegacyConsentService service;

	private final RequestHasher hasher;

	private final Clock clock;

	public LegacyConsentController(LegacyConsentService service, RequestHasher hasher, Clock clock) {
		this.service = service;
		this.hasher = hasher;
		this.clock = clock;
	}

	@Operation(summary = "A9: check the rows of a legacy consent file and store the batch (needs Idempotency-Key)")
	@PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<ApiResponse<LegacyBatchView>> check(@Valid @RequestBody LegacyCheckRequest body,
			@CurrentUser AuthenticatedUser user, HttpServletRequest request) {
		String key = IdempotencyKey.require(request);
		LegacyBatchView batch = service.check(body, key, hasher.hash(Map.of("action", "CHECK", "body", body)),
				user,
				ClientIp.of(request));
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(ApiResponse.created(batch, TraceIdFilter.currentTraceId(request), clock));
	}

	@Operation(summary = "A10: import the valid rows of a checked batch, once (needs Idempotency-Key)")
	@PostMapping("/{batchId}/import")
	public ApiResponse<LegacyBatchView> importBatch(@PathVariable String batchId,
			@CurrentUser AuthenticatedUser user, HttpServletRequest request) {
		String key = IdempotencyKey.require(request);
		String id = batchId(batchId);
		LegacyBatchView batch = service.importBatch(id, key, hasher.hash(Map.of("action", "IMPORT", "batch",
				id)),
				user, ClientIp.of(request));
		return ApiResponse.ok(batch, TraceIdFilter.currentTraceId(request), clock);
	}

	/** Checked by hand rather than with {@code @Pattern}: see {@code AdminNoticeController#positive}. */
	private static String batchId(String batchId) {
		if (!UUID_TEXT.matcher(batchId).matches()) {
			throw new ConsentApiException(ErrorCode.VALIDATION_FAILED, null, null,
					List.of(new FieldErrorDetail("batchId", "must be a UUID")));
		}
		return batchId.toLowerCase(Locale.ROOT);
	}
}
