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

package com.iemr.consent.repository.grant;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.iemr.consent.data.grant.AccessGrant;
import com.iemr.consent.data.grant.GrantStatus;

import jakarta.persistence.LockModeType;

/** Access grants ({@code cm_access_grant}). */
@Repository
public interface AccessGrantRepository extends JpaRepository<AccessGrant, String> {

	List<AccessGrant> findByBeneficiaryRegIdAndDeletedFalseOrderByCreatedDateDesc(Long beneficiaryRegId);

	Optional<AccessGrant> findByRequestedByAndRequestKey(Long requestedBy, String requestKey);

	/** One grant, locked until the transaction ends (approve, revoke). */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select g from AccessGrant g where g.grantId = :id and g.deleted = false")
	Optional<AccessGrant> findLockedByGrantId(@Param("id") String grantId);

	/** A patient's grants in the given stored statuses, locked (withdrawal cascade). */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select g from AccessGrant g where g.beneficiaryRegId = :ben and g.status in :statuses"
			+ " and g.deleted = false")
	List<AccessGrant> findLockedByBeneficiaryRegIdAndStatusIn(@Param("ben") Long beneficiaryRegId,
			@Param("statuses") Collection<GrantStatus> statuses);
}
