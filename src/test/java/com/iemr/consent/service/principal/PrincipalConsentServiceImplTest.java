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
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import com.iemr.consent.data.consent.Consent;
import com.iemr.consent.data.consent.Decision;
import com.iemr.consent.data.consent.PurposeStatus;
import com.iemr.consent.model.principal.PatientConsents;
import com.iemr.consent.model.principal.PreferenceRequest;
import com.iemr.consent.model.principal.PurposeSelection;
import com.iemr.consent.model.principal.WithdrawalRequest;
import com.iemr.consent.model.principal.WithdrawalResult;
import com.iemr.consent.repository.consent.ConsentPurposeRepository;
import com.iemr.consent.repository.consent.ConsentRepository;
import com.iemr.consent.repository.notice.PurposeRepository;
import com.iemr.consent.support.ConsentFixtures;
import com.iemr.consent.support.PrincipalFixtures;
import com.iemr.consent.support.TestFixtures;
import com.iemr.consent.utils.exception.ConsentApiException;
import com.iemr.consent.utils.exception.ErrorCode;

class PrincipalConsentServiceImplTest {

	private static final Long BEN = PrincipalFixtures.BEN;

	private static final String KEY = "key-1";

	private static final String IP = "10.0.0.1";

	private ConsentRepository consents;

	private ConsentPurposeRepository decisions;

	private PurposeRepository purposes;

	private PreferenceChangeWriter preferenceWriter;

	private WithdrawalWriter withdrawalWriter;

	private PrincipalConsentServiceImpl service;

	private final PreferenceRequest preference = new PreferenceRequest(List.of("P-REM"), Decision.DENY,
			Map.of("P-REM", 1), null, null, null, null, null);

	@BeforeEach
	void setUp() {
		consents = mock(ConsentRepository.class);
		decisions = mock(ConsentPurposeRepository.class);
		purposes = mock(PurposeRepository.class);
		preferenceWriter = mock(PreferenceChangeWriter.class);
		withdrawalWriter = mock(WithdrawalWriter.class);
		when(purposes.findByPurposeCodeInAndActiveTrueAndDeletedFalse(any())).thenReturn(ConsentFixtures.purposes());
		when(consents.findByCapturedByAndRequestKey(42L, KEY)).thenReturn(Optional.empty());
		service = new PrincipalConsentServiceImpl(consents, decisions, purposes,
				new ConsentViewMapper(new EffectiveStatus(TestFixtures.CLOCK)), preferenceWriter, withdrawalWriter);
	}

	private void stored() {
		Consent first = PrincipalFixtures.event("c-1", TestFixtures.NOW.minusSeconds(60));
		Consent second = PrincipalFixtures.event("c-2", TestFixtures.NOW);
		when(consents.findByBeneficiaryRegIdOrderByServerReceivedAtDesc(BEN)).thenReturn(List.of(second, first));
		when(decisions.findByConsentIdInOrderByConsentPurposeIdAsc(List.of("c-2", "c-1"))).thenReturn(List.of(
				PrincipalFixtures.row("c-1", "P-REG", PurposeStatus.GRANTED, 1, null),
				PrincipalFixtures.row("c-1", "P-REM", PurposeStatus.SUPERSEDED, 1, null),
				PrincipalFixtures.row("c-2", "P-REM", PurposeStatus.GRANTED, 2, null)));
	}

	private static Consent keyed(String hash) {
		Consent consent = PrincipalFixtures.event("c-9", TestFixtures.NOW);
		consent.setRequestHash(hash);
		return consent;
	}

	@Test
	@DisplayName("API 8: current decisions and the full history, read with two queries plus the purpose names")
	void consentsShowCurrentDecisionsAndHistory() {
		stored();

		PatientConsents result = service.consents(BEN);

		assertThat(result.current()).extracting("purposeCode", "status").containsExactly(
				tuple("P-REG", PurposeStatus.GRANTED),
				tuple("P-REM", PurposeStatus.GRANTED));
		assertThat(result.history()).extracting("consentId").containsExactly("c-2", "c-1");
		verify(consents).findByBeneficiaryRegIdOrderByServerReceivedAtDesc(BEN);
		verify(decisions).findByConsentIdInOrderByConsentPurposeIdAsc(any());
		verify(purposes).findByPurposeCodeInAndActiveTrueAndDeletedFalse(List.of("P-REG", "P-REM"));
	}

	@Test
	void aPatientWithNoConsentHasEmptyLists() {
		assertThat(service.consents(BEN)).isEqualTo(new PatientConsents(List.of(), List.of()));
		verifyNoInteractions(decisions, purposes);
	}

	@Test
	void aNewPreferenceIsWrittenAndTheConsentsReturned() {
		stored();
		PatientConsents result = service.changePreference(BEN, preference, KEY, "hash-1", ConsentFixtures.WORKER, IP);
		verify(preferenceWriter).write(BEN, preference, KEY, "hash-1", ConsentFixtures.WORKER, IP);
		assertThat(result.history()).hasSize(2);
	}

	@Test
	@DisplayName("C18: a repeat with the same key changes nothing and returns the current consents")
	void aRepeatChangesNothing() {
		stored();
		when(consents.findByCapturedByAndRequestKey(42L, KEY)).thenReturn(Optional.of(keyed("hash-1")));

		PatientConsents result = service.changePreference(BEN, preference, KEY, "hash-1", ConsentFixtures.WORKER, IP);

		verify(preferenceWriter, never()).write(any(), any(), anyString(), anyString(), any(), anyString());
		assertThat(result.current()).hasSize(2);
	}

	@Test
	@DisplayName("C18 / I1: the same key with another request, or for another patient, is a conflict")
	void aKeyReusedForAnotherRequestOrPatientIsAConflict() {
		when(consents.findByCapturedByAndRequestKey(42L, KEY)).thenReturn(Optional.of(keyed("hash-of-patient-2002")));
		assertThatThrownBy(() -> service.changePreference(BEN, preference, KEY, "hash-1", ConsentFixtures.WORKER, IP))
				.isInstanceOf(ConsentApiException.class).extracting("errorCode").isEqualTo(ErrorCode.STATE_CONFLICT);
		verify(preferenceWriter, never()).write(any(), any(), anyString(), anyString(), any(), anyString());
	}

	@Test
	void aLostRaceReplaysTheWinnerOrFails() {
		doThrow(new DataIntegrityViolationException("dup")).when(preferenceWriter).write(any(), any(), anyString(),
				anyString(), any(), anyString());
		when(consents.findByCapturedByAndRequestKey(42L, KEY)).thenReturn(Optional.empty(),
				Optional.of(keyed("hash-1")));
		assertThat(service.changePreference(BEN, preference, KEY, "hash-1", ConsentFixtures.WORKER, IP).current())
				.isEmpty();

		when(consents.findByCapturedByAndRequestKey(42L, KEY)).thenReturn(Optional.empty(), Optional.empty());
		assertThatThrownBy(() -> service.changePreference(BEN, preference, KEY, "hash-1", ConsentFixtures.WORKER, IP))
				.isInstanceOf(ConsentApiException.class).extracting("errorCode").isEqualTo(ErrorCode.STATE_CONFLICT);
		verify(preferenceWriter, times(2)).write(any(), any(), anyString(), anyString(), any(), anyString());
	}

	@Test
	@DisplayName("C11: ALL reports the granted purposes it could not withdraw; named purposes report none")
	void withdrawalReportsWhatAllLeftUnchanged() {
		stored();
		WithdrawalRequest all = new WithdrawalRequest(PurposeSelection.everything(), Map.of(), null, null, null,
				null);
		WithdrawalResult result = service.withdraw(BEN, all, KEY, "hash-1", ConsentFixtures.WORKER, IP);
		verify(withdrawalWriter).write(BEN, all, KEY, "hash-1", ConsentFixtures.WORKER, IP);
		assertThat(result.notWithdrawn()).containsExactly("P-REG");
		assertThat(result.history()).hasSize(2);

		WithdrawalRequest named = new WithdrawalRequest(PurposeSelection.of(List.of("P-REM")), Map.of("P-REM", 2),
				null, null, null, null);
		assertThat(service.withdraw(BEN, named, "key-2", "hash-2", ConsentFixtures.WORKER, IP).notWithdrawn())
				.isEmpty();
	}
}
