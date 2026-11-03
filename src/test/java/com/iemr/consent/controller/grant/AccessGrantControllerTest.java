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

package com.iemr.consent.controller.grant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.iemr.consent.data.grant.GrantStatus;
import com.iemr.consent.data.grant.GrantType;
import com.iemr.consent.model.grant.AccessGrantView;
import com.iemr.consent.model.grant.ApproveRequest;
import com.iemr.consent.model.grant.GranteeView;
import com.iemr.consent.service.grant.AccessGrantService;
import com.iemr.consent.support.ConsentFixtures;
import com.iemr.consent.support.LogCapture;
import com.iemr.consent.support.TestFixtures;
import com.iemr.consent.utils.auth.AuthenticatedUser;
import com.iemr.consent.utils.auth.CurrentUserArgumentResolver;
import com.iemr.consent.utils.exception.ConsentApiException;
import com.iemr.consent.utils.exception.ErrorCode;
import com.iemr.consent.utils.exception.ErrorResponseWriter;
import com.iemr.consent.utils.exception.GlobalExceptionHandler;
import com.iemr.consent.utils.http.TraceIdFilter;
import com.iemr.consent.utils.idempotency.RequestHasher;

class AccessGrantControllerTest {

	static final String KEY = "3f2504e0-4f89-11d3-9a0c-0305e82c3301";

	static final String GRANT = "9a2504e0-4f89-11d3-9a0c-0305e82c3301";

	static final String OTP = "6f2504e0-4f89-11d3-9a0c-0305e82c3301";

	static final String REQUEST = "{\"beneficiaryRegId\":1001,\"purposeCodes\":[\"P-REM\"],\"days\":7,"
			+ "\"reason\":\"Referral follow-up\",\"facilityId\":5}";

	@RegisterExtension
	final LogCapture logs = new LogCapture();

	private final RequestHasher hasher = new RequestHasher(TestFixtures.objectMapper());

	private AccessGrantService service;

	private MockMvc mvc;

	private final AccessGrantView grant = new AccessGrantView(GRANT, 1001L, new GranteeView(77L, "doctor77", 5),
			List.of("P-REM"), GrantType.CONSENTED, GrantStatus.PENDING, 7, "Referral follow-up", null, null, null,
			null);

	@BeforeEach
	void setUp() {
		service = mock(AccessGrantService.class);
		mvc = MockMvcBuilders.standaloneSetup(new AccessGrantController(service, hasher, TestFixtures.CLOCK))
				.setControllerAdvice(new GlobalExceptionHandler(new ErrorResponseWriter(TestFixtures.objectMapper(),
						TestFixtures.CLOCK, TestFixtures.consentProperties())))
				.setCustomArgumentResolvers(new CurrentUserArgumentResolver()).addFilters(new TraceIdFilter()).build();
	}

	private static MockHttpServletRequestBuilder write(String path, String body) {
		return post(path).requestAttr(AuthenticatedUser.ATTRIBUTE, ConsentFixtures.WORKER)
				.contentType(MediaType.APPLICATION_JSON).content(body).header("Idempotency-Key", KEY);
	}

	@Test
	void anAccessRequestIs201AndNeverLogsTheReason() throws Exception {
		when(service.request(any(), eq(KEY), anyString(), eq(ConsentFixtures.WORKER), anyString())).thenReturn(grant);
		mvc.perform(write("/consent/v1/access-grants", REQUEST)).andExpect(status().isCreated())
				.andExpect(jsonPath("$.statusCode").value(201)).andExpect(jsonPath("$.data.grantId").value(GRANT))
				.andExpect(jsonPath("$.data.grantee.name").value("doctor77"))
				.andExpect(jsonPath("$.data.status").value("PENDING"));
		logs.assertNoneContain("Referral follow-up");
	}

	@Test
	void accessRequestsAreValidated() throws Exception {
		String[] bad = { REQUEST.replace("\"days\":7", "\"days\":0"), REQUEST.replace("Referral follow-up", " "),
			REQUEST.replace("Referral follow-up", "a".repeat(256)), REQUEST.replace("Referral follow-up", "<script>"),
			REQUEST.replace("[\"P-REM\"]", "[]"), REQUEST.replace("[\"P-REM\"]", "[\"P-REM\",\"P-REM\"]"),
			REQUEST.replace("1001", "0"),
			REQUEST.replace("[\"P-REM\"]", codes(21, "P-%02d")), REQUEST.replace("[\"P-REM\"]", codes(13, "P-%018d")) };
		for (String body : bad) {
			mvc.perform(write("/consent/v1/access-grants", body)).andExpect(status().isBadRequest());
		}
		mvc.perform(post("/consent/v1/access-grants").requestAttr(AuthenticatedUser.ATTRIBUTE, ConsentFixtures.WORKER)
				.contentType(MediaType.APPLICATION_JSON).content(REQUEST)).andExpect(status().isBadRequest());
		verify(service, never()).request(any(), anyString(), anyString(), any(), anyString());
	}

	private static String codes(int count, String format) {
		StringBuilder json = new StringBuilder("[");
		for (int i = 0; i < count; i++) {
			json.append(i == 0 ? "" : ",").append('"').append(String.format(format, i)).append('"');
		}
		return json.append(']').toString();
	}

	@Test
	@DisplayName("API 12: list by comma-separated status; no filter = all")
	void listFiltersByStatus() throws Exception {
		when(service.list(eq(1001L), any())).thenReturn(List.of(grant));
		mvc.perform(get("/consent/v1/principals/1001/access-grants").param("status", "ACTIVE, PENDING"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data[0].grantId").value(GRANT));
		verify(service).list(1001L, EnumSet.of(GrantStatus.ACTIVE, GrantStatus.PENDING));
		mvc.perform(get("/consent/v1/principals/1001/access-grants")).andExpect(status().isOk());
		mvc.perform(get("/consent/v1/principals/1001/access-grants").param("status", " ")).andExpect(status().isOk());
		verify(service, times(2)).list(1001L, Set.of());
		mvc.perform(get("/consent/v1/principals/1001/access-grants").param("status", "BAD"))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.details[0].field").value("status"));
		mvc.perform(get("/consent/v1/principals/0/access-grants")).andExpect(status().isBadRequest());
	}

	@Test
	@DisplayName("H6 / I1: approval hashes the grant id with the body")
	void approveIs200() throws Exception {
		when(service.approve(eq(GRANT), any(), eq(KEY), anyString(), any(), anyString())).thenReturn(grant);
		mvc.perform(write("/consent/v1/access-grants/" + GRANT + "/approve", "{\"otpChallengeId\":\"" + OTP + "\"}"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.grantId").value(GRANT));

		ArgumentCaptor<ApproveRequest> body = ArgumentCaptor.forClass(ApproveRequest.class);
		ArgumentCaptor<String> hash = ArgumentCaptor.forClass(String.class);
		verify(service).approve(eq(GRANT), body.capture(), eq(KEY), hash.capture(), any(), anyString());
		assertThat(hash.getValue()).isEqualTo(hasher.hash(Map.of("target", GRANT, "body", body.getValue())));
	}

	@Test
	void approveIsValidatedAndMapsServiceErrors() throws Exception {
		mvc.perform(write("/consent/v1/access-grants/not-a-uuid/approve", "{\"otpChallengeId\":\"" + OTP + "\"}"))
				.andExpect(status().isBadRequest());
		mvc.perform(write("/consent/v1/access-grants/" + GRANT + "/approve", "{}")).andExpect(status().isBadRequest());
		verify(service, never()).approve(anyString(), any(), anyString(), anyString(), any(), anyString());

		when(service.approve(anyString(), any(), anyString(), anyString(), any(), anyString()))
				.thenThrow(new ConsentApiException(ErrorCode.NOT_FOUND), new ConsentApiException(ErrorCode.STATE_CONFLICT),
						new ConsentApiException(ErrorCode.OTP_INVALID));
		String path = "/consent/v1/access-grants/" + GRANT + "/approve";
		String body = "{\"otpChallengeId\":\"" + OTP + "\"}";
		mvc.perform(write(path, body)).andExpect(status().isNotFound());
		mvc.perform(write(path, body)).andExpect(status().isConflict());
		mvc.perform(write(path, body)).andExpect(status().isUnprocessableEntity())
				.andExpect(jsonPath("$.error.code").value("OTP_INVALID"));
	}

	@Test
	@DisplayName("I1: the same empty revoke body for two grants gives two different hashes")
	void revokeIs200AndHashesTheGrantId() throws Exception {
		when(service.revoke(anyString(), eq(KEY), anyString(), any(), anyString())).thenReturn(grant);
		mvc.perform(write("/consent/v1/access-grants/" + GRANT + "/revoke", "{}")).andExpect(status().isOk());
		mvc.perform(write("/consent/v1/access-grants/" + GRANT.toUpperCase() + "/revoke", "{}"))
				.andExpect(status().isOk());

		ArgumentCaptor<String> hash = ArgumentCaptor.forClass(String.class);
		verify(service, times(2)).revoke(anyString(), eq(KEY), hash.capture(), any(),
				anyString());
		assertThat(hash.getAllValues()).containsOnly(hasher.hash(Map.of("target", GRANT, "body", Map.of())));
		assertThat(hash.getValue()).isNotEqualTo(
				hasher.hash(Map.of("target", "8b2504e0-4f89-11d3-9a0c-0305e82c3301", "body", Map.of())));

		when(service.revoke(anyString(), anyString(), anyString(), any(), anyString()))
				.thenThrow(new ConsentApiException(ErrorCode.STATE_CONFLICT));
		mvc.perform(write("/consent/v1/access-grants/" + GRANT + "/revoke", "{}")).andExpect(status().isConflict());
	}
}
