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

package com.iemr.consent.repository.evidence;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.iemr.consent.data.evidence.ConsentEvidence;
import com.iemr.consent.data.evidence.EvidenceType;

/** Evidence ({@code cm_consent_evidence}). */
@Repository
public interface ConsentEvidenceRepository extends JpaRepository<ConsentEvidence, String> {

	Optional<ConsentEvidence> findByUploadedByAndRequestKey(Long uploadedBy, String requestKey);

	/** Evidence linked to a consent and not purged, oldest first (evidence retrieval). */
	List<ConsentEvidence> findByConsentIdAndDeletedFalseOrderByCreatedAtAsc(String consentId);

	/**
	 * Links evidence to a consent only if it is still unlinked and not removed by the clean-up job.
	 *
	 * @return 1 when linked, 0 when another consent linked it first or it was purged
	 */
	@Modifying
	@Query("update ConsentEvidence e set e.consentId = :consentId where e.evidenceId = :evidenceId"
			+ " and e.consentId is null and e.deleted = false")
	int linkToConsent(@Param("evidenceId") String evidenceId, @Param("consentId") String consentId);

	/** Uploaded files no consent has linked, older than {@code cutoff}, oldest first (feature 004, R8). */
	@Query("select e from ConsentEvidence e where e.consentId is null and e.deleted = false"
			+ " and e.createdAt < :cutoff and e.type in :types order by e.createdAt")
	List<ConsentEvidence> findPurgeCandidates(@Param("cutoff") Instant cutoff,
			@Param("types") Collection<EvidenceType> types, Pageable page);

	/**
	 * Soft-deletes unlinked evidence; safe on several nodes and against a save linking it at the same time.
	 *
	 * @return 1 when this call removed it, 0 when it was linked or removed meanwhile
	 */
	@Modifying
	@Query("update ConsentEvidence e set e.deleted = true, e.modifiedBy = :by where e.evidenceId = :evidenceId"
			+ " and e.consentId is null and e.deleted = false")
	int markPurged(@Param("evidenceId") String evidenceId, @Param("by") String modifiedBy);
}
