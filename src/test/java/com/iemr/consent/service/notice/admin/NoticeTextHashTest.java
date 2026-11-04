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

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.iemr.consent.data.notice.NoticeTranslation;
import com.iemr.consent.utils.crypto.Sha256;

/** Feature 006, research R10: the hash must equal MySQL SHA2(CONCAT_WS('\n', ...), 256), skipping nulls. */
class NoticeTextHashTest {

	@Test
	void joinsTheSixSectionsWithNewlinesSkippingEmptyOnes() {
		NoticeTranslation t = new NoticeTranslation();
		t.setBody("Body");
		t.setRetention("Kept 5 years");
		t.setGrievanceInfo("Officer");
		t.setDeclaration("not part of the hash");

		// Same value as MySQL: SELECT SHA2(CONCAT_WS('\n', 'Body', NULL, 'Kept 5 years', NULL, NULL, 'Officer'), 256)
		assertThat(NoticeTextHash.of(t)).isEqualTo("515126bab225d5347d13fb9b70f803af5b8f7fd25684515df34c0a265f0b875e");
	}

	@Test
	void everySectionCounts() {
		NoticeTranslation t = new NoticeTranslation();
		t.setBody("a");
		t.setDataItems("b");
		t.setRetention("c");
		t.setRecipients("d");
		t.setWithdrawalInfo("e");
		t.setGrievanceInfo("f");
		assertThat(NoticeTextHash.of(t)).isEqualTo(Sha256.hex("a\nb\nc\nd\ne\nf"));
	}
}
