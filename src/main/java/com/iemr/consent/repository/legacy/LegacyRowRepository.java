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

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.iemr.consent.data.legacy.LegacyRow;
import com.iemr.consent.data.legacy.LegacyRowStatus;

/** Rows of legacy consent files ({@code cm_legacy_row}, feature 008). */
@Repository
public interface LegacyRowRepository extends JpaRepository<LegacyRow, Long> {

	List<LegacyRow> findByBatchIdOrderByRowNoAsc(String batchId);

	/** Rows of these patients in one status: the IMPORTED ones give {@code ALREADY_IMPORTED} on a check. */
	List<LegacyRow> findByBeneficiaryRegIdInAndStatus(Collection<Long> beneficiaryRegIds, LegacyRowStatus status);

	/** Whether this patient's legacy consent of that date was imported (by another batch, meanwhile). */
	boolean existsByBeneficiaryRegIdAndConsentDateAndStatus(Long beneficiaryRegId, LocalDate consentDate,
			LegacyRowStatus status);
}
