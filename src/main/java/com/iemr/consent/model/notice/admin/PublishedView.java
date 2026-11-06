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

import java.time.Instant;

import com.iemr.consent.model.notice.GrievanceContact;

/**
 * The published text of one language of a message entry (A6, feature 006). Every language of a version shares its
 * {@code noticeVersionId}; machine translations name the English {@code sourceVersion}. The text is never printed.
 */
public record PublishedView(String body, String dataItems, String retention, String recipients,
		String withdrawalInfo, String grievanceInfo, String declaration, String representativeDeclaration,
		GrievanceContact grievance, Long noticeVersionId, Integer version, String publishedBy,
		Instant publishedAt,
		boolean machineTranslated, Integer sourceVersion, boolean material) {

	@Override
	public String toString() {
		return "PublishedView[noticeVersionId=" + noticeVersionId + ", version=" + version + "]";
	}
}
