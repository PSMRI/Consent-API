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

import com.iemr.consent.data.notice.Notice;

/**
 * The grievance officer's official contact shown with a notice and after consent (feature 004). Published business
 * contact, not patient data.
 *
 * @param officerName officer's name
 * @param phone       office phone
 * @param email       office email
 * @param address     office address
 * @param officeHours when the office can be reached
 */
public record GrievanceContact(String officerName, String phone, String email, String address, String officeHours) {

	/** The notice's contact, or {@code null} when none of its grievance fields is set. */
	public static GrievanceContact from(Notice n) {
		GrievanceContact contact = new GrievanceContact(n.getGrievanceOfficerName(), n.getGrievancePhone(),
				n.getGrievanceEmail(), n.getGrievanceAddress(), n.getGrievanceOfficeHours());
		return contact.equals(new GrievanceContact(null, null, null, null, null)) ? null : contact;
	}
}
