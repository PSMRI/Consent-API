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

package com.iemr.consent.model.notice;

/**
 * A purpose as shown in the notice (front-end API 3).
 *
 * @param purposeCode    e.g. {@code P-REM}
 * @param name           display name
 * @param description    what the data is used for
 * @param isMandatory    the service cannot continue without it
 * @param isWithdrawable it can be withdrawn later
 * @param validityDays   days a grant stays valid, or {@code null} for no expiry
 */
public record NoticePurposeView(String purposeCode, String name, String description, boolean isMandatory,
		boolean isWithdrawable, Integer validityDays) {
}
