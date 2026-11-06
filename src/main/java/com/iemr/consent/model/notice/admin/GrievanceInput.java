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

import jakarta.validation.constraints.Size;

/**
 * The grievance officer's official contact as Admin enters it (feature 006). Sizes follow the V35 columns.
 *
 * @param officerName officer's name
 * @param phone       office phone
 * @param email       office email
 * @param address     office address
 * @param officeHours when the office can be reached
 */
public record GrievanceInput(@Size(max = 100) String officerName, @Size(max = 20) String phone,
		@Size(max = 100) String email, @Size(max = 500) String address, @Size(max = 100) String officeHours) {
}
