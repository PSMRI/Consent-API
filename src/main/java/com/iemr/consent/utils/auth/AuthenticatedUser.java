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

import com.iemr.consent.utils.exception.ErrorCode;

/**
 * The AMRIT user making the current request (FR-005). Built by {@link AuthenticationFilter},
 * kept as a request attribute and resolved into controllers with {@link CurrentUser}.
 *
 * @param userId     AMRIT user id; always positive
 * @param userName   user name, or {@code null} when the login carries none; never logged
 * @param clientType web or mobile login
 */
public record AuthenticatedUser(Long userId, String userName, ClientType clientType) {

	/** Request attribute holding the {@code AuthenticatedUser}. */
	public static final String ATTRIBUTE = AuthenticatedUser.class.getName();

	/**
	 * Builds a user from a raw id taken from a token claim or session.
	 *
	 * @throws AuthenticationException {@code UNAUTHENTICATED} if the id is missing, not a number
	 *                                 or not positive
	 */
	public static AuthenticatedUser of(Object rawUserId, String userName, ClientType clientType) {
		long userId;
		try {
			userId = Long.parseLong(String.valueOf(rawUserId).trim());
		} catch (NumberFormatException e) {
			throw new AuthenticationException(ErrorCode.UNAUTHENTICATED);
		}
		if (userId <= 0) {
			throw new AuthenticationException(ErrorCode.UNAUTHENTICATED);
		}
		return new AuthenticatedUser(userId, userName, clientType);
	}

	/** Value for the standard {@code CreatedBy} / {@code ModifiedBy} audit columns. */
	public String auditName() {
		return userName != null ? userName : String.valueOf(userId);
	}

	@Override
	public String toString() {
		return "AuthenticatedUser[userId=" + userId + ", clientType=" + clientType + "]";
	}
}
