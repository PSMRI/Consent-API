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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.iemr.consent.data.consent.Consent;
import com.iemr.consent.data.consent.DeclarationWording;
import com.iemr.consent.data.evidence.ConsentEvidence;
import com.iemr.consent.data.evidence.EvidenceType;
import com.iemr.consent.repository.evidence.ConsentEvidenceRepository;
import com.iemr.consent.support.ConsentFixtures;
import com.iemr.consent.support.TestFixtures;

/** Storing the declaration as a DECLARATION evidence row (feature 004, FR-014). */
class DeclarationProofWriterTest {

	private final ConsentEvidenceRepository repository = mock(ConsentEvidenceRepository.class);

	private final DeclarationProofWriter writer = new DeclarationProofWriter(repository, TestFixtures.CLOCK);

	@Test
	@DisplayName("C2: an accept's declaration is one DECLARATION row of the consent")
	void writesOneDeclarationRow() {
		Consent consent = new Consent();
		consent.setConsentId("c-1");

		writer.write(consent, new DeclarationCheck("I agree", DeclarationWording.REPRESENTATIVE, true, "h".repeat(64)),
				ConsentFixtures.WORKER);

		ArgumentCaptor<ConsentEvidence> saved = ArgumentCaptor.forClass(ConsentEvidence.class);
		verify(repository).save(saved.capture());
		ConsentEvidence row = saved.getValue();
		assertThat(row.getConsentId()).isEqualTo("c-1");
		assertThat(row.getType()).isEqualTo(EvidenceType.DECLARATION);
		assertThat(row.getDeclarationText()).isEqualTo("I agree");
		assertThat(row.getDeclarationWording()).isEqualTo(DeclarationWording.REPRESENTATIVE);
		assertThat(row.getDeclarationAppDefault()).isTrue();
		assertThat(row.getSha256()).isEqualTo("h".repeat(64));
		assertThat(row.getUploadedBy()).isEqualTo(42L);
		assertThat(row.getCreatedAt()).isEqualTo(TestFixtures.NOW);
	}

	@Test
	@DisplayName("C3: a decline writes no declaration")
	void declineWritesNothing() {
		writer.write(new Consent(), null, ConsentFixtures.WORKER);
		verify(repository, never()).save(any());
	}
}
