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

package com.iemr.consent.model.consent;

import com.iemr.consent.data.consent.DeclarationWording;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * The declaration exactly as shown when the patient (or a guardian / representative) accepted (feature 004).
 *
 * @param text            declaration text
 * @param languageCode    language it was shown in; must be the consent's
 * @param noticeVersionId notice version it belongs to; must be the consent's
 * @param wording         PATIENT for the patient's own consent, REPRESENTATIVE for a guardian's
 */
public record DeclarationInput(@NotBlank @Size(max = 4000) String text,
		@NotNull @Pattern(regexp = "^[a-z]{2,3}$") String languageCode, @NotNull Long noticeVersionId,
		@NotNull DeclarationWording wording) {

	@Override
	public String toString() {
		return "DeclarationInput[languageCode=" + languageCode + ", wording=" + wording + "]";
	}
}
