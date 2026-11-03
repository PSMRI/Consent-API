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

import com.iemr.consent.data.notice.Notice;
import com.iemr.consent.data.notice.NoticeTranslation;
import com.iemr.consent.data.notice.Purpose;

/**
 * A published notice version in one language with its purposes, used to validate a save.
 *
 * @param notice      the notice version
 * @param translation the language shown
 * @param purposes    purposes the notice covers, mandatory first then display order
 */
public record PublishedNotice(Notice notice, NoticeTranslation translation, List<Purpose> purposes) {
}
