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

package com.iemr.consent.service.principal;

import java.time.Clock;
import java.time.Instant;

import org.springframework.stereotype.Component;

import com.iemr.consent.data.consent.ConsentPurpose;
import com.iemr.consent.data.consent.PurposeStatus;
import com.iemr.consent.data.grant.AccessGrant;
import com.iemr.consent.data.grant.GrantStatus;

/**
 * Status as of now (research R4): a GRANTED decision or an ACTIVE grant whose end date has passed is
 * EXPIRED, even before a scheduled job records it. Used by reads and writes alike; nothing is written.
 */
@Component
public class EffectiveStatus {

	private final Clock clock;

	public EffectiveStatus(Clock clock) {
		this.clock = clock;
	}

	public PurposeStatus of(ConsentPurpose decision) {
		if (decision.getStatus() == PurposeStatus.GRANTED && ended(decision.getValidUntil())) {
			return PurposeStatus.EXPIRED;
		}
		return decision.getStatus();
	}

	public GrantStatus of(AccessGrant grant) {
		if (grant.getStatus() == GrantStatus.ACTIVE && ended(grant.getValidUntil())) {
			return GrantStatus.EXPIRED;
		}
		return grant.getStatus();
	}

	private boolean ended(Instant validUntil) {
		return validUntil != null && validUntil.isBefore(clock.instant());
	}
}
