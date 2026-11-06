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

import java.time.Clock;

import org.springframework.stereotype.Component;

import com.iemr.consent.data.consent.Consent;
import com.iemr.consent.data.evidence.ConsentEvidence;
import com.iemr.consent.data.evidence.EvidenceType;
import com.iemr.consent.repository.evidence.ConsentEvidenceRepository;
import com.iemr.consent.utils.auth.AuthenticatedUser;

/**
 * Stores the declaration agreed to as one {@code DECLARATION} evidence row of the consent (feature 004, FR-014). Called
 * by {@link ConsentWriter} inside the save transaction with the result of {@link DeclarationRule}.
 */
@Component
public class DeclarationProofWriter {

	private final ConsentEvidenceRepository repository;

	private final Clock clock;

	public DeclarationProofWriter(ConsentEvidenceRepository repository, Clock clock) {
		this.repository = repository;
		this.clock = clock;
	}

	/** Writes nothing for a decline ({@code declaration == null}). */
	public void write(Consent consent, DeclarationCheck declaration, AuthenticatedUser user) {
		if (declaration == null) {
			return;
		}
		ConsentEvidence row = ProofRows.row(consent, EvidenceType.DECLARATION, user, clock);
		row.setDeclarationText(declaration.text());
		row.setDeclarationWording(declaration.wording());
		row.setDeclarationAppDefault(declaration.appDefault());
		row.setSha256(declaration.sha256());
		repository.save(row);
	}
}
