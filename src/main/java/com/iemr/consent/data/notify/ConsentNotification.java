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

package com.iemr.consent.data.notify;

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
 * The SMS sent after one consent ({@code cm_consent_notification}). The phone is stored encrypted only; the masked
 * form is derived when read (Principle IX).
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "cm_consent_notification")
public class ConsentNotification extends AuditColumns {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "notification_id")
	private Long notificationId;

	@Column(name = "consent_id")
	private String consentId;

	@Enumerated(EnumType.STRING)
	@Column(name = "channel")
	private NotifyChannel channel;

	@Column(name = "phone")
	private String phoneEncrypted;

	@Enumerated(EnumType.STRING)
	@Column(name = "status")
	private NotificationStatus status;

	@Column(name = "attempted_at")
	private Instant attemptedAt;
}
