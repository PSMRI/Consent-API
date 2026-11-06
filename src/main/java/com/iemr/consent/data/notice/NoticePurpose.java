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
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** A purpose covered by a notice version, with its display order ({@code cm_notice_purpose}). */
@Getter
@Setter
@NoArgsConstructor
@Entity
@IdClass(NoticePurposeId.class)
@Table(name = "cm_notice_purpose")
public class NoticePurpose extends AuditColumns {

	@Id
	@Column(name = "notice_version_id")
	private Long noticeVersionId;

	@Id
	@Column(name = "purpose_code")
	private String purposeCode;

	@Column(name = "display_rank")
	private Integer displayRank;
}
