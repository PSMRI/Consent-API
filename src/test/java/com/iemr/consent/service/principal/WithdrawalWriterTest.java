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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.iemr.consent.data.consent.Channel;
import com.iemr.consent.data.consent.Consent;
import com.iemr.consent.data.consent.ConsentPurpose;
import com.iemr.consent.data.consent.ConsentingParty;
import com.iemr.consent.data.consent.EventType;
import com.iemr.consent.data.consent.GuardianLink;
import com.iemr.consent.data.consent.PurposeStatus;
import com.iemr.consent.data.grant.AccessGrant;
import com.iemr.consent.data.grant.GrantStatus;
import com.iemr.consent.data.notice.Purpose;
import com.iemr.consent.model.principal.PurposeSelection;
import com.iemr.consent.model.principal.WithdrawalRequest;
import com.iemr.consent.repository.consent.ConsentPurposeRepository;
import com.iemr.consent.repository.consent.ConsentRepository;
import com.iemr.consent.repository.consent.GuardianLinkRepository;
import com.iemr.consent.repository.grant.AccessGrantRepository;
import com.iemr.consent.repository.notice.PurposeRepository;
import com.iemr.consent.service.audit.AuditLogService;
import com.iemr.consent.service.grant.AccessGrantService;
import com.iemr.consent.support.ConsentFixtures;
import com.iemr.consent.support.PrincipalFixtures;
import com.iemr.consent.support.TestFixtures;
import com.iemr.consent.utils.crypto.FieldEncryptor;
import com.iemr.consent.utils.exception.ConsentApiException;
import com.iemr.consent.utils.exception.ErrorCode;

class WithdrawalWriterTest {

	private static final Long BEN = PrincipalFixtures.BEN;

	private static final String IP = "10.0.0.1";

	private ConsentRepository consents;

	private ConsentPurposeRepository decisions;

	private GuardianLinkRepository links;

	private AccessGrantRepository grants;

	private AccessGrantService grantService;

	private AuditLogService audit;

	private WithdrawalWriter writer;

	private final List<ConsentPurpose> saved = new ArrayList<>();

	private final Consent source = PrincipalFixtures.event("c-1", TestFixtures.NOW.minusSeconds(3600));

	@BeforeEach
	void setUp() {
		consents = mock(ConsentRepository.class);
		decisions = mock(ConsentPurposeRepository.class);
		links = mock(GuardianLinkRepository.class);
		grants = mock(AccessGrantRepository.class);
		grantService = mock(AccessGrantService.class);
		audit = mock(AuditLogService.class);
		PurposeRepository purposes = mock(PurposeRepository.class);
		List<Purpose> catalogue = new ArrayList<>(ConsentFixtures.purposes());
		catalogue.add(ConsentFixtures.purpose("P-FAM", false, null, 1));
		when(purposes.findByPurposeCodeInAndActiveTrueAndDeletedFalse(any())).thenReturn(catalogue);
		when(consents.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
		when(consents.findByConsentIdIn(any())).thenReturn(List.of(source));
		when(decisions.save(any())).thenAnswer(i -> {
			saved.add(i.getArgument(0));
			return i.getArgument(0);
		});
		EffectiveStatus status = new EffectiveStatus(TestFixtures.CLOCK);
		PrincipalEventFactory events = new PrincipalEventFactory(consents, decisions, links,
				new FieldEncryptor(TestFixtures.consentProperties()), TestFixtures.CLOCK);
		writer = new WithdrawalWriter(decisions, purposes, grants, grantService, events, status, audit);
	}

	private void current(ConsentPurpose... rows) {
		for (ConsentPurpose row : rows) {
			when(decisions.findCurrent(BEN, row.getPurposeCode())).thenReturn(Optional.of(row));
		}
		when(decisions.findByBeneficiaryRegIdAndCurrentTrue(BEN)).thenReturn(List.of(rows));
	}

	private static WithdrawalRequest named(Map<String, Integer> versions, String... codes) {
		return new WithdrawalRequest(PurposeSelection.of(List.of(codes)), versions, null, null, null, null);
	}

	private void write(WithdrawalRequest request) {
		writer.write(BEN, request, "key-1", "hash-1", ConsentFixtures.WORKER, IP);
	}

	private void assertRefused(WithdrawalRequest request, ErrorCode code, String purposeCode) {
		assertThatThrownBy(() -> write(request)).isInstanceOf(ConsentApiException.class)
				.extracting("errorCode", "purposeCode").containsExactly(code, purposeCode);
		verify(consents, never()).saveAndFlush(any());
		verify(audit, never()).append(anyString(), any(), any(), any(), any(), any(), any());
	}

	@Test
	@DisplayName("C11 / H10: withdrawing one purpose records a WITHDRAWAL event at the next version")
	void withdrawsOnePurposeAtTheNextVersion() {
		ConsentPurpose rem = PrincipalFixtures.row("c-1", "P-REM", PurposeStatus.GRANTED, 2, TestFixtures.NOW.plusSeconds(60));
		current(rem, PrincipalFixtures.row("c-1", "P-REG", PurposeStatus.GRANTED, 1, null));
		List<AccessGrant> open = List.of(PrincipalFixtures.grant("g-1", GrantStatus.ACTIVE, "P-REM"));
		when(grants.findLockedByBeneficiaryRegIdAndStatusIn(BEN, List.of(GrantStatus.PENDING, GrantStatus.ACTIVE)))
				.thenReturn(open);

		write(named(Map.of("P-REM", 2), "P-REM"));

		ArgumentCaptor<Consent> event = ArgumentCaptor.forClass(Consent.class);
		verify(consents).saveAndFlush(event.capture());
		assertThat(event.getValue().getEventType()).isEqualTo(EventType.WITHDRAWAL);
		assertThat(event.getValue().getChannel()).isEqualTo(Channel.PREFERENCE_CENTRE);
		assertThat(event.getValue().getMode()).isNull();
		assertThat(event.getValue().getConsentingParty()).isEqualTo(ConsentingParty.SELF);
		assertThat(rem.getStatus()).isEqualTo(PurposeStatus.SUPERSEDED);
		assertThat(saved).singleElement().satisfies(row -> {
			assertThat(row.getPurposeCode()).isEqualTo("P-REM");
			assertThat(row.getStatus()).isEqualTo(PurposeStatus.WITHDRAWN);
			assertThat(row.getVersionNo()).isEqualTo(3);
			assertThat(row.getValidUntil()).isNull();
		});
		verify(grantService).revokeForWithdrawal(open, Set.of("P-REM"), ConsentFixtures.WORKER, IP);
		verify(audit).append(AuditLogService.CONSENT_WITHDRAWN, event.getValue().getConsentId(), BEN,
				"P-REM:WITHDRAWN", "WITHDRAWN", 42L, IP);
	}

	@Test
	@DisplayName("C12: a purpose that cannot be withdrawn refuses the whole request")
	void refusesAPurposeThatCannotBeWithdrawn() {
		current(PrincipalFixtures.row("c-1", "P-REG", PurposeStatus.GRANTED, 1, null),
				PrincipalFixtures.row("c-1", "P-REM", PurposeStatus.GRANTED, 1, null));
		assertRefused(named(Map.of("P-REG", 1, "P-REM", 1), "P-REG", "P-REM"), ErrorCode.PURPOSE_NOT_WITHDRAWABLE,
				"P-REG");
	}

	@Test
	void aPurposeMissingFromTheCatalogueCannotBeWithdrawn() {
		current(PrincipalFixtures.row("c-1", "P-OLD", PurposeStatus.GRANTED, 1, null));
		assertRefused(named(Map.of("P-OLD", 1), "P-OLD"), ErrorCode.PURPOSE_NOT_WITHDRAWABLE, "P-OLD");
	}

	@Test
	@DisplayName("C20: a stale version changes nothing")
	void staleVersionChangesNothing() {
		current(PrincipalFixtures.row("c-1", "P-REM", PurposeStatus.GRANTED, 2, null));
		assertRefused(named(Map.of("P-REM", 1), "P-REM"), ErrorCode.STALE_VERSION, null);
	}

	@Test
	void onlyACurrentlyGrantedPurposeCanBeWithdrawn() {
		current(PrincipalFixtures.row("c-1", "P-REM", PurposeStatus.DENIED, 1, null));
		assertRefused(named(Map.of("P-REM", 1), "P-REM"), ErrorCode.STATE_CONFLICT, "P-REM");
	}

	@Test
	@DisplayName("C16: an expired grant has nothing left to withdraw")
	void anExpiredPurposeCannotBeWithdrawn() {
		current(PrincipalFixtures.row("c-1", "P-REM", PurposeStatus.GRANTED, 1, TestFixtures.NOW.minusSeconds(1)));
		assertRefused(named(Map.of("P-REM", 1), "P-REM"), ErrorCode.STATE_CONFLICT, "P-REM");
	}

	@Test
	void aPurposeWithNoDecisionIsABusinessRuleError() {
		when(decisions.findCurrent(BEN, "P-REM")).thenReturn(Optional.empty());
		assertRefused(named(Map.of("P-REM", 1), "P-REM"), ErrorCode.BUSINESS_RULE_VIOLATION, "P-REM");
	}

	@Test
	@DisplayName("C11: ALL withdraws every granted purpose that can be withdrawn and nothing else")
	void allWithdrawsOnlyGrantedWithdrawablePurposes() {
		current(PrincipalFixtures.row("c-1", "P-REG", PurposeStatus.GRANTED, 1, null),
				PrincipalFixtures.row("c-1", "P-REM", PurposeStatus.GRANTED, 2, null),
				PrincipalFixtures.row("c-1", "P-FAM", PurposeStatus.DENIED, 1, null));

		write(new WithdrawalRequest(PurposeSelection.everything(), Map.of("P-REM", 2), null, null, null, null));

		assertThat(saved).extracting(ConsentPurpose::getPurposeCode).containsExactly("P-REM");
		verify(grantService).revokeForWithdrawal(List.of(), Set.of("P-REM"), ConsentFixtures.WORKER, IP);
	}

	@Test
	void allNeedsTheVersionOfEveryPurposeItWithdrawsAndSomethingToWithdraw() {
		current(PrincipalFixtures.row("c-1", "P-REM", PurposeStatus.GRANTED, 2, null));
		assertRefused(new WithdrawalRequest(PurposeSelection.everything(), Map.of(), null, null, null, null),
				ErrorCode.STALE_VERSION, null);

		current(PrincipalFixtures.row("c-1", "P-REG", PurposeStatus.GRANTED, 1, null),
				PrincipalFixtures.row("c-1", "P-REM", PurposeStatus.WITHDRAWN, 3, null));
		assertRefused(new WithdrawalRequest(PurposeSelection.everything(), Map.of(), null, null, null, null),
				ErrorCode.STATE_CONFLICT, null);
	}

	@Test
	@DisplayName("C9: the withdrawal is the guardian's while the guardian link is valid")
	void consentingPartyFollowsTheGuardianLink() {
		source.setConsentingParty(ConsentingParty.GUARDIAN);
		when(links.findFirstByConsentIdAndDeletedFalse("c-1")).thenReturn(Optional.of(new GuardianLink()));
		current(PrincipalFixtures.row("c-1", "P-REM", PurposeStatus.GRANTED, 1, null));

		write(named(Map.of("P-REM", 1), "P-REM"));

		ArgumentCaptor<Consent> event = ArgumentCaptor.forClass(Consent.class);
		verify(consents).saveAndFlush(event.capture());
		assertThat(event.getValue().getConsentingParty()).isEqualTo(ConsentingParty.GUARDIAN);
		verify(grantService).revokeForWithdrawal(eq(List.of()), eq(Set.of("P-REM")), any(), eq(IP));
	}
}
