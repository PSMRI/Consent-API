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
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One consent event ({@code cm_consent}). Append-only: the service only ever inserts rows
 * (constitution IX, proposal §5).
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "cm_consent")
public class Consent {

	@Id
	@Column(name = "consent_id")
	private String consentId;

	@Column(name = "beneficiary_reg_id")
	private Long beneficiaryRegId;

	@Enumerated(EnumType.STRING)
	@Column(name = "event_type")
	private EventType eventType;

	@Enumerated(EnumType.STRING)
	@Column(name = "consenting_party")
	private ConsentingParty consentingParty;

	@Column(name = "notice_version_id")
	private Long noticeVersionId;

	@Column(name = "language_code")
	private String languageCode;

	@Column(name = "text_hash")
	private String textHash;

	@Enumerated(EnumType.STRING)
	@Column(name = "channel")
	private Channel channel;

	@Enumerated(EnumType.STRING)
	@Column(name = "mode")
	private ConsentMode mode;

	@Column(name = "captured_by")
	private Long capturedBy;

	@Column(name = "provider_service_map_id")
	private Integer providerServiceMapId;

	@Column(name = "facility_id")
	private Integer facilityId;

	@Column(name = "service_point_id")
	private Integer servicePointId;

	@Column(name = "service_line")
	private String serviceLine;

	@Column(name = "device_id")
	private String deviceId;

	@Column(name = "call_id")
	private String callId;

	@Column(name = "captured_at")
	private Instant capturedAt;

	@Column(name = "server_received_at")
	private Instant serverReceivedAt;

	@Column(name = "capture_time_flagged")
	private boolean captureTimeFlagged;

	@Enumerated(EnumType.STRING)
	@Column(name = "sync_status")
	private SyncStatus syncStatus;

	@Column(name = "project_id")
	private Integer projectId;

	@Column(name = "config_version")
	private Integer configVersion;

	@Column(name = "request_key")
	private String requestKey;

	@Column(name = "request_hash")
	private String requestHash;
}
