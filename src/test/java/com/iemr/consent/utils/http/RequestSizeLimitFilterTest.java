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

package com.iemr.consent.utils.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.DelegatingServletInputStream;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import com.fasterxml.jackson.databind.JsonNode;
import com.iemr.consent.support.TestFixtures;
import com.iemr.consent.utils.exception.ErrorResponseWriter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.http.HttpServletRequest;

class RequestSizeLimitFilterTest {

	private static final long LIMIT = 10;

	private final RequestSizeLimitFilter filter = new RequestSizeLimitFilter(TestFixtures.consentProperties(LIMIT),
			new ErrorResponseWriter(TestFixtures.objectMapper(), TestFixtures.CLOCK, TestFixtures.consentProperties()));

	private static MockHttpServletRequest post(byte[] body) {
		MockHttpServletRequest request = new MockHttpServletRequest("POST", "/consent/v1/x");
		request.setContent(body);
		return request;
	}

	@Test
	void declaredLengthOverLimitIsRefusedWith413() throws Exception {
		FilterChain chain = mock(FilterChain.class);
		MockHttpServletResponse response = new MockHttpServletResponse();

		filter.doFilter(post(new byte[11]), response, chain);

		assertThat(response.getStatus()).isEqualTo(413);
		JsonNode body = TestFixtures.objectMapper().readTree(response.getContentAsString());
		assertThat(body.get("statusCode").asInt()).isEqualTo(413);
		assertThat(body.get("error").get("code").asText()).isEqualTo("PAYLOAD_TOO_LARGE");
		verify(chain, never()).doFilter(any(), any());
	}

	@Test
	void bodyAtTheLimitIsReadInFull() throws Exception {
		AtomicReference<String> read = new AtomicReference<>();
		filter.doFilter(post("0123456789".getBytes(StandardCharsets.UTF_8)), new MockHttpServletResponse(),
				(req, res) -> read.set(new String(req.getInputStream().readAllBytes(), StandardCharsets.UTF_8)));

		assertThat(read.get()).isEqualTo("0123456789");
	}

	@Test
	void chunkedBodyGrowingPastTheLimitThrowsWhileReading() throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest("POST", "/consent/v1/x");
		request.setContent(new byte[0]);
		HttpServletRequest chunked = mock(HttpServletRequest.class);
		when(chunked.getContentLengthLong()).thenReturn(-1L);
		when(chunked.getRequestURI()).thenReturn("/consent/v1/x");
		when(chunked.getContextPath()).thenReturn("");
		when(chunked.getInputStream())
				.thenReturn(new DelegatingServletInputStream(new ByteArrayInputStream(new byte[25])));

		AtomicReference<ServletRequest> wrapped = new AtomicReference<>();
		filter.doFilter(chunked, new MockHttpServletResponse(), (req, res) -> wrapped.set(req));

		ServletInputStream stream = wrapped.get().getInputStream();
		assertThat(wrapped.get().getInputStream()).isSameAs(stream);
		assertThatThrownBy(stream::readAllBytes).isInstanceOf(PayloadTooLargeException.class);
	}

	@Test
	void singleByteReadsAreCountedToo() throws Exception {
		AtomicReference<ServletRequest> wrapped = new AtomicReference<>();
		HttpServletRequest chunked = mock(HttpServletRequest.class);
		when(chunked.getContentLengthLong()).thenReturn(-1L);
		when(chunked.getRequestURI()).thenReturn("/consent/v1/x");
		when(chunked.getContextPath()).thenReturn("");
		when(chunked.getInputStream())
				.thenReturn(new DelegatingServletInputStream(new ByteArrayInputStream(new byte[11])));
		filter.doFilter(chunked, new MockHttpServletResponse(), (req, res) -> wrapped.set(req));

		ServletInputStream stream = wrapped.get().getInputStream();
		for (int i = 0; i < LIMIT; i++) {
			assertThat(stream.read()).isZero();
		}
		assertThatThrownBy(stream::read).isInstanceOf(PayloadTooLargeException.class);
	}

	@Test
	void endOfStreamIsNotCounted() throws Exception {
		AtomicReference<ServletRequest> wrapped = new AtomicReference<>();
		filter.doFilter(post(new byte[0]), new MockHttpServletResponse(), (req, res) -> wrapped.set(req));

		ServletInputStream stream = wrapped.get().getInputStream();
		assertThat(stream.read()).isEqualTo(-1);
		assertThat(stream.read(new byte[4], 0, 4)).isEqualTo(-1);
		assertThat(stream.isFinished()).isTrue();
		assertThat(stream.isReady()).isTrue();
	}

	@Test
	void readerUsesRequestCharsetOrUtf8() throws Exception {
		AtomicReference<ServletRequest> wrapped = new AtomicReference<>();
		MockHttpServletRequest latin = post("café".getBytes(StandardCharsets.ISO_8859_1));
		latin.setCharacterEncoding("ISO-8859-1");
		filter.doFilter(latin, new MockHttpServletResponse(), (req, res) -> wrapped.set(req));
		try (BufferedReader reader = wrapped.get().getReader()) {
			assertThat(reader.readLine()).isEqualTo("café");
		}

		MockHttpServletRequest utf8 = post("café".getBytes(StandardCharsets.UTF_8));
		filter.doFilter(utf8, new MockHttpServletResponse(), (req, res) -> wrapped.set(req));
		try (BufferedReader reader = wrapped.get().getReader()) {
			assertThat(reader.readLine()).isEqualTo("café");
		}
	}

	@Test
	void evidenceUploadUsesTheEvidenceLimit() throws Exception {
		FilterChain chain = mock(FilterChain.class);
		MockHttpServletRequest upload = post(new byte[1024 + 65536]);
		upload.setRequestURI("/consent/v1/evidence");
		filter.doFilter(upload, new MockHttpServletResponse(), chain);
		verify(chain).doFilter(any(), any());

		MockHttpServletResponse response = new MockHttpServletResponse();
		MockHttpServletRequest tooBig = post(new byte[1024 + 65537]);
		tooBig.setRequestURI("/consent/v1/evidence");
		filter.doFilter(tooBig, response, chain);
		assertThat(response.getStatus()).isEqualTo(413);
	}

	@Test
	void getWithoutBodyPasses() throws Exception {
		FilterChain chain = mock(FilterChain.class);
		filter.doFilter(new MockHttpServletRequest("GET", "/consent/v1/x"), new MockHttpServletResponse(), chain);
		verify(chain).doFilter(any(), any());
	}

	@Test
	void readListenerIsDelegated() throws Exception {
		ServletInputStream delegate = mock(ServletInputStream.class);
		ReadListener listener = mock(ReadListener.class);
		new RequestSizeLimitFilter.LimitedInputStream(delegate, LIMIT).setReadListener(listener);
		verify(delegate).setReadListener(listener);
	}
}
