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

import com.iemr.consent.data.notify.NotificationStatus;

/**
 * Outcome of the consent SMS in the save answer (feature 004).
 *
 * @param status      SENT, QUEUED, FAILED or NOT_REQUESTED
 * @param maskedPhone e.g. {@code 98******10}; {@code null} when not requested
 */
public record NotificationResult(NotificationStatus status, String maskedPhone) {

	/** The answer when the save carried no notify. */
	public static NotificationResult notRequested() {
		return new NotificationResult(NotificationStatus.NOT_REQUESTED, null);
	}
}
