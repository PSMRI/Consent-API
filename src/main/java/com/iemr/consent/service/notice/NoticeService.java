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

package com.iemr.consent.service.notice;

import java.util.List;
import java.util.Optional;

import com.iemr.consent.data.notice.NoticeContext;
import com.iemr.consent.model.notice.ActiveNotice;
import com.iemr.consent.model.notice.NoticeLanguage;

/** Published notices (FR-001 – FR-003). */
public interface NoticeService {

	/** Languages of the active notice for a context; empty when none is published. */
	List<NoticeLanguage> languages(NoticeContext context);

	/** The active notice in one language; {@code NOT_FOUND} when there is none. */
	ActiveNotice activeNotice(NoticeContext context, String languageCode);

	/** A notice version for a save, only if it is still published and has this language. */
	Optional<PublishedNotice> findPublished(Long noticeVersionId, String languageCode);
}
