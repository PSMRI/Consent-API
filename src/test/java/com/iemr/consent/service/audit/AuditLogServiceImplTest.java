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

package com.iemr.consent.service.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.iemr.consent.data.audit.AuditLogEntry;
import com.iemr.consent.repository.audit.AuditLogRepository;
import com.iemr.consent.support.TestFixtures;
import com.iemr.consent.utils.crypto.Sha256;

class AuditLogServiceImplTest {

	private final AuditLogRepository repository = mock(AuditLogRepository.class);

	private final AuditLogServiceImpl service = new AuditLogServiceImpl(repository, TestFixtures.CLOCK);

	@Test
	void newEntryChainsToTheLockedLastEntry() {
		AuditLogEntry last = new AuditLogEntry();
		last.setEntryHash("a".repeat(64));
		when(repository.findTopByOrderByIdDesc()).thenReturn(Optional.of(last));

		service.append(AuditLogService.CONSENT_CAPTURED, "c-1", 42L, "P-REG:GRANTED", "GRANTED", 7L, "10.0.0.1");

		ArgumentCaptor<AuditLogEntry> saved = ArgumentCaptor.forClass(AuditLogEntry.class);
		verify(repository).save(saved.capture());
		AuditLogEntry entry = saved.getValue();
		assertThat(entry.getPrevHash()).isEqualTo("a".repeat(64));
		assertThat(entry.getCreatedAt()).isEqualTo(TestFixtures.NOW);
		assertThat(entry.getEntryHash()).isEqualTo(Sha256.hex("a".repeat(64)
				+ "CONSENT_CAPTURED|c-1|42|P-REG:GRANTED|GRANTED|7|10.0.0.1|2026-10-29T10:00:00Z"));
	}

	@Test
	void nullFieldsHashAsEmpty() {
		AuditLogEntry entry = new AuditLogEntry();
		entry.setAction("X");
		entry.setCreatedAt(TestFixtures.NOW);

		assertThat(AuditLogServiceImpl.entryHash("p", entry))
				.isEqualTo(Sha256.hex("pX|||||||2026-10-29T10:00:00Z"));
	}

	@Test
	void missingGenesisRowFails() {
		when(repository.findTopByOrderByIdDesc()).thenReturn(Optional.empty());

		assertThatIllegalStateException().isThrownBy(() -> service.append("X", null, null, null, null, 1L, null));
		verify(repository, never()).save(org.mockito.ArgumentMatchers.any());
	}
}
