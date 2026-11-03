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

import com.iemr.consent.utils.exception.ConsentApiException;
import com.iemr.consent.utils.exception.ErrorCode;

/**
 * A refused login: only {@code UNAUTHENTICATED} or {@code SESSION_EXPIRED} (FR-002,
 * clarification 3).
 */
public class AuthenticationException extends ConsentApiException {

	private static final long serialVersionUID = 1L;

	public AuthenticationException(ErrorCode code) {
		super(requireAuthCode(code));
	}

	private static ErrorCode requireAuthCode(ErrorCode code) {
		if (code != ErrorCode.UNAUTHENTICATED && code != ErrorCode.SESSION_EXPIRED) {
			throw new IllegalArgumentException("code must be UNAUTHENTICATED or SESSION_EXPIRED");
		}
		return code;
	}
}
