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

package com.iemr.consent.service.principal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.iemr.consent.data.consent.Consent;
import com.iemr.consent.data.consent.ConsentPurpose;
import com.iemr.consent.data.consent.ConsentMode;
import com.iemr.consent.data.consent.ConsentingParty;
import com.iemr.consent.data.consent.EventType;
import com.iemr.consent.data.consent.GuardianLink;
import com.iemr.consent.data.consent.PurposeStatus;
import com.iemr.consent.data.notice.Notice;
import com.iemr.consent.data.notice.NoticeContext;
import com.iemr.consent.data.notice.NoticePurpose;
import com.iemr.consent.data.notice.NoticeStatus;
import com.iemr.consent.model.principal.PendingAction;
import com.iemr.consent.model.principal.PendingReason;
import com.iemr.consent.repository.consent.ConsentPurposeRepository;
import com.iemr.consent.repository.consent.ConsentRepository;
import com.iemr.consent.repository.consent.GuardianLinkRepository;
import com.iemr.consent.repository.notice.NoticePurposeRepository;
import com.iemr.consent.repository.notice.NoticeRepository;
import com.iemr.consent.support.ConsentFixtures;
import com.iemr.consent.support.PrincipalFixtures;
import com.iemr.consent.support.TestFixtures;

class PendingActionServiceImplTest {

	private static final Long BEN = PrincipalFixtures.BEN;

	private static final Long REVISIT_NOTICE = 12L;

	private ConsentPurposeRepository decisions;

	private ConsentRepository consents;

	private NoticeRepository notices;

	private NoticePurposeRepository noticePurposes;

	private GuardianLinkRepository links;

	private PendingActionServiceImpl service;

	private final List<Consent> events = new ArrayList<>();

	@BeforeEach
	void setUp() {
		decisions = mock(ConsentPurposeRepository.class);
		consents = mock(ConsentRepository.class);
		notices = mock(NoticeRepository.class);
		noticePurposes = mock(NoticePurposeRepository.class);
		links = mock(GuardianLinkRepository.class);
		Notice registration = ConsentFixtures.notice(NoticeStatus.PUBLISHED);
		when(notices.findFirstByContextAndProjectIdIsNullAndStatusAndDeletedFalseOrderByVersionNoDesc(NoticeContext.REGISTRATION,
				NoticeStatus.PUBLISHED)).thenReturn(Optional.of(registration));
		when(notices.findById(ConsentFixtures.NOTICE_ID)).thenReturn(Optional.of(registration));
		when(noticePurposes.findByNoticeVersionIdAndDeletedFalseOrderByDisplayRankAsc(ConsentFixtures.NOTICE_ID))
				.thenReturn(List.of(link("P-REG"), link("P-REM")));
		when(consents.findByConsentIdIn(any())).thenReturn(events);
		service = new PendingActionServiceImpl(decisions, consents, notices, noticePurposes, links,
				new EffectiveStatus(TestFixtures.CLOCK), TestFixtures.CLOCK);
	}

	private static NoticePurpose link(String code) {
		NoticePurpose purpose = new NoticePurpose();
		purpose.setPurposeCode(code);
		return purpose;
	}

	private Consent event(String id, EventType type, ConsentingParty party) {
		Consent consent = PrincipalFixtures.event(id, TestFixtures.NOW.minusSeconds(86400));
		consent.setEventType(type);
		consent.setConsentingParty(party);
		events.add(consent);
		return consent;
	}

	private void current(ConsentPurpose... rows) {
		when(decisions.findByBeneficiaryRegIdAndCurrentTrue(BEN)).thenReturn(List.of(rows));
	}

	private List<PendingAction> pending() {
		return service.pending(BEN, NoticeContext.REGISTRATION);
	}

	@Test
	@DisplayName("H3: a valid decision for every purpose needs nothing")
	void validDecisionsNeedNothing() {
		event("c-1", EventType.NEW, ConsentingParty.SELF);
		current(PrincipalFixtures.row("c-1", "P-REG", PurposeStatus.GRANTED, 1, null),
				PrincipalFixtures.row("c-1", "P-REM", PurposeStatus.DENIED, 1, null));
		assertThat(pending()).isEmpty();
	}

	@Test
	@DisplayName("C4 / H4: no consent at all needs every purpose of the active notice")
	void noConsentNeedsEveryPurpose() {
		current();
		assertThat(pending()).containsExactly(
				new PendingAction(PendingReason.CONSENT_MISSING, List.of("P-REG", "P-REM"), null));
		verify(consents, never()).findByConsentIdIn(any());
	}

	@Test
	void aNewPurposeOnTheNoticeIsMissing() {
		event("c-1", EventType.NEW, ConsentingParty.SELF);
		current(PrincipalFixtures.row("c-1", "P-REG", PurposeStatus.GRANTED, 1, null));
		assertThat(pending())
				.containsExactly(new PendingAction(PendingReason.CONSENT_MISSING, List.of("P-REM"),
						null));
	}

	@Test
	@DisplayName("US1 (004): pending actions use the central notice only, never a project notice")
	void pendingActionsUseTheCentralNoticeOnly() {
		current();
		pending();
		verify(notices).findFirstByContextAndProjectIdIsNullAndStatusAndDeletedFalseOrderByVersionNoDesc(
				NoticeContext.REGISTRATION, NoticeStatus.PUBLISHED);
		verify(notices, never())
				.findFirstByContextAndProjectIdAndStatusAndDeletedFalseOrderByVersionNoDesc(any(),
				any(), any());
	}

	@Test
	void revisitUsesTheRevisitNoticeAndNoNoticeMeansOnlyDecisionRules() {
		Notice revisit = ConsentFixtures.notice(NoticeStatus.PUBLISHED);
		revisit.setNoticeVersionId(REVISIT_NOTICE);
		when(notices.findFirstByContextAndProjectIdIsNullAndStatusAndDeletedFalseOrderByVersionNoDesc(NoticeContext.REVISIT,
				NoticeStatus.PUBLISHED)).thenReturn(Optional.of(revisit));
		when(noticePurposes.findByNoticeVersionIdAndDeletedFalseOrderByDisplayRankAsc(REVISIT_NOTICE))
				.thenReturn(List.of(link("P-TRT")));
		current();
		assertThat(service.pending(BEN, NoticeContext.REVISIT))
				.containsExactly(new PendingAction(PendingReason.CONSENT_MISSING, List.of("P-TRT"),
						null));

		when(notices.findFirstByContextAndProjectIdIsNullAndStatusAndDeletedFalseOrderByVersionNoDesc(NoticeContext.REGISTRATION,
				NoticeStatus.PUBLISHED)).thenReturn(Optional.empty());
		assertThat(pending()).isEmpty();
	}

	@Test
	@DisplayName("C5: decisions from a legacy event need fresh consent")
	void legacyDecisionsAreReported() {
		Consent legacy = event("c-0", EventType.LEGACY, ConsentingParty.SELF);
		current(PrincipalFixtures.row("c-0", "P-REM", PurposeStatus.GRANTED, 1, null),
				PrincipalFixtures.row("c-0", "P-REG", PurposeStatus.GRANTED, 1, null));
		assertThat(pending()).containsExactly(
				new PendingAction(PendingReason.LEGACY, List.of("P-REG", "P-REM"),
						legacy.getCapturedAt()));
	}

	@Test
	@DisplayName("Feature 008: an imported PAPER legacy consent is LEGACY pending; ungranted P-REM is missing")
	void importedPaperLegacyConsentIsPending() {
		Consent legacy = event("c-legacy", EventType.LEGACY, ConsentingParty.SELF);
		legacy.setMode(ConsentMode.PAPER);
		current(PrincipalFixtures.row("c-legacy", "P-REG", PurposeStatus.GRANTED, 1, null));
		assertThat(pending()).extracting(PendingAction::reason).containsExactly(PendingReason.CONSENT_MISSING,
				PendingReason.LEGACY);
		assertThat(pending()).filteredOn(a -> a.reason() == PendingReason.LEGACY).singleElement()
				.isEqualTo(new PendingAction(PendingReason.LEGACY, List.of("P-REG"),
						legacy.getCapturedAt()));
	}

	@Test
	@DisplayName("C14 / C15 / C16: re-consent is due when marked, expired or after a material notice change")
	void reconsentReasons() {
		Consent source = event("c-1", EventType.NEW, ConsentingParty.SELF);
		Instant expiredAt = TestFixtures.NOW.minusSeconds(10);
		current(PrincipalFixtures.row("c-1", "P-REG", PurposeStatus.RECONSENT_REQUIRED, 1, null),
				PrincipalFixtures.row("c-1", "P-REM", PurposeStatus.GRANTED, 1, expiredAt),
				PrincipalFixtures.row("c-1", "P-FAM", PurposeStatus.WITHDRAWN, 2, null));
		assertThat(pending()).containsExactly(new PendingAction(PendingReason.RECONSENT_REQUIRED,
				List.of("P-REG", "P-REM"), source.getCapturedAt()));

		Notice newer = ConsentFixtures.notice(NoticeStatus.PUBLISHED);
		newer.setPublishedAt(TestFixtures.NOW.minusSeconds(5));
		when(notices.findNewerMaterial("MMU-REGISTRATION", 1)).thenReturn(List.of(newer));
		current(PrincipalFixtures.row("c-1", "P-REG", PurposeStatus.GRANTED, 1, null),
				PrincipalFixtures.row("c-1", "P-REM", PurposeStatus.DENIED, 1, null));
		assertThat(pending()).containsExactly(new PendingAction(PendingReason.RECONSENT_REQUIRED,
				List.of("P-REG", "P-REM"), newer.getPublishedAt()));
		verify(notices, times(1)).findNewerMaterial("MMU-REGISTRATION", 1);

		when(notices.findNewerMaterial("MMU-REGISTRATION", 1)).thenReturn(List.of());
		assertThat(pending()).isEmpty();
		when(notices.findById(ConsentFixtures.NOTICE_ID)).thenReturn(Optional.empty());
		assertThat(pending()).isEmpty();
	}

	@Test
	@DisplayName("Feature 006, R8: a material version retired by a later publish still asks for re-consent")
	void retiredMaterialVersionStillCounts() {
		Notice retired = ConsentFixtures.notice(NoticeStatus.RETIRED);
		retired.setMaterial(true);
		retired.setPublishedAt(TestFixtures.NOW.minusSeconds(60));
		when(notices.findNewerMaterial("MMU-REGISTRATION", 1)).thenReturn(List.of(retired));
		event("c-1", EventType.NEW, ConsentingParty.SELF);
		current(PrincipalFixtures.row("c-1", "P-REG", PurposeStatus.GRANTED, 1, null));

		assertThat(pending()).contains(new PendingAction(PendingReason.RECONSENT_REQUIRED, List.of("P-REG"),
				retired.getPublishedAt()));
	}

	@Test
	@DisplayName("C16: an expired purpose is due from its end date")
	void expiredIsDueFromItsEndDate() {
		event("c-1", EventType.NEW, ConsentingParty.SELF);
		Instant end = TestFixtures.NOW.minusSeconds(10);
		current(PrincipalFixtures.row("c-1", "P-REG", PurposeStatus.GRANTED, 1, null),
				PrincipalFixtures.row("c-1", "P-REM", PurposeStatus.GRANTED, 1, end));
		assertThat(pending())
				.containsExactly(new PendingAction(PendingReason.RECONSENT_REQUIRED, List.of("P-REM"),
						end));
	}

	@Test
	@DisplayName("C9: a guardian's consent for someone now an adult must be taken again from the patient")
	void guardianToSelfWhenTheLinkHasEnded() {
		event("c-1", EventType.NEW, ConsentingParty.GUARDIAN);
		Instant eighteenth = TestFixtures.NOW.minusSeconds(3600);
		GuardianLink ended = new GuardianLink();
		ended.setValidUntil(eighteenth);
		when(links.findFirstByConsentIdAndDeletedFalse("c-1")).thenReturn(Optional.of(ended));
		current(PrincipalFixtures.row("c-1", "P-REG", PurposeStatus.GRANTED, 1, null),
				PrincipalFixtures.row("c-1", "P-REM", PurposeStatus.DENIED, 1, null));

		assertThat(pending()).containsExactly(
				new PendingAction(PendingReason.GUARDIAN_TO_SELF, List.of("P-REG", "P-REM"),
						eighteenth));
		verify(links, times(1)).findFirstByConsentIdAndDeletedFalse("c-1");

		ended.setValidUntil(TestFixtures.NOW.plusSeconds(60));
		assertThat(pending()).isEmpty();
		ended.setValidUntil(null);
		assertThat(pending()).isEmpty();
		when(links.findFirstByConsentIdAndDeletedFalse("c-1")).thenReturn(Optional.empty());
		assertThat(pending()).isEmpty();
	}

	@Test
	void actionsComeInTheContractOrder() {
		Consent legacy = event("c-0", EventType.LEGACY, ConsentingParty.GUARDIAN);
		GuardianLink ended = new GuardianLink();
		ended.setValidUntil(TestFixtures.NOW.minusSeconds(1));
		when(links.findFirstByConsentIdAndDeletedFalse("c-0")).thenReturn(Optional.of(ended));
		current(PrincipalFixtures.row("c-0", "P-REG", PurposeStatus.RECONSENT_REQUIRED, 1, null));

		assertThat(pending()).extracting(PendingAction::reason).containsExactly(PendingReason.CONSENT_MISSING,
				PendingReason.LEGACY, PendingReason.RECONSENT_REQUIRED, PendingReason.GUARDIAN_TO_SELF);
		assertThat(pending().get(1).dueSince()).isEqualTo(legacy.getCapturedAt());
	}

	@Nested
	@DisplayName("API 15 (feature 007): the batch gives API 9's answer for every patient")
	class Batch {

		private static final long NONE = 1L;

		private static final long OLD = 2L;

		private static final long DONE = 3L;

		private ConsentPurpose row(long ben, String consentId, String code, PurposeStatus status) {
			ConsentPurpose row = PrincipalFixtures.row(consentId, code, status, 1, null);
			row.setBeneficiaryRegId(ben);
			return row;
		}

		private List<ConsentPurpose> threePatients() {
			event("c-old", EventType.LEGACY, ConsentingParty.SELF);
			event("c-done", EventType.NEW, ConsentingParty.SELF);
			List<ConsentPurpose> rows = List.of(row(OLD, "c-old", "P-REG", PurposeStatus.GRANTED),
					row(DONE, "c-done", "P-REG", PurposeStatus.GRANTED),
					row(DONE, "c-done", "P-REM", PurposeStatus.DENIED));
			when(decisions.findByBeneficiaryRegIdInAndCurrentTrue(List.of(NONE, OLD, DONE)))
					.thenReturn(rows);
			return rows;
		}

		@Test
		@DisplayName("each patient's list equals API 9's, keys in request order")
		void sameAnswerAsApi9() {
			List<ConsentPurpose> rows = threePatients();

			Map<Long, List<PendingAction>> batch = service.pendingFor(List.of(NONE, OLD, DONE),
					NoticeContext.REGISTRATION);

			assertThat(batch).containsOnlyKeys(NONE, OLD, DONE);
			assertThat(batch.keySet()).containsExactly(NONE, OLD, DONE);
			assertThat(batch.get(NONE)).containsExactly(
					new PendingAction(PendingReason.CONSENT_MISSING, List.of("P-REG", "P-REM"),
							null));
			assertThat(batch.get(OLD)).extracting(PendingAction::reason)
					.containsExactly(PendingReason.CONSENT_MISSING, PendingReason.LEGACY);
			assertThat(batch.get(DONE)).isEmpty();

			for (long ben : List.of(NONE, OLD, DONE)) {
				when(decisions.findByBeneficiaryRegIdAndCurrentTrue(ben))
						.thenReturn(rows.stream().filter(r -> r.getBeneficiaryRegId() == ben)
								.toList());
				assertThat(service.pending(ben, NoticeContext.REGISTRATION)).as("patient " + ben)
						.isEqualTo(batch.get(ben));
			}
		}

		@Test
		@DisplayName("the page needs two decision/consent queries, and the notice is read once")
		void sharedLookups() {
			threePatients();
			Notice newer = ConsentFixtures.notice(NoticeStatus.RETIRED);
			newer.setPublishedAt(TestFixtures.NOW.minusSeconds(5));
			when(notices.findNewerMaterial("MMU-REGISTRATION", 1)).thenReturn(List.of(newer));

			Map<Long, List<PendingAction>> batch = service.pendingFor(List.of(NONE, OLD, DONE),
					NoticeContext.REGISTRATION);

			assertThat(batch.get(DONE)).extracting(PendingAction::reason)
					.containsExactly(PendingReason.RECONSENT_REQUIRED);
			verify(decisions, times(1)).findByBeneficiaryRegIdInAndCurrentTrue(any());
			verify(decisions, never()).findByBeneficiaryRegIdAndCurrentTrue(any());
			verify(consents, times(1)).findByConsentIdIn(any());
			verify(notices, times(1))
					.findFirstByContextAndProjectIdIsNullAndStatusAndDeletedFalseOrderByVersionNoDesc(
					NoticeContext.REGISTRATION, NoticeStatus.PUBLISHED);
			verify(noticePurposes, times(1)).findByNoticeVersionIdAndDeletedFalseOrderByDisplayRankAsc(
					ConsentFixtures.NOTICE_ID);
			verify(notices, times(1)).findNewerMaterial("MMU-REGISTRATION", 1);
		}

		@Test
		@DisplayName("revisit uses the revisit notice; no notice means no CONSENT_MISSING")
		void revisitContext() {
			when(decisions.findByBeneficiaryRegIdInAndCurrentTrue(List.of(NONE))).thenReturn(List.of());

			assertThat(service.pendingFor(List.of(NONE), NoticeContext.REVISIT)).containsEntry(NONE,
					List.of());
			verify(consents, never()).findByConsentIdIn(any());
		}

		@Test
		@DisplayName("an unknown patient is a patient with no consent")
		void unknownPatient() {
			when(decisions.findByBeneficiaryRegIdInAndCurrentTrue(List.of(999L))).thenReturn(List.of());

			assertThat(service.pendingFor(List.of(999L), NoticeContext.REGISTRATION).get(999L))
					.containsExactly(
					new PendingAction(PendingReason.CONSENT_MISSING, List.of("P-REG", "P-REM"),
							null));
		}

		@Test
		@DisplayName("an error for any patient fails the whole batch")
		void oneFailureFailsTheBatch() {
			threePatients();
			when(consents.findByConsentIdIn(any())).thenThrow(new IllegalStateException("db down"));

			org.assertj.core.api.Assertions.assertThatThrownBy(
					() -> service.pendingFor(List.of(NONE, OLD, DONE), NoticeContext.REGISTRATION))
					.isInstanceOf(IllegalStateException.class);
		}
	}
}
