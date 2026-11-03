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

import java.util.HashSet;
import java.util.List;
import java.util.Objects;

import com.iemr.consent.model.consent.ConsentPatterns;

/** Shared rule for a list of purpose codes in a request. */
public final class PurposeCodes {

	private PurposeCodes() {
	}

	/** @return true when the list has 1..{@code max} unique, well-formed purpose codes */
	public static boolean valid(List<String> codes, int max) {
		return codes != null && !codes.isEmpty() && codes.size() <= max
				&& codes.stream().allMatch(c -> c != null && c.matches(ConsentPatterns.PURPOSE_CODE))
				&& new HashSet<>(codes).size() == codes.size();
	}

	/** @return true when the codes, joined with commas, fit {@code cm_access_grant.purposes} (255) */
	public static boolean fitColumn(List<String> codes) {
		return codes == null || String.join(",", codes.stream().map(Objects::toString).toList())
				.length() <= 255;
	}
}
