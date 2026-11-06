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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.iemr.consent.data.notice.NoticeContext;
import com.iemr.consent.model.principal.PendingAction;
import com.iemr.consent.model.principal.PendingReason;
import com.iemr.consent.service.principal.PendingActionService;
import com.iemr.consent.support.TestFixtures;
import com.iemr.consent.utils.exception.ErrorResponseWriter;
import com.iemr.consent.utils.exception.GlobalExceptionHandler;
import com.iemr.consent.utils.http.TraceIdFilter;

/** API 15 (feature 007): contract pending-actions-batch.openapi.yaml. */
class PendingActionBatchControllerTest {

	private static final String PATH = "/consent/v1/principals/pending-actions/batch";

	private PendingActionService service;

	private MockMvc mvc;

	@BeforeEach
	void setUp() {
		service = mock(PendingActionService.class);
		mvc = MockMvcBuilders.standaloneSetup(new PendingActionBatchController(service, TestFixtures.CLOCK))
				.setControllerAdvice(new GlobalExceptionHandler(new ErrorResponseWriter(TestFixtures.objectMapper(),
						TestFixtures.CLOCK, TestFixtures.consentProperties())))
				.addFilters(new TraceIdFilter()).build();
	}

	private ResultActions send(String body) throws Exception {
		return mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(body));
	}

	private static String ids(LongStream ids) {
		return "{\"beneficiaryRegIds\":[" + ids.mapToObj(Long::toString).collect(Collectors.joining(",")) + "]}";
	}

	@Test
	void answersOneEntryPerPatientWithoutIdempotencyKey() throws Exception {
		Map<Long, List<PendingAction>> answer = new LinkedHashMap<>();
		answer.put(101L, List.of(new PendingAction(PendingReason.CONSENT_MISSING, List.of("P-REG"), null)));
		answer.put(102L, List.of());
		when(service.pendingFor(Set.of(101L, 102L), NoticeContext.REGISTRATION)).thenReturn(answer);

		send("{\"beneficiaryRegIds\":[101,102]}").andExpect(status().isOk())
				.andExpect(jsonPath("$.statusCode").value(200))
				.andExpect(jsonPath("$.data['101'][0].reason").value("CONSENT_MISSING"))
				.andExpect(jsonPath("$.data['101'][0].purposeCodes[0]").value("P-REG"))
				.andExpect(jsonPath("$.data['102']").isArray())
				.andExpect(jsonPath("$.data['102']", Matchers.hasSize(0)));
	}

	@Test
	void revisitIsPassedThrough() throws Exception {
		mvc.perform(post(PATH + "?context=REVISIT").contentType(MediaType.APPLICATION_JSON)
				.content("{\"beneficiaryRegIds\":[7]}")).andExpect(status().isOk());
		verify(service).pendingFor(Set.of(7L), NoticeContext.REVISIT);
	}

	@Test
	void duplicatesCountOnceAndKeepTheirFirstOrder() throws Exception {
		String body = ids(LongStream.concat(LongStream.rangeClosed(1, 100), LongStream.of(2, 1)));

		send(body).andExpect(status().isOk());

		@SuppressWarnings("unchecked")
		ArgumentCaptor<java.util.Collection<Long>> sent = ArgumentCaptor.forClass(java.util.Collection.class);
		verify(service).pendingFor(sent.capture(), eq(NoticeContext.REGISTRATION));
		org.assertj.core.api.Assertions.assertThat(sent.getValue()).hasSize(100).startsWith(1L, 2L, 3L).endsWith(100L);
	}

	@Test
	void badListsAreRefusedWithoutComputing() throws Exception {
		for (String body : List.of("{\"beneficiaryRegIds\":[]}", "{}", "{\"beneficiaryRegIds\":[0]}",
				"{\"beneficiaryRegIds\":[-1]}", "{\"beneficiaryRegIds\":[null]}")) {
			send(body).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
					.andExpect(jsonPath("$.error.details[0].field", Matchers.startsWith("beneficiaryRegIds")));
		}
		send(ids(LongStream.rangeClosed(1, 101))).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.details[0].field").value("beneficiaryRegIds"))
				.andExpect(jsonPath("$.error.details[0].message").value("must contain at most 100 different ids"));
		verifyNoInteractions(service);
	}

	@Test
	void unknownContextAndUnreadableJsonAreMalformed() throws Exception {
		mvc.perform(post(PATH + "?context=HELPLINE").contentType(MediaType.APPLICATION_JSON)
				.content("{\"beneficiaryRegIds\":[7]}")).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("MALFORMED_REQUEST"));
		send("not json").andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("MALFORMED_REQUEST"));
		verify(service, org.mockito.Mockito.never()).pendingFor(any(), any());
	}
}
