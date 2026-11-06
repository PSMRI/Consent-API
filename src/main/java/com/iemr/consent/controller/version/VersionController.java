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

package com.iemr.consent.controller.version;

import java.time.Clock;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.iemr.consent.service.version.BuildInfo;
import com.iemr.consent.service.version.VersionService;
import com.iemr.consent.utils.http.TraceIdFilter;
import com.iemr.consent.utils.response.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Public build information (FR-018), as Common-API's
 * {@code controller/version/VersionController}.
 */
@RestController
public class VersionController {

	private final VersionService versionService;

	private final Clock clock;

	public VersionController(VersionService versionService, Clock clock) {
		this.versionService = versionService;
		this.clock = clock;
	}

	@Operation(summary = "Running build", description = "Public. Version, build time, branch and commit.")
	@GetMapping(value = "/version", produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<ApiResponse<BuildInfo>> version(HttpServletRequest request) {
		return ResponseEntity.ok(ApiResponse.ok(versionService.getBuildInfo(),
				TraceIdFilter.currentTraceId(request), clock));
	}
}
