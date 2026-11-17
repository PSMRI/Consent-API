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
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.iemr.consent.utils.exception.ErrorCode;

class AuthenticatedUserTest {

	@Test
	void parsesNumericIdFromStringOrNumber() {
		assertThat(AuthenticatedUser.of(" 12 ", "n", ClientType.WEB).userId()).isEqualTo(12L);
		assertThat(AuthenticatedUser.of(13, null, ClientType.MOBILE).userId()).isEqualTo(13L);
	}

	@Test
	void missingIdIsUnauthenticated() {
		assertThatThrownBy(() -> AuthenticatedUser.of(null, "n", ClientType.WEB))
				.isInstanceOf(AuthenticationException.class);
	}

	@Test
	void toStringOmitsUserName() {
		assertThat(new AuthenticatedUser(1L, "asha-private", ClientType.WEB).toString())
				.doesNotContain("asha-private").contains("userId=1");
	}

	@Test
	void authenticationExceptionAcceptsOnlyLoginCodes() {
		assertThat(new AuthenticationException(ErrorCode.UNAUTHENTICATED).getErrorCode())
				.isEqualTo(ErrorCode.UNAUTHENTICATED);
		assertThat(new AuthenticationException(ErrorCode.SESSION_EXPIRED).getErrorCode())
				.isEqualTo(ErrorCode.SESSION_EXPIRED);
		assertThatIllegalArgumentException().isThrownBy(() -> new AuthenticationException(ErrorCode.NOT_FOUND));
	}
}
