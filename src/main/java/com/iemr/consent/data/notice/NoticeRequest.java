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
 * One admin draft save or publish ({@code cm_notice_request}, feature 006). A repeat of the same
 * {@code Idempotency-Key} by the same user is answered from this row (research R6). Append-only.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "cm_notice_request")
public class NoticeRequest extends AuditColumns {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "request_id")
	private Long requestId;

	@Column(name = "actor_user_id")
	private Long actorUserId;

	@Column(name = "request_key")
	private String requestKey;

	@Column(name = "request_hash")
	private String requestHash;

	@Enumerated(EnumType.STRING)
	@Column(name = "action")
	private NoticeRequestAction action;

	@Column(name = "notice_version_id")
	private Long noticeVersionId;
}
