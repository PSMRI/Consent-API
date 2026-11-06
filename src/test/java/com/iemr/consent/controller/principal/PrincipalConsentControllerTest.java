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

package com.iemr.consent.controller.principal;

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

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.iemr.consent.data.consent.PurposeStatus;
import com.iemr.consent.data.notice.NoticeContext;
import com.iemr.consent.model.principal.CurrentDecisionView;
import com.iemr.consent.model.principal.PatientConsents;
import com.iemr.consent.model.principal.PendingAction;
import com.iemr.consent.model.principal.PendingReason;
import com.iemr.consent.model.principal.PreferenceRequest;
import com.iemr.consent.model.principal.PurposeSelection;
import com.iemr.consent.model.principal.WithdrawalRequest;
import com.iemr.consent.model.principal.WithdrawalResult;
import com.iemr.consent.service.principal.PendingActionService;
import com.iemr.consent.service.principal.PrincipalConsentService;
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

class PrincipalConsentControllerTest {

	static final String KEY = "3f2504e0-4f89-11d3-9a0c-0305e82c3301";

	static final String OTP = "6f2504e0-4f89-11d3-9a0c-0305e82c3301";

	private static final String BASE = "/consent/v1/principals/";

	private final RequestHasher hasher = new RequestHasher(TestFixtures.objectMapper());

	private PrincipalConsentService consents;

	private PendingActionService pending;

	private MockMvc mvc;

	private final PatientConsents result = new PatientConsents(List.of(new CurrentDecisionView("P-REM", "Reminders",
			false, true, PurposeStatus.GRANTED, Instant.parse("2027-10-29T10:00:00Z"), 2)), List.of());

	@BeforeEach
	void setUp() {
		consents = mock(PrincipalConsentService.class);
		pending = mock(PendingActionService.class);
		mvc = MockMvcBuilders
				.standaloneSetup(new PrincipalConsentController(consents, pending, hasher, TestFixtures.CLOCK))
				.setControllerAdvice(new GlobalExceptionHandler(new ErrorResponseWriter(TestFixtures.objectMapper(),
						TestFixtures.CLOCK, TestFixtures.consentProperties())))
				.setCustomArgumentResolvers(new CurrentUserArgumentResolver()).addFilters(new TraceIdFilter()).build();
	}

	private static MockHttpServletRequestBuilder write(String path, String body) {
		return post(BASE + path).requestAttr(AuthenticatedUser.ATTRIBUTE, ConsentFixtures.WORKER)
				.contentType(MediaType.APPLICATION_JSON).content(body).header("Idempotency-Key", KEY);
	}

	@Test
	@DisplayName("API 8: the consents view in the contract shape")
	void consentsAre200() throws Exception {
		when(consents.consents(1001L)).thenReturn(result);
		mvc.perform(get(BASE + "1001/consents")).andExpect(status().isOk()).andExpect(jsonPath("$.statusCode").value(200))
				.andExpect(jsonPath("$.data.current[0].purposeCode").value("P-REM"))
				.andExpect(jsonPath("$.data.current[0].isMandatory").value(false))
				.andExpect(jsonPath("$.data.current[0].isWithdrawable").value(true))
				.andExpect(jsonPath("$.data.current[0].versionNo").value(2))
				.andExpect(jsonPath("$.data.history").isArray());
	}

	@Test
	void aPatientIdMustBeAPositiveNumber() throws Exception {
		mvc.perform(get(BASE + "0/consents")).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
		mvc.perform(get(BASE + "-1/consents")).andExpect(status().isBadRequest());
		mvc.perform(get(BASE + "abc/consents")).andExpect(status().isBadRequest());
		verify(consents, never()).consents(any());
	}

	@Test
	@DisplayName("H4: pending actions default to the registration notice; REVISIT is passed through")
	void pendingActions() throws Exception {
		when(pending.pending(1001L, NoticeContext.REGISTRATION))
				.thenReturn(List.of(new PendingAction(PendingReason.CONSENT_MISSING, List.of("P-REG"), null)));
		mvc.perform(get(BASE + "1001/pending-actions")).andExpect(status().isOk())
				.andExpect(jsonPath("$.data[0].reason").value("CONSENT_MISSING"))
				.andExpect(jsonPath("$.data[0].purposeCodes[0]").value("P-REG"));
		mvc.perform(get(BASE + "1001/pending-actions").param("context", "REVISIT")).andExpect(status().isOk());
		verify(pending).pending(1001L, NoticeContext.REVISIT);
		mvc.perform(get(BASE + "1001/pending-actions").param("context", "OTHER")).andExpect(status().isBadRequest());
	}

	@Test
	@DisplayName("C10: a preference change returns the consents; the hash covers the patient id")
	void preferenceChangeIs200() throws Exception {
		when(consents.changePreference(eq(1001L), any(), eq(KEY), anyString(), eq(ConsentFixtures.WORKER), anyString()))
				.thenReturn(result);

		mvc.perform(write("1001/preferences", "{\"purposeCodes\":[\"P-REM\"],\"decision\":\"GRANT\","
				+ "\"versions\":{\"P-REM\":1},\"otpChallengeId\":\"" + OTP + "\"}")).andExpect(status().isOk())
				.andExpect(jsonPath("$.data.current[0].status").value("GRANTED"));

		ArgumentCaptor<PreferenceRequest> body = ArgumentCaptor.forClass(PreferenceRequest.class);
		ArgumentCaptor<String> hash = ArgumentCaptor.forClass(String.class);
		verify(consents).changePreference(eq(1001L), body.capture(), eq(KEY), hash.capture(), any(), anyString());
		assertThat(hash.getValue()).isEqualTo(hasher.hash(Map.of("target", 1001L, "body", body.getValue())))
				.isNotEqualTo(hasher.hash(Map.of("target", 2002L, "body", body.getValue())));
	}

	@Test
	void preferenceRequestsAreValidated() throws Exception {
		String[] bad = { "{\"purposeCodes\":[\"P-REM\"],\"versions\":{\"P-REM\":1}}",
			"{\"purposeCodes\":[\"P-REM\"],\"decision\":\"MAYBE\",\"versions\":{\"P-REM\":1}}",
			"{\"purposeCodes\":[\"P-REM\"],\"decision\":\"GRANT\",\"versions\":{\"P-REM\":1}}",
			"{\"purposeCodes\":[\"P-REM\"],\"decision\":\"GRANT\",\"versions\":{\"P-REM\":1},\"otpChallengeId\":\"x\"}",
			"{\"purposeCodes\":[\"P-REM\"],\"decision\":\"DENY\",\"versions\":{\"P-FAM\":1}}",
			"{\"purposeCodes\":[\"P-REM\",\"P-REM\"],\"decision\":\"DENY\",\"versions\":{\"P-REM\":1}}",
			"{\"purposeCodes\":[],\"decision\":\"DENY\",\"versions\":{}}",
			"{\"purposeCodes\":[\"P-REM\"],\"decision\":\"DENY\",\"versions\":{\"P-REM\":0}}",
			"{\"purposeCodes\":[\"P-REM\"],\"decision\":\"DENY\"}" };
		for (String body : bad) {
			mvc.perform(write("1001/preferences", body)).andExpect(status().isBadRequest());
		}
		mvc.perform(post(BASE + "1001/preferences").requestAttr(AuthenticatedUser.ATTRIBUTE, ConsentFixtures.WORKER)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"purposeCodes\":[\"P-REM\"],\"decision\":\"DENY\",\"versions\":{\"P-REM\":1}}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.details[0].field").value("Idempotency-Key"));
		verify(consents, never()).changePreference(any(), any(), anyString(), anyString(), any(), anyString());

		mvc.perform(write("1001/preferences", "{\"purposeCodes\":[\"P-REM\"],\"decision\":\"DENY\","
				+ "\"versions\":{\"P-REM\":1}}")).andExpect(status().isOk());
	}

	@Test
	@DisplayName("C11: ALL or a list of purposes; notWithdrawn is in the response")
	void withdrawalIs200() throws Exception {
		when(consents.withdraw(eq(1001L), any(), eq(KEY), anyString(), any(), anyString()))
				.thenReturn(new WithdrawalResult(result.current(), List.of(), List.of("P-REG")));

		mvc.perform(write("1001/withdrawals", "{\"purposeCodes\":\"ALL\",\"versions\":{\"P-REM\":2}}"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.notWithdrawn[0]").value("P-REG"));
		mvc.perform(write("1001/withdrawals", "{\"purposeCodes\":[\"P-REM\"],\"versions\":{\"P-REM\":2},"
				+ "\"deviceId\":\"tab-3\",\"serviceLine\":\"HWC\"}")).andExpect(status().isOk());

		ArgumentCaptor<WithdrawalRequest> body = ArgumentCaptor.forClass(WithdrawalRequest.class);
		verify(consents, times(2)).withdraw(eq(1001L), body.capture(), eq(KEY), anyString(),
				any(), anyString());
		assertThat(body.getAllValues().get(0).purposeCodes()).isEqualTo(PurposeSelection.everything());
		assertThat(body.getAllValues().get(1).purposeCodes()).isEqualTo(PurposeSelection.of(List.of("P-REM")));
		assertThat(body.getAllValues().get(1).deviceId()).isEqualTo("tab-3");
	}

	@Test
	void withdrawalRequestsAreValidated() throws Exception {
		String[] bad = { "{\"purposeCodes\":\"SOME\",\"versions\":{}}", "{\"purposeCodes\":5,\"versions\":{}}",
			"{\"purposeCodes\":[],\"versions\":{}}", "{\"purposeCodes\":[\"P-REM\",\"P-REM\"],\"versions\":{}}",
			"{\"purposeCodes\":[\"bad code\"],\"versions\":{}}", "{\"purposeCodes\":\"ALL\"}",
			"{\"versions\":{}}", "{\"purposeCodes\":\"ALL\",\"versions\":{\"P-REM\":0}}",
			"{\"purposeCodes\":\"ALL\",\"versions\":{},\"deviceId\":\"tab 3; drop\"}" };
		for (String body : bad) {
			mvc.perform(write("1001/withdrawals", body)).andExpect(status().isBadRequest());
		}
		verify(consents, never()).withdraw(any(), any(), anyString(), anyString(), any(), anyString());
	}

	@Test
	@DisplayName("C12 / C20: business-rule and stale errors keep their status and code")
	void errorsMapToTheirStatus() throws Exception {
		when(consents.withdraw(any(), any(), anyString(), anyString(), any(), anyString()))
				.thenThrow(new ConsentApiException(ErrorCode.PURPOSE_NOT_WITHDRAWABLE, null, "P-REG", null));
		mvc.perform(write("1001/withdrawals", "{\"purposeCodes\":[\"P-REG\"],\"versions\":{\"P-REG\":1}}"))
				.andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.statusCode").value(422))
				.andExpect(jsonPath("$.error.code").value("PURPOSE_NOT_WITHDRAWABLE"))
				.andExpect(jsonPath("$.error.purposeCode").value("P-REG"));

		when(consents.changePreference(any(), any(), anyString(), anyString(), any(), anyString()))
				.thenThrow(new ConsentApiException(ErrorCode.STALE_VERSION));
		mvc.perform(write("1001/preferences", "{\"purposeCodes\":[\"P-REM\"],\"decision\":\"DENY\","
				+ "\"versions\":{\"P-REM\":1}}")).andExpect(status().isConflict())
				.andExpect(jsonPath("$.error.code").value("STALE_VERSION"));
	}
}
