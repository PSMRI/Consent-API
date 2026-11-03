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

package com.iemr.consent.utils.idempotency;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import com.iemr.consent.utils.exception.ConsentApiException;
import com.iemr.consent.utils.exception.ErrorCode;
import com.iemr.consent.utils.response.FieldErrorDetail;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Reads the client request key that every write must carry (FR-017, constitution Consent-API
 * constraints, research R4). A missing or non-UUID key is {@code VALIDATION_FAILED}.
 */
public final class IdempotencyKey {

	public static final String HEADER = "Idempotency-Key";

	private static final Pattern UUID = Pattern
			.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

	private IdempotencyKey() {
	}

	/** @return the key, lower-cased */
	public static String require(HttpServletRequest request) {
		String key = request.getHeader(HEADER);
		if (key == null || !UUID.matcher(key.trim()).matches()) {
			throw new ConsentApiException(ErrorCode.VALIDATION_FAILED, null, null,
					List.of(new FieldErrorDetail(HEADER, "must be a UUID")));
		}
		return key.trim().toLowerCase(Locale.ROOT);
	}
}
