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

package com.iemr.consent.controller.health;

import java.time.Clock;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.iemr.consent.service.health.HealthService;
import com.iemr.consent.service.health.HealthStatus;
import com.iemr.consent.utils.exception.ConsentApiException;
import com.iemr.consent.utils.exception.ErrorCode;
import com.iemr.consent.utils.http.TraceIdFilter;
import com.iemr.consent.utils.response.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Public health check (FR-017), as Common-API's {@code controller/health/HealthController}:
 * 200 when the database and cache are up, 503 {@code DEPENDENCY_UNAVAILABLE} naming the failed
 * component(s) otherwise. No connection details are returned.
 */
@RestController
public class HealthController {

	private final HealthService healthService;

	private final Clock clock;

	public HealthController(HealthService healthService, Clock clock) {
		this.healthService = healthService;
		this.clock = clock;
	}

	@Operation(summary = "Service and dependency health",
			description = "Public. 200 when the database and cache are up; "
					+ "503 naming the failed component.")
	@GetMapping(value = "/health", produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<ApiResponse<HealthStatus>> health(HttpServletRequest request) {
		HealthStatus status = healthService.check();
		if (!status.isUp()) {
			throw new ConsentApiException(ErrorCode.DEPENDENCY_UNAVAILABLE,
					"Service unavailable: " + status.downComponents());
		}
		return ResponseEntity.ok(ApiResponse.ok(status, TraceIdFilter.currentTraceId(request), clock));
	}
}
