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
import org.springframework.stereotype.Repository;

import com.iemr.consent.data.consent.Consent;
import com.iemr.consent.data.consent.EventType;

/** Consent events ({@code cm_consent}); insert-only in code. */
@Repository
public interface ConsentRepository extends JpaRepository<Consent, String> {

	Optional<Consent> findByCapturedByAndRequestKey(Long capturedBy, String requestKey);

	/** A patient's consent events, newest first (API 8 history). */
	List<Consent> findByBeneficiaryRegIdOrderByServerReceivedAtDesc(Long beneficiaryRegId);

	List<Consent> findByConsentIdIn(Collection<String> consentIds);

	/** Whether the patient has any consent event other than this type: a non-LEGACY one was taken in the app. */
	boolean existsByBeneficiaryRegIdAndEventTypeNot(Long beneficiaryRegId, EventType eventType);

	/** The patient's newest event of this type by capture time (the newest legacy consent, feature 008). */
	Optional<Consent> findFirstByBeneficiaryRegIdAndEventTypeOrderByCapturedAtDesc(Long beneficiaryRegId,
			EventType eventType);
}
