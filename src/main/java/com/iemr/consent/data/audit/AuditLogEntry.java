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

package com.iemr.consent.data.audit;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One hash-chained, append-only audit entry ({@code cm_audit_log}): {@code entry_hash} =
 * SHA-256 of {@code prev_hash} and the entry's content. Holds IDs and codes only, no personal
 * values.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "cm_audit_log")
public class AuditLogEntry {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id")
	private Long id;

	@Column(name = "action")
	private String action;

	@Column(name = "consent_id")
	private String consentId;

	@Column(name = "beneficiary_reg_id")
	private Long beneficiaryRegId;

	@Column(name = "purpose")
	private String purpose;

	@Column(name = "status")
	private String status;

	@Column(name = "initiator")
	private Long initiator;

	@Column(name = "source_ip")
	private String sourceIp;

	@Column(name = "created_at")
	private Instant createdAt;

	@Column(name = "prev_hash")
	private String prevHash;

	@Column(name = "entry_hash")
	private String entryHash;
}
