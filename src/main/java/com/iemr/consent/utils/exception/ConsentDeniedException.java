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

/**
 * The one shared way to refuse an action for lack of consent (FR-009): HTTP 403 with one of
 * the six consent reason codes and the purpose code.
 */
public class ConsentDeniedException extends ConsentApiException {

	private static final long serialVersionUID = 1L;

	/**
	 * @param reason      a consent reason code ({@link ErrorCode#isConsentReason()})
	 * @param purposeCode the purpose that lacks consent, e.g. {@code P-SHR}; required
	 */
	public ConsentDeniedException(ErrorCode reason, String purposeCode) {
		super(requireConsentReason(reason), null, requirePurpose(purposeCode), null);
	}

	private static ErrorCode requireConsentReason(ErrorCode reason) {
		if (reason == null || !reason.isConsentReason()) {
			throw new IllegalArgumentException("reason must be a consent reason code");
		}
		return reason;
	}

	private static String requirePurpose(String purposeCode) {
		if (purposeCode == null || purposeCode.isBlank()) {
			throw new IllegalArgumentException("purposeCode is required");
		}
		return purposeCode;
	}
}
