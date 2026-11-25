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

package com.iemr.consent.service.legacy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.iemr.consent.data.legacy.LegacyRowStatus;
import com.iemr.consent.data.notice.NoticeContext;
import com.iemr.consent.data.notice.Purpose;
import com.iemr.consent.model.legacy.LegacyRowInput;
import com.iemr.consent.model.notice.NoticeLanguage;
import com.iemr.consent.repository.legacy.LegacyRowRepository;
import com.iemr.consent.repository.notice.PurposeRepository;
import com.iemr.consent.service.notice.NoticeService;
import com.iemr.consent.support.ConsentFixtures;
import com.iemr.consent.support.TestFixtures;

/** Feature 008, research R4: every reason of a legacy row, in the data-model order. */
class LegacyRowCheckerTest {

	private PurposeRepository purposes;

	private LegacyRowRepository legacyRows;

	private NoticeService noticeService;

	private LegacyRowChecker checker;

	@BeforeEach
	void setUp() {
		purposes = mock(PurposeRepository.class);
		legacyRows = mock(LegacyRowRepository.class);
		noticeService = mock(NoticeService.class);
		when(purposes.findByPurposeCodeInAndActiveTrueAndDeletedFalse(anyCollection()))
				.thenAnswer(i -> ConsentFixtures.purposes().stream()
				.filter(p -> i.<Collection<String>>getArgument(0)
								.contains(p.getPurposeCode())).toList());
		when(noticeService.languages(NoticeContext.REGISTRATION, null)).thenReturn(List.of(
				new NoticeLanguage("en", "English", true), new NoticeLanguage("hi", "Hindi", false)));
		checker = new LegacyRowChecker(purposes, noticeService, legacyRows, TestFixtures.CLOCK);
	}

	private List<String> reasons(LegacyRowInput row) {
		return checker.check(List.of(row)).get(0).reasons();
	}

	@Test
	void aValidRowHasNoReasonsAndIsNormalised() {
		CheckedRow row = checker.check(List.of(LegacyFixtures.input(1, 7001L, "2025-06-12",
				" P-REM ; P-REG;P-REM;",
				" HI ", " paper ", null))).get(0);

		assertThat(row.reasons()).isEmpty();
		assertThat(row.status()).isEqualTo(LegacyRowStatus.VALID);
		assertThat(row.consentDate()).isEqualTo(LocalDate.of(2025, 6, 12));
		assertThat(row.purposes()).isEqualTo("P-REM;P-REG");
		assertThat(row.language()).isEqualTo("hi");
		assertThat(row.mode()).isEqualTo("PAPER");
		assertThat(row.toString()).isEqualTo("CheckedRow[rowNo=1, reasons=[]]");
	}

	@Test
	void anEmptyRowIsMissingEveryColumn() {
		CheckedRow row = checker.check(List.of(new LegacyRowInput(4, " ", null, null, " ; ", "", null, null,
				null,
				null))).get(0);

		assertThat(row.reasons()).containsExactly("MISSING:beneficiaryId", "MISSING:consentDate",
		"MISSING:purposesGranted", "MISSING:languageCode", "MISSING:mode");
		assertThat(row.status()).isEqualTo(LegacyRowStatus.ERROR);
		assertThat(row.purposes()).isNull();
		assertThat(row.language()).isNull();
		assertThat(row.mode()).isNull();
		verify(purposes, never()).findByPurposeCodeInAndActiveTrueAndDeletedFalse(any());
		verify(legacyRows, never()).findByBeneficiaryRegIdInAndStatus(any(), any());
	}

	@Test
	void anUnknownBeneficiaryHasNoRegistrationId() {
		assertThat(reasons(LegacyFixtures.input(1, null, "2025-06-12", "P-REG", "hi", "PAPER", null)))
				.containsExactly("UNKNOWN_BENEFICIARY");
	}

	@Test
	void datesMustBeIsoAndNotInTheFuture() {
		assertThat(reasons(LegacyFixtures.input(1, 7001L, "12/06/2025", "P-REG", "hi", "PAPER", null)))
				.containsExactly("BAD_DATE");
		assertThat(reasons(LegacyFixtures.input(1, 7001L, "2025-13-01", "P-REG", "hi", "PAPER", null)))
				.containsExactly("BAD_DATE");
		assertThat(reasons(LegacyFixtures.input(1, 7001L, "2026-10-30", "P-REG", "hi", "PAPER", null)))
				.as("tomorrow in India").containsExactly("FUTURE_DATE");
		assertThat(reasons(LegacyFixtures.input(1, 7001L, "2026-10-29", "P-REG", "hi", "PAPER", null)))
				.as("today is accepted").isEmpty();
	}

	@Test
	void codesMustBeKnown() {
		assertThat(reasons(LegacyFixtures.input(1, 7001L, "2025-06-12", "P-REG;P-XYZ", "hi", "PAPER", null)))
				.containsExactly("UNKNOWN_PURPOSE");
		assertThat(reasons(LegacyFixtures.input(1, 7001L, "2025-06-12", "P-REG", "ta", "PAPER", null)))
				.containsExactly("UNKNOWN_LANGUAGE");
		assertThat(reasons(LegacyFixtures.input(1, 7001L, "2025-06-12", "P-REG", "hi", "VOICE_CLIP", null)))
				.containsExactly("UNKNOWN_MODE");
		assertThat(reasons(LegacyFixtures.input(1, 7001L, "2025-06-12", "P-REG", "hi", "X", null)))
				.containsExactly("UNKNOWN_MODE");
	}

	@Test
	void witnessedNeedsARepresentative() {
		assertThat(reasons(LegacyFixtures.input(1, 7001L, "2025-06-12", "P-REG", "hi", "WITNESSED", " ")))
				.containsExactly("REPRESENTATIVE_REQUIRED");
		assertThat(reasons(LegacyFixtures.input(1, 7001L, "2025-06-12", "P-REG", "hi", "WITNESSED", "Gita")))
				.isEmpty();
	}

	@Test
	void aRepeatedPatientAndDateIsADuplicateOnlyTheSecondTime() {
		List<CheckedRow> rows = checker.check(List.of(LegacyFixtures.input(1), LegacyFixtures.input(2),
				LegacyFixtures.input(3, 7001L, "2025-06-13", "P-REG", "hi", "PAPER", null)));

		assertThat(rows.get(0).reasons()).isEmpty();
		assertThat(rows.get(1).reasons()).containsExactly("DUPLICATE_ROW");
		assertThat(rows.get(2).reasons()).isEmpty();
	}

	@Test
	void aPatientAndDateImportedBeforeIsRefused() {
		when(legacyRows.findByBeneficiaryRegIdInAndStatus(Set.of(7001L), LegacyRowStatus.IMPORTED))
				.thenReturn(List.of(LegacyFixtures.row(9, LegacyRowStatus.IMPORTED, 7001L,
				LocalDate.of(2025, 6, 12),
						"P-REG")));

		assertThat(reasons(LegacyFixtures.input(1))).containsExactly("ALREADY_IMPORTED");
	}

	@Test
	void everyReasonIsListedInOrder() {
		when(legacyRows.findByBeneficiaryRegIdInAndStatus(anyCollection(), any())).thenReturn(List.of(
				LegacyFixtures.row(9, LegacyRowStatus.IMPORTED, 7001L, LocalDate.of(2025, 6, 12),
				"P-REG")));

		List<CheckedRow> rows = checker.check(List.of(LegacyFixtures.input(1),
		LegacyFixtures.input(2, 7001L, "2025-06-12", "P-ZZZ", "ta", "WITNESSED", null),
				new LegacyRowInput(3, "100245678902", null, "2030-01-01", "P-REG", "hi", "BAD", null,
				null, null)));

		assertThat(rows.get(1).reasons()).containsExactly("UNKNOWN_PURPOSE", "UNKNOWN_LANGUAGE",
		"REPRESENTATIVE_REQUIRED", "DUPLICATE_ROW", "ALREADY_IMPORTED");
		assertThat(rows.get(2).reasons()).containsExactly("UNKNOWN_BENEFICIARY", "FUTURE_DATE", "UNKNOWN_MODE");
	}

	@Test
	void purposeCodesOfACell() {
		assertThat(LegacyRowChecker.codes(null)).isEmpty();
		assertThat(LegacyRowChecker.codes("P-A; ;P-B;P-A")).containsExactly("P-A", "P-B");
		assertThat(ConsentFixtures.purposes()).extracting(Purpose::getPurposeCode).contains("P-REG", "P-REM");
	}
}
