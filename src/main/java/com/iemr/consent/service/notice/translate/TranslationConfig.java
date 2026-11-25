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

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.iemr.consent.config.ConsentProperties;

/**
 * Chooses the {@link NoticeTranslator} from {@code consent.translation.provider} (feature 006). No provider is built
 * yet, so a non-blank value stops start-up instead of being silently ignored; a real service is added here as one
 * more choice once it is decided.
 */
@Configuration
public class TranslationConfig {

	@Bean
	public NoticeTranslator noticeTranslator(ConsentProperties properties) {
		String provider = properties.translation().provider();
		if (provider == null || provider.isBlank()) {
			return new DisabledNoticeTranslator();
		}
		throw new IllegalStateException("Unknown consent.translation.provider: " + provider);
	}
}
