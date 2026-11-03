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

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.iemr.consent.data.consent.ConsentPurpose;
import com.iemr.consent.data.consent.PurposeStatus;
import com.iemr.consent.data.grant.AccessGrant;
import com.iemr.consent.data.grant.GrantStatus;
import com.iemr.consent.support.TestFixtures;

class EffectiveStatusTest {

	private final EffectiveStatus status = new EffectiveStatus(TestFixtures.CLOCK);

	private static ConsentPurpose decision(PurposeStatus stored, Instant validUntil) {
		ConsentPurpose row = new ConsentPurpose();
		row.setStatus(stored);
		row.setValidUntil(validUntil);
		return row;
	}

	private static AccessGrant grant(GrantStatus stored, Instant validUntil) {
		AccessGrant grant = new AccessGrant();
		grant.setStatus(stored);
		grant.setValidUntil(validUntil);
		return grant;
	}

	@Test
	@DisplayName("C16: a granted decision past its end date reads as EXPIRED; at the end instant it is still granted")
	void grantedDecisionExpiresAfterItsEndDate() {
		assertThat(status.of(decision(PurposeStatus.GRANTED, TestFixtures.NOW.minusMillis(1))))
				.isEqualTo(PurposeStatus.EXPIRED);
		assertThat(status.of(decision(PurposeStatus.GRANTED, TestFixtures.NOW))).isEqualTo(PurposeStatus.GRANTED);
		assertThat(status.of(decision(PurposeStatus.GRANTED, null))).isEqualTo(PurposeStatus.GRANTED);
		assertThat(status.of(decision(PurposeStatus.DENIED, TestFixtures.NOW.minusSeconds(60))))
				.isEqualTo(PurposeStatus.DENIED);
	}

	@Test
	void activeGrantExpiresAfterItsEndDate() {
		assertThat(status.of(grant(GrantStatus.ACTIVE, TestFixtures.NOW.minusMillis(1))))
				.isEqualTo(GrantStatus.EXPIRED);
		assertThat(status.of(grant(GrantStatus.ACTIVE, TestFixtures.NOW))).isEqualTo(GrantStatus.ACTIVE);
		assertThat(status.of(grant(GrantStatus.PENDING, null))).isEqualTo(GrantStatus.PENDING);
		assertThat(status.of(grant(GrantStatus.REVOKED, TestFixtures.NOW.minusSeconds(60))))
				.isEqualTo(GrantStatus.REVOKED);
	}
}
