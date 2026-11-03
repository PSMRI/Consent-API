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

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.iemr.consent.data.evidence.ConsentEvidence;

/** Evidence ({@code cm_consent_evidence}). */
@Repository
public interface ConsentEvidenceRepository extends JpaRepository<ConsentEvidence, String> {

	Optional<ConsentEvidence> findByUploadedByAndRequestKey(Long uploadedBy, String requestKey);

	/**
	 * Links evidence to a consent only if it is still unlinked.
	 *
	 * @return 1 when linked, 0 when another consent linked it first
	 */
	@Modifying
	@Query("update ConsentEvidence e set e.consentId = :consentId where e.evidenceId = :evidenceId"
			+ " and e.consentId is null")
	int linkToConsent(@Param("evidenceId") String evidenceId, @Param("consentId") String consentId);
}
