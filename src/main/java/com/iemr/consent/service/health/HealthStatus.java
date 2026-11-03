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

package com.iemr.consent.service.health;

import java.util.Map;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * Result of a health check (data-model.md): overall {@code UP} only when every component is
 * {@code UP}. Holds no host, port, URL or exception text.
 *
 * @param status     {@code UP} or {@code DOWN}
 * @param checkedAt  ISO-8601 UTC time of the check
 * @param components {@code database} and {@code cache}, in that order
 */
public record HealthStatus(String status, String checkedAt, Map<String, ComponentStatus> components) {

	public static final String UP = "UP";

	public static final String DOWN = "DOWN";

	/** @param status {@code UP} or {@code DOWN} */
	public record ComponentStatus(String status) {
	}

	@JsonIgnore
	public boolean isUp() {
		return UP.equals(status);
	}

	/** Names of the components that are down, comma-separated, e.g. {@code database, cache}. */
	public String downComponents() {
		return components.entrySet().stream().filter(entry -> !UP.equals(entry.getValue().status()))
				.map(Map.Entry::getKey).collect(Collectors.joining(", "));
	}
}
