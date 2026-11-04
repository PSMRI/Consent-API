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

/** Notice text in one language ({@code cm_notice_translation}); never logged. */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "cm_notice_translation")
public class NoticeTranslation extends AuditColumns {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "translation_id")
	private Long translationId;

	@Column(name = "notice_version_id")
	private Long noticeVersionId;

	@Column(name = "language_code")
	private String languageCode;

	@Column(name = "language_name")
	private String languageName;

	@Column(name = "body")
	private String body;

	@Column(name = "data_items")
	private String dataItems;

	@Column(name = "retention")
	private String retention;

	@Column(name = "recipients")
	private String recipients;

	@Column(name = "withdrawal_info")
	private String withdrawalInfo;

	@Column(name = "grievance_info")
	private String grievanceInfo;

	@Column(name = "text_hash")
	private String textHash;

	@Column(name = "audio_ref")
	private String audioRef;

	@Column(name = "helpline_script")
	private String helplineScript;

	/** Patient declaration in this language; {@code null} = the app's default wording. */
	@Column(name = "declaration")
	private String declaration;

	/** Guardian / representative declaration; {@code null} = use {@link #declaration}. */
	@Column(name = "representative_declaration")
	private String representativeDeclaration;

	/** {@code true} when the text was produced by machine translation of the English version (feature 006). */
	@Column(name = "machine_translated")
	private boolean machineTranslated;

	/** For machine translations: the English version number they came from; otherwise {@code null}. */
	@Column(name = "source_version")
	private Integer sourceVersion;
}
