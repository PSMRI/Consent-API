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

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.iemr.consent.data.notice.NoticeContext;
import com.iemr.consent.model.notice.ActiveNotice;
import com.iemr.consent.model.notice.NoticeLanguage;
import com.iemr.consent.service.notice.NoticeService;
import com.iemr.consent.support.TestFixtures;
import com.iemr.consent.utils.exception.ConsentApiException;
import com.iemr.consent.utils.exception.ErrorCode;
import com.iemr.consent.utils.exception.ErrorResponseWriter;
import com.iemr.consent.utils.exception.GlobalExceptionHandler;
import com.iemr.consent.utils.http.TraceIdFilter;

class NoticeControllerTest {

	private NoticeService service;

	private MockMvc mvc;

	@BeforeEach
	void setUp() {
		service = mock(NoticeService.class);
		mvc = MockMvcBuilders.standaloneSetup(new NoticeController(service, TestFixtures.CLOCK))
				.setControllerAdvice(new GlobalExceptionHandler(new ErrorResponseWriter(TestFixtures.objectMapper(),
						TestFixtures.CLOCK, TestFixtures.consentProperties())))
				.addFilters(new TraceIdFilter()).build();
	}

	@Test
	void languages() throws Exception {
		when(service.languages(NoticeContext.REGISTRATION)).thenReturn(List.of(new NoticeLanguage("hi", "हिन्दी", true)));

		mvc.perform(get("/consent/v1/notices/active/languages").param("context", "REGISTRATION"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.statusCode").value(200))
				.andExpect(jsonPath("$.data[0].languageCode").value("hi"))
				.andExpect(jsonPath("$.data[0].hasAudio").value(true));
	}

	@Test
	void activeNotice() throws Exception {
		when(service.activeNotice(NoticeContext.REVISIT, "hi")).thenReturn(new ActiveNotice(11L, "hi", "REVISIT", "b",
				"h", null, "d", "r", "rc", "w", "g", List.of()));

		mvc.perform(get("/consent/v1/notices/active").param("context", "REVISIT").param("lang", "hi"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data.noticeVersionId").value(11));
	}

	@Test
	void badParametersAre400() throws Exception {
		mvc.perform(get("/consent/v1/notices/active").param("context", "REGISTRATION"))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("MALFORMED_REQUEST"));
		mvc.perform(get("/consent/v1/notices/active").param("context", "OTHER").param("lang", "hi"))
				.andExpect(status().isBadRequest());
		mvc.perform(get("/consent/v1/notices/active").param("context", "REGISTRATION").param("lang", "HINDI"))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
	}

	@Test
	void notFound() throws Exception {
		when(service.activeNotice(NoticeContext.REGISTRATION, "xx"))
				.thenThrow(new ConsentApiException(ErrorCode.NOT_FOUND));

		mvc.perform(get("/consent/v1/notices/active").param("context", "REGISTRATION").param("lang", "xx"))
				.andExpect(status().isNotFound()).andExpect(jsonPath("$.statusCode").value(404));
	}
}
