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
 * The decision for one purpose within a consent event ({@code cm_consent_purpose}). The only
 * change ever made to a stored row is the supersede step ({@code is_current}, {@code status});
 * {@code current_key} is a database-generated column backing "one current decision per patient
 * and purpose".
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "cm_consent_purpose")
public class ConsentPurpose {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "consent_purpose_id")
	private Long consentPurposeId;

	@Column(name = "consent_id")
	private String consentId;

	@Column(name = "beneficiary_reg_id")
	private Long beneficiaryRegId;

	@Column(name = "purpose_code")
	private String purposeCode;

	@Enumerated(EnumType.STRING)
	@Column(name = "decision")
	private Decision decision;

	@Enumerated(EnumType.STRING)
	@Column(name = "status")
	private PurposeStatus status;

	@Column(name = "version_no")
	private Integer versionNo;

	@Column(name = "is_current")
	private boolean current;

	@Column(name = "valid_until")
	private Instant validUntil;

	@Column(name = "current_key", insertable = false, updatable = false)
	private String currentKey;
}
