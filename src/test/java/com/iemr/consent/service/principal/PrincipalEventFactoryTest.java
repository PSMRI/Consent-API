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
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import com.iemr.consent.data.consent.Channel;
import com.iemr.consent.data.consent.Consent;
import com.iemr.consent.data.consent.ConsentPurpose;
import com.iemr.consent.data.consent.ConsentingParty;
import com.iemr.consent.data.consent.Decision;
import com.iemr.consent.data.consent.EventType;
import com.iemr.consent.data.consent.GuardianLink;
import com.iemr.consent.data.consent.PurposeStatus;
import com.iemr.consent.data.consent.SyncStatus;
import com.iemr.consent.data.consent.ConsentMode;
import com.iemr.consent.model.principal.PurposeSelection;
import com.iemr.consent.model.principal.WithdrawalRequest;
import com.iemr.consent.repository.consent.ConsentPurposeRepository;
import com.iemr.consent.repository.consent.ConsentRepository;
import com.iemr.consent.repository.consent.GuardianLinkRepository;
import com.iemr.consent.support.ConsentFixtures;
import com.iemr.consent.support.PrincipalFixtures;
import com.iemr.consent.support.TestFixtures;
import com.iemr.consent.utils.crypto.FieldEncryptor;
import com.iemr.consent.utils.crypto.Sha256;
import com.iemr.consent.utils.exception.ConsentApiException;
import com.iemr.consent.utils.exception.ErrorCode;

class PrincipalEventFactoryTest {

	private static final Long BEN = PrincipalFixtures.BEN;

	private final FieldEncryptor encryptor = new FieldEncryptor(TestFixtures.consentProperties());

	private ConsentRepository consents;

	private ConsentPurposeRepository decisions;

	private GuardianLinkRepository links;

	private PrincipalEventFactory factory;

	@BeforeEach
	void setUp() {
		consents = mock(ConsentRepository.class);
		decisions = mock(ConsentPurposeRepository.class);
		links = mock(GuardianLinkRepository.class);
		factory = new PrincipalEventFactory(consents, decisions, links, encryptor, TestFixtures.CLOCK);
		when(consents.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
		when(decisions.save(any())).thenAnswer(i -> i.getArgument(0));
	}

	private void current(ConsentPurpose... rows) {
		for (ConsentPurpose row : rows) {
			when(decisions.findCurrent(BEN, row.getPurposeCode())).thenReturn(Optional.of(row));
		}
	}

	private static GuardianLink link(java.time.Instant validUntil, String phoneEncrypted) {
		GuardianLink link = new GuardianLink();
		link.setValidUntil(validUntil);
		link.setGuardianPhoneEncrypted(phoneEncrypted);
		return link;
	}

	@Test
	void lockCurrentLocksInCodeOrderAndReturnsRowsByCode() {
		ConsentPurpose rem = PrincipalFixtures.row("c-1", "P-REM", PurposeStatus.GRANTED, 2, null);
		ConsentPurpose fam = PrincipalFixtures.row("c-1", "P-FAM", PurposeStatus.GRANTED, 1, null);
		current(rem, fam);

		Map<String, ConsentPurpose> rows = factory.lockCurrent(BEN, List.of("P-REM", "P-FAM"),
				Map.of("P-REM", 2, "P-FAM", 1));

		assertThat(rows).containsExactly(Map.entry("P-FAM", fam), Map.entry("P-REM", rem));
		InOrder order = inOrder(decisions);
		order.verify(decisions).findCurrent(BEN, "P-FAM");
		order.verify(decisions).findCurrent(BEN, "P-REM");
	}

	@Test
	void lockCurrentRefusesAMissingDecision() {
		assertThatThrownBy(() -> factory.lockCurrent(BEN, List.of("P-REM"), Map.of("P-REM", 1)))
				.isInstanceOf(ConsentApiException.class)
				.extracting("errorCode", "purposeCode").containsExactly(ErrorCode.BUSINESS_RULE_VIOLATION, "P-REM");
	}

	@Test
	@DisplayName("C20: a version that is not the current one, or is missing, is stale")
	void lockCurrentRefusesStaleOrMissingVersions() {
		current(PrincipalFixtures.row("c-1", "P-REM", PurposeStatus.GRANTED, 2, null));
		assertThatThrownBy(() -> factory.lockCurrent(BEN, List.of("P-REM"), Map.of("P-REM", 1)))
				.isInstanceOf(ConsentApiException.class).extracting("errorCode").isEqualTo(ErrorCode.STALE_VERSION);
		assertThatThrownBy(() -> factory.lockCurrent(BEN, List.of("P-REM"), Map.of()))
				.isInstanceOf(ConsentApiException.class).extracting("errorCode").isEqualTo(ErrorCode.STALE_VERSION);
	}

	@Test
	void newestSourceIsTheLatestReceivedEvent() {
		Consent older = PrincipalFixtures.event("c-1", TestFixtures.NOW.minusSeconds(60));
		Consent newer = PrincipalFixtures.event("c-2", TestFixtures.NOW);
		when(consents.findByConsentIdIn(List.of("c-1", "c-2"))).thenReturn(List.of(older, newer));

		assertThat(factory.newestSource(List.of(PrincipalFixtures.row("c-1", "P-REG", PurposeStatus.GRANTED, 1, null),
				PrincipalFixtures.row("c-2", "P-REM", PurposeStatus.GRANTED, 2, null)))).isSameAs(newer);
		assertThatThrownBy(() -> factory.newestSource(List.of())).isInstanceOf(IllegalStateException.class);
	}

	@Test
	@DisplayName("C9 / T9: a guardian's consent counts as the guardian's only while the link is valid")
	void guardianLinkDecidesTheConsentingParty() {
		Consent self = PrincipalFixtures.event("c-1", TestFixtures.NOW);
		assertThat(factory.partyFor(self)).isEqualTo(ConsentingParty.SELF);
		verify(links, never()).findFirstByConsentIdAndDeletedFalse(any());

		Consent guardian = PrincipalFixtures.event("c-2", TestFixtures.NOW);
		guardian.setConsentingParty(ConsentingParty.GUARDIAN);
		when(links.findFirstByConsentIdAndDeletedFalse("c-2")).thenReturn(Optional.of(link(null, null)));
		assertThat(factory.partyFor(guardian)).isEqualTo(ConsentingParty.GUARDIAN);
		when(links.findFirstByConsentIdAndDeletedFalse("c-2")).thenReturn(Optional.of(link(TestFixtures.NOW, null)));
		assertThat(factory.partyFor(guardian)).isEqualTo(ConsentingParty.GUARDIAN);
		when(links.findFirstByConsentIdAndDeletedFalse("c-2"))
				.thenReturn(Optional.of(link(TestFixtures.NOW.minusSeconds(1), null)));
		assertThat(factory.partyFor(guardian)).isEqualTo(ConsentingParty.SELF);
		when(links.findFirstByConsentIdAndDeletedFalse("c-2")).thenReturn(Optional.empty());
		assertThat(factory.activeGuardian(guardian)).isEmpty();
	}

	@Test
	void guardianPhoneHashNeedsAPhone() {
		assertThat(factory.guardianPhoneHash(link(null, encryptor.encrypt(ConsentFixtures.GUARDIAN_PHONE))))
				.isEqualTo(Sha256.hex(ConsentFixtures.GUARDIAN_PHONE));
		assertThatThrownBy(() -> factory.guardianPhoneHash(link(null, null))).isInstanceOf(ConsentApiException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.OTP_INVALID);
	}

	@Test
	@DisplayName("R1: the new event copies the source notice and takes the worker from the login")
	void saveEventCopiesTheSourceNoticeAndUsesServerTime() {
		Consent source = PrincipalFixtures.event("c-1", TestFixtures.NOW.minusSeconds(3600));
		WithdrawalRequest change = new WithdrawalRequest(PurposeSelection.of(List.of("P-REM")), Map.of("P-REM", 1),
				null, 7, 8, "HWC");

		Consent event = factory.saveEvent(BEN, EventType.WITHDRAWAL, null, source, ConsentingParty.SELF, change,
				"key-1", "hash-1", ConsentFixtures.WORKER);

		assertThat(event.getConsentId()).isNotBlank().isNotEqualTo("c-1");
		assertThat(event.getEventType()).isEqualTo(EventType.WITHDRAWAL);
		assertThat(event.getChannel()).isEqualTo(Channel.PREFERENCE_CENTRE);
		assertThat(event.getMode()).isNull();
		assertThat(event.getNoticeVersionId()).isEqualTo(ConsentFixtures.NOTICE_ID);
		assertThat(event.getLanguageCode()).isEqualTo("hi");
		assertThat(event.getTextHash()).isEqualTo(ConsentFixtures.TEXT_HASH);
		assertThat(event.getCapturedBy()).isEqualTo(42L);
		assertThat(event.getCapturedAt()).isEqualTo(TestFixtures.NOW);
		assertThat(event.getServerReceivedAt()).isEqualTo(TestFixtures.NOW);
		assertThat(event.getDeviceId()).isEqualTo(PrincipalEventFactory.DEFAULT_DEVICE);
		assertThat(event.getFacilityId()).isEqualTo(7);
		assertThat(event.getProviderServiceMapId()).isEqualTo(8);
		assertThat(event.getServiceLine()).isEqualTo("HWC");
		assertThat(event.getSyncStatus()).isEqualTo(SyncStatus.SYNCED);
		assertThat(event.getRequestKey()).isEqualTo("key-1");
		assertThat(event.getRequestHash()).isEqualTo("hash-1");

		WithdrawalRequest withDevice = new WithdrawalRequest(PurposeSelection.everything(), Map.of(), "tab-3", null,
				null, null);
		assertThat(factory.saveEvent(BEN, EventType.UPDATE, ConsentMode.OTP, source, ConsentingParty.GUARDIAN,
				withDevice, "key-2", "hash-2", ConsentFixtures.WORKER).getDeviceId()).isEqualTo("tab-3");
	}

	@Test
	void replaceSupersedesThePreviousDecisionAndAddsTheNextVersion() {
		ConsentPurpose previous = PrincipalFixtures.row("c-1", "P-REM", PurposeStatus.DENIED, 3, null);
		Consent event = PrincipalFixtures.event("c-9", TestFixtures.NOW);

		ConsentPurpose granted = factory.replace(previous, event, PurposeStatus.GRANTED, TestFixtures.NOW);

		assertThat(previous.isCurrent()).isFalse();
		assertThat(previous.getStatus()).isEqualTo(PurposeStatus.SUPERSEDED);
		verify(decisions).saveAndFlush(previous);
		assertThat(granted.getConsentId()).isEqualTo("c-9");
		assertThat(granted.getBeneficiaryRegId()).isEqualTo(BEN);
		assertThat(granted.getVersionNo()).isEqualTo(4);
		assertThat(granted.isCurrent()).isTrue();
		assertThat(granted.getDecision()).isEqualTo(Decision.GRANT);
		assertThat(granted.getValidUntil()).isEqualTo(TestFixtures.NOW);
		assertThat(factory.replace(granted, event, PurposeStatus.WITHDRAWN, null).getDecision())
				.isEqualTo(Decision.DENY);
	}
}
