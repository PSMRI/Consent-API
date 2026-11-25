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

package com.iemr.consent.repository.consent;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.iemr.consent.data.consent.ConsentPurpose;

import jakarta.persistence.LockModeType;

/** Purpose decisions ({@code cm_consent_purpose}). */
@Repository
public interface ConsentPurposeRepository extends JpaRepository<ConsentPurpose, Long> {

	List<ConsentPurpose> findByConsentIdOrderByConsentPurposeIdAsc(String consentId);

	List<ConsentPurpose> findByConsentIdInOrderByConsentPurposeIdAsc(Collection<String> consentIds);

	/** A patient's current decisions, one per purpose (not locked; reads only). */
	List<ConsentPurpose> findByBeneficiaryRegIdAndCurrentTrue(Long beneficiaryRegId);

	/** The current decisions of many patients in one query (API 15 batch, feature 007). */
	List<ConsentPurpose> findByBeneficiaryRegIdInAndCurrentTrue(Collection<Long> beneficiaryRegIds);

	/** The current decision for a patient and purpose, locked until the transaction ends. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select p from ConsentPurpose p where p.beneficiaryRegId = :ben and p.purposeCode = :code"
			+ " and p.current = true")
	Optional<ConsentPurpose> findCurrent(@Param("ben") Long beneficiaryRegId, @Param("code") String purposeCode);
}
