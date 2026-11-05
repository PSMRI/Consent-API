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

package com.iemr.consent.repository.legacy;

import java.time.Instant;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.iemr.consent.data.legacy.LegacyBatch;

/** Legacy consent files ({@code cm_legacy_batch}, feature 008). */
@Repository
public interface LegacyBatchRepository extends JpaRepository<LegacyBatch, String> {

	/** An earlier check by the same user with the same {@code Idempotency-Key} (A9 replay). */
	Optional<LegacyBatch> findByUploadedByUserIdAndRequestKey(Long uploadedByUserId, String requestKey);

	/**
	 * Claims a checked, unexpired batch for import (A10, research R5); 0 means it is unknown, expired or already
	 * imported, so two imports of one batch can never both run.
	 */
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("update LegacyBatch b set b.status = com.iemr.consent.data.legacy.LegacyBatchStatus.IMPORTED,"
			+ " b.importedBy = :importedBy, b.importedAt = :importedAt, b.importRequestKey = :importKey,"
			+ " b.importRequestHash = :importHash, b.modifiedBy = :importedBy where b.batchId = :batchId"
			+ " and b.status = com.iemr.consent.data.legacy.LegacyBatchStatus.CHECKED"
			+ " and b.expiresAt > :now and b.deleted = false")
	int claimForImport(@Param("batchId") String batchId, @Param("importedBy") String importedBy,
			@Param("importedAt") Instant importedAt, @Param("importKey") String importKey,
			@Param("importHash") String importHash, @Param("now") Instant now);
}
