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

package com.iemr.consent.service.audit;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.iemr.consent.data.audit.AuditLogEntry;
import com.iemr.consent.repository.audit.AuditLogRepository;
import com.iemr.consent.utils.crypto.Sha256;

/**
 * Hash-chained audit log (research R7). The last entry is read with a row lock, so concurrent
 * appends are serialised; the new {@code entry_hash} = SHA-256 of the previous hash and the
 * entry's content. It must run inside the transaction of the action it records
 * ({@code MANDATORY}), so either both are kept or neither.
 */
@Service
public class AuditLogServiceImpl implements AuditLogService {

	private final AuditLogRepository repository;

	private final Clock clock;

	public AuditLogServiceImpl(AuditLogRepository repository, Clock clock) {
		this.repository = repository;
		this.clock = clock;
	}

	@Override
	@Transactional(propagation = Propagation.MANDATORY)
	public void append(String action, String consentId, Long beneficiaryRegId, String purposeSummary,
			String status, Long initiator, String sourceIp) {
		String prevHash = repository.findTopByOrderByIdDesc().map(AuditLogEntry::getEntryHash)
				.orElseThrow(() -> new IllegalStateException("Audit log genesis row is missing"));
		AuditLogEntry entry = new AuditLogEntry();
		entry.setAction(action);
		entry.setConsentId(consentId);
		entry.setBeneficiaryRegId(beneficiaryRegId);
		entry.setPurpose(purposeSummary);
		entry.setStatus(status);
		entry.setInitiator(initiator);
		entry.setSourceIp(sourceIp);
		entry.setCreatedAt(clock.instant());
		entry.setPrevHash(prevHash);
		entry.setEntryHash(entryHash(prevHash, entry));
		repository.save(entry);
	}

	/** SHA-256 of {@code prevHash} followed by the canonical content of the entry. */
	static String entryHash(String prevHash, AuditLogEntry entry) {
		Instant createdAt = entry.getCreatedAt();
		String content = String.join("|", entry.getAction(), Objects.toString(entry.getConsentId(), ""),
				Objects.toString(entry.getBeneficiaryRegId(), ""),
				Objects.toString(entry.getPurpose(), ""), Objects.toString(entry.getStatus(), ""),
				Objects.toString(entry.getInitiator(), ""), Objects.toString(entry.getSourceIp(), ""),
				createdAt.toString());
		return Sha256.hex(prevHash + content);
	}
}
