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

package com.iemr.consent.data.consent;

import java.time.Instant;

import com.iemr.consent.data.AuditColumns;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * The guardian who consented for a patient ({@code cm_guardian_link}). Name and phone hold
 * {@code FieldEncryptor} output, never plain values.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "cm_guardian_link")
public class GuardianLink extends AuditColumns {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "guardian_link_id")
	private Long guardianLinkId;

	@Column(name = "consent_id")
	private String consentId;

	@Column(name = "beneficiary_reg_id")
	private Long beneficiaryRegId;

	@Enumerated(EnumType.STRING)
	@Column(name = "guardian_type")
	private GuardianType guardianType;

	@Column(name = "guardian_name")
	private String guardianNameEncrypted;

	@Column(name = "guardian_phone")
	private String guardianPhoneEncrypted;

	@Column(name = "relationship")
	private String relationship;

	@Enumerated(EnumType.STRING)
	@Column(name = "verification_method")
	private GuardianVerification verificationMethod;

	@Column(name = "verified_at")
	private Instant verifiedAt;

	@Column(name = "follow_up_required")
	private boolean followUpRequired;

	@Column(name = "valid_until")
	private Instant validUntil;
}
