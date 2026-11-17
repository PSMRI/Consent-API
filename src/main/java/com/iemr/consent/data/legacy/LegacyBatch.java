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

import java.time.Instant;

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
 * One uploaded legacy consent file ({@code cm_legacy_batch}, feature 008, AMRIT-DB V37). Created by a check (A9)
 * and imported at most once (A10); the import claim is a conditional update, so two imports cannot both run.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "cm_legacy_batch")
public class LegacyBatch extends AuditColumns {

	@Id
	@Column(name = "batch_id")
	private String batchId;

	@Column(name = "file_name")
	private String fileName;

	@Column(name = "service_provider_id")
	private Integer serviceProviderId;

	@Enumerated(EnumType.STRING)
	@Column(name = "status")
	private LegacyBatchStatus status;

	@Column(name = "total")
	private int total;

	@Column(name = "valid")
	private int valid;

	@Column(name = "imported")
	private int imported;

	@Column(name = "skipped")
	private int skipped;

	@Column(name = "failed")
	private int failed;

	@Column(name = "uploaded_by_user_id")
	private Long uploadedByUserId;

	@Column(name = "uploaded_by")
	private String uploadedBy;

	@Column(name = "uploaded_at")
	private Instant uploadedAt;

	@Column(name = "imported_by")
	private String importedBy;

	@Column(name = "imported_at")
	private Instant importedAt;

	@Column(name = "expires_at")
	private Instant expiresAt;

	@Column(name = "request_key")
	private String requestKey;

	@Column(name = "request_hash")
	private String requestHash;

	@Column(name = "import_request_key")
	private String importRequestKey;

	@Column(name = "import_request_hash")
	private String importRequestHash;
}
