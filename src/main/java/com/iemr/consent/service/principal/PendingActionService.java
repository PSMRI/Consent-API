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

package com.iemr.consent.service.principal;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import com.iemr.consent.data.notice.NoticeContext;
import com.iemr.consent.model.principal.PendingAction;

/** Consent actions still needed for a patient (front-end API 9; research R5). */
public interface PendingActionService {

	/**
	 * @param context which active notice defines the purposes needed
	 * @return actions in the order CONSENT_MISSING, LEGACY, RECONSENT_REQUIRED, GUARDIAN_TO_SELF; empty when
	 *         nothing is needed (H3)
	 */
	List<PendingAction> pending(Long benRegId, NoticeContext context);

	/**
	 * API 15 (feature 007): API 9's answer for each patient, computed with the same rules.
	 *
	 * @param benRegIds distinct patient ids, 1 to 100 (checked by the caller)
	 * @return one entry per id, in the given order; an empty list when nothing is needed
	 */
	Map<Long, List<PendingAction>> pendingFor(Collection<Long> benRegIds, NoticeContext context);
}
