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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import com.iemr.consent.data.consent.Consent;
import com.iemr.consent.data.consent.ConsentPurpose;
import com.iemr.consent.data.consent.Decision;
import com.iemr.consent.data.consent.PurposeStatus;
import com.iemr.consent.data.consent.SyncStatus;
import com.iemr.consent.model.consent.PurposeResult;
import com.iemr.consent.model.consent.SaveConsentRequest;
import com.iemr.consent.model.consent.SaveConsentResult;
import com.iemr.consent.repository.consent.ConsentPurposeRepository;
import com.iemr.consent.repository.consent.ConsentRepository;
import com.iemr.consent.support.ConsentFixtures;
import com.iemr.consent.utils.exception.ConsentApiException;
import com.iemr.consent.utils.exception.ErrorCode;

class ConsentServiceImplTest {

	private ConsentRepository consents;

	private ConsentPurposeRepository purposes;

	private ConsentValidator validator;

	private ConsentWriter writer;

	private ConsentServiceImpl service;

	private final SaveConsentRequest request = ConsentFixtures.request().build();

	private final ValidatedConsent validated = new ValidatedConsent(ConsentFixtures.publishedNotice(), List.of());

	@BeforeEach
	void setUp() {
		consents = mock(ConsentRepository.class);
		purposes = mock(ConsentPurposeRepository.class);
		validator = mock(ConsentValidator.class);
		writer = mock(ConsentWriter.class);
		service = new ConsentServiceImpl(consents, purposes, validator, writer);
		when(consents.findByCapturedByAndRequestKey(42L, "k")).thenReturn(Optional.empty());
		when(validator.validate(request, ConsentFixtures.WORKER)).thenReturn(validated);
	}

	private static Consent stored(String hash) {
		Consent consent = new Consent();
		consent.setConsentId("c-1");
		consent.setSyncStatus(SyncStatus.SYNCED);
		consent.setRequestHash(hash);
		return consent;
	}

	private static ConsentPurpose row(String code, Decision decision, PurposeStatus status, int version) {
		ConsentPurpose row = new ConsentPurpose();
		row.setPurposeCode(code);
		row.setDecision(decision);
		row.setStatus(status);
		row.setVersionNo(version);
		return row;
	}

	@Test
	void newRequestIsValidatedThenWritten() {
		SaveConsentResult written = new SaveConsentResult("c-9", SyncStatus.SYNCED, List.of());
		when(writer.write(request, validated, "k", "h", ConsentFixtures.WORKER, "ip")).thenReturn(written);

		assertThat(service.save(request, "k", "h", ConsentFixtures.WORKER, "ip")).isSameAs(written);
	}

	@Test
	@DisplayName("C18: same key and request replays the original answer from the stored decision")
	void replayUsesTheStoredDecisionNotTheCurrentStatus() {
		when(consents.findByCapturedByAndRequestKey(42L, "k")).thenReturn(Optional.of(stored("h")));
		when(purposes.findByConsentIdOrderByConsentPurposeIdAsc("c-1")).thenReturn(List.of(
				row("P-REG", Decision.GRANT, PurposeStatus.SUPERSEDED, 1),
				row("P-REM", Decision.DENY, PurposeStatus.DENIED, 4)));

		SaveConsentResult result = service.save(request, "k", "h", ConsentFixtures.WORKER, "ip");

		assertThat(result.consentId()).isEqualTo("c-1");
		assertThat(result.purposes()).containsExactly(new PurposeResult("P-REG", PurposeStatus.GRANTED, 1),
				new PurposeResult("P-REM", PurposeStatus.DENIED, 4));
		verify(validator, never()).validate(any(), any());
		verify(writer, never()).write(any(), any(), any(), any(), any(), any());
	}

	@Test
	void sameKeyWithADifferentRequestIsAConflict() {
		when(consents.findByCapturedByAndRequestKey(42L, "k")).thenReturn(Optional.of(stored("other")));

		assertThatThrownBy(() -> service.save(request, "k", "h", ConsentFixtures.WORKER, "ip"))
				.isInstanceOf(ConsentApiException.class).extracting("errorCode").isEqualTo(ErrorCode.STATE_CONFLICT);
	}

	@Test
	void raceOnTheSameKeyReplaysTheWinner() {
		when(writer.write(request, validated, "k", "h", ConsentFixtures.WORKER, "ip"))
				.thenThrow(new DataIntegrityViolationException("dup"));
		when(consents.findByCapturedByAndRequestKey(42L, "k")).thenReturn(Optional.empty(),
				Optional.of(stored("h")));
		when(purposes.findByConsentIdOrderByConsentPurposeIdAsc("c-1")).thenReturn(List.of());

		assertThat(service.save(request, "k", "h", ConsentFixtures.WORKER, "ip").consentId()).isEqualTo("c-1");
	}

	@Test
	void constraintViolationWithoutAWinnerIsAConflict() {
		when(writer.write(request, validated, "k", "h", ConsentFixtures.WORKER, "ip"))
				.thenThrow(new DataIntegrityViolationException("current_key"));

		assertThatThrownBy(() -> service.save(request, "k", "h", ConsentFixtures.WORKER, "ip"))
				.isInstanceOf(ConsentApiException.class).extracting("errorCode").isEqualTo(ErrorCode.STATE_CONFLICT);
	}
}
