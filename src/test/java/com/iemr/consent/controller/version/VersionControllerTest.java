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

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.iemr.consent.service.version.BuildInfo;
import com.iemr.consent.service.version.VersionService;
import com.iemr.consent.support.TestFixtures;
import com.iemr.consent.utils.http.TraceIdFilter;

class VersionControllerTest {

	@Test
	@DisplayName("US3-4: version without login returns build information")
	void returnsBuildInfo() throws Exception {
		VersionService service = mock(VersionService.class);
		when(service.getBuildInfo()).thenReturn(new BuildInfo("0.1.0", "t", "main", "abc1234"));
		MockMvc mvc = MockMvcBuilders.standaloneSetup(new VersionController(service, TestFixtures.CLOCK))
				.addFilters(new TraceIdFilter()).build();

		mvc.perform(get("/version")).andExpect(status().isOk()).andExpect(jsonPath("$.statusCode").value(200))
				.andExpect(jsonPath("$.success").value(true)).andExpect(jsonPath("$.data.version").value("0.1.0"))
				.andExpect(jsonPath("$.data.buildTimestamp").value("t"))
				.andExpect(jsonPath("$.data.branch").value("main"))
				.andExpect(jsonPath("$.data.commitHash").value("abc1234"))
				.andExpect(jsonPath("$.traceId").isNotEmpty());
	}
}
