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

package com.iemr.consent.controller.evidence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.iemr.consent.data.evidence.EvidenceType;
import com.iemr.consent.model.evidence.EvidenceUploadResult;
import com.iemr.consent.model.evidence.EvidenceView;
import com.iemr.consent.service.evidence.EvidenceAccessService;
import com.iemr.consent.service.evidence.EvidenceFile;
import com.iemr.consent.service.evidence.EvidenceService;
import com.iemr.consent.service.evidence.EvidenceUpload;
import com.iemr.consent.support.ConsentFixtures;
import com.iemr.consent.support.TestFixtures;
import com.iemr.consent.utils.auth.AuthenticatedUser;
import com.iemr.consent.utils.auth.CurrentUserArgumentResolver;
import com.iemr.consent.utils.exception.ConsentApiException;
import com.iemr.consent.utils.exception.ErrorCode;
import com.iemr.consent.utils.exception.ErrorResponseWriter;
import com.iemr.consent.utils.exception.GlobalExceptionHandler;
import com.iemr.consent.utils.http.TraceIdFilter;

class EvidenceControllerTest {

	private static final String KEY = "3f2504e0-4f89-11d3-9a0c-0305e82c3301";

	private static final String SHA = "b".repeat(64);

	private static final String ID = "0f8fad5b-d9cb-469f-a165-70867728950e";

	private EvidenceService service;

	private EvidenceAccessService access;

	private MockMvc mvc;

	@BeforeEach
	void setUp() {
		service = mock(EvidenceService.class);
		access = mock(EvidenceAccessService.class);
		mvc = MockMvcBuilders.standaloneSetup(new EvidenceController(service, access, TestFixtures.CLOCK))
				.setControllerAdvice(new GlobalExceptionHandler(new ErrorResponseWriter(TestFixtures.objectMapper(),
						TestFixtures.CLOCK, TestFixtures.consentProperties())))
				.setCustomArgumentResolvers(new CurrentUserArgumentResolver()).addFilters(new TraceIdFilter()).build();
	}

	@Test
	void uploadIs201() throws Exception {
		when(service.upload(any(), eq(KEY), eq(ConsentFixtures.WORKER))).thenReturn(new EvidenceUploadResult("e1", SHA));

		mvc.perform(multipart("/consent/v1/evidence")
				.file(new MockMultipartFile("file", "clip.webm", "audio/webm", "abc".getBytes()))
				.param("type", "VOICE_CLIP").param("sha256", SHA).param("durationSec", "35")
				.header("Idempotency-Key", KEY).requestAttr(AuthenticatedUser.ATTRIBUTE, ConsentFixtures.WORKER))
				.andExpect(status().isCreated()).andExpect(jsonPath("$.data.evidenceId").value("e1"));

		ArgumentCaptor<EvidenceUpload> upload = ArgumentCaptor.forClass(EvidenceUpload.class);
		verify(service).upload(upload.capture(), eq(KEY), eq(ConsentFixtures.WORKER));
		assertThat(upload.getValue().type()).isEqualTo(EvidenceType.VOICE_CLIP);
		assertThat(upload.getValue().durationSec()).isEqualByComparingTo(BigDecimal.valueOf(35));
		assertThat(upload.getValue().contentType()).isEqualTo("audio/webm");
	}

	@Test
	void missingPartBadHashOrJsonBody() throws Exception {
		mvc.perform(multipart("/consent/v1/evidence").param("type", "VOICE_CLIP").param("sha256", SHA)
				.header("Idempotency-Key", KEY).requestAttr(AuthenticatedUser.ATTRIBUTE, ConsentFixtures.WORKER))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.details[0].field").value("file"));
		mvc.perform(multipart("/consent/v1/evidence")
				.file(new MockMultipartFile("file", "x", "image/png", "abc".getBytes())).param("type", "PATIENT_PHOTO")
				.param("sha256", "XYZ").header("Idempotency-Key", KEY)
				.requestAttr(AuthenticatedUser.ATTRIBUTE, ConsentFixtures.WORKER)).andExpect(status().isBadRequest());
		mvc.perform(post("/consent/v1/evidence").contentType(MediaType.APPLICATION_JSON).content("{}")
				.requestAttr(AuthenticatedUser.ATTRIBUTE, ConsentFixtures.WORKER))
				.andExpect(status().isUnsupportedMediaType());
	}

	@Test
	void unreadableFileIsMalformed() throws Exception {
		MockMultipartFile broken = new MockMultipartFile("file", "x", "image/png", "abc".getBytes()) {
			@Override
			public byte[] getBytes() throws IOException {
				throw new IOException("disk");
			}
		};
		mvc.perform(multipart("/consent/v1/evidence").file(broken).param("type", "PATIENT_PHOTO").param("sha256", SHA)
				.header("Idempotency-Key", KEY).requestAttr(AuthenticatedUser.ATTRIBUTE, ConsentFixtures.WORKER))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("MALFORMED_REQUEST"));
	}

	@Test
	void listsTheFilesOfAConsent() throws Exception {
		when(access.listForConsent(ID)).thenReturn(List.of(new EvidenceView("e1", EvidenceType.VOICE_CLIP,
				"audio/webm", 3L, BigDecimal.valueOf(35), "worker42", Instant.parse("2026-11-05T08:00:00Z"))));
		mvc.perform(get("/consent/v1/evidence").param("consentId", ID.toUpperCase())
				.requestAttr(AuthenticatedUser.ATTRIBUTE, ConsentFixtures.WORKER))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data[0].evidenceId").value("e1"))
				.andExpect(jsonPath("$.data[0].type").value("VOICE_CLIP"));
	}

	@Test
	void playsInlineOrDownloads() throws Exception {
		EvidenceFile file = new EvidenceFile("consent-evidence-e1.webm", "audio/webm", "abc".getBytes());
		when(access.open(eq(ID), eq(false), eq(ConsentFixtures.WORKER), any())).thenReturn(file);
		when(access.open(eq(ID), eq(true), eq(ConsentFixtures.WORKER), any())).thenReturn(file);

		mvc.perform(get("/consent/v1/evidence/" + ID + "/content")
				.requestAttr(AuthenticatedUser.ATTRIBUTE, ConsentFixtures.WORKER))
				.andExpect(status().isOk()).andExpect(content().contentType("audio/webm"))
				.andExpect(content().bytes("abc".getBytes()))
				.andExpect(header().string("Content-Disposition", "inline; filename=\"consent-evidence-e1.webm\""))
				.andExpect(header().string("Cache-Control", "no-store"))
				.andExpect(header().string("X-Content-Type-Options", "nosniff"));
		mvc.perform(get("/consent/v1/evidence/" + ID + "/content").param("download", "true")
				.requestAttr(AuthenticatedUser.ATTRIBUTE, ConsentFixtures.WORKER))
				.andExpect(status().isOk()).andExpect(header().string("Content-Disposition",
						"attachment; filename=\"consent-evidence-e1.webm\""));
	}

	@Test
	void unknownOrMalformedIdsAreNotFound() throws Exception {
		when(access.open(eq(ID), eq(false), any(), any())).thenThrow(new ConsentApiException(ErrorCode.NOT_FOUND));
		mvc.perform(get("/consent/v1/evidence/" + ID + "/content")
				.requestAttr(AuthenticatedUser.ATTRIBUTE, ConsentFixtures.WORKER))
				.andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
		mvc.perform(get("/consent/v1/evidence/not-a-uuid/content")
				.requestAttr(AuthenticatedUser.ATTRIBUTE, ConsentFixtures.WORKER)).andExpect(status().isNotFound());
		mvc.perform(get("/consent/v1/evidence").param("consentId", "x")
				.requestAttr(AuthenticatedUser.ATTRIBUTE, ConsentFixtures.WORKER)).andExpect(status().isNotFound());
	}
}
