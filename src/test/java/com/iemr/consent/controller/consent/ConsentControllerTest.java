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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.iemr.consent.data.consent.Channel;
import com.iemr.consent.data.consent.ConsentMode;
import com.iemr.consent.data.consent.ConsentingParty;
import com.iemr.consent.data.consent.Decision;
import com.iemr.consent.data.consent.EventType;
import com.iemr.consent.data.consent.PurposeStatus;
import com.iemr.consent.data.consent.SyncStatus;
import com.iemr.consent.model.consent.ConsentDetail;
import com.iemr.consent.model.consent.NotificationResult;
import com.iemr.consent.model.consent.PurposeResult;
import com.iemr.consent.model.consent.SaveConsentResult;
import com.iemr.consent.service.consent.ConsentDetailService;
import com.iemr.consent.service.consent.ConsentService;
import com.iemr.consent.support.ConsentFixtures;
import com.iemr.consent.support.TestFixtures;
import com.iemr.consent.utils.auth.AuthenticatedUser;
import com.iemr.consent.utils.auth.CurrentUserArgumentResolver;
import com.iemr.consent.utils.exception.ConsentApiException;
import com.iemr.consent.utils.exception.ErrorCode;
import com.iemr.consent.utils.exception.ErrorResponseWriter;
import com.iemr.consent.utils.exception.GlobalExceptionHandler;
import com.iemr.consent.utils.http.TraceIdFilter;
import com.iemr.consent.utils.idempotency.RequestHasher;

class ConsentControllerTest {

	static final String KEY = "3f2504e0-4f89-11d3-9a0c-0305e82c3301";

	static final String BODY = "{\"beneficiaryRegId\":1001,\"eventType\":\"NEW\",\"consentingParty\":\"SELF\","
			+ "\"noticeVersionId\":11,\"languageCode\":\"hi\",\"textHash\":\"" + "a".repeat(64) + "\","
			+ "\"channel\":\"FACILITY\",\"mode\":\"SELF_READ\",\"capturedBy\":999,\"deviceId\":\"van-07\","
			+ "\"serviceLine\":\"MMU\",\"capturedAt\":\"2026-10-29T10:00:00Z\",\"decisions\":["
			+ "{\"purposeCode\":\"P-REG\",\"decision\":\"GRANT\"},{\"purposeCode\":\"P-REM\",\"decision\":\"DENY\"}]}";

	private ConsentService service;

	private ConsentDetailService detailService;

	private MockMvc mvc;

	@BeforeEach
	void setUp() {
		service = mock(ConsentService.class);
		detailService = mock(ConsentDetailService.class);
		mvc = MockMvcBuilders
				.standaloneSetup(new ConsentController(service, detailService,
						new RequestHasher(TestFixtures.objectMapper()), TestFixtures.CLOCK))
				.setControllerAdvice(new GlobalExceptionHandler(new ErrorResponseWriter(TestFixtures.objectMapper(),
						TestFixtures.CLOCK, TestFixtures.consentProperties())))
				.setCustomArgumentResolvers(new CurrentUserArgumentResolver()).addFilters(new TraceIdFilter()).build();
	}

	private static MockHttpServletRequestBuilder save(String body) {
		return post("/consent/v1/consents").requestAttr(AuthenticatedUser.ATTRIBUTE, ConsentFixtures.WORKER)
				.contentType(MediaType.APPLICATION_JSON).content(body);
	}

	@Test
	void savedConsentIs201WithStatuses() throws Exception {
		when(service.save(any(), eq(KEY), anyString(), eq(ConsentFixtures.WORKER), anyString(), any()))
				.thenReturn(new SaveConsentResult("c-1", SyncStatus.SYNCED,
						List.of(new PurposeResult("P-REG", PurposeStatus.GRANTED, 1)), NotificationResult.notRequested()));

		mvc.perform(save(BODY).header("Idempotency-Key", KEY)).andExpect(status().isCreated())
				.andExpect(jsonPath("$.statusCode").value(201)).andExpect(jsonPath("$.data.consentId").value("c-1"))
				.andExpect(jsonPath("$.data.purposes[0].status").value("GRANTED"))
				.andExpect(jsonPath("$.data.notification.status").value("NOT_REQUESTED"));
	}

	@Test
	void missingKeyOrInvalidBodyIs400() throws Exception {
		mvc.perform(save(BODY)).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.details[0].field").value("Idempotency-Key"));
		mvc.perform(save(BODY.replace("van-07", "van 07; drop")).header("Idempotency-Key", KEY))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.details[0].field").value("deviceId"));
		mvc.perform(save("{}").header("Idempotency-Key", KEY)).andExpect(status().isBadRequest());
	}

	@Test
	void saveWithoutAPositivePatientIs400AndNothingIsSaved() throws Exception {
		List<String> bodies = List.of(BODY.replace("\"beneficiaryRegId\":1001,", ""),
				BODY.replace("\"beneficiaryRegId\":1001", "\"beneficiaryRegId\":null"),
				BODY.replace("\"beneficiaryRegId\":1001", "\"beneficiaryRegId\":0"),
				BODY.replace("\"beneficiaryRegId\":1001", "\"beneficiaryRegId\":-5"));
		for (String body : bodies) {
			mvc.perform(save(body).header("Idempotency-Key", KEY)).andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.statusCode").value(400))
					.andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
					.andExpect(jsonPath("$.error.details[0].field").value("beneficiaryRegId"));
		}
		verify(service, never()).save(any(), anyString(), anyString(), any(), anyString(), any());
	}

	@Test
	void staleNoticeIs409() throws Exception {
		when(service.save(any(), anyString(), anyString(), any(), anyString(), any()))
				.thenThrow(new ConsentApiException(ErrorCode.STALE_VERSION));

		mvc.perform(save(BODY).header("Idempotency-Key", KEY)).andExpect(status().isConflict())
				.andExpect(jsonPath("$.error.code").value("STALE_VERSION"));
	}

	@Test
	void oneConsentByItsId() throws Exception {
		ConsentDetail detail = new ConsentDetail("26102948271", 1001L, EventType.NEW, ConsentingParty.SELF,
				Channel.FACILITY, ConsentMode.SELF_READ, "hi", "a".repeat(64), null, null,
				List.of(new ConsentDetail.PurposeDecision("P-REG", "Registration", null, null, true, false,
						Decision.GRANT, PurposeStatus.GRANTED, 1, true, null)),
				null, List.of(), NotificationResult.notRequested());
		when(detailService.detail(eq("26102948271"), eq(ConsentFixtures.WORKER), anyString())).thenReturn(detail);

		mvc.perform(get("/consent/v1/consents/26102948271").requestAttr(AuthenticatedUser.ATTRIBUTE,
				ConsentFixtures.WORKER)).andExpect(status().isOk())
				.andExpect(jsonPath("$.data.beneficiaryRegId").value(1001))
				.andExpect(jsonPath("$.data.purposes[0].purposeCode").value("P-REG"))
				.andExpect(jsonPath("$.data.purposes[0].isMandatory").value(true));
	}

	@Test
	void unknownConsentIs404() throws Exception {
		when(detailService.detail(eq("x"), any(), anyString())).thenThrow(new ConsentApiException(ErrorCode.NOT_FOUND));
		mvc.perform(get("/consent/v1/consents/x").requestAttr(AuthenticatedUser.ATTRIBUTE, ConsentFixtures.WORKER))
				.andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
	}
}
