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

package com.iemr.consent.data.legacy;

import java.time.LocalDate;

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
 * One row of a legacy file ({@code cm_legacy_row}, feature 008, AMRIT-DB V37) with its check result. The
 * representative's name and the remarks are stored encrypted ({@code FieldEncryptor}); {@code toString} shows
 * neither.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "cm_legacy_row")
public class LegacyRow extends AuditColumns {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "row_id")
	private Long rowId;

	@Column(name = "batch_id")
	private String batchId;

	@Column(name = "row_no")
	private Integer rowNo;

	@Column(name = "beneficiary_id")
	private String beneficiaryId;

	@Column(name = "beneficiary_reg_id")
	private Long beneficiaryRegId;

	@Column(name = "consent_date")
	private LocalDate consentDate;

	@Column(name = "purposes")
	private String purposes;

	@Column(name = "language_code")
	private String languageCode;

	@Column(name = "mode")
	private String mode;

	@Column(name = "representative_name")
	private String representativeNameEncrypted;

	@Column(name = "representative_relationship")
	private String representativeRelationship;

	@Column(name = "remarks")
	private String remarksEncrypted;

	@Enumerated(EnumType.STRING)
	@Column(name = "status")
	private LegacyRowStatus status;

	@Column(name = "reasons")
	private String reasons;

	@Column(name = "consent_id")
	private String consentId;

	@Override
	public String toString() {
		return "LegacyRow[rowId=" + rowId + ", rowNo=" + rowNo + ", status=" + status + "]";
	}
}
