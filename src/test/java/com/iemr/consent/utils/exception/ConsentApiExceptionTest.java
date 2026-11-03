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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.iemr.consent.utils.response.FieldErrorDetail;

class ConsentApiExceptionTest {

	@Test
	void codeOnlyUsesTheDefaultMessage() {
		ConsentApiException e = new ConsentApiException(ErrorCode.NOT_FOUND);

		assertThat(e.getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND);
		assertThat(e.getMessage()).isEqualTo(ErrorCode.NOT_FOUND.getDefaultMessage());
		assertThat(e.getPurposeCode()).isNull();
		assertThat(e.getDetails()).isNull();
	}

	@Test
	void keepsSafeMessage() {
		assertThat(new ConsentApiException(ErrorCode.DEPENDENCY_UNAVAILABLE, "Service unavailable: cache")
				.getMessage()).isEqualTo("Service unavailable: cache");
	}

	@Test
	void keepsPurposeCodeAndDetailsUnchanged() {
		List<FieldErrorDetail> details = List.of(new FieldErrorDetail("f", "m"));
		ConsentApiException e = new ConsentApiException(ErrorCode.VALIDATION_FAILED, null, "P-1", details);

		assertThat(e.getPurposeCode()).isEqualTo("P-1");
		assertThat(e.getDetails()).isSameAs(details);
	}
}
