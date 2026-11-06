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

package com.iemr.consent.config;

import java.util.List;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.iemr.consent.utils.auth.AuthenticationFilter;
import com.iemr.consent.utils.auth.CurrentUserArgumentResolver;
import com.iemr.consent.utils.auth.ServiceKeyInterceptor;
import com.iemr.consent.utils.cors.OriginFilter;
import com.iemr.consent.utils.http.RequestLoggingFilter;
import com.iemr.consent.utils.http.RequestSizeLimitFilter;
import com.iemr.consent.utils.http.TraceIdFilter;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.Filter;

/**
 * Registers the servlet filters in their fixed order and the {@code @CurrentUser} resolver.
 *
 * <p>Order: trace id, request log (wraps everything below, so refusals are logged), origin,
 * request size, authentication. Every filter is registered for {@code REQUEST} dispatch only, so
 * the container's error forward to {@code /error} is not filtered again and
 * {@code /error} does not need to be a public path.</p>
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

	public static final int TRACE = Ordered.HIGHEST_PRECEDENCE;

	public static final int REQUEST_LOG = TRACE + 5;

	public static final int ORIGIN = TRACE + 10;

	public static final int SIZE = TRACE + 20;

	public static final int AUTH = TRACE + 30;

	private final CurrentUserArgumentResolver currentUserArgumentResolver;

	private final ServiceKeyInterceptor serviceKeyInterceptor;

	public WebConfig(CurrentUserArgumentResolver currentUserArgumentResolver,
			ServiceKeyInterceptor serviceKeyInterceptor) {
		this.currentUserArgumentResolver = currentUserArgumentResolver;
		this.serviceKeyInterceptor = serviceKeyInterceptor;
	}

	/** Admin endpoints also need Common-API's service key (feature 006). */
	@Override
	public void addInterceptors(InterceptorRegistry registry) {
		registry.addInterceptor(serviceKeyInterceptor).addPathPatterns("/consent/v1/admin/**");
	}

	@Bean
	public FilterRegistrationBean<TraceIdFilter> traceIdFilterRegistration(TraceIdFilter filter) {
		return register(filter, TRACE);
	}

	@Bean
	public FilterRegistrationBean<RequestLoggingFilter> requestLoggingFilterRegistration(
			RequestLoggingFilter filter) {
		return register(filter, REQUEST_LOG);
	}

	@Bean
	public FilterRegistrationBean<OriginFilter> originFilterRegistration(OriginFilter filter) {
		return register(filter, ORIGIN);
	}

	@Bean
	public FilterRegistrationBean<RequestSizeLimitFilter> requestSizeLimitFilterRegistration(
			RequestSizeLimitFilter filter) {
		return register(filter, SIZE);
	}

	@Bean
	public FilterRegistrationBean<AuthenticationFilter> authenticationFilterRegistration(
			AuthenticationFilter filter) {
		return register(filter, AUTH);
	}

	@Override
	public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
		resolvers.add(currentUserArgumentResolver);
	}

	private static <T extends Filter> FilterRegistrationBean<T> register(T filter, int order) {
		FilterRegistrationBean<T> registration = new FilterRegistrationBean<>(filter);
		registration.setOrder(order);
		registration.addUrlPatterns("/*");
		registration.setDispatcherTypes(DispatcherType.REQUEST);
		return registration;
	}
}
