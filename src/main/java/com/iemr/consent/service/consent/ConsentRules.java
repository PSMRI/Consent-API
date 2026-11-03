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

package com.iemr.consent.service.consent;

import java.util.List;

import com.iemr.consent.utils.exception.ConsentApiException;
import com.iemr.consent.utils.exception.ErrorCode;
import com.iemr.consent.utils.response.FieldErrorDetail;

/** Shared helper for consent validation failures. */
final class ConsentRules {

	private ConsentRules() {
	}

	/** {@code VALIDATION_FAILED} naming one field; the message never contains the value. */
	static ConsentApiException invalid(String field, String message) {
		return new ConsentApiException(ErrorCode.VALIDATION_FAILED, null, null,
				List.of(new FieldErrorDetail(field, message)));
	}

	static boolean blank(String value) {
		return value == null || value.isBlank();
	}
}
