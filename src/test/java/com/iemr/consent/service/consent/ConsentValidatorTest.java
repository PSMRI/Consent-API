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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import com.iemr.consent.model.consent.Proof;
import com.iemr.consent.data.consent.ConsentingParty;
import com.iemr.consent.data.consent.DeclarationWording;
import com.iemr.consent.data.evidence.EvidenceType;
import com.iemr.consent.data.consent.ConsentMode;
import com.iemr.consent.data.consent.Decision;
import com.iemr.consent.data.consent.EventType;
import com.iemr.consent.data.evidence.ConsentEvidence;
import com.iemr.consent.model.consent.DecisionItem;
import com.iemr.consent.model.consent.GuardianInfo;
import com.iemr.consent.model.consent.SaveConsentRequest;
import com.iemr.consent.service.evidence.EvidenceService;
import com.iemr.consent.service.notice.NoticeService;
import com.iemr.consent.service.notice.PublishedNotice;
import com.iemr.consent.support.ConsentFixtures;
import com.iemr.consent.utils.exception.ConsentApiException;
import com.iemr.consent.utils.exception.ErrorCode;
import com.iemr.consent.utils.response.FieldErrorDetail;

class ConsentValidatorTest {

	private NoticeService notices;

	private EvidenceService evidence;

	private GuardianRule guardianRule;

	private ModeProofRule selfRule;

	private ModeProofRule witnessRule;

	private ConsentValidator validator;

	@BeforeEach
	void setUp() {
		notices = mock(NoticeService.class);
		evidence = mock(EvidenceService.class);
		guardianRule = mock(GuardianRule.class);
		selfRule = mock(ModeProofRule.class);
		when(selfRule.mode()).thenReturn(ConsentMode.SELF_READ);
		witnessRule = mock(ModeProofRule.class);
		when(witnessRule.mode()).thenReturn(ConsentMode.WITNESSED);
		when(notices.findPublished(ConsentFixtures.NOTICE_ID, "hi"))
				.thenReturn(Optional.of(ConsentFixtures.publishedNotice()));
		when(evidence.requireLinkable(anyList(), any())).thenReturn(List.of());
		validator = new ConsentValidator(notices, evidence, guardianRule, new DeclarationRule(),
				List.of(selfRule, witnessRule));
	}

	private static void assertError(Runnable call, ErrorCode code) {
		assertThatThrownBy(call::run).isInstanceOf(ConsentApiException.class).extracting("errorCode")
				.isEqualTo(code);
	}

	@Test
	void selfReadAcceptRunsItsRule() {
		SaveConsentRequest request = ConsentFixtures.request().build();
		ValidatedConsent result = validator.validate(request, ConsentFixtures.WORKER);

		assertThat(result.notice().purposes()).hasSize(2);
		verify(selfRule).check(request, ConsentFixtures.WORKER, List.of());
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
	@DisplayName("FR-012a: a project notice is only valid for its own project; a central notice for any")
	void projectNoticeOnlyForItsProject() {
		PublishedNotice central = ConsentFixtures.publishedNotice();
		PublishedNotice project = ConsentFixtures.publishedNotice();
		project.notice().setProjectId(3);
		when(notices.findPublished(ConsentFixtures.NOTICE_ID, "hi")).thenReturn(Optional.of(project));
		SaveConsentRequest forProject12 = ConsentFixtures.request().build();

		assertError(() -> validator.validate(forProject12, ConsentFixtures.WORKER), ErrorCode.STALE_VERSION);
		project.notice().setProjectId(12);
		assertThat(validator.validate(forProject12, ConsentFixtures.WORKER)).isNotNull();
		when(notices.findPublished(ConsentFixtures.NOTICE_ID, "hi")).thenReturn(Optional.of(central));
		assertThat(validator.validate(forProject12, ConsentFixtures.WORKER).declaration().appDefault()).isTrue();
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
		validator.validate(ConsentFixtures.request().mode(ConsentMode.SELF_READ)
				.decisions(Decision.DENY, Decision.DENY).build(), ConsentFixtures.WORKER);
		verify(selfRule, never()).check(any(), any(), any());
	}

	@Test
	void grantRunsTheModeRuleWithTheLinkableEvidence() {
		UUID clip = UUID.randomUUID();
		List<ConsentEvidence> files = List.of(new ConsentEvidence());
		when(evidence.requireLinkable(List.of(clip), ConsentFixtures.WORKER)).thenReturn(files);
		SaveConsentRequest request = ConsentFixtures.request().mode(ConsentMode.WITNESSED).evidence(List.of(clip))
				.build();

		assertThat(validator.validate(request, ConsentFixtures.WORKER).evidence()).isEqualTo(files);
		verify(witnessRule).check(request, ConsentFixtures.WORKER, files);
	}

	@ParameterizedTest
	@EnumSource(value = ConsentMode.class, names = { "OTP", "READ_ALOUD", "SIGNATURE_PHOTO", "VOICE_CLIP" })
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

	@Test
	@DisplayName("US3 (004): mode VOICE_CLIP is no longer supported")
	void voiceClipModeIsRefused() {
		assertThatThrownBy(() -> validator.validate(ConsentFixtures.request().mode(ConsentMode.VOICE_CLIP).build(),
				ConsentFixtures.WORKER)).isInstanceOf(ConsentApiException.class).extracting("details")
				.isEqualTo(List.of(new FieldErrorDetail("mode", "is not supported")));
	}

	@Test
	@DisplayName("Feature 008: mode PAPER is for legacy records only; a save refuses it like VOICE_CLIP")
	void paperModeIsRefused() {
		assertThatThrownBy(() -> validator.validate(ConsentFixtures.request().mode(ConsentMode.PAPER).build(),
				ConsentFixtures.WORKER)).isInstanceOf(ConsentApiException.class).extracting("details")
				.isEqualTo(List.of(new FieldErrorDetail("mode", "is not supported")));
	}

	@ParameterizedTest
	@EnumSource(value = ConsentMode.class, names = { "SELF_READ", "WITNESSED" })
	@DisplayName("US3 (004): more than one recording is refused in every mode, even on a decline")
	void atMostOneRecordingInAnyMode(ConsentMode mode) {
		ConsentEvidence clip = new ConsentEvidence();
		clip.setType(EvidenceType.VOICE_CLIP);
		when(evidence.requireLinkable(anyList(), any())).thenReturn(List.of(clip, clip));

		assertThatThrownBy(() -> validator.validate(ConsentFixtures.request().mode(mode).build(),
				ConsentFixtures.WORKER)).isInstanceOf(ConsentApiException.class).extracting("details")
				.isEqualTo(List.of(new FieldErrorDetail("evidenceIds", "must contain at most one recording")));
		assertThatThrownBy(() -> validator.validate(ConsentFixtures.request().mode(mode)
				.decisions(Decision.DENY, Decision.DENY).build(), ConsentFixtures.WORKER))
				.isInstanceOf(ConsentApiException.class);
		verify(selfRule, never()).check(any(), any(), any());
	}

	@Test
	@DisplayName("C6 / M3: WITNESSED by the patient is a witness to the patient's own consent")
	void witnessedBySelf() {
		SaveConsentRequest request = ConsentFixtures.request().mode(ConsentMode.WITNESSED)
				.proof(new Proof(null, "Gita Bai", "Neighbour", null)).build();

		assertThat(validator.validate(request, ConsentFixtures.WORKER).declaration().wording())
				.isEqualTo(DeclarationWording.PATIENT);
		verify(witnessRule).check(request, ConsentFixtures.WORKER, List.of());
		verify(guardianRule, never()).check(any(), any());
	}

	@Test
	@DisplayName("C6: WITNESSED by a guardian runs every guardian rule and needs the REPRESENTATIVE wording")
	void witnessedByGuardian() {
		SaveConsentRequest request = ConsentFixtures.request().mode(ConsentMode.WITNESSED)
				.proof(new Proof(null, "Gita Bai", "Neighbour", null))
				.guardian(ConsentFixtures.guardianOtp(UUID.randomUUID())).build();

		assertThat(validator.validate(request, ConsentFixtures.WORKER).declaration().wording())
				.isEqualTo(DeclarationWording.REPRESENTATIVE);
		verify(guardianRule).check(request, ConsentFixtures.WORKER);

		SaveConsentRequest patientWording = ConsentFixtures.request().mode(ConsentMode.WITNESSED)
				.guardian(ConsentFixtures.guardianOtp(UUID.randomUUID()))
				.declaration(ConsentFixtures.defaultDeclaration(ConsentingParty.SELF)).build();
		assertError(() -> validator.validate(patientWording, ConsentFixtures.WORKER), ErrorCode.VALIDATION_FAILED);
	}

	@Test
	@DisplayName("C6: WITNESSED by a guardian without guardian details is refused by the guardian rule")
	void witnessedByGuardianWithoutDetails() {
		SaveConsentRequest request = ConsentFixtures.request().mode(ConsentMode.WITNESSED)
				.guardian(ConsentFixtures.guardianOtp(null)).build();
		org.mockito.Mockito.doThrow(new ConsentApiException(ErrorCode.VALIDATION_FAILED)).when(guardianRule)
				.check(request, ConsentFixtures.WORKER);

		assertError(() -> validator.validate(request, ConsentFixtures.WORKER), ErrorCode.VALIDATION_FAILED);
	}
}
