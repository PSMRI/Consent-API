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
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.iemr.consent.data.notice.NoticeContext;
import com.iemr.consent.model.notice.admin.DraftRequest;
import com.iemr.consent.model.notice.admin.MessageEntry;
import com.iemr.consent.model.notice.admin.PublishRequest;
import com.iemr.consent.service.notice.admin.NoticeAuthoringService;
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
 * Admin authoring of a project's consent message (feature 006). Called only by Common-API's A6 to A8: besides the
 * user's login, every call needs {@code X-Consent-Service-Key} ({@code ServiceKeyInterceptor}). Writes need an
 * {@code Idempotency-Key}; the actor is always the logged-in user.
 */
@RestController
@RequestMapping(value = "/consent/v1/admin/projects/{projectId}/notices", produces = MediaType.APPLICATION_JSON_VALUE)
public class AdminNoticeController {

	private final NoticeAuthoringService service;

	private final RequestHasher hasher;

	private final Clock clock;

	public AdminNoticeController(NoticeAuthoringService service, RequestHasher hasher, Clock clock) {
		this.service = service;
		this.hasher = hasher;
		this.clock = clock;
	}

	@Operation(summary = "A6: a project's consent messages, one entry per context and language")
	@GetMapping
	public ApiResponse<List<MessageEntry>> list(@PathVariable Integer projectId,
			HttpServletRequest request) {
		return ApiResponse.ok(service.list(positive(projectId)), TraceIdFilter.currentTraceId(request), clock);
	}

	@Operation(summary = "A7: save the English draft (needs Idempotency-Key)")
	@PutMapping(value = "/{context}/draft", consumes = MediaType.APPLICATION_JSON_VALUE)
	public ApiResponse<MessageEntry> saveDraft(@PathVariable Integer projectId,
			@PathVariable NoticeContext context, @Valid @RequestBody DraftRequest body,
			@CurrentUser AuthenticatedUser user, HttpServletRequest request) {
		String key = IdempotencyKey.require(request);
		MessageEntry entry = service.saveDraft(positive(projectId), context, body, key,
				hash("DRAFT", projectId, context, body), user, ClientIp.of(request));
		return ApiResponse.ok(entry, TraceIdFilter.currentTraceId(request), clock);
	}

	@Operation(summary = "A8: publish the draft, translated into the requested languages (needs Idempotency-Key)")
	@PostMapping(value = "/{context}/publish", consumes = MediaType.APPLICATION_JSON_VALUE)
	public ApiResponse<MessageEntry> publish(@PathVariable Integer projectId,
			@PathVariable NoticeContext context, @Valid @RequestBody PublishRequest body,
			@CurrentUser AuthenticatedUser user, HttpServletRequest request) {
		String key = IdempotencyKey.require(request);
		MessageEntry entry = service.publish(positive(projectId), context, body, key,
				hash("PUBLISH", projectId, context, body), user, ClientIp.of(request));
		return ApiResponse.ok(entry, TraceIdFilter.currentTraceId(request), clock);
	}

	/**
	 * Checked here rather than with {@code @Positive}: a constraint on a path variable makes Spring validate the
	 * whole method, and the request body's errors would lose their field names ({@code languageCode},
	 * {@code grievance.phone}).
	 */
	private static Integer positive(Integer projectId) {
		if (projectId < 1) {
			throw new ConsentApiException(ErrorCode.VALIDATION_FAILED, null, null,
					List.of(new FieldErrorDetail("projectId", "must be greater than 0")));
		}
		return projectId;
	}

	private String hash(String action, Integer projectId, NoticeContext context, Object body) {
		return hasher.hash(Map.of("action", action, "project", projectId, "context", context.name(),
				"body", body));
	}
}
