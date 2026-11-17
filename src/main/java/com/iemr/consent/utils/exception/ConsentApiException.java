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

import java.util.List;

import com.iemr.consent.utils.response.FieldErrorDetail;

import lombok.Getter;

/**
 * Typed base exception: every expected failure is thrown as this (or a subclass) and turned
 * into the standard error body by {@link GlobalExceptionHandler}. Controllers never build error
 * responses themselves (FR-010).
 */
@Getter
public class ConsentApiException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	private final ErrorCode errorCode;

	private final String purposeCode;

	private final transient List<FieldErrorDetail> details;

	public ConsentApiException(ErrorCode errorCode) {
		this(errorCode, null, null, null);
	}

	/**
	 * @param safeMessage message shown to the caller; must not contain internal or personal data
	 */
	public ConsentApiException(ErrorCode errorCode, String safeMessage) {
		this(errorCode, safeMessage, null, null);
	}

	public ConsentApiException(ErrorCode errorCode, String safeMessage, String purposeCode,
			List<FieldErrorDetail> details) {
		super(safeMessage != null ? safeMessage : errorCode.getDefaultMessage());
		this.errorCode = errorCode;
		this.purposeCode = purposeCode;
		this.details = details;
	}
}
