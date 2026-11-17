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

package com.iemr.consent.model.notice.admin;

import java.util.List;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Publishing options (A8, feature 006).
 *
 * @param requireReconsent {@code true} publishes a material version, so earlier consents need re-consent (C14);
 *                         {@code null} = {@code false}
 * @param translateTo      languages to machine-translate into; {@code null} = none, "en" is ignored
 */
public record PublishRequest(Boolean requireReconsent,
		@Size(max = 23) List<@NotNull @Pattern(regexp = "^[a-z]{2,3}$") String> translateTo) {
}
