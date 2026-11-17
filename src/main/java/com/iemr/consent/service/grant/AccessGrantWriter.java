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

import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.iemr.consent.data.grant.AccessGrant;
import com.iemr.consent.data.grant.GrantStatus;
import com.iemr.consent.data.grant.GrantType;
import com.iemr.consent.model.grant.AccessRequest;
import com.iemr.consent.repository.grant.AccessGrantRepository;
import com.iemr.consent.service.audit.AuditLogService;
import com.iemr.consent.utils.auth.AuthenticatedUser;

/** Saves a new PENDING access grant and its audit entry in one transaction (FR-017, FR-021). */
@Component
public class AccessGrantWriter {

	private final AccessGrantRepository repository;

	private final AuditLogService auditLogService;

	public AccessGrantWriter(AccessGrantRepository repository, AuditLogService auditLogService) {
		this.repository = repository;
		this.auditLogService = auditLogService;
	}

	@Transactional
	public AccessGrant create(AccessRequest request, String requestKey, String requestHash, AuthenticatedUser user,
			String sourceIp) {
		AccessGrant grant = new AccessGrant();
		grant.setGrantId(UUID.randomUUID().toString());
		grant.setBeneficiaryRegId(request.beneficiaryRegId());
		grant.setGrantType(GrantType.CONSENTED);
		grant.setGranteeUserId(user.userId());
		grant.setGranteeName(user.auditName());
		grant.setGranteeFacilityId(request.facilityId());
		grant.setPurposeList(request.purposeCodes());
		grant.setReason(request.reason());
		grant.setDays(request.days());
		grant.setStatus(GrantStatus.PENDING);
		grant.setRequestedBy(user.userId());
		grant.setRequestKey(requestKey);
		grant.setRequestHash(requestHash);
		grant.setCreatedBy(user.auditName());
		AccessGrant saved = repository.saveAndFlush(grant);
		auditLogService.append(AuditLogService.ACCESS_REQUESTED, null, saved.getBeneficiaryRegId(),
				"grant:" + saved.getGrantId(), GrantStatus.PENDING.name(), user.userId(), sourceIp);
		return saved;
	}
}
