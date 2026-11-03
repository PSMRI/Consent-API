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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import com.iemr.consent.data.consent.ConsentMode;
import com.iemr.consent.data.consent.Decision;
import com.iemr.consent.data.consent.EventType;
import com.iemr.consent.data.evidence.ConsentEvidence;
import com.iemr.consent.model.consent.DecisionItem;
import com.iemr.consent.model.consent.GuardianInfo;
import com.iemr.consent.model.consent.SaveConsentRequest;
import com.iemr.consent.service.evidence.EvidenceService;
import com.iemr.consent.service.notice.NoticeService;
import com.iemr.consent.support.ConsentFixtures;
import com.iemr.consent.utils.exception.ConsentApiException;
import com.iemr.consent.utils.exception.ErrorCode;
import com.iemr.consent.utils.response.FieldErrorDetail;

class ConsentValidatorTest {

	private NoticeService notices;

	private EvidenceService evidence;

	private GuardianRule guardianRule;

	private ModeProofRule voiceRule;

	private ConsentValidator validator;

	@BeforeEach
	void setUp() {
		notices = mock(NoticeService.class);
		evidence = mock(EvidenceService.class);
		guardianRule = mock(GuardianRule.class);
		voiceRule = mock(ModeProofRule.class);
		when(voiceRule.mode()).thenReturn(ConsentMode.VOICE_CLIP);
		when(notices.findPublished(ConsentFixtures.NOTICE_ID, "hi"))
				.thenReturn(Optional.of(ConsentFixtures.publishedNotice()));
		when(evidence.requireLinkable(anyList(), any())).thenReturn(List.of());
		validator = new ConsentValidator(notices, evidence, guardianRule, List.of(voiceRule));
	}

	private static void assertError(Runnable call, ErrorCode code) {
		assertThatThrownBy(call::run).isInstanceOf(ConsentApiException.class).extracting("errorCode")
				.isEqualTo(code);
	}

	@Test
	void selfReadAcceptNeedsNoProof() {
		ValidatedConsent result = validator.validate(ConsentFixtures.request().build(), ConsentFixtures.WORKER);

		assertThat(result.notice().purposes()).hasSize(2);
		verify(guardianRule, never()).check(any(), any());
	}

	@ParameterizedTest
	@EnumSource(value = EventType.class, names = { "NEW", "UPDATE", "RENEWAL" })
	void consentScreenEventTypesAreAccepted(EventType type) {
		assertThat(validator.validate(ConsentFixtures.request().eventType(type).build(), ConsentFixtures.WORKER))
				.isNotNull();
	}

	@ParameterizedTest
	@EnumSource(value = EventType.class, names = { "WITHDRAWAL", "LEGACY" })
	void withdrawalAndLegacyAreNotConsentScreenEvents(EventType type) {
		assertThatThrownBy(() -> validator.validate(ConsentFixtures.request().eventType(type).build(),
				ConsentFixtures.WORKER)).isInstanceOf(ConsentApiException.class)
				.extracting("errorCode", "details").containsExactly(ErrorCode.VALIDATION_FAILED,
						List.of(new FieldErrorDetail("eventType",
								"must be NEW, UPDATE or RENEWAL")));
		verify(notices, never()).findPublished(any(), any());
	}

	@Test
	void unpublishedNoticeOrWrongFingerprintIsStale() {
		assertError(() -> validator.validate(ConsentFixtures.request().hash("b".repeat(64)).build(),
				ConsentFixtures.WORKER), ErrorCode.STALE_VERSION);
		when(notices.findPublished(ConsentFixtures.NOTICE_ID, "hi")).thenReturn(Optional.empty());
		assertError(() -> validator.validate(ConsentFixtures.request().build(), ConsentFixtures.WORKER),
				ErrorCode.STALE_VERSION);
	}

	@Test
	void decisionsMustCoverExactlyTheNoticePurposesOnce() {
		List<List<DecisionItem>> bad = List.of(List.of(new DecisionItem("P-REG", Decision.GRANT)),
				List.of(new DecisionItem("P-REG", Decision.GRANT), new DecisionItem("P-REM", Decision.GRANT),
						new DecisionItem("P-RES", Decision.GRANT)),
				List.of(new DecisionItem("P-REG", Decision.GRANT), new DecisionItem("P-REG", Decision.DENY),
						new DecisionItem("P-REM", Decision.GRANT)));
		for (List<DecisionItem> decisions : bad) {
			SaveConsentRequest request = ConsentFixtures.request().decisionList(decisions).build();
			assertThatThrownBy(() -> validator.validate(request, ConsentFixtures.WORKER))
					.isInstanceOf(ConsentApiException.class)
					.satisfies(e -> assertThat(((ConsentApiException) e).getDetails().get(0).field())
							.isEqualTo("decisions"));
		}
	}

	@Test
	void declineWithoutModeIsAllowedButGrantNeedsAMode() {
		validator.validate(ConsentFixtures.request().mode(null).decisions(Decision.DENY, Decision.DENY).build(),
				ConsentFixtures.WORKER);
		assertError(() -> validator.validate(ConsentFixtures.request().mode(null).build(), ConsentFixtures.WORKER),
				ErrorCode.VALIDATION_FAILED);
	}

	@Test
	void declineWithAModeSkipsTheProofRule() {
		validator.validate(ConsentFixtures.request().mode(ConsentMode.VOICE_CLIP)
				.decisions(Decision.DENY, Decision.DENY).build(), ConsentFixtures.WORKER);
		verify(voiceRule, never()).check(any(), any(), any());
	}

	@Test
	void grantRunsTheModeRuleWithTheLinkableEvidence() {
		UUID clip = UUID.randomUUID();
		List<ConsentEvidence> files = List.of(new ConsentEvidence());
		when(evidence.requireLinkable(List.of(clip), ConsentFixtures.WORKER)).thenReturn(files);
		SaveConsentRequest request = ConsentFixtures.request().mode(ConsentMode.VOICE_CLIP).evidence(List.of(clip))
				.build();

		assertThat(validator.validate(request, ConsentFixtures.WORKER).evidence()).isEqualTo(files);
		verify(voiceRule).check(request, ConsentFixtures.WORKER, files);
	}

	@ParameterizedTest
	@EnumSource(value = ConsentMode.class, names = { "OTP", "WITNESSED", "READ_ALOUD", "SIGNATURE_PHOTO" })
	void modeWithoutARuleIsRefused(ConsentMode mode) {
		assertError(() -> validator.validate(ConsentFixtures.request().mode(mode).build(), ConsentFixtures.WORKER),
				ErrorCode.VALIDATION_FAILED);
	}

	@Test
	void guardianConsentRunsTheGuardianRuleAndChecksItsEvidence() {
		UUID photo = UUID.randomUUID();
		GuardianInfo guardian = new GuardianInfo(ConsentFixtures.guardianOtp(UUID.randomUUID()).guardianType(),
				"Sita", "Mother", "9876543210", ConsentFixtures.guardianOtp(null).verification(), UUID.randomUUID(),
				null, List.of(photo));
		ConsentEvidence guardianPhoto = new ConsentEvidence();
		when(evidence.requireLinkable(List.of(photo), ConsentFixtures.WORKER)).thenReturn(List.of(guardianPhoto));
		SaveConsentRequest request = ConsentFixtures.request().guardian(guardian).build();

		assertThat(validator.validate(request, ConsentFixtures.WORKER).evidence()).containsExactly(guardianPhoto);
		verify(guardianRule).check(request, ConsentFixtures.WORKER);
	}

	@Test
	void guardianWithoutEvidenceListIsFine() {
		SaveConsentRequest request = ConsentFixtures.request().guardian(ConsentFixtures.guardianOtp(UUID.randomUUID()))
				.build();
		assertThat(validator.validate(request, ConsentFixtures.WORKER).evidence()).isEmpty();
	}
}
