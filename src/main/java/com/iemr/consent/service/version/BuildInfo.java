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

package com.iemr.consent.service.version;

/**
 * Build information returned by {@code GET /version} (FR-018); each value is
 * {@code "unknown"} when not available.
 *
 * @param version        {@code git.build.version}
 * @param buildTimestamp {@code git.build.time}
 * @param branch         {@code git.branch}
 * @param commitHash     {@code git.commit.id.abbrev}
 */
public record BuildInfo(String version, String buildTimestamp, String branch, String commitHash) {
}
