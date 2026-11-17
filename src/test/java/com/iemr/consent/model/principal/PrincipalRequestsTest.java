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

package com.iemr.consent.model.principal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import com.iemr.consent.data.consent.Decision;
import com.iemr.consent.model.grant.AccessRequest;
import com.iemr.consent.support.TestFixtures;

import jakarta.validation.Validation;
import jakarta.validation.Validator;

/** Request rules of feature 003 that Bean Validation and the JSON reader enforce. */
class PrincipalRequestsTest {

	private final ObjectMapper mapper = TestFixtures.objectMapper();

	private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

	@Test
	void purposeSelectionReadsAllOrAListAndWritesItBack() throws Exception {
		WithdrawalRequest all = mapper.readValue("{\"purposeCodes\":\"ALL\",\"versions\":{}}", WithdrawalRequest.class);
		assertThat(all.purposeCodes()).isEqualTo(PurposeSelection.everything());
		assertThat(mapper.writeValueAsString(all.purposeCodes())).isEqualTo("\"ALL\"");

		WithdrawalRequest named = mapper.readValue("{\"purposeCodes\":[\"P-REM\"],\"versions\":{\"P-REM\":2}}",
				WithdrawalRequest.class);
		assertThat(named.purposeCodes().codes()).containsExactly("P-REM");
		assertThat(mapper.writeValueAsString(named.purposeCodes())).isEqualTo("[\"P-REM\"]");

		assertThatThrownBy(() -> mapper.readValue("{\"purposeCodes\":\"SOME\"}", WithdrawalRequest.class))
				.isInstanceOf(MismatchedInputException.class);
	}

	@Test
	void purposeCodesRules() {
		assertThat(PurposeCodes.valid(List.of("P-REG", "P-REM"), 2)).isTrue();
		assertThat(PurposeCodes.valid(null, 2)).isFalse();
		assertThat(PurposeCodes.valid(List.of(), 2)).isFalse();
		assertThat(PurposeCodes.valid(List.of("A", "B", "C"), 2)).isFalse();
		assertThat(PurposeCodes.valid(Arrays.asList("A", null), 2)).isFalse();
		assertThat(PurposeCodes.valid(List.of("bad code"), 2)).isFalse();
		assertThat(PurposeCodes.valid(List.of("A", "A"), 2)).isFalse();
		assertThat(PurposeCodes.fitColumn(null)).isTrue();
		assertThat(PurposeCodes.fitColumn(List.of("x".repeat(255)))).isTrue();
		assertThat(PurposeCodes.fitColumn(List.of("x".repeat(128), "y".repeat(127)))).isFalse();
	}

	@Test
	void missingFieldsAreReportedByNotNullNotByTheCrossFieldRules() {
		assertThat(validator.validate(new WithdrawalRequest(null, null, null, null, null, null)))
				.extracting(v -> v.getPropertyPath().toString()).containsExactlyInAnyOrder("purposeCodes", "versions");
		assertThat(validator.validate(new PreferenceRequest(null, null, null, null, null, null, null, null)))
				.extracting(v -> v.getPropertyPath().toString())
				.containsExactlyInAnyOrder("purposeCodes", "decision", "versions");
		assertThat(validator.validate(new PreferenceRequest(List.of("P-REM"), Decision.DENY, null, null, null, null,
				null, null))).extracting(v -> v.getPropertyPath().toString()).containsExactly("versions");
		assertThat(validator.validate(new AccessRequest(1L, null, 1, "ok", null)))
				.extracting(v -> v.getPropertyPath().toString()).containsExactly("purposeCodes");
	}

	@Test
	void crossFieldRules() {
		assertThat(validator.validate(new WithdrawalRequest(PurposeSelection.of(List.of()), Map.of(), null, null,
				null, null))).extracting(v -> v.getPropertyPath().toString()).containsExactly("purposeCodesValid");
		assertThat(validator.validate(new PreferenceRequest(List.of("P-REM"), Decision.GRANT, Map.of("P-REM", 1),
				null, null, null, null, null))).extracting(v -> v.getPropertyPath().toString())
				.containsExactly("otpChallengeIdPresent");
		assertThat(validator.validate(new PreferenceRequest(List.of("P-REM"), Decision.GRANT, Map.of("P-REM", 1),
				UUID.randomUUID(), null, null, null, null))).isEmpty();
		assertThat(validator.validate(new AccessRequest(1L, List.of("P-REM"), 1, "Referral follow-up", null)))
				.isEmpty();
		assertThat(new AccessRequest(1L, List.of("P-REM"), 1, "Referral follow-up", null).toString())
				.doesNotContain("Referral");
	}
}
