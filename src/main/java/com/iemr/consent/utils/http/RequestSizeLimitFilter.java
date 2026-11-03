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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.iemr.consent.config.ConsentProperties;
import com.iemr.consent.utils.exception.ErrorCode;
import com.iemr.consent.utils.exception.ErrorResponseWriter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Refuses request bodies larger than {@code consent.request.max-body-bytes} with 413
 * {@code PAYLOAD_TOO_LARGE} (research R6).
 *
 * <p>A declared {@code Content-Length} over the limit is refused at once. Otherwise the body
 * stream is wrapped and counted, so a chunked body that grows past the limit throws
 * {@link PayloadTooLargeException} while it is read.</p>
 */
@Component
public class RequestSizeLimitFilter extends OncePerRequestFilter {

	static final String EVIDENCE_PATH = "/consent/v1/evidence";

	static final long MULTIPART_ENVELOPE_BYTES = 65536;

	private final ConsentProperties properties;

	private final ErrorResponseWriter errorResponseWriter;

	public RequestSizeLimitFilter(ConsentProperties properties, ErrorResponseWriter errorResponseWriter) {
		this.properties = properties;
		this.errorResponseWriter = errorResponseWriter;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
			FilterChain filterChain) throws ServletException, IOException {
		long limit = limitFor(request);
		if (request.getContentLengthLong() > limit) {
			errorResponseWriter.write(request, response, ErrorCode.PAYLOAD_TOO_LARGE, null);
			return;
		}
		filterChain.doFilter(new LimitedRequest(request, limit), response);
	}

	/**
	 * The evidence upload has its own, larger limit ({@code consent.evidence.max-bytes} plus room for
	 * the multipart envelope); every other request uses {@code consent.request.max-body-bytes}.
	 */
	private long limitFor(HttpServletRequest request) {
		String path = request.getRequestURI().substring(request.getContextPath().length());
		return EVIDENCE_PATH.equals(path) ? properties.evidence().maxBytes() + MULTIPART_ENVELOPE_BYTES
				: properties.request().maxBodyBytes();
	}

	/** Request whose body stream throws once more than {@code limit} bytes are read. */
	static final class LimitedRequest extends HttpServletRequestWrapper {

		private final long limit;

		private ServletInputStream stream;

		LimitedRequest(HttpServletRequest request, long limit) {
			super(request);
			this.limit = limit;
		}

		@Override
		public ServletInputStream getInputStream() throws IOException {
			if (stream == null) {
				stream = new LimitedInputStream(super.getInputStream(), limit);
			}
			return stream;
		}

		@Override
		public BufferedReader getReader() throws IOException {
			String encoding = getCharacterEncoding();
			Charset charset = encoding != null ? Charset.forName(encoding) : StandardCharsets.UTF_8;
			return new BufferedReader(new InputStreamReader(getInputStream(), charset));
		}
	}

	/** Counting stream; throws {@link PayloadTooLargeException} past the limit. */
	static final class LimitedInputStream extends ServletInputStream {

		private final ServletInputStream delegate;

		private final long limit;

		private long count;

		LimitedInputStream(ServletInputStream delegate, long limit) {
			this.delegate = delegate;
			this.limit = limit;
		}

		@Override
		public int read() throws IOException {
			int value = delegate.read();
			if (value != -1) {
				add(1);
			}
			return value;
		}

		@Override
		public int read(byte[] buffer, int offset, int length) throws IOException {
			int read = delegate.read(buffer, offset, length);
			if (read > 0) {
				add(read);
			}
			return read;
		}

		@Override
		public boolean isFinished() {
			return delegate.isFinished();
		}

		@Override
		public boolean isReady() {
			return delegate.isReady();
		}

		@Override
		public void setReadListener(ReadListener readListener) {
			delegate.setReadListener(readListener);
		}

		private void add(long bytes) {
			count += bytes;
			if (count > limit) {
				throw new PayloadTooLargeException();
			}
		}
	}
}
