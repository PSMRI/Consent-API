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

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;

/**
 * The purposes a withdrawal names: either the JSON string {@code "ALL"} or a list of purpose codes
 * (front-end API 11).
 *
 * @param all   true for {@code "ALL"}
 * @param codes the named codes; empty when {@code all}
 */
@JsonDeserialize(using = PurposeSelection.Reader.class)
public record PurposeSelection(boolean all, List<String> codes) {

	public static final String ALL = "ALL";

	public static PurposeSelection everything() {
		return new PurposeSelection(true, List.of());
	}

	public static PurposeSelection of(List<String> codes) {
		return new PurposeSelection(false, codes);
	}

	/** Written back as it was read, so request hashes are stable. */
	@JsonValue
	public Object json() {
		return all ? ALL : codes;
	}

	/** Reads {@code "ALL"} or an array of strings; anything else is a malformed request. */
	static class Reader extends JsonDeserializer<PurposeSelection> {

		@Override
		public PurposeSelection deserialize(JsonParser parser,
				DeserializationContext context) throws IOException {
			if (parser.currentToken() == JsonToken.VALUE_STRING) {
				if (ALL.equals(parser.getText())) {
					return everything();
				}
				throw MismatchedInputException.from(parser, PurposeSelection.class,
						"purposeCodes must be a list of purpose codes or \"ALL\"");
			}
			return of(Arrays.asList(context.readValue(parser, String[].class)));
		}
	}
}
