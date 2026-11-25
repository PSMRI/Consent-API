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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.iemr.consent.config.ConsentProperties;
import com.iemr.consent.support.TestFixtures;

/** Feature 006, Q1 = A: no translation service is configured by default. */
class TranslationConfigTest {

	private static ConsentProperties withProvider(String provider) {
		ConsentProperties base = TestFixtures.consentProperties();
		return new ConsentProperties(base.request(), base.health(), base.redis(), base.rateLimit(), base.evidence(),
				base.capture(), base.commonApi(), base.notice(), base.otp(), base.encryption(), base.accessGrant(),
				base.sms(), base.admin(), new ConsentProperties.Translation(provider, 10000),
				base.legacy());
	}

	@Test
	void blankProviderMeansNotConfigured() {
		TranslationConfig config = new TranslationConfig();
		NoticeTranslator translator = config.noticeTranslator(withProvider(""));
		assertThat(translator).isInstanceOf(DisabledNoticeTranslator.class);
		assertThat(translator.isConfigured()).isFalse();
		assertThat(config.noticeTranslator(withProvider(null)).isConfigured()).isFalse();
		assertThat(config.noticeTranslator(withProvider("  ")).isConfigured()).isFalse();
		assertThatThrownBy(() -> translator.translate(Map.of("body", "x"), "en", "hi"))
				.isInstanceOf(TranslationException.class).hasMessage("Translation is not configured");
	}

	@Test
	void anUnknownProviderStopsStartUp() {
		assertThatThrownBy(() -> new TranslationConfig().noticeTranslator(withProvider("acme")))
				.isInstanceOf(IllegalStateException.class).hasMessageContaining("acme");
	}
}
