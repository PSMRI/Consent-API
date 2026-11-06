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

package com.iemr.consent.utils.response;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.iemr.consent.utils.exception.ErrorCode;

/**
 * The {@code error} part of an {@link ApiResponse}.
 *
 * @param code        stable machine-readable code
 * @param purposeCode purpose of a consent refusal; {@code null} otherwise
 * @param details     invalid fields for {@code VALIDATION_FAILED}; {@code null} otherwise
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record ErrorBody(ErrorCode code, String purposeCode, List<FieldErrorDetail> details) {
}
