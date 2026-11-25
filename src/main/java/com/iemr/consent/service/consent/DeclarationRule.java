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

import org.springframework.stereotype.Component;

import com.iemr.consent.data.consent.ConsentingParty;
import com.iemr.consent.data.consent.DeclarationWording;
import com.iemr.consent.data.notice.NoticeTranslation;
import com.iemr.consent.model.consent.DeclarationInput;
import com.iemr.consent.model.consent.SaveConsentRequest;
import com.iemr.consent.service.notice.PublishedNotice;
import com.iemr.consent.utils.crypto.Sha256;
import com.iemr.consent.utils.exception.ConsentApiException;
import com.iemr.consent.utils.exception.ErrorCode;

/**
 * Checks the declaration of a save (feature 004, FR-011 to FR-013): required on an accept, absent on a decline, for
 * the consent's notice version and language, worded for the consenting party, and equal to the published text when
 * one is published.
 */
@Component
public class DeclarationRule {

	/** @return the declaration to store, or {@code null} for a decline */
	public DeclarationCheck check(SaveConsentRequest request, PublishedNotice notice) {
		DeclarationInput declaration = request.declaration();
		boolean anyGrant = ProofRows.anyGrant(request);
		if (anyGrant && declaration == null) {
			throw ConsentRules.invalid("declaration", "is required when any purpose is granted");
		}
		if (!anyGrant) {
			if (declaration != null) {
				throw ConsentRules.invalid("declaration", "must be absent on a decline");
			}
			return null;
		}
		if (!declaration.noticeVersionId().equals(request.noticeVersionId())
				|| !declaration.languageCode().equals(request.languageCode())) {
			throw new ConsentApiException(ErrorCode.STALE_VERSION);
		}
		DeclarationWording expected = request.consentingParty() == ConsentingParty.GUARDIAN
				? DeclarationWording.REPRESENTATIVE : DeclarationWording.PATIENT;
		if (declaration.wording() != expected) {
			throw ConsentRules.invalid("declaration.wording", "must be " + expected + " for this party");
		}
		String published = published(notice.translation(), declaration.wording());
		if (published != null && !published.equals(declaration.text())) {
			throw new ConsentApiException(ErrorCode.STALE_VERSION);
		}
		return new DeclarationCheck(declaration.text(), declaration.wording(), published == null,
				Sha256.hex(declaration.text()));
	}

	/** The published wording; a representative without its own wording uses the patient's. */
	private static String published(NoticeTranslation translation, DeclarationWording wording) {
		String representative = translation.getRepresentativeDeclaration();
		if (wording == DeclarationWording.REPRESENTATIVE && representative != null) {
			return representative;
		}
		return translation.getDeclaration();
	}
}
