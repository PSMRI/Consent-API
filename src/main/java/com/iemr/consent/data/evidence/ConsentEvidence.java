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

package com.iemr.consent.data.evidence;

import java.math.BigDecimal;
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
 * A proof file or proof detail ({@code cm_consent_evidence}). Files are stored encrypted outside
 * the database; the row keeps the reference and SHA-256. {@code consent_id} is set once, when a
 * save links the evidence. {@code witness_name} holds {@code FieldEncryptor} output.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "cm_consent_evidence")
public class ConsentEvidence extends AuditColumns {

	@Id
	@Column(name = "evidence_id")
	private String evidenceId;

	@Column(name = "consent_id")
	private String consentId;

	@Enumerated(EnumType.STRING)
	@Column(name = "type")
	private EvidenceType type;

	@Column(name = "file_ref")
	private String fileRef;

	@Column(name = "sha256")
	private String sha256;

	@Column(name = "content_type")
	private String contentType;

	@Column(name = "size_bytes")
	private Long sizeBytes;

	@Column(name = "duration_sec")
	private BigDecimal durationSec;

	@Column(name = "witness_name")
	private String witnessNameEncrypted;

	@Column(name = "witness_relationship")
	private String witnessRelationship;

	@Column(name = "otp_status")
	private String otpStatus;

	@Column(name = "masked_phone")
	private String maskedPhone;

	@Column(name = "call_id")
	private String callId;

	@Column(name = "uploaded_by")
	private Long uploadedBy;

	@Column(name = "request_key")
	private String requestKey;

	@Column(name = "request_hash")
	private String requestHash;

	@Column(name = "created_at")
	private Instant createdAt;
}
