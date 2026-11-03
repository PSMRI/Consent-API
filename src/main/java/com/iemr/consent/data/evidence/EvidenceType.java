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

package com.iemr.consent.data.evidence;

/** Kind of evidence row: uploaded files, and proof details written at save. */
public enum EvidenceType {
	VOICE_CLIP(true), PATIENT_PHOTO(true), SIGNED_FORM_PHOTO(true), WITNESS(false), WORKER_CONFIRMATION(false),
	OTP(false), GUARDIAN_WITNESS(false);

	private final boolean file;

	EvidenceType(boolean file) {
		this.file = file;
	}

	/** {@code true} for types uploaded as files through the evidence endpoint. */
	public boolean isFile() {
		return file;
	}

	/** {@code true} for the two photo types of the SIGNATURE_PHOTO mode. */
	public boolean isPhoto() {
		return this == PATIENT_PHOTO || this == SIGNED_FORM_PHOTO;
	}
}
