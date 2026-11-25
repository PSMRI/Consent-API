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

import java.util.List;

import com.iemr.consent.data.notice.NoticeContext;
import com.iemr.consent.model.notice.admin.DraftRequest;
import com.iemr.consent.model.notice.admin.MessageEntry;
import com.iemr.consent.model.notice.admin.PublishRequest;
import com.iemr.consent.utils.auth.AuthenticatedUser;

/** Admin authoring of a project's consent message: list (A6), draft (A7) and publish (A8), feature 006. */
public interface NoticeAuthoringService {

	List<MessageEntry> list(Integer projectId);

	MessageEntry saveDraft(Integer projectId, NoticeContext context, DraftRequest request, String requestKey,
			String requestHash, AuthenticatedUser user, String sourceIp);

	MessageEntry publish(Integer projectId, NoticeContext context, PublishRequest request, String requestKey,
			String requestHash, AuthenticatedUser user, String sourceIp);
}
