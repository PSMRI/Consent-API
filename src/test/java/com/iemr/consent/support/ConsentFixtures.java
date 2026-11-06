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

package com.iemr.consent.support;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.iemr.consent.data.consent.Channel;
import com.iemr.consent.data.consent.ConsentMode;
import com.iemr.consent.data.consent.ConsentingParty;
import com.iemr.consent.data.consent.Decision;
import com.iemr.consent.data.consent.DeclarationWording;
import com.iemr.consent.data.consent.EventType;
import com.iemr.consent.data.consent.GuardianType;
import com.iemr.consent.data.consent.GuardianVerification;
import com.iemr.consent.data.notice.Notice;
import com.iemr.consent.data.notice.NoticeContext;
import com.iemr.consent.data.notice.NoticeStatus;
import com.iemr.consent.data.notice.NoticeTranslation;
import com.iemr.consent.data.notice.Purpose;
import com.iemr.consent.model.consent.DecisionItem;
import com.iemr.consent.model.consent.DeclarationInput;
import com.iemr.consent.model.consent.GuardianInfo;
import com.iemr.consent.model.consent.NotifyInput;
import com.iemr.consent.model.consent.Proof;
import com.iemr.consent.model.consent.SaveConsentRequest;
import com.iemr.consent.service.notice.PublishedNotice;
import com.iemr.consent.utils.auth.AuthenticatedUser;
import com.iemr.consent.utils.auth.ClientType;

/** Synthetic notices, purposes and save requests for consent tests. */
public final class ConsentFixtures {

	public static final long NOTICE_ID = 11L;

	public static final String TEXT_HASH = "a".repeat(64);

	public static final AuthenticatedUser WORKER = new AuthenticatedUser(42L, "worker42", ClientType.WEB);

	public static final String GUARDIAN_PHONE = "9876543210";

	private ConsentFixtures() {
	}

	public static Notice notice(NoticeStatus status) {
		Notice notice = new Notice();
		notice.setNoticeVersionId(NOTICE_ID);
		notice.setNoticeCode("MMU-REGISTRATION");
		notice.setVersionNo(1);
		notice.setContext(NoticeContext.REGISTRATION);
		notice.setStatus(status);
		return notice;
	}

	public static NoticeTranslation translation(String lang, String audioRef) {
		NoticeTranslation t = new NoticeTranslation();
		t.setNoticeVersionId(NOTICE_ID);
		t.setLanguageCode(lang);
		t.setLanguageName("name-" + lang);
		t.setBody("[DEMO] notice body " + lang);
		t.setDataItems("items");
		t.setRetention("retention");
		t.setRecipients("recipients");
		t.setWithdrawalInfo("withdraw");
		t.setGrievanceInfo("grievance");
		t.setTextHash(TEXT_HASH);
		t.setAudioRef(audioRef);
		return t;
	}

	public static Purpose purpose(String code, boolean mandatory, Integer validityDays, int version) {
		Purpose p = new Purpose();
		p.setPurposeCode(code);
		p.setVersion(version);
		p.setName("name " + code);
		p.setDescription("desc " + code);
		p.setMandatory(mandatory);
		p.setWithdrawable(!mandatory);
		p.setValidityDays(validityDays);
		p.setActive(true);
		return p;
	}

	/** P-REG (mandatory, no expiry) and P-REM (optional, 365 days). */
	public static List<Purpose> purposes() {
		return List.of(purpose("P-REG", true, null, 1), purpose("P-REM", false, 365, 1));
	}

	public static PublishedNotice publishedNotice() {
		return new PublishedNotice(notice(NoticeStatus.PUBLISHED), translation("hi", null), purposes());
	}

	/** Builder for {@link SaveConsentRequest}; defaults to a self-read accept of both purposes. */
	public static final class RequestBuilder {

		private Long beneficiary = 1001L;

		private ConsentingParty party = ConsentingParty.SELF;

		private String hash = TEXT_HASH;

		private ConsentMode mode = ConsentMode.SELF_READ;

		private Instant capturedAt = TestFixtures.NOW;

		private List<DecisionItem> decisions = new ArrayList<>(List.of(new DecisionItem("P-REG", Decision.GRANT),
				new DecisionItem("P-REM", Decision.GRANT)));

		private List<UUID> evidence;

		private Proof proof;

		private GuardianInfo guardian;

		private EventType eventType = EventType.NEW;

		private DeclarationInput declaration;

		private boolean declarationSet;

		private NotifyInput notify;

		/** Overrides the default declaration (by default an accept carries the app's default wording). */
		public RequestBuilder declaration(DeclarationInput value) {
			declaration = value;
			declarationSet = true;
			return this;
		}

		public RequestBuilder notifyBy(NotifyInput value) {
			notify = value;
			return this;
		}

		public RequestBuilder eventType(EventType value) {
			eventType = value;
			return this;
		}

		public RequestBuilder beneficiary(Long value) {
			beneficiary = value;
			return this;
		}

		public RequestBuilder party(ConsentingParty value) {
			party = value;
			return this;
		}

		public RequestBuilder hash(String value) {
			hash = value;
			return this;
		}

		public RequestBuilder mode(ConsentMode value) {
			mode = value;
			return this;
		}

		public RequestBuilder capturedAt(Instant value) {
			capturedAt = value;
			return this;
		}

		public RequestBuilder decisions(Decision reg, Decision rem) {
			decisions = new ArrayList<>(List.of(new DecisionItem("P-REG", reg), new DecisionItem("P-REM", rem)));
			return this;
		}

		public RequestBuilder decisionList(List<DecisionItem> value) {
			decisions = value;
			return this;
		}

		public RequestBuilder evidence(List<UUID> value) {
			evidence = value;
			return this;
		}

		public RequestBuilder proof(Proof value) {
			proof = value;
			return this;
		}

		public RequestBuilder guardian(GuardianInfo value) {
			guardian = value;
			party = ConsentingParty.GUARDIAN;
			return this;
		}

		public SaveConsentRequest build() {
			boolean anyGrant = decisions.stream().anyMatch(d -> d.decision() == Decision.GRANT);
			if (!declarationSet) {
				declaration = anyGrant ? defaultDeclaration(party) : null;
			}
			return new SaveConsentRequest(beneficiary, eventType, party, NOTICE_ID, "hi", hash, Channel.FACILITY,
					mode, 999L, 1, 2, 3, "MMU", "van-07", capturedAt, decisions, evidence, proof, guardian, 12, 3,
					declaration, notify);
		}
	}

	/** The declaration a 004 accept carries when the notice publishes none (the app's default wording). */
	public static final String DEFAULT_DECLARATION = "I have read or heard the notice and I agree.";

	public static DeclarationInput defaultDeclaration(ConsentingParty party) {
		return new DeclarationInput(DEFAULT_DECLARATION, "hi", NOTICE_ID,
				party == ConsentingParty.GUARDIAN ? DeclarationWording.REPRESENTATIVE : DeclarationWording.PATIENT);
	}

	public static RequestBuilder request() {
		return new RequestBuilder();
	}

	public static GuardianInfo guardianOtp(UUID challengeId) {
		return new GuardianInfo(GuardianType.PARENT, "Sita Devi", "Mother", GUARDIAN_PHONE, GuardianVerification.OTP,
				challengeId, null, null);
	}

	public static GuardianInfo guardianFallback(GuardianVerification verification, String witness) {
		return new GuardianInfo(GuardianType.DISABILITY_GUARDIAN, "Ravi Kumar", "Brother", null, verification, null,
				witness, null);
	}
}
