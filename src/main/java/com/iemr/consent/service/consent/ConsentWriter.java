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

package com.iemr.consent.service.consent;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.iemr.consent.config.ConsentProperties;
import com.iemr.consent.data.consent.Consent;
import com.iemr.consent.data.consent.ConsentPurpose;
import com.iemr.consent.data.consent.PurposeStatus;
import com.iemr.consent.data.consent.SyncStatus;
import com.iemr.consent.data.notice.Purpose;
import com.iemr.consent.model.consent.DecisionItem;
import com.iemr.consent.model.consent.PurposeResult;
import com.iemr.consent.model.consent.SaveConsentRequest;
import com.iemr.consent.model.consent.SaveConsentResult;
import com.iemr.consent.repository.consent.ConsentPurposeRepository;
import com.iemr.consent.repository.consent.ConsentRepository;
import com.iemr.consent.service.audit.AuditLogService;
import com.iemr.consent.utils.auth.AuthenticatedUser;

/**
 * Writes one validated save in a single transaction (FR-011, FR-013, research R6): the consent
 * event, one decision per purpose, the proof rows and the audit entry; if anything fails, nothing
 * is kept.
 *
 * <p>For a known patient, an earlier current decision for the same purpose is marked
 * {@code SUPERSEDED} and the new one gets the next version (plan deviation 6); the current row is
 * locked, so two simultaneous saves cannot both become current. A consent without a patient
 * (taken before registration) is stored unlinked.</p>
 */
@Component
public class ConsentWriter {

	private final ConsentRepository consentRepository;

	private final ConsentPurposeRepository purposeRepository;

	private final List<ConsentProofWriter> proofWriters;

	private final AuditLogService auditLogService;

	private final Clock clock;

	private final Duration maxSkew;

	public ConsentWriter(ConsentRepository consentRepository, ConsentPurposeRepository purposeRepository,
			List<ConsentProofWriter> proofWriters, AuditLogService auditLogService, Clock clock,
			ConsentProperties properties) {
		this.consentRepository = consentRepository;
		this.purposeRepository = purposeRepository;
		this.proofWriters = proofWriters;
		this.auditLogService = auditLogService;
		this.clock = clock;
		this.maxSkew = Duration.ofMinutes(properties.capture().maxSkewMinutes());
	}

	@Transactional
	public SaveConsentResult write(SaveConsentRequest request, ValidatedConsent validated, String requestKey,
			String requestHash, AuthenticatedUser user, String sourceIp) {
		Consent consent = consentRepository.saveAndFlush(newConsent(request, requestKey, requestHash, user));
		Map<String, Purpose> purposes = validated.notice().purposes().stream()
				.collect(Collectors.toMap(Purpose::getPurposeCode, Function.identity()));
		List<PurposeResult> results = new ArrayList<>();
		for (DecisionItem decision : request.decisions()) {
			results.add(writeDecision(consent, decision, purposes.get(decision.purposeCode())));
		}
		proofWriters.forEach(writer -> writer.write(consent, request, user));
		auditLogService.append(AuditLogService.CONSENT_CAPTURED, consent.getConsentId(),
				consent.getBeneficiaryRegId(), summary(results), overall(results), user.userId(),
				sourceIp);
		return new SaveConsentResult(consent.getConsentId(), consent.getSyncStatus(), results);
	}

	private PurposeResult writeDecision(Consent consent, DecisionItem decision, Purpose purpose) {
		int version = 1;
		Long patient = consent.getBeneficiaryRegId();
		if (patient != null) {
			ConsentPurpose previous = purposeRepository.findCurrent(patient, decision.purposeCode())
					.orElse(null);
			if (previous != null) {
				previous.setCurrent(false);
				previous.setStatus(PurposeStatus.SUPERSEDED);
				purposeRepository.saveAndFlush(previous);
				version = previous.getVersionNo() + 1;
			}
		}
		PurposeStatus status = ConsentResultMapper.statusOf(decision.decision());
		ConsentPurpose row = new ConsentPurpose();
		row.setConsentId(consent.getConsentId());
		row.setBeneficiaryRegId(patient);
		row.setPurposeCode(decision.purposeCode());
		row.setDecision(decision.decision());
		row.setStatus(status);
		row.setVersionNo(version);
		row.setCurrent(true);
		if (status == PurposeStatus.GRANTED && purpose.getValidityDays() != null) {
			row.setValidUntil(consent.getCapturedAt().plus(Duration.ofDays(purpose.getValidityDays())));
		}
		purposeRepository.save(row);
		return new PurposeResult(decision.purposeCode(), status, version);
	}

	private Consent newConsent(SaveConsentRequest request, String requestKey, String requestHash,
			AuthenticatedUser user) {
		Instant now = clock.instant();
		Consent consent = new Consent();
		consent.setConsentId(UUID.randomUUID().toString());
		consent.setBeneficiaryRegId(request.beneficiaryRegId());
		consent.setEventType(request.eventType());
		consent.setConsentingParty(request.consentingParty());
		consent.setNoticeVersionId(request.noticeVersionId());
		consent.setLanguageCode(request.languageCode());
		consent.setTextHash(request.textHash());
		consent.setChannel(request.channel());
		consent.setMode(request.mode());
		consent.setCapturedBy(user.userId());
		consent.setProviderServiceMapId(request.providerServiceMapId());
		consent.setFacilityId(request.facilityId());
		consent.setServicePointId(request.servicePointId());
		consent.setServiceLine(request.serviceLine());
		consent.setDeviceId(request.deviceId());
		consent.setCapturedAt(request.capturedAt());
		consent.setServerReceivedAt(now);
		consent.setCaptureTimeFlagged(Duration.between(request.capturedAt(), now).abs().compareTo(maxSkew) > 0);
		consent.setSyncStatus(SyncStatus.SYNCED);
		consent.setProjectId(request.projectId());
		consent.setConfigVersion(request.configVersion());
		consent.setRequestKey(requestKey);
		consent.setRequestHash(requestHash);
		return consent;
	}

	private static String summary(List<PurposeResult> results) {
		return results.stream().map(r -> r.purposeCode() + ":" + r.status()).collect(Collectors.joining(","));
	}

	private static String overall(List<PurposeResult> results) {
		long granted = results.stream().filter(r -> r.status() == PurposeStatus.GRANTED).count();
		if (granted == 0) {
			return PurposeStatus.DENIED.name();
		}
		return granted == results.size() ? PurposeStatus.GRANTED.name() : "PARTIAL";
	}
}
