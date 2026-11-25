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

package com.iemr.consent.service.notice.translate;

import java.util.Map;

/**
 * Machine translation of notice text (feature 006, research R9). The service is an open decision (TODO-LEGAL /
 * tech lead); until one is configured {@link DisabledNoticeTranslator} is used and only English is published.
 */
public interface NoticeTranslator {

	/** Whether a translation service is configured. */
	boolean isConfigured();

	/**
	 * Translates every given field.
	 *
	 * @param fields field name to English text; only non-null fields are passed
	 * @return the same field names with the translated text
	 * @throws TranslationException on any failure or time-out
	 */
	Map<String, String> translate(Map<String, String> fields, String from, String to);
}
