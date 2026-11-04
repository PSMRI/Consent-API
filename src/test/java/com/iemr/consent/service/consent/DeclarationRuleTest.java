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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.iemr.consent.data.consent.Decision;
import com.iemr.consent.data.consent.DeclarationWording;
import com.iemr.consent.model.consent.DeclarationInput;
import com.iemr.consent.model.consent.SaveConsentRequest;
import com.iemr.consent.service.notice.PublishedNotice;
import com.iemr.consent.support.ConsentFixtures;
import com.iemr.consent.utils.crypto.Sha256;
import com.iemr.consent.utils.exception.ConsentApiException;
import com.iemr.consent.utils.exception.ErrorCode;

/** Declaration rules of a save (feature 004, FR-011 to FR-013). */
class DeclarationRuleTest {

	private static final String PATIENT_TEXT = "I agree (published)";

	private static final String REPRESENTATIVE_TEXT = "We agree for the patient (published)";

	private final DeclarationRule rule = new DeclarationRule();

	private static PublishedNotice published(String declaration, String representative) {
		PublishedNotice notice = ConsentFixtures.publishedNotice();
		notice.translation().setDeclaration(declaration);
		notice.translation().setRepresentativeDeclaration(representative);
		return notice;
	}

	private static DeclarationInput input(String text, DeclarationWording wording) {
		return new DeclarationInput(text, "hi", ConsentFixtures.NOTICE_ID, wording);
	}

	private static void assertField(Runnable call, String field) {
		assertThatThrownBy(call::run).isInstanceOf(ConsentApiException.class)
				.satisfies(e -> assertThat(((ConsentApiException) e).getErrorCode())
						.isEqualTo(ErrorCode.VALIDATION_FAILED))
				.satisfies(e -> assertThat(((ConsentApiException) e).getDetails().get(0).field()).isEqualTo(field));
	}

	private static void assertStale(Runnable call) {
		assertThatThrownBy(call::run).isInstanceOf(ConsentApiException.class).extracting("errorCode")
				.isEqualTo(ErrorCode.STALE_VERSION);
	}

	@Test
	@DisplayName("C2: an accept without a declaration is refused")
	void c2AcceptNeedsADeclaration() {
		SaveConsentRequest request = ConsentFixtures.request().decisions(Decision.GRANT, Decision.DENY)
				.declaration(null).build();
		assertField(() -> rule.check(request, published(PATIENT_TEXT, null)), "declaration");
	}

	@Test
	@DisplayName("C3: a decline carries no declaration")
	void c3DeclineHasNoDeclaration() {
		SaveConsentRequest decline = ConsentFixtures.request().decisions(Decision.DENY, Decision.DENY).build();
		assertThat(rule.check(decline, published(PATIENT_TEXT, null))).isNull();

		SaveConsentRequest declineWith = ConsentFixtures.request().decisions(Decision.DENY, Decision.DENY)
				.declaration(input(PATIENT_TEXT, DeclarationWording.PATIENT)).build();
		assertField(() -> rule.check(declineWith, published(PATIENT_TEXT, null)), "declaration");
	}

	@Test
	void anotherNoticeVersionOrLanguageIsStale() {
		SaveConsentRequest otherVersion = ConsentFixtures.request()
				.declaration(new DeclarationInput(PATIENT_TEXT, "hi", 99L, DeclarationWording.PATIENT)).build();
		SaveConsentRequest otherLanguage = ConsentFixtures.request().declaration(
				new DeclarationInput(PATIENT_TEXT, "en", ConsentFixtures.NOTICE_ID, DeclarationWording.PATIENT))
				.build();

		assertStale(() -> rule.check(otherVersion, published(PATIENT_TEXT, null)));
		assertStale(() -> rule.check(otherLanguage, published(PATIENT_TEXT, null)));
	}

	@Test
	@DisplayName("C6: the wording must match the consenting party")
	void c6WordingMatchesTheParty() {
		SaveConsentRequest guardianWithPatientWording = ConsentFixtures.request()
				.guardian(ConsentFixtures.guardianOtp(null))
				.declaration(input(PATIENT_TEXT, DeclarationWording.PATIENT)).build();
		SaveConsentRequest selfWithRepresentativeWording = ConsentFixtures.request()
				.declaration(input(REPRESENTATIVE_TEXT, DeclarationWording.REPRESENTATIVE)).build();

		assertField(() -> rule.check(guardianWithPatientWording, published(PATIENT_TEXT, REPRESENTATIVE_TEXT)),
				"declaration.wording");
		assertField(() -> rule.check(selfWithRepresentativeWording, published(PATIENT_TEXT, REPRESENTATIVE_TEXT)),
				"declaration.wording");
	}

	@Test
	@DisplayName("C2: the patient declaration must equal the published text")
	void patientTextMustEqualThePublishedText() {
		SaveConsentRequest exact = ConsentFixtures.request()
				.declaration(input(PATIENT_TEXT, DeclarationWording.PATIENT)).build();
		SaveConsentRequest changed = ConsentFixtures.request()
				.declaration(input(PATIENT_TEXT + ".", DeclarationWording.PATIENT)).build();

		DeclarationCheck check = rule.check(exact, published(PATIENT_TEXT, REPRESENTATIVE_TEXT));
		assertThat(check).isEqualTo(new DeclarationCheck(PATIENT_TEXT, DeclarationWording.PATIENT, false,
				Sha256.hex(PATIENT_TEXT)));
		assertThat(check.toString()).doesNotContain(PATIENT_TEXT);
		assertStale(() -> rule.check(changed, published(PATIENT_TEXT, REPRESENTATIVE_TEXT)));
	}

	@Test
	@DisplayName("C6: representative wording uses its own text, or the patient text when it has none")
	void representativeTextFallsBackToThePatientText() {
		SaveConsentRequest ownText = ConsentFixtures.request().guardian(ConsentFixtures.guardianOtp(null))
				.declaration(input(REPRESENTATIVE_TEXT, DeclarationWording.REPRESENTATIVE)).build();
		SaveConsentRequest patientText = ConsentFixtures.request().guardian(ConsentFixtures.guardianOtp(null))
				.declaration(input(PATIENT_TEXT, DeclarationWording.REPRESENTATIVE)).build();

		assertThat(rule.check(ownText, published(PATIENT_TEXT, REPRESENTATIVE_TEXT)).appDefault()).isFalse();
		assertStale(() -> rule.check(patientText, published(PATIENT_TEXT, REPRESENTATIVE_TEXT)));
		assertThat(rule.check(patientText, published(PATIENT_TEXT, null)).wording())
				.isEqualTo(DeclarationWording.REPRESENTATIVE);
	}

	@Test
	@DisplayName("Nothing published: the app's default wording is stored and marked")
	void nothingPublishedMeansAppDefault() {
		SaveConsentRequest self = ConsentFixtures.request().build();
		SaveConsentRequest guardian = ConsentFixtures.request().guardian(ConsentFixtures.guardianOtp(null)).build();

		assertThat(rule.check(self, published(null, null)).appDefault()).isTrue();
		assertThat(rule.check(guardian, published(null, null))).extracting("wording", "appDefault")
				.containsExactly(DeclarationWording.REPRESENTATIVE, true);
	}
}
