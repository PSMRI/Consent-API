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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.iemr.consent.support.LogCapture;
import com.iemr.consent.support.TestFixtures;
import com.iemr.consent.support.TestTokens;
import com.iemr.consent.utils.exception.ErrorCode;

class JwtValidatorTest {

	@RegisterExtension
	final LogCapture logs = new LogCapture();

	private final TokenDenylist denylist = mock(TokenDenylist.class);

	private final JwtValidator validator = new JwtValidator(TestFixtures.securityProperties(), denylist,
			TestFixtures.CLOCK);

	private static void assertRefused(Runnable call, ErrorCode code) {
		assertThatThrownBy(call::run).isInstanceOf(AuthenticationException.class)
				.extracting(e -> ((AuthenticationException) e).getErrorCode()).isEqualTo(code);
	}

	@Test
	void validTokenGivesWebUser() {
		String token = TestTokens.valid("42", "asha", "jti-1");

		AuthenticatedUser user = validator.validate(token);

		assertThat(user).isEqualTo(new AuthenticatedUser(42L, "asha", ClientType.WEB));
		verify(denylist).isDenylisted("jti-1");
		logs.assertNoneContain(token);
	}

	@Test
	void expiredTokenIsSessionExpired() {
		String token = TestTokens.expired("42");
		assertRefused(() -> validator.validate(token), ErrorCode.SESSION_EXPIRED);
		logs.assertNoneContain(token);
	}

	@Test
	void badSignatureIsUnauthenticated() {
		String token = TestTokens.badSignature("42");
		assertRefused(() -> validator.validate(token), ErrorCode.UNAUTHENTICATED);
		logs.assertNoneContain(token);
	}

	@ParameterizedTest
	@ValueSource(strings = { TestTokens.MALFORMED, "", "a.b.c", "eyJhbGciOiJub25lIn0.eyJ1c2VySWQiOiI0MiJ9." })
	void malformedUnsupportedOrEmptyIsUnauthenticated(String token) {
		assertRefused(() -> validator.validate(token), ErrorCode.UNAUTHENTICATED);
	}

	@Test
	void nullTokenIsUnauthenticated() {
		assertRefused(() -> validator.validate(null), ErrorCode.UNAUTHENTICATED);
	}

	@Test
	void missingUserIdIsUnauthenticated() {
		assertRefused(() -> validator.validate(TestTokens.withoutUserId()), ErrorCode.UNAUTHENTICATED);
	}

	@ParameterizedTest
	@ValueSource(strings = { "abc", "0", "-5" })
	void nonNumericOrNonPositiveUserIdIsUnauthenticated(String userId) {
		assertRefused(() -> validator.validate(TestTokens.valid(userId, "x", "jti")), ErrorCode.UNAUTHENTICATED);
	}

	@Test
	void denylistedTokenIsUnauthenticated() {
		when(denylist.isDenylisted("jti-out")).thenReturn(true);
		assertRefused(() -> validator.validate(TestTokens.valid("42", "asha", "jti-out")), ErrorCode.UNAUTHENTICATED);
	}

	@Test
	void tokenWithoutJtiSkipsTheDenylist() {
		assertThat(validator.validate(TestTokens.valid("7", "n", null)).userId()).isEqualTo(7L);
		verify(denylist, never()).isDenylisted(any());
	}
}
