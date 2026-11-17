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

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.iemr.consent.data.consent.ConsentMode;
import com.iemr.consent.data.legacy.LegacyRowStatus;
import com.iemr.consent.data.notice.NoticeContext;
import com.iemr.consent.data.notice.Purpose;
import com.iemr.consent.model.legacy.LegacyRowInput;
import com.iemr.consent.model.notice.NoticeLanguage;
import com.iemr.consent.repository.legacy.LegacyRowRepository;
import com.iemr.consent.repository.notice.PurposeRepository;
import com.iemr.consent.service.notice.NoticeService;

/**
 * Checks the rows of a legacy consent file (A9, feature 008, research R4) and collects <b>every</b> reason a row
 * cannot be imported, in this order: {@code MISSING:<column>}, {@code UNKNOWN_BENEFICIARY}, {@code BAD_DATE},
 * {@code FUTURE_DATE}, {@code UNKNOWN_PURPOSE}, {@code UNKNOWN_LANGUAGE}, {@code UNKNOWN_MODE},
 * {@code REPRESENTATIVE_REQUIRED}, {@code DUPLICATE_ROW}, {@code ALREADY_IMPORTED}. The masters (active purposes,
 * central REGISTRATION languages) and earlier imports are read once per file. "Today" is the India date.
 */
@Component
public class LegacyRowChecker {

	/** Consent dates and capture times are India dates. */
	public static final ZoneId INDIA = ZoneId.of("Asia/Kolkata");

	/** Modes a legacy row may have: the five capture modes and {@code PAPER}. */
	static final Set<ConsentMode> MODES = EnumSet.of(ConsentMode.SELF_READ, ConsentMode.READ_ALOUD,
			ConsentMode.WITNESSED, ConsentMode.SIGNATURE_PHOTO, ConsentMode.OTP, ConsentMode.PAPER);

	static final String PURPOSE_SEPARATOR = ";";

	private final PurposeRepository purposes;

	private final NoticeService noticeService;

	private final LegacyRowRepository legacyRows;

	private final Clock clock;

	public LegacyRowChecker(PurposeRepository purposes, NoticeService noticeService, LegacyRowRepository legacyRows,
			Clock clock) {
		this.purposes = purposes;
		this.noticeService = noticeService;
		this.legacyRows = legacyRows;
		this.clock = clock;
	}

	/** The checked rows, in the order given. */
	public List<CheckedRow> check(List<LegacyRowInput> rows) {
		Set<String> knownPurposes = knownPurposes(rows);
		Set<String> languages = noticeService.languages(NoticeContext.REGISTRATION, null).stream()
				.map(NoticeLanguage::languageCode).collect(Collectors.toSet());
		Set<String> imported = importedPairs(rows);
		LocalDate today = LocalDate.now(clock.withZone(INDIA));
		Set<String> seen = new HashSet<>();
		List<CheckedRow> checked = new ArrayList<>(rows.size());
		for (LegacyRowInput row : rows) {
			checked.add(checkRow(row, knownPurposes, languages, imported, today, seen));
		}
		return checked;
	}

	private CheckedRow checkRow(LegacyRowInput row, Set<String> knownPurposes, Set<String> languages,
			Set<String> imported, LocalDate today, Set<String> seen) {
		List<String> reasons = new ArrayList<>();
		missing(reasons, "beneficiaryId", row.beneficiaryId());
		missing(reasons, "consentDate", row.consentDate());
		List<String> codes = codes(row.purposesGranted());
		if (codes.isEmpty()) {
			reasons.add("MISSING:purposesGranted");
		}
		missing(reasons, "languageCode", row.languageCode());
		missing(reasons, "mode", row.mode());
		if (!blank(row.beneficiaryId()) && row.beneficiaryRegId() == null) {
			reasons.add("UNKNOWN_BENEFICIARY");
		}
		LocalDate date = date(row.consentDate());
		if (!blank(row.consentDate()) && date == null) {
			reasons.add("BAD_DATE");
		}
		if (date != null && date.isAfter(today)) {
			reasons.add("FUTURE_DATE");
		}
		if (!knownPurposes.containsAll(codes)) {
			reasons.add("UNKNOWN_PURPOSE");
		}
		String language = blank(row.languageCode()) ? null : row.languageCode().trim().toLowerCase(Locale.ROOT);
		if (language != null && !languages.contains(language)) {
			reasons.add("UNKNOWN_LANGUAGE");
		}
		String mode = blank(row.mode()) ? null : row.mode().trim().toUpperCase(Locale.ROOT);
		ConsentMode known = mode(mode);
		if (mode != null && known == null) {
			reasons.add("UNKNOWN_MODE");
		}
		if (known == ConsentMode.WITNESSED && blank(row.representativeName())) {
			reasons.add("REPRESENTATIVE_REQUIRED");
		}
		if (row.beneficiaryRegId() != null && date != null) {
			String pair = pair(row.beneficiaryRegId(), date);
			if (!seen.add(pair)) {
				reasons.add("DUPLICATE_ROW");
			}
			if (imported.contains(pair)) {
				reasons.add("ALREADY_IMPORTED");
			}
		}
		String joined = codes.isEmpty() ? null : String.join(PURPOSE_SEPARATOR, codes);
		return new CheckedRow(row, date, joined, language, mode, List.copyOf(reasons));
	}

	private Set<String> knownPurposes(List<LegacyRowInput> rows) {
		Set<String> all = rows.stream().flatMap(r -> codes(r.purposesGranted()).stream())
				.collect(Collectors.toSet());
		if (all.isEmpty()) {
			return Set.of();
		}
		return purposes.findByPurposeCodeInAndActiveTrueAndDeletedFalse(all).stream()
				.map(Purpose::getPurposeCode)
				.collect(Collectors.toSet());
	}

	private Set<String> importedPairs(List<LegacyRowInput> rows) {
		Collection<Long> patients = rows.stream().map(LegacyRowInput::beneficiaryRegId).filter(Objects::nonNull)
				.collect(Collectors.toSet());
		if (patients.isEmpty()) {
			return Set.of();
		}
		return legacyRows.findByBeneficiaryRegIdInAndStatus(patients, LegacyRowStatus.IMPORTED).stream()
				.map(r -> pair(r.getBeneficiaryRegId(), r.getConsentDate()))
				.collect(Collectors.toSet());
	}

	/** The purpose codes of a cell: split on {@code ;}, trimmed, blanks and repeats removed, order kept. */
	public static List<String> codes(String cell) {
		if (cell == null) {
			return List.of();
		}
		return List.copyOf(Arrays.stream(cell.split(PURPOSE_SEPARATOR)).map(String::trim)
				.filter(c -> !c.isEmpty())
				.collect(Collectors.toCollection(LinkedHashSet::new)));
	}

	static String pair(Long beneficiaryRegId, LocalDate date) {
		return beneficiaryRegId + "|" + date;
	}

	/** The date of a strict {@code YYYY-MM-DD} cell, or {@code null}. */
	private static LocalDate date(String cell) {
		if (blank(cell)) {
			return null;
		}
		try {
			return LocalDate.parse(cell.trim());
		} catch (DateTimeParseException e) {
			return null;
		}
	}

	private static ConsentMode mode(String mode) {
		return MODES.stream().filter(m -> m.name().equals(mode)).findFirst().orElse(null);
	}

	private static void missing(List<String> reasons, String column, String value) {
		if (blank(value)) {
			reasons.add("MISSING:" + column);
		}
	}

	static boolean blank(String value) {
		return value == null || value.isBlank();
	}
}
