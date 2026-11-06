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

/** A versioned notice for one context ({@code cm_notice}). */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "cm_notice")
public class Notice extends AuditColumns {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "notice_version_id")
	private Long noticeVersionId;

	@Column(name = "notice_code")
	private String noticeCode;

	@Column(name = "version_no")
	private Integer versionNo;

	@Enumerated(EnumType.STRING)
	@Column(name = "context")
	private NoticeContext context;

	@Column(name = "is_material")
	private boolean material;

	@Enumerated(EnumType.STRING)
	@Column(name = "status")
	private NoticeStatus status;

	@Column(name = "approved_by")
	private String approvedBy;

	@Column(name = "published_at")
	private Instant publishedAt;

	/** {@code null} = central notice; otherwise the project that published it (feature 004). */
	@Column(name = "project_id")
	private Integer projectId;

	@Column(name = "grievance_officer_name")
	private String grievanceOfficerName;

	@Column(name = "grievance_phone")
	private String grievancePhone;

	@Column(name = "grievance_email")
	private String grievanceEmail;

	@Column(name = "grievance_address")
	private String grievanceAddress;

	@Column(name = "grievance_office_hours")
	private String grievanceOfficeHours;
}
