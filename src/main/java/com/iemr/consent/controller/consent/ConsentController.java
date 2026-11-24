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

package com.iemr.consent.controller.consent;

import java.time.Clock;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.iemr.consent.model.consent.ConsentDetail;
import com.iemr.consent.model.consent.SaveConsentRequest;
import com.iemr.consent.model.consent.SaveConsentResult;
import com.iemr.consent.service.consent.ConsentDetailService;
import com.iemr.consent.service.consent.ConsentService;
import com.iemr.consent.utils.auth.AuthenticatedUser;
import com.iemr.consent.utils.auth.CurrentUser;
import com.iemr.consent.utils.http.ClientIp;
import com.iemr.consent.utils.http.ForwardedAuth;
import com.iemr.consent.utils.http.TraceIdFilter;
import com.iemr.consent.utils.idempotency.IdempotencyKey;
import com.iemr.consent.utils.idempotency.RequestHasher;
import com.iemr.consent.utils.response.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

/** Saves the consent dialog's Accept or Decline (front-end API 7; FR-009 – FR-017) and reads one back (API 17). */
@RestController
@RequestMapping(value = "/consent/v1/consents", produces = MediaType.APPLICATION_JSON_VALUE)
public class ConsentController {

	private final ConsentService consentService;

	private final ConsentDetailService detailService;

	private final RequestHasher hasher;

	private final Clock clock;

	public ConsentController(ConsentService consentService, ConsentDetailService detailService,
			RequestHasher hasher, Clock clock) {
		this.consentService = consentService;
		this.detailService = detailService;
		this.hasher = hasher;
		this.clock = clock;
	}

	@Operation(summary = "Save a consent decision (needs Idempotency-Key)")
	@PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<ApiResponse<SaveConsentResult>> save(@Valid @RequestBody SaveConsentRequest body,
			@CurrentUser AuthenticatedUser user, HttpServletRequest request) {
		String key = IdempotencyKey.require(request);
		String ip = ClientIp.of(request);
		SaveConsentResult result = consentService.save(body, key, hasher.hash(body), user, ip,
				ForwardedAuth.from(request));
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(ApiResponse.created(result, TraceIdFilter.currentTraceId(request), clock));
	}

	@Operation(summary = "One consent in full by its consent id: patient, notice, purposes, guardian, proofs, SMS")
	@GetMapping("/{consentId}")
	public ApiResponse<ConsentDetail> detail(@PathVariable String consentId, @CurrentUser AuthenticatedUser user,
			HttpServletRequest request) {
		ConsentDetail detail = detailService.detail(consentId, user, ClientIp.of(request));
		return ApiResponse.ok(detail, TraceIdFilter.currentTraceId(request), clock);
	}
}
