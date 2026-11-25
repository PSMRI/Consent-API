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

package com.iemr.consent.model.notice.admin;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * The English draft of a project's consent message (A7, feature 006). Only English is edited; other languages come
 * from translation on publish. The text is never printed by {@link #toString()}.
 */
public record DraftRequest(
		@NotNull @Pattern(regexp = "^en$", message = "must be en; translations are generated on publish")
		String languageCode, @NotBlank @Size(max = 20000) String body, @Size(max = 4000) String dataItems,
		@Size(max = 4000) String retention, @Size(max = 4000) String recipients,
		@Size(max = 4000) String withdrawalInfo, @Size(max = 4000) String grievanceInfo,
		@Size(max = 4000) String declaration, @Size(max = 4000) String representativeDeclaration,
		@Valid GrievanceInput grievance) {

	@Override
	public String toString() {
		return "DraftRequest[languageCode=" + languageCode + "]";
	}
}
