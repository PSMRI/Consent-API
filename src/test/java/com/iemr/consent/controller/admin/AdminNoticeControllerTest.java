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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.iemr.consent.data.notice.NoticeContext;
import com.iemr.consent.model.notice.admin.MessageEntry;
import com.iemr.consent.service.notice.admin.NoticeAuthoringService;
import com.iemr.consent.support.ConsentFixtures;
import com.iemr.consent.support.TestFixtures;
import com.iemr.consent.utils.auth.AuthenticatedUser;
import com.iemr.consent.utils.auth.CurrentUserArgumentResolver;
import com.iemr.consent.utils.auth.ServiceKeyInterceptor;
import com.iemr.consent.utils.exception.ConsentApiException;
import com.iemr.consent.utils.exception.ErrorCode;
import com.iemr.consent.utils.exception.ErrorResponseWriter;
import com.iemr.consent.utils.exception.GlobalExceptionHandler;
import com.iemr.consent.utils.http.TraceIdFilter;
import com.iemr.consent.utils.idempotency.RequestHasher;

/** Feature 006: the admin notice endpoints (contract consent-api-admin-notices.openapi.yaml). */
class AdminNoticeControllerTest {

	private static final String BASE = "/consent/v1/admin/projects/3/notices";

	private static final String KEY = "3f2504e0-4f89-11d3-9a0c-0305e82c3301";

	private static final String DRAFT = "{\"languageCode\":\"en\",\"body\":\"Body\",\"declaration\":\"I agree\"}";

	private static final MessageEntry ENTRY = new MessageEntry(3, NoticeContext.REGISTRATION, "en", null, null);

	private NoticeAuthoringService service;

	private MockMvc mvc;

	@BeforeEach
	void setUp() {
		service = mock(NoticeAuthoringService.class);
		mvc = MockMvcBuilders
				.standaloneSetup(new AdminNoticeController(service, new RequestHasher(TestFixtures.objectMapper()),
						TestFixtures.CLOCK))
				.addMappedInterceptors(new String[] { "/consent/v1/admin/**" },
						new ServiceKeyInterceptor(TestFixtures.consentProperties()))
				.setControllerAdvice(new GlobalExceptionHandler(new ErrorResponseWriter(TestFixtures.objectMapper(),
						TestFixtures.CLOCK, TestFixtures.consentProperties())))
				.setCustomArgumentResolvers(new CurrentUserArgumentResolver()).addFilters(new TraceIdFilter()).build();
	}

	private static MockHttpServletRequestBuilder admin(MockHttpServletRequestBuilder request) {
		return request.requestAttr(AuthenticatedUser.ATTRIBUTE, ConsentFixtures.WORKER)
				.header(ServiceKeyInterceptor.HEADER, TestFixtures.SERVICE_KEY);
	}

	private static MockHttpServletRequestBuilder draft(String body) {
		return admin(put(BASE + "/REGISTRATION/draft")).header("Idempotency-Key", KEY)
				.contentType(MediaType.APPLICATION_JSON).content(body);
	}

	private static MockHttpServletRequestBuilder publish(String body) {
		return admin(post(BASE + "/REVISIT/publish")).header("Idempotency-Key", KEY)
				.contentType(MediaType.APPLICATION_JSON).content(body);
	}

	@Test
	void listIs200() throws Exception {
		when(service.list(3)).thenReturn(List.of(ENTRY));

		mvc.perform(admin(get(BASE))).andExpect(status().isOk()).andExpect(jsonPath("$.statusCode").value(200))
				.andExpect(jsonPath("$.data[0].languageCode").value("en"))
				.andExpect(jsonPath("$.data[0].context").value("REGISTRATION"))
				.andExpect(jsonPath("$.data[0].draft").isEmpty());
	}

	@Test
	void withoutTheServiceKeyItIs403() throws Exception {
		mvc.perform(get(BASE).requestAttr(AuthenticatedUser.ATTRIBUTE, ConsentFixtures.WORKER))
				.andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("ACCESS_DENIED"));
		mvc.perform(get(BASE).requestAttr(AuthenticatedUser.ATTRIBUTE, ConsentFixtures.WORKER)
				.header(ServiceKeyInterceptor.HEADER, "wrong")).andExpect(status().isForbidden());
		verify(service, never()).list(any());
	}

	@Test
	void aBadProjectIdIs400() throws Exception {
		mvc.perform(admin(get("/consent/v1/admin/projects/0/notices"))).andExpect(status().isBadRequest());
	}

	@Test
	void draftIs200() throws Exception {
		when(service.saveDraft(eq(3), eq(NoticeContext.REGISTRATION), any(), eq(KEY), anyString(),
				eq(ConsentFixtures.WORKER), anyString())).thenReturn(ENTRY);

		mvc.perform(draft(DRAFT)).andExpect(status().isOk()).andExpect(jsonPath("$.data.languageCode").value("en"));
	}

	@Test
	void draftFieldsAreChecked() throws Exception {
		mvc.perform(draft(DRAFT.replace("\"en\"", "\"hi\""))).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.details[0].field").value("languageCode"));
		mvc.perform(draft(DRAFT.replace("\"Body\"", "\" \""))).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.details[0].field").value("body"));
		mvc.perform(draft(DRAFT.replace("\"Body\"", "\"" + "b".repeat(20001) + "\"")))
				.andExpect(status().isBadRequest());
		mvc.perform(draft(DRAFT.replace("\"I agree\"", "\"" + "d".repeat(4001) + "\"")))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.details[0].field").value("declaration"));
		mvc.perform(draft(DRAFT.replace("}", ",\"grievance\":{\"phone\":\"" + "9".repeat(21) + "\"}}")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.details[0].field").value("grievance.phone"));
		mvc.perform(admin(put(BASE + "/REGISTRATION/draft")).contentType(MediaType.APPLICATION_JSON).content(DRAFT))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.details[0].field").value("Idempotency-Key"));
		mvc.perform(admin(put(BASE + "/HELPLINE/draft")).header("Idempotency-Key", KEY)
				.contentType(MediaType.APPLICATION_JSON).content(DRAFT)).andExpect(status().isBadRequest());
		verify(service, never()).saveDraft(any(), any(), any(), any(), any(), any(), any());
	}

	@Test
	void publishIs200AndRefusalsKeepTheirCode() throws Exception {
		when(service.publish(eq(3), eq(NoticeContext.REVISIT), any(), eq(KEY), anyString(),
				eq(ConsentFixtures.WORKER), anyString())).thenReturn(ENTRY);
		mvc.perform(publish("{\"requireReconsent\":true,\"translateTo\":[\"hi\"]}")).andExpect(status().isOk());

		when(service.publish(any(), any(), any(), any(), any(), any(), any())).thenThrow(
				new ConsentApiException(ErrorCode.BUSINESS_RULE_VIOLATION, "Enter the patient declaration"));
		mvc.perform(publish("{}")).andExpect(status().isUnprocessableEntity())
				.andExpect(jsonPath("$.message").value("Enter the patient declaration"));
	}

	@Test
	void publishOptionsAreChecked() throws Exception {
		mvc.perform(publish("{\"translateTo\":[\"HIN1\"]}")).andExpect(status().isBadRequest());
		String tooMany = "\"hi\",".repeat(23) + "\"kn\"";
		mvc.perform(publish("{\"translateTo\":[" + tooMany + "]}")).andExpect(status().isBadRequest());
		verify(service, never()).publish(any(), any(), any(), any(), any(), any(), any());
	}
}
