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

package com.iemr.consent.model.notice;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * The active notice for a context and language (front-end API 3). {@code noticeVersionId} and
 * {@code textHash} are sent back when saving, to prove which text was shown. Never logged.
 *
 * @param noticeVersionId notice version
 * @param languageCode    language shown
 * @param context         {@code REGISTRATION} or {@code REVISIT}
 * @param body            notice text
 * @param textHash        SHA-256 fingerprint of the text sections
 * @param audioUrl        recorded audio, or {@code null}
 * @param dataItems       data collected
 * @param retention       how long it is kept
 * @param recipients      who it is shared with
 * @param withdrawalInfo  how to withdraw
 * @param grievanceInfo   how to complain
 * @param purposes        mandatory first, then by display order
 * @param declaration     patient declaration; omitted when not published (app default wording, feature 004)
 * @param representativeDeclaration guardian / representative wording; omitted when not published
 * @param grievance       grievance officer's contact, or {@code null}
 */
public record ActiveNotice(Long noticeVersionId, String languageCode, String context, String body,
		String textHash, String audioUrl, String dataItems, String retention, String recipients,
		String withdrawalInfo, String grievanceInfo, List<NoticePurposeView> purposes,
		@JsonInclude(JsonInclude.Include.NON_NULL) String declaration,
		@JsonInclude(JsonInclude.Include.NON_NULL) String representativeDeclaration,
		GrievanceContact grievance) {

	@Override
	public String toString() {
		return "ActiveNotice[noticeVersionId=" + noticeVersionId + ", languageCode=" + languageCode + "]";
	}
}
