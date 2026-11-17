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

package com.iemr.consent.model.consent;

/** Allowed characters for free-text request fields (input sanitising, constitution IX). */
public final class ConsentPatterns {

	/** Names and relationships: letters in any script, spaces and {@code .'-}. */
	public static final String NAME = "^[\\p{L}\\p{M} .'-]{1,100}$";

	/** Identifiers such as device id, service line and reference id. */
	public static final String CODE = "^[A-Za-z0-9_.:-]+$";

	/** Indian 10-digit mobile number. */
	public static final String PHONE = "^[6-9][0-9]{9}$";

	/** A purpose code, as {@code cm_purpose.purpose_code} (at most 20 characters). */
	public static final String PURPOSE_CODE = "^[A-Za-z0-9_.:-]{1,20}$";

	/** Short free text, such as an access-request reason: letters, digits, spaces and basic punctuation. */
	public static final String TEXT = "^[\\p{L}\\p{M}\\p{N} .,'()/:;-]{1,255}$";

	private ConsentPatterns() {
	}
}
