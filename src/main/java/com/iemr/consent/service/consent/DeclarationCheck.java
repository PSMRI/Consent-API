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

import com.iemr.consent.data.consent.DeclarationWording;

/**
 * A declaration that passed {@link DeclarationRule}: what is stored with the consent (feature 004).
 *
 * @param text       the declaration text as agreed to
 * @param wording    PATIENT or REPRESENTATIVE
 * @param appDefault {@code true} when the notice publishes no declaration and the app's wording was used
 * @param sha256     SHA-256 of {@code text}
 */
public record DeclarationCheck(String text, DeclarationWording wording, boolean appDefault, String sha256) {

	@Override
	public String toString() {
		return "DeclarationCheck[wording=" + wording + ", appDefault=" + appDefault + "]";
	}
}
