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

package com.iemr.consent.data.notice;

import com.iemr.consent.data.AuditColumns;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** A use of patient data ({@code cm_purpose}), versioned. */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "cm_purpose")
public class Purpose extends AuditColumns {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "purpose_id")
	private Long purposeId;

	@Column(name = "purpose_code")
	private String purposeCode;

	@Column(name = "version")
	private Integer version;

	@Column(name = "name")
	private String name;

	@Column(name = "description")
	private String description;

	@Column(name = "legal_basis")
	private String legalBasis;

	@Column(name = "is_mandatory")
	private boolean mandatory;

	@Column(name = "is_withdrawable")
	private boolean withdrawable;

	@Column(name = "validity_days")
	private Integer validityDays;

	@Column(name = "is_active")
	private boolean active;
}
