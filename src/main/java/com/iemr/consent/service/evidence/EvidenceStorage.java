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

package com.iemr.consent.service.evidence;

/** Protected file storage for proof files; never the database (constitution IX, research R2). */
public interface EvidenceStorage {

	/** Stores the file and returns its storage reference. */
	String store(String evidenceId, byte[] content);

	/** Reads a file back, checking it still matches its SHA-256 (hashes are verified on read). */
	byte[] read(String fileRef, String expectedSha256);

	/** Removes a file; used when the database row could not be saved. */
	void delete(String fileRef);
}
