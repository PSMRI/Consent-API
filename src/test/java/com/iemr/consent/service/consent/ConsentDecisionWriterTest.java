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

package com.iemr.consent.service.consent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.iemr.consent.data.consent.Consent;
import com.iemr.consent.data.consent.ConsentPurpose;
import com.iemr.consent.data.consent.Decision;
import com.iemr.consent.data.consent.PurposeStatus;
import com.iemr.consent.model.consent.PurposeResult;
import com.iemr.consent.repository.consent.ConsentPurposeRepository;
import com.iemr.consent.support.ConsentFixtures;
import com.iemr.consent.support.TestFixtures;

/** Feature 008, research R6: the decision writer shared by API 7 and the legacy import. */
class ConsentDecisionWriterTest {

	private ConsentPurposeRepository repository;

	private ConsentDecisionWriter writer;

	@BeforeEach
	void setUp() {
		repository = mock(ConsentPurposeRepository.class);
		writer = new ConsentDecisionWriter(repository);
	}

	private static Consent consent(Long patient) {
		Consent consent = new Consent();
		consent.setConsentId("c-1");
		consent.setBeneficiaryRegId(patient);
		consent.setCapturedAt(TestFixtures.NOW);
		return consent;
	}

	private ConsentPurpose saved() {
		ArgumentCaptor<ConsentPurpose> row = ArgumentCaptor.forClass(ConsentPurpose.class);
		verify(repository).save(row.capture());
		return row.getValue();
	}

	@Test
	void firstDecisionIsVersionOneAndValidForThePurposeDays() {
		when(repository.findCurrent(7L, "P-REM")).thenReturn(Optional.empty());

		PurposeResult result = writer.write(consent(7L), "P-REM", Decision.GRANT,
		ConsentFixtures.purpose("P-REM", false, 365, 1));

		assertThat(result).isEqualTo(new PurposeResult("P-REM", PurposeStatus.GRANTED, 1));
		ConsentPurpose row = saved();
		assertThat(row.getConsentId()).isEqualTo("c-1");
		assertThat(row.getBeneficiaryRegId()).isEqualTo(7L);
		assertThat(row.isCurrent()).isTrue();
		assertThat(row.getDecision()).isEqualTo(Decision.GRANT);
		assertThat(row.getValidUntil()).isEqualTo(TestFixtures.NOW.plus(Duration.ofDays(365)));
		verify(repository, never()).saveAndFlush(any());
	}

	@Test
	void earlierCurrentDecisionIsSupersededAndTheVersionGoesUp() {
		ConsentPurpose previous = new ConsentPurpose();
		previous.setVersionNo(2);
		previous.setCurrent(true);
		previous.setStatus(PurposeStatus.GRANTED);
		when(repository.findCurrent(7L, "P-REG")).thenReturn(Optional.of(previous));

		PurposeResult result = writer.write(consent(7L), "P-REG", Decision.GRANT,
		ConsentFixtures.purpose("P-REG", true, null, 1));

		assertThat(result.versionNo()).isEqualTo(3);
		assertThat(previous.isCurrent()).isFalse();
		assertThat(previous.getStatus()).isEqualTo(PurposeStatus.SUPERSEDED);
		verify(repository).saveAndFlush(previous);
		assertThat(saved().getValidUntil()).as("no validity days").isNull();
	}

	@Test
	void unlinkedConsentAndDenialsHaveNoSupersedeOrValidity() {
		PurposeResult result = writer.write(consent(null), "P-REM", Decision.DENY,
				ConsentFixtures.purpose("P-REM", false, 365, 1));

		assertThat(result).isEqualTo(new PurposeResult("P-REM", PurposeStatus.DENIED, 1));
		verify(repository, never()).findCurrent(any(), any());
		assertThat(saved().getValidUntil()).isNull();
	}
}
