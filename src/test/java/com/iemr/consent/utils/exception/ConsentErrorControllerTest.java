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

package com.iemr.consent.utils.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.EnumSet;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.iemr.consent.config.WebConfig;
import com.iemr.consent.support.LogCapture;
import com.iemr.consent.support.TestFixtures;
import com.iemr.consent.utils.auth.AuthenticationFilter;
import com.iemr.consent.utils.auth.CurrentUserArgumentResolver;
import com.iemr.consent.utils.cors.OriginFilter;
import com.iemr.consent.utils.http.RequestLoggingFilter;
import com.iemr.consent.utils.http.RequestSizeLimitFilter;
import com.iemr.consent.utils.http.TraceIdFilter;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.RequestDispatcher;

class ConsentErrorControllerTest {

	@RegisterExtension
	final LogCapture logs = new LogCapture();

	private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new ConsentErrorController(
			new ErrorResponseWriter(TestFixtures.objectMapper(), TestFixtures.CLOCK, TestFixtures.consentProperties())))
			.build();

	@ParameterizedTest
	@CsvSource({ "404,NOT_FOUND,404", "405,METHOD_NOT_ALLOWED,405", "413,PAYLOAD_TOO_LARGE,413",
		"415,UNSUPPORTED_MEDIA_TYPE,415", "400,MALFORMED_REQUEST,400", "431,MALFORMED_REQUEST,400",
		"500,INTERNAL_ERROR,500", "503,INTERNAL_ERROR,500", "302,INTERNAL_ERROR,500" })
	void containerErrorsUseTheStandardBody(int containerStatus, String code, int status) throws Exception {
		mvc.perform(get("/error").requestAttr(RequestDispatcher.ERROR_STATUS_CODE, containerStatus)
				.requestAttr(TraceIdFilter.ATTRIBUTE, "0123456789abcdef")).andExpect(status().is(status))
				.andExpect(jsonPath("$.statusCode").value(status)).andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.error.code").value(code))
				.andExpect(jsonPath("$.traceId").value("0123456789abcdef"));
	}

	@Test
	void exceptionEscapingAFilterIs500WithoutItsText() throws Exception {
		MvcResult result = mvc.perform(get("/error")
				.requestAttr(RequestDispatcher.ERROR_EXCEPTION, new IllegalStateException("token=abc.def.ghi")))
				.andExpect(status().isInternalServerError()).andExpect(jsonPath("$.error.code").value("INTERNAL_ERROR"))
				.andReturn();

		assertThat(result.getResponse().getContentAsString()).doesNotContain("token=").doesNotContain("Illegal");
		logs.assertNoneContain("abc.def.ghi");
		assertThat(logs.messages()).anyMatch(line -> line.contains("IllegalStateException"));
	}

	@Test
	void nonIntegerStatusDefaultsTo500() throws Exception {
		mvc.perform(get("/error").requestAttr(RequestDispatcher.ERROR_STATUS_CODE, "404"))
				.andExpect(status().isInternalServerError());
	}

	@Test
	void everyFilterIsRegisteredForRequestDispatchOnlyInTheFixedOrder() {
		WebConfig config = new WebConfig(new CurrentUserArgumentResolver());
		List<FilterRegistrationBean<?>> registrations = List.of(
				config.traceIdFilterRegistration(new TraceIdFilter()),
				config.requestLoggingFilterRegistration(new RequestLoggingFilter()),
				config.originFilterRegistration(mock(OriginFilter.class)),
				config.requestSizeLimitFilterRegistration(mock(RequestSizeLimitFilter.class)),
				config.authenticationFilterRegistration(mock(AuthenticationFilter.class)));

		assertThat(registrations).extracting(FilterRegistrationBean::getOrder).containsExactly(WebConfig.TRACE,
				WebConfig.REQUEST_LOG, WebConfig.ORIGIN, WebConfig.SIZE, WebConfig.AUTH).isSorted();
		for (FilterRegistrationBean<?> registration : registrations) {
			assertThat(registration.getUrlPatterns()).containsExactly("/*");
			assertThat(dispatcherTypes(registration)).isEqualTo(EnumSet.of(DispatcherType.REQUEST));
		}
	}

	private static EnumSet<DispatcherType> dispatcherTypes(FilterRegistrationBean<?> registration) {
		try {
			var field = org.springframework.boot.web.servlet.AbstractFilterRegistrationBean.class
					.getDeclaredField("dispatcherTypes");
			field.setAccessible(true);
			@SuppressWarnings("unchecked")
			EnumSet<DispatcherType> types = (EnumSet<DispatcherType>) field.get(registration);
			return types;
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
	}
}
