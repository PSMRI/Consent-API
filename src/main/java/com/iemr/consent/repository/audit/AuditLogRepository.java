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

package com.iemr.consent.repository.audit;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.stereotype.Repository;

import com.iemr.consent.data.audit.AuditLogEntry;

import jakarta.persistence.LockModeType;

/** The hash-chained audit log ({@code cm_audit_log}); insert-only in code. */
@Repository
public interface AuditLogRepository extends JpaRepository<AuditLogEntry, Long> {

	/** The last entry, locked so appends are serialised and the chain stays linear. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	Optional<AuditLogEntry> findTopByOrderByIdDesc();
}
