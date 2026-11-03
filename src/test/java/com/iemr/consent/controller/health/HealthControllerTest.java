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

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.iemr.consent.service.health.HealthService;
import com.iemr.consent.service.health.HealthStatus;
import com.iemr.consent.service.health.HealthStatus.ComponentStatus;
import com.iemr.consent.support.TestFixtures;
import com.iemr.consent.utils.exception.ErrorResponseWriter;
import com.iemr.consent.utils.exception.GlobalExceptionHandler;
import com.iemr.consent.utils.http.TraceIdFilter;

class HealthControllerTest {

	private HealthService healthService;

	private MockMvc mvc;

	@BeforeEach
	void setUp() {
		healthService = mock(HealthService.class);
		mvc = MockMvcBuilders.standaloneSetup(new HealthController(healthService, TestFixtures.CLOCK))
				.setControllerAdvice(new GlobalExceptionHandler(new ErrorResponseWriter(TestFixtures.objectMapper(),
						TestFixtures.CLOCK, TestFixtures.consentProperties())))
				.addFilters(new TraceIdFilter()).build();
	}

	private static HealthStatus health(String database, String cache) {
		Map<String, ComponentStatus> components = new LinkedHashMap<>();
		components.put("database", new ComponentStatus(database));
		components.put("cache", new ComponentStatus(cache));
		String overall = "UP".equals(database) && "UP".equals(cache) ? "UP" : "DOWN";
		return new HealthStatus(overall, "2026-10-29T10:00:00Z", components);
	}

	@Test
	@DisplayName("US3-2: dependencies up, no login needed: 200 UP")
	void up() throws Exception {
		when(healthService.check()).thenReturn(health("UP", "UP"));

		mvc.perform(get("/health")).andExpect(status().isOk()).andExpect(jsonPath("$.statusCode").value(200))
				.andExpect(jsonPath("$.data.status").value("UP"))
				.andExpect(jsonPath("$.data.components.database.status").value("UP"))
				.andExpect(jsonPath("$.data.components.cache.status").value("UP"))
				.andExpect(jsonPath("$.data.up").doesNotExist());
	}

	@Test
	@DisplayName("US3-3 / edge: cache down, database up: 503 naming the cache, no data")
	void cacheDown() throws Exception {
		when(healthService.check()).thenReturn(health("UP", "DOWN"));

		mvc.perform(get("/health")).andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.statusCode").value(503)).andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.error.code").value("DEPENDENCY_UNAVAILABLE"))
				.andExpect(jsonPath("$.data").value(nullValue()))
				.andExpect(jsonPath("$.message").value("Service unavailable: cache"));
	}

	@Test
	void bothDownNamesBoth() throws Exception {
		when(healthService.check()).thenReturn(health("DOWN", "DOWN"));

		mvc.perform(get("/health")).andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.message").value("Service unavailable: database, cache"));
	}
}
