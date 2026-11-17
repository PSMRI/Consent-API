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

package com.iemr.consent.utils.idempotency;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.iemr.consent.utils.crypto.Sha256;

/**
 * SHA-256 of a request's canonical JSON (sorted properties and map keys), stored with the
 * request key so a repeat with a different body is detected (research R4).
 */
@Component
public class RequestHasher {

	private final ObjectMapper canonical;

	public RequestHasher(ObjectMapper objectMapper) {
		this.canonical = objectMapper.copy().configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true)
				.configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
	}

	public String hash(Object request) {
		try {
			return Sha256.hex(canonical.writeValueAsBytes(request));
		} catch (JsonProcessingException e) {
			throw new IllegalStateException("Request could not be hashed", e);
		}
	}
}
