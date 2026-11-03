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

package com.iemr.consent.data;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;

/**
 * Standard AMRIT audit columns with soft delete (constitution IX), for every {@code cm_} table
 * except the append-only consent and audit tables. {@code CreatedDate} and {@code LastModDate}
 * are maintained by the database.
 */
@Getter
@Setter
@MappedSuperclass
public abstract class AuditColumns {

	@Column(name = "CreatedBy", nullable = false, updatable = false)
	private String createdBy;

	@Column(name = "CreatedDate", insertable = false, updatable = false)
	private Instant createdDate;

	@Column(name = "ModifiedBy")
	private String modifiedBy;

	@Column(name = "LastModDate", insertable = false, updatable = false)
	private Instant lastModDate;

	@Column(name = "Deleted", nullable = false)
	private boolean deleted;
}
