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
package com.iemr.consent.service.notice.admin;

import java.time.Instant;

import com.iemr.consent.data.notice.Notice;
import com.iemr.consent.data.notice.NoticeContext;
import com.iemr.consent.data.notice.NoticeStatus;
import com.iemr.consent.data.notice.NoticeTranslation;
import com.iemr.consent.model.notice.admin.DraftRequest;
import com.iemr.consent.model.notice.admin.GrievanceInput;

/** Synthetic notices for the feature 006 admin tests. */
final class NoticeAdminFixtures {

	static final Integer PROJECT = 3;

	static final String KEY = "3f2504e0-4f89-11d3-9a0c-0305e82c3301";

	static final String HASH = "h1";

	private NoticeAdminFixtures() {
	}

	static Notice notice(long id, int version, NoticeStatus status) {
		Notice n = new Notice();
		n.setNoticeVersionId(id);
		n.setNoticeCode("MMU-REGISTRATION-P3");
		n.setVersionNo(version);
		n.setContext(NoticeContext.REGISTRATION);
		n.setStatus(status);
		n.setProjectId(PROJECT);
		n.setCreatedBy("first.admin");
		n.setGrievanceOfficerName("A. Kumar");
		n.setGrievancePhone("1800111222");
		if (status != NoticeStatus.DRAFT) {
			n.setApprovedBy("publisher");
			n.setPublishedAt(Instant.parse("2026-11-01T09:00:00Z"));
		}
		return n;
	}

	static NoticeTranslation text(long noticeId, String language, String declaration) {
		NoticeTranslation t = new NoticeTranslation();
		t.setNoticeVersionId(noticeId);
		t.setLanguageCode(language);
		t.setLanguageName(language);
		t.setBody("Body " + language);
		t.setDeclaration(declaration);
		return t;
	}

	static DraftRequest draft(String declaration) {
		return new DraftRequest("en", "New body", "Items", "Five years", "Doctors", "Withdraw anytime", "Officer",
				declaration, "On behalf", new GrievanceInput("A. Kumar", "1800111222", "g@example.org", "Office",
						"10-5"));
	}
}
