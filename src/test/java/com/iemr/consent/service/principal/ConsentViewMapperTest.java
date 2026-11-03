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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.iemr.consent.data.consent.Consent;
import com.iemr.consent.data.consent.ConsentPurpose;
import com.iemr.consent.data.consent.PurposeStatus;
import com.iemr.consent.data.notice.Purpose;
import com.iemr.consent.model.principal.ConsentEventView;
import com.iemr.consent.model.principal.ConsentEventView.PurposeOutcome;
import com.iemr.consent.model.principal.CurrentDecisionView;
import com.iemr.consent.support.ConsentFixtures;
import com.iemr.consent.support.PrincipalFixtures;
import com.iemr.consent.support.TestFixtures;

class ConsentViewMapperTest {

	private final ConsentViewMapper mapper = new ConsentViewMapper(new EffectiveStatus(TestFixtures.CLOCK));

	@Test
	void latestByCodeKeepsTheHighestVersion() {
		Purpose v1 = ConsentFixtures.purpose("P-REM", false, 365, 1);
		Purpose v2 = ConsentFixtures.purpose("P-REM", false, 180, 2);
		assertThat(ConsentViewMapper.latestByCode(List.of(v1, v2)).get("P-REM")).isSameAs(v2);
		assertThat(ConsentViewMapper.latestByCode(List.of(v2, v1)).get("P-REM")).isSameAs(v2);
	}

	@Test
	void currentIsSortedByCodeWithPurposeFlagsAndEffectiveStatus() {
		ConsentPurpose rem = PrincipalFixtures.row("c-1", "P-REM", PurposeStatus.GRANTED, 2,
				TestFixtures.NOW.minusSeconds(1));
		ConsentPurpose reg = PrincipalFixtures.row("c-1", "P-REG", PurposeStatus.GRANTED, 1, null);
		ConsentPurpose gone = PrincipalFixtures.row("c-1", "P-OLD", PurposeStatus.DENIED, 1, null);
		Map<String, Purpose> purposes = ConsentViewMapper.latestByCode(ConsentFixtures.purposes());

		List<CurrentDecisionView> views = mapper.current(List.of(rem, reg, gone), purposes);

		assertThat(views).extracting(CurrentDecisionView::purposeCode).containsExactly("P-OLD", "P-REG", "P-REM");
		assertThat(views.get(0)).isEqualTo(new CurrentDecisionView("P-OLD", null, false, false,
				PurposeStatus.DENIED, null, 1));
		assertThat(views.get(1)).isEqualTo(new CurrentDecisionView("P-REG", "name P-REG", true, false,
				PurposeStatus.GRANTED, null, 1));
		assertThat(views.get(2).status()).isEqualTo(PurposeStatus.EXPIRED);
		assertThat(views.get(2).isWithdrawable()).isTrue();
	}

	@Test
	void historyKeepsEventOrderAndGroupsOutcomesPerEvent() {
		Consent newer = PrincipalFixtures.event("c-2", TestFixtures.NOW);
		Consent older = PrincipalFixtures.event("c-1", TestFixtures.NOW.minusSeconds(60));
		Consent empty = PrincipalFixtures.event("c-0", TestFixtures.NOW.minusSeconds(120));
		List<ConsentPurpose> rows = List.of(
				PrincipalFixtures.row("c-1", "P-REG", PurposeStatus.GRANTED, 1, null),
				PrincipalFixtures.row("c-1", "P-REM", PurposeStatus.SUPERSEDED, 1, null),
				PrincipalFixtures.row("c-2", "P-REM", PurposeStatus.WITHDRAWN, 2, null));

		List<ConsentEventView> history = mapper.history(List.of(newer, older, empty), rows);

		assertThat(history).extracting(ConsentEventView::consentId).containsExactly("c-2", "c-1", "c-0");
		assertThat(history.get(0).purposes())
				.containsExactly(new PurposeOutcome("P-REM", PurposeStatus.WITHDRAWN, 2));
		assertThat(history.get(1).purposes()).extracting(PurposeOutcome::purposeCode).containsExactly("P-REG",
				"P-REM");
		assertThat(history.get(2).purposes()).isEmpty();
		assertThat(history.get(0).capturedAt()).isEqualTo(TestFixtures.NOW);
		assertThat(history.get(0).deviceId()).isEqualTo(newer.getDeviceId());
	}
}
