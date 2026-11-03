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

package com.iemr.consent.service.evidence;

import java.math.BigDecimal;

import com.iemr.consent.data.evidence.EvidenceType;

/**
 * One uploaded proof file. The bytes are never logged.
 *
 * @param type        VOICE_CLIP, PATIENT_PHOTO or SIGNED_FORM_PHOTO
 * @param sha256      fingerprint computed by the browser
 * @param durationSec voice clip length
 * @param contentType file content type
 * @param content     file bytes
 */
public record EvidenceUpload(EvidenceType type, String sha256, BigDecimal durationSec, String contentType,
		byte[] content) {

	@Override
	public String toString() {
		return "EvidenceUpload[type=" + type + "]";
	}
}
