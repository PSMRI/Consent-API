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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.iemr.consent.data.legacy.LegacyBatchStatus;
import com.iemr.consent.data.legacy.LegacyRowStatus;
import com.iemr.consent.model.legacy.LegacyBatchView;
import com.iemr.consent.model.legacy.LegacyRowView;
import com.iemr.consent.service.legacy.LegacyConsentService;
import com.iemr.consent.support.ConsentFixtures;
import com.iemr.consent.support.TestFixtures;
import com.iemr.consent.utils.auth.AuthenticatedUser;
import com.iemr.consent.utils.auth.CurrentUserArgumentResolver;
import com.iemr.consent.utils.auth.ServiceKeyInterceptor;
import com.iemr.consent.utils.exception.ErrorResponseWriter;
import com.iemr.consent.utils.exception.GlobalExceptionHandler;
import com.iemr.consent.utils.http.TraceIdFilter;
import com.iemr.consent.utils.idempotency.RequestHasher;

/** Feature 008: the legacy upload endpoints (contract consent-api-legacy.openapi.yaml). */
class LegacyConsentControllerTest {

	private static final String BASE = "/consent/v1/admin/legacy/batches";

	private static final String KEY = "3f2504e0-4f89-11d3-9a0c-0305e82c3301";

	private static final String BATCH = "8a1c3e2b-1111-4222-8333-944455556666";

	private static final LegacyBatchView VIEW = new LegacyBatchView(BATCH, "old.csv", "worker", TestFixtures.NOW,
			LegacyBatchStatus.CHECKED, 1, 1, 0, 0, 0,
			List.of(new LegacyRowView(2, "100245678901", LegacyRowStatus.VALID, List.of())));

	private LegacyConsentService service;

	private MockMvc mvc;

	@BeforeEach
	void setUp() {
		service = mock(LegacyConsentService.class);
		mvc = MockMvcBuilders
				.standaloneSetup(new LegacyConsentController(service,
				new RequestHasher(TestFixtures.objectMapper()), TestFixtures.CLOCK))
				.addMappedInterceptors(new String[] { "/consent/v1/admin/**" },
						new ServiceKeyInterceptor(TestFixtures.consentProperties()))
				.setControllerAdvice(new GlobalExceptionHandler(new ErrorResponseWriter(
						TestFixtures.objectMapper(), TestFixtures.CLOCK,
						TestFixtures.consentProperties())))
				.setCustomArgumentResolvers(new CurrentUserArgumentResolver())
						.addFilters(new TraceIdFilter()).build();
	}

	private static MockHttpServletRequestBuilder admin(MockHttpServletRequestBuilder request) {
		return request.requestAttr(AuthenticatedUser.ATTRIBUTE, ConsentFixtures.WORKER)
				.header(ServiceKeyInterceptor.HEADER, TestFixtures.SERVICE_KEY)
				.header("Idempotency-Key", KEY);
	}

	private static MockHttpServletRequestBuilder check(String body) {
		return admin(post(BASE)).contentType(MediaType.APPLICATION_JSON).content(body);
	}

	private static String rows(int count) {
		return "{\"fileName\":\"old.csv\",\"rows\":[" + IntStream.rangeClosed(1, count)
				.mapToObj(i -> "{\"rowNo\":" + i + "}").collect(Collectors.joining(",")) + "]}";
	}

	@Test
	void checkIs201WithTheBatch() throws Exception {
		when(service.check(any(), eq(KEY), anyString(), eq(ConsentFixtures.WORKER), anyString()))
				.thenReturn(VIEW);

		mvc.perform(check(rows(1))).andExpect(status().isCreated()).andExpect(jsonPath("$.statusCode")
		.value(201))
				.andExpect(jsonPath("$.data.batchId").value(BATCH))
				.andExpect(jsonPath("$.data.rows[0].status").value("VALID"))
				.andExpect(jsonPath("$.data.rows[0].reasons").isEmpty());
	}

	@Test
	void emptyOrTooLargeFilesAreRefused() throws Exception {
		mvc.perform(check("{\"rows\":[]}")).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
				.andExpect(jsonPath("$.error.details[0].field").value("rows"));
		mvc.perform(check(rows(5001))).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.details[0].field").value("rows"));
		verifyNoInteractions(service);
	}

	@Test
	void rowFieldsKeepTheirNames() throws Exception {
		mvc.perform(check("{\"rows\":[{\"beneficiaryId\":\"1\"}]}")).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.details[0].field").value("rows[0].rowNo"));
		mvc.perform(check("{\"rows\":[{\"rowNo\":1,\"languageCode\":\"abcdefghijk\"}]}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.details[0].field").value("rows[0].languageCode"));
		verifyNoInteractions(service);
	}

	@Test
	void serviceKeyAndIdempotencyKeyAreRequired() throws Exception {
		mvc.perform(post(BASE).requestAttr(AuthenticatedUser.ATTRIBUTE, ConsentFixtures.WORKER)
				.header("Idempotency-Key", KEY).contentType(MediaType.APPLICATION_JSON)
				.content(rows(1)))
				.andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code")
						.value("ACCESS_DENIED"));
		mvc.perform(post(BASE).requestAttr(AuthenticatedUser.ATTRIBUTE, ConsentFixtures.WORKER)
				.header(ServiceKeyInterceptor.HEADER, TestFixtures.SERVICE_KEY)
				.contentType(MediaType.APPLICATION_JSON)
				.content(rows(1))).andExpect(status().isBadRequest());
		mvc.perform(post(BASE + "/" + BATCH + "/import").requestAttr(AuthenticatedUser.ATTRIBUTE,
				ConsentFixtures.WORKER).header(ServiceKeyInterceptor.HEADER, TestFixtures.SERVICE_KEY))
				.andExpect(status().isBadRequest());
		mvc.perform(post(BASE + "/" + BATCH + "/import").requestAttr(AuthenticatedUser.ATTRIBUTE,
				ConsentFixtures.WORKER).header("Idempotency-Key", KEY)).andExpect(status()
				.isForbidden());
		verifyNoInteractions(service);
	}

	@Test
	void importIs200() throws Exception {
		when(service.importBatch(eq(BATCH), eq(KEY), anyString(), eq(ConsentFixtures.WORKER), anyString()))
				.thenReturn(VIEW);

		mvc.perform(admin(post(BASE + "/" + BATCH.toUpperCase() + "/import"))).andExpect(status().isOk())
		.andExpect(jsonPath("$.statusCode").value(200)).andExpect(jsonPath("$.data.total")
						.value(1));
	}

	@Test
	void aBadBatchIdIs400() throws Exception {
		mvc.perform(admin(post(BASE + "/not-a-uuid/import"))).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.details[0].field").value("batchId"));
		verifyNoInteractions(service);
	}
}
