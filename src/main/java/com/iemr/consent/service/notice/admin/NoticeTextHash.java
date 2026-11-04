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

package com.iemr.consent.service.notice.admin;

import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.iemr.consent.data.notice.NoticeTranslation;
import com.iemr.consent.utils.crypto.Sha256;

/**
 * The {@code text_hash} of a notice translation (feature 006, research R10): SHA-256 of the six text sections joined
 * with a newline, skipping empty (null) sections, exactly like MySQL {@code SHA2(CONCAT_WS('\n', ...), 256)} used
 * by the demo seed. API 7 compares the client's hash with it.
 */
public final class NoticeTextHash {

	private NoticeTextHash() {
	}

	public static String of(NoticeTranslation t) {
		return Sha256.hex(Stream.of(t.getBody(), t.getDataItems(), t.getRetention(), t.getRecipients(),
				t.getWithdrawalInfo(), t.getGrievanceInfo()).filter(Objects::nonNull)
				.collect(Collectors.joining("\n")));
	}
}
