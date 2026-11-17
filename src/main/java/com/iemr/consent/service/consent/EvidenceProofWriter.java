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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.iemr.consent.data.consent.Consent;
import com.iemr.consent.data.evidence.ConsentEvidence;
import com.iemr.consent.data.evidence.EvidenceType;
import com.iemr.consent.model.consent.Proof;
import com.iemr.consent.model.consent.SaveConsentRequest;
import com.iemr.consent.repository.evidence.ConsentEvidenceRepository;
import com.iemr.consent.utils.auth.AuthenticatedUser;
import com.iemr.consent.utils.crypto.FieldEncryptor;
import com.iemr.consent.utils.exception.ConsentApiException;
import com.iemr.consent.utils.exception.ErrorCode;

/**
 * Links uploaded files to the consent (once only; a file already linked by another save is
 * {@code STATE_CONFLICT}) and records the witness (name encrypted) and the worker's read-aloud
 * confirmation.
 */
@Component
public class EvidenceProofWriter implements ConsentProofWriter {

	private final ConsentEvidenceRepository repository;

	private final FieldEncryptor encryptor;

	private final Clock clock;

	public EvidenceProofWriter(ConsentEvidenceRepository repository, FieldEncryptor encryptor, Clock clock) {
		this.repository = repository;
		this.encryptor = encryptor;
		this.clock = clock;
	}

	@Override
	public void write(Consent consent, SaveConsentRequest request, AuthenticatedUser user) {
		for (UUID id : evidenceIds(request)) {
			if (repository.linkToConsent(id.toString(), consent.getConsentId()) != 1) {
				throw new ConsentApiException(ErrorCode.STATE_CONFLICT);
			}
		}
		Proof proof = request.proof();
		if (proof == null) {
			return;
		}
		if (!ConsentRules.blank(proof.witnessName())) {
			ConsentEvidence witness = ProofRows.row(consent, EvidenceType.WITNESS, user, clock);
			witness.setWitnessNameEncrypted(encryptor.encrypt(proof.witnessName()));
			witness.setWitnessRelationship(proof.witnessRelationship());
			repository.save(witness);
		}
		if (Boolean.TRUE.equals(proof.workerConfirmed())) {
			repository.save(ProofRows.row(consent, EvidenceType.WORKER_CONFIRMATION, user, clock));
		}
	}

	private static List<UUID> evidenceIds(SaveConsentRequest request) {
		List<UUID> ids = new ArrayList<>();
		if (request.evidenceIds() != null) {
			ids.addAll(request.evidenceIds());
		}
		if (request.guardian() != null && request.guardian().evidenceIds() != null) {
			ids.addAll(request.guardian().evidenceIds());
		}
		return ids.stream().distinct().toList();
	}
}
