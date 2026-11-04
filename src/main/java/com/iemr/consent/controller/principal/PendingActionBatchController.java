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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.iemr.consent.data.notice.NoticeContext;
import com.iemr.consent.model.principal.PendingAction;
import com.iemr.consent.model.principal.PendingActionBatchRequest;
import com.iemr.consent.service.principal.PendingActionService;
import com.iemr.consent.utils.exception.ConsentApiException;
import com.iemr.consent.utils.exception.ErrorCode;
import com.iemr.consent.utils.http.TraceIdFilter;
import com.iemr.consent.utils.response.ApiResponse;
import com.iemr.consent.utils.response.FieldErrorDetail;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

/**
 * API 15 (feature 007): the pending actions of every patient on an MMU search page, so the "Consent pending" chip
 * needs one request instead of one per patient. Each list is exactly API 9's answer. A read: nothing is written, no
 * {@code Idempotency-Key} is needed, and patient ids are never logged.
 */
@RestController
public class PendingActionBatchController {

	/** The app's search page size (front-end list, 3 Nov 2026). */
	static final int MAX_IDS = 100;

	private final PendingActionService pendingActionService;

	private final Clock clock;

	public PendingActionBatchController(PendingActionService pendingActionService, Clock clock) {
		this.pendingActionService = pendingActionService;
		this.clock = clock;
	}

	@Operation(summary = "Pending actions of up to 100 patients (front-end API 15)")
	@PostMapping(value = "/consent/v1/principals/pending-actions/batch",
			consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
	public ApiResponse<Map<Long, List<PendingAction>>> batch(@Valid @RequestBody PendingActionBatchRequest body,
			@RequestParam(defaultValue = "REGISTRATION") NoticeContext context,
			HttpServletRequest request) {
		Set<Long> ids = new LinkedHashSet<>(body.beneficiaryRegIds());
		if (ids.size() > MAX_IDS) {
			FieldErrorDetail detail = new FieldErrorDetail("beneficiaryRegIds",
					"must contain at most " + MAX_IDS + " different ids");
			throw new ConsentApiException(ErrorCode.VALIDATION_FAILED, null, null, List.of(detail));
		}
		Map<Long, List<PendingAction>> pending = pendingActionService.pendingFor(ids, context);
		return ApiResponse.ok(pending, TraceIdFilter.currentTraceId(request), clock);
	}
}
