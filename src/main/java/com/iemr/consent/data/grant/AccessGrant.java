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

package com.iemr.consent.data.grant;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.TreeSet;

import com.iemr.consent.data.AuditColumns;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A doctor's time-limited access to a patient's data ({@code cm_access_grant}, AMRIT-DB V33). Only
 * approve, revoke and the withdrawal cascade change a row. {@code purposes} holds sorted, unique
 * purpose codes joined with commas.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "cm_access_grant")
public class AccessGrant extends AuditColumns {

	@Id
	@Column(name = "grant_id")
	private String grantId;

	@Column(name = "beneficiary_reg_id")
	private Long beneficiaryRegId;

	@Enumerated(EnumType.STRING)
	@Column(name = "grant_type")
	private GrantType grantType;

	@Column(name = "grantee_user_id")
	private Long granteeUserId;

	@Column(name = "grantee_name")
	private String granteeName;

	@Column(name = "grantee_facility_id")
	private Integer granteeFacilityId;

	@Column(name = "purposes")
	private String purposes;

	@Column(name = "reason")
	private String reason;

	@Column(name = "days")
	private Integer days;

	@Enumerated(EnumType.STRING)
	@Column(name = "status")
	private GrantStatus status;

	@Column(name = "valid_until")
	private Instant validUntil;

	@Column(name = "approved_at")
	private Instant approvedAt;

	@Column(name = "approved_by")
	private Long approvedBy;

	@Column(name = "approval_masked_phone")
	private String approvalMaskedPhone;

	@Column(name = "revoked_at")
	private Instant revokedAt;

	@Column(name = "reviewed_by")
	private Long reviewedBy;

	@Enumerated(EnumType.STRING)
	@Column(name = "revoke_reason")
	private RevokeReason revokeReason;

	@Column(name = "requested_by")
	private Long requestedBy;

	@Column(name = "request_key")
	private String requestKey;

	@Column(name = "request_hash")
	private String requestHash;

	/** @return the purpose codes, in stored (sorted) order */
	public List<String> purposeList() {
		return Arrays.asList(purposes.split(","));
	}

	/** Stores the codes sorted and without duplicates. */
	public void setPurposeList(Collection<String> codes) {
		this.purposes = String.join(",", new TreeSet<>(codes));
	}

	@Override
	public String toString() {
		return "AccessGrant[grantId=" + grantId + ", status=" + status + "]";
	}
}
