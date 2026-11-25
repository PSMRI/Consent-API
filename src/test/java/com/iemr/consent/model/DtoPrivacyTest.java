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

package com.iemr.consent.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.iemr.consent.model.notice.admin.DraftRequest;
import com.iemr.consent.model.notice.admin.DraftView;
import com.iemr.consent.model.notice.admin.PublishedView;
import com.iemr.consent.data.consent.DeclarationWording;
import com.iemr.consent.data.notify.NotifyChannel;
import com.iemr.consent.model.consent.DeclarationInput;
import com.iemr.consent.model.consent.NotifyInput;
import com.iemr.consent.data.grant.GrantStatus;
import com.iemr.consent.data.grant.GrantType;
import com.iemr.consent.model.consent.Proof;
import com.iemr.consent.model.grant.AccessGrantView;
import com.iemr.consent.model.grant.AccessRequest;
import com.iemr.consent.model.grant.GranteeView;
import com.iemr.consent.model.legacy.LegacyRowInput;
import com.iemr.consent.model.otp.OtpReason;
import com.iemr.consent.model.otp.OtpSendRequest;
import com.iemr.consent.model.otp.OtpVerifyRequest;
import com.iemr.consent.support.ConsentFixtures;

/** SC-005: request objects never print personal values, so they cannot leak through logs. */
class DtoPrivacyTest {

	@Test
	@DisplayName("toString hides phones, OTPs and names")
	void requestsHidePersonalValues() {
		List<String> printed = List.of(new OtpSendRequest(OtpReason.CONSENT, "9876543210", "ref").toString(),
				new OtpVerifyRequest("123456").toString(),
				new Proof(true, "Gita Bai", "Neighbour", UUID.randomUUID()).toString(),
				ConsentFixtures.guardianOtp(UUID.randomUUID()).toString(),
				ConsentFixtures.request().guardian(ConsentFixtures.guardianOtp(null))
						.proof(new Proof(null, "Gita Bai", "Neighbour", null)).build().toString(),
				new AccessRequest(1001L, List.of("P-REM"), 7, "Sita follow-up", null).toString(),
				new AccessGrantView("g-1", 1001L, new GranteeView(7L, "doctor", null), List.of("P-REM"),
						GrantType.CONSENTED, GrantStatus.PENDING, 7, "Sita follow-up", null, null, null, null)
						.toString(),
				new DeclarationInput("I, Sita, agree", "hi", 11L, DeclarationWording.PATIENT).toString(),
				new NotifyInput(NotifyChannel.SMS, "9876543210").toString(),
				ConsentFixtures.request().notifyBy(new NotifyInput(NotifyChannel.SMS, "9876543210"))
						.declaration(new DeclarationInput("I, Sita, agree", "hi", 11L, DeclarationWording.PATIENT))
						.build().toString(),
				new DraftRequest("en", "Sita notice body", null, null, null, null, null, "I, Sita, agree", null,
						null).toString(),
				new DraftView("Sita notice body", null, null, null, null, null, "I, Sita, agree", null, null,
						"admin", null).toString(),
				new PublishedView("Sita notice body", null, null, null, null, null, "I, Sita, agree", null, null, 1L,
						1, "admin", null, false, null, false).toString(),
				new LegacyRowInput(2, "100245678901", 7001L, "2025-06-12", "P-REG", "hi", "WITNESSED", "Gita Bai",
						"Neighbour", "Sita signed at camp").toString());

		assertThat(printed).allSatisfy(text -> assertThat(text).doesNotContain("9876543210").doesNotContain("123456")
				.doesNotContain("Gita").doesNotContain("Sita")
				.doesNotContain("100245678901"));
	}
}
