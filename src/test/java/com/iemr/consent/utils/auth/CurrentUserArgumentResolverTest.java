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

package com.iemr.consent.utils.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.ServletWebRequest;

import com.iemr.consent.utils.exception.ConsentApiException;
import com.iemr.consent.utils.exception.ErrorCode;

class CurrentUserArgumentResolverTest {

	private final CurrentUserArgumentResolver resolver = new CurrentUserArgumentResolver();

	@SuppressWarnings("unused")
	void handler(@CurrentUser AuthenticatedUser annotated, AuthenticatedUser plain, @CurrentUser String wrongType) {
	}

	private static MethodParameter parameter(int index) throws NoSuchMethodException {
		Method method = CurrentUserArgumentResolverTest.class.getDeclaredMethod("handler", AuthenticatedUser.class,
				AuthenticatedUser.class, String.class);
		return new MethodParameter(method, index);
	}

	@Test
	void supportsOnlyAnnotatedAuthenticatedUserParameters() throws Exception {
		assertThat(resolver.supportsParameter(parameter(0))).isTrue();
		assertThat(resolver.supportsParameter(parameter(1))).isFalse();
		assertThat(resolver.supportsParameter(parameter(2))).isFalse();
	}

	@Test
	void returnsTheRequestAttribute() throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest();
		AuthenticatedUser user = new AuthenticatedUser(9L, "n", ClientType.WEB);
		request.setAttribute(AuthenticatedUser.ATTRIBUTE, user);

		assertThat(resolver.resolveArgument(parameter(0), null, new ServletWebRequest(request), null)).isSameAs(user);
	}

	@Test
	void missingUserIsUnauthenticated() {
		ServletWebRequest request = new ServletWebRequest(new MockHttpServletRequest());

		assertThatThrownBy(() -> resolver.resolveArgument(parameter(0), null, request, null))
				.isInstanceOf(ConsentApiException.class)
				.extracting(e -> ((ConsentApiException) e).getErrorCode()).isEqualTo(ErrorCode.UNAUTHENTICATED);
	}
}
