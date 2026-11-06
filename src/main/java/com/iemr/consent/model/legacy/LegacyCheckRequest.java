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

package com.iemr.consent.model.legacy;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * A legacy consent file to check (A9, feature 008): 1 to {@value #MAX_ROWS} rows. The uploader is the logged-in
 * user, never a body field.
 */
public record LegacyCheckRequest(Integer serviceProviderId, @Size(max = 255) String fileName,
		@NotEmpty @Size(max = MAX_ROWS) List<@Valid @NotNull LegacyRowInput> rows) {

	/** Most rows in one file (the front-end list's limit). */
	public static final int MAX_ROWS = 5000;
}
