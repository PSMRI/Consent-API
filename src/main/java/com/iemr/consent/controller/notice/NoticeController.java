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

package com.iemr.consent.controller.notice;

import java.time.Clock;
import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.iemr.consent.data.notice.NoticeContext;
import com.iemr.consent.model.notice.ActiveNotice;
import com.iemr.consent.model.notice.NoticeLanguage;
import com.iemr.consent.service.notice.NoticeService;
import com.iemr.consent.utils.http.TraceIdFilter;
import com.iemr.consent.utils.response.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

/** The consent dialog's notice step (front-end APIs 2 and 3; FR-001, FR-002). */
@RestController
@RequestMapping(value = "/consent/v1/notices", produces = MediaType.APPLICATION_JSON_VALUE)
public class NoticeController {

	private final NoticeService noticeService;

	private final Clock clock;

	public NoticeController(NoticeService noticeService, Clock clock) {
		this.noticeService = noticeService;
		this.clock = clock;
	}

	@Operation(summary = "Languages the active notice is published in (central plus the project's own)")
	@GetMapping("/active/languages")
	public ApiResponse<List<NoticeLanguage>> languages(@RequestParam NoticeContext context,
			@Parameter(description = "Project; adds the languages of its own published notice")
			@RequestParam(required = false) @Positive Integer projectId, HttpServletRequest request) {
		List<NoticeLanguage> languages = noticeService.languages(context, projectId);
		return ApiResponse.ok(languages, TraceIdFilter.currentTraceId(request), clock);
	}

	@Operation(summary = "Active notice for a context and language (the project's own when published)")
	@GetMapping("/active")
	public ApiResponse<ActiveNotice> active(@RequestParam NoticeContext context,
			@RequestParam("lang") @Pattern(regexp = "^[a-z]{2,3}$") String languageCode,
			@Parameter(description = "Project; its own notice in that language if published, else central")
			@RequestParam(required = false) @Positive Integer projectId, HttpServletRequest request) {
		return ApiResponse.ok(noticeService.activeNotice(context, languageCode, projectId),
				TraceIdFilter.currentTraceId(request), clock);
	}
}
