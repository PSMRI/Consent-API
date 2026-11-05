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
package com.iemr.consent.model.evidence;

import java.math.BigDecimal;
import java.time.Instant;

import com.iemr.consent.data.evidence.EvidenceType;

/**
 * A proof file linked to a consent, without its content (evidence retrieval). Fetch the file itself from
 * {@code GET /consent/v1/evidence/{evidenceId}/content}.
 *
 * @param evidenceId  id of the file
 * @param type        VOICE_CLIP, SIGNED_FORM_PHOTO (or PATIENT_PHOTO on older consents)
 * @param contentType stored media type, e.g. audio/webm or image/jpeg
 * @param sizeBytes   size of the original file
 * @param durationSec length of a voice clip; null for photos
 * @param uploadedBy  user name of the worker who uploaded it
 * @param uploadedAt  when it was uploaded
 */
public record EvidenceView(String evidenceId, EvidenceType type, String contentType, Long sizeBytes,
		BigDecimal durationSec, String uploadedBy, Instant uploadedAt) {
}
