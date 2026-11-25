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

package com.iemr.consent.service.grant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.iemr.consent.data.grant.AccessGrant;
import com.iemr.consent.data.grant.GrantStatus;
import com.iemr.consent.data.grant.GrantType;
import com.iemr.consent.model.grant.AccessRequest;
import com.iemr.consent.repository.grant.AccessGrantRepository;
import com.iemr.consent.service.audit.AuditLogService;
import com.iemr.consent.support.ConsentFixtures;

class AccessGrantWriterTest {

	@Test
	void createsAPendingGrantForTheLoggedInUserAndAuditsIt() {
		AccessGrantRepository repository = mock(AccessGrantRepository.class);
		AuditLogService audit = mock(AuditLogService.class);
		when(repository.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
		AccessRequest request = new AccessRequest(1001L, List.of("P-REM", "P-FAM"), 7, "Referral follow-up", 5);

		AccessGrant grant = new AccessGrantWriter(repository, audit).create(request, "key-1", "hash-1",
				ConsentFixtures.WORKER, "10.0.0.1");

		assertThat(grant.getGrantId()).hasSize(36);
		assertThat(grant.getGrantType()).isEqualTo(GrantType.CONSENTED);
		assertThat(grant.getStatus()).isEqualTo(GrantStatus.PENDING);
		assertThat(grant.getGranteeUserId()).isEqualTo(42L);
		assertThat(grant.getGranteeName()).isEqualTo("worker42");
		assertThat(grant.getGranteeFacilityId()).isEqualTo(5);
		assertThat(grant.getRequestedBy()).isEqualTo(42L);
		assertThat(grant.getPurposes()).isEqualTo("P-FAM,P-REM");
		assertThat(grant.getDays()).isEqualTo(7);
		assertThat(grant.getReason()).isEqualTo("Referral follow-up");
		assertThat(grant.getRequestKey()).isEqualTo("key-1");
		assertThat(grant.getRequestHash()).isEqualTo("hash-1");
		assertThat(grant.getCreatedBy()).isEqualTo("worker42");
		verify(audit).append(AuditLogService.ACCESS_REQUESTED, null, 1001L, "grant:" + grant.getGrantId(),
				"PENDING", 42L, "10.0.0.1");
	}
}
