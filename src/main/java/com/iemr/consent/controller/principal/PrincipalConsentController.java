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

package com.iemr.consent.controller.principal;

import java.time.Clock;
import java.util.List;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.iemr.consent.data.notice.NoticeContext;
import com.iemr.consent.model.principal.PatientConsents;
import com.iemr.consent.model.principal.PendingAction;
import com.iemr.consent.model.principal.PreferenceRequest;
import com.iemr.consent.model.principal.WithdrawalRequest;
import com.iemr.consent.model.principal.WithdrawalResult;
import com.iemr.consent.service.principal.PendingActionService;
import com.iemr.consent.service.principal.PrincipalConsentService;
import com.iemr.consent.utils.auth.AuthenticatedUser;
import com.iemr.consent.utils.auth.CurrentUser;
import com.iemr.consent.utils.http.ClientIp;
import com.iemr.consent.utils.http.TraceIdFilter;
import com.iemr.consent.utils.idempotency.IdempotencyKey;
import com.iemr.consent.utils.idempotency.RequestHasher;
import com.iemr.consent.utils.response.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;

/**
 * A patient's consents in the preference centre (front-end APIs 8 – 11). Writes need an
 * {@code Idempotency-Key}; the request hash covers the patient id as well as the body, so a key cannot
 * replay another patient's result (research R2).
 */
@RestController
@RequestMapping(value = "/consent/v1/principals/{benRegId}", produces = MediaType.APPLICATION_JSON_VALUE)
public class PrincipalConsentController {

	private final PrincipalConsentService consentService;

	private final PendingActionService pendingActionService;

	private final RequestHasher hasher;

	private final Clock clock;

	public PrincipalConsentController(PrincipalConsentService consentService,
			PendingActionService pendingActionService, RequestHasher hasher, Clock clock) {
		this.consentService = consentService;
		this.pendingActionService = pendingActionService;
		this.hasher = hasher;
		this.clock = clock;
	}

	@Operation(summary = "Patient's current consents and history (front-end API 8)")
	@GetMapping("/consents")
	public ApiResponse<PatientConsents> consents(@PathVariable @Positive Long benRegId,
			HttpServletRequest request) {
		return ApiResponse.ok(consentService.consents(benRegId), TraceIdFilter.currentTraceId(request), clock);
	}

	@Operation(summary = "Consent actions still needed for a patient (front-end API 9)")
	@GetMapping("/pending-actions")
	public ApiResponse<List<PendingAction>> pendingActions(@PathVariable @Positive Long benRegId,
			@RequestParam(defaultValue = "REGISTRATION") NoticeContext context,
			HttpServletRequest request) {
		List<PendingAction> actions = pendingActionService.pending(benRegId, context);
		return ApiResponse.ok(actions, TraceIdFilter.currentTraceId(request), clock);
	}

	@Operation(summary = "Change purposes to GRANT (with OTP) or DENY (front-end API 10; needs Idempotency-Key)")
	@PostMapping(value = "/preferences", consumes = MediaType.APPLICATION_JSON_VALUE)
	public ApiResponse<PatientConsents> changePreference(@PathVariable @Positive Long benRegId,
			@Valid @RequestBody PreferenceRequest body, @CurrentUser AuthenticatedUser user,
			HttpServletRequest request) {
		String key = IdempotencyKey.require(request);
		PatientConsents result = consentService.changePreference(benRegId, body, key, hash(benRegId, body),
				user,
				ClientIp.of(request));
		return ApiResponse.ok(result, TraceIdFilter.currentTraceId(request), clock);
	}

	@Operation(summary = "Withdraw purposes or ALL (front-end API 11; needs Idempotency-Key)")
	@PostMapping(value = "/withdrawals", consumes = MediaType.APPLICATION_JSON_VALUE)
	public ApiResponse<WithdrawalResult> withdraw(@PathVariable @Positive Long benRegId,
			@Valid @RequestBody WithdrawalRequest body, @CurrentUser AuthenticatedUser user,
			HttpServletRequest request) {
		String key = IdempotencyKey.require(request);
		WithdrawalResult result = consentService.withdraw(benRegId, body, key, hash(benRegId, body), user,
				ClientIp.of(request));
		return ApiResponse.ok(result, TraceIdFilter.currentTraceId(request), clock);
	}

	private String hash(Long benRegId, Object body) {
		return hasher.hash(Map.of("target", benRegId, "body", body));
	}
}
