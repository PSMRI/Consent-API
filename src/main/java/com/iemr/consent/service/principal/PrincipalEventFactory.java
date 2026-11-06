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

package com.iemr.consent.service.principal;

import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeSet;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.iemr.consent.data.consent.Channel;
import com.iemr.consent.data.consent.Consent;
import com.iemr.consent.data.consent.ConsentMode;
import com.iemr.consent.data.consent.ConsentPurpose;
import com.iemr.consent.data.consent.ConsentingParty;
import com.iemr.consent.data.consent.Decision;
import com.iemr.consent.data.consent.EventType;
import com.iemr.consent.data.consent.GuardianLink;
import com.iemr.consent.data.consent.PurposeStatus;
import com.iemr.consent.data.consent.SyncStatus;
import com.iemr.consent.model.principal.PrincipalChange;
import com.iemr.consent.repository.consent.ConsentPurposeRepository;
import com.iemr.consent.repository.consent.ConsentRepository;
import com.iemr.consent.repository.consent.GuardianLinkRepository;
import com.iemr.consent.utils.auth.AuthenticatedUser;
import com.iemr.consent.utils.crypto.FieldEncryptor;
import com.iemr.consent.utils.crypto.Sha256;
import com.iemr.consent.utils.exception.ConsentApiException;
import com.iemr.consent.utils.exception.ErrorCode;

/**
 * Shared steps of a preference change or withdrawal (research R1, R3): lock the current decisions and
 * check their versions, find the source event, write the new event and replace a decision with a new
 * version. The caller's transaction holds the locks.
 */
@Component
public class PrincipalEventFactory {

	/** Device id recorded when the request carries none. */
	static final String DEFAULT_DEVICE = "PREFERENCE_CENTRE";

	private final ConsentRepository consentRepository;

	private final ConsentPurposeRepository purposeRepository;

	private final GuardianLinkRepository guardianLinkRepository;

	private final FieldEncryptor encryptor;

	private final Clock clock;

	public PrincipalEventFactory(ConsentRepository consentRepository, ConsentPurposeRepository purposeRepository,
			GuardianLinkRepository guardianLinkRepository, FieldEncryptor encryptor, Clock clock) {
		this.consentRepository = consentRepository;
		this.purposeRepository = purposeRepository;
		this.guardianLinkRepository = guardianLinkRepository;
		this.encryptor = encryptor;
		this.clock = clock;
	}

	/**
	 * Locks the current decision of each purpose, in code order so two requests cannot deadlock, then checks
	 * the versions.
	 *
	 * @return current decisions by code, in code order
	 * @throws ConsentApiException {@code BUSINESS_RULE_VIOLATION} when a purpose has no current decision;
	 *                             {@code STALE_VERSION} when a version is missing or not the current one
	 */
	public Map<String, ConsentPurpose> lockCurrent(Long benRegId, Collection<String> codes,
			Map<String, Integer> versions) {
		Map<String, ConsentPurpose> rows = new LinkedHashMap<>();
		for (String code : new TreeSet<>(codes)) {
			rows.put(code, purposeRepository.findCurrent(benRegId, code)
					.orElseThrow(() -> new ConsentApiException(ErrorCode.BUSINESS_RULE_VIOLATION,
							"No consent is recorded for this purpose", code, null)));
		}
		boolean stale = rows.values().stream()
				.anyMatch(r -> !Objects.equals(versions.get(r.getPurposeCode()), r.getVersionNo()));
		if (stale) {
			throw new ConsentApiException(ErrorCode.STALE_VERSION);
		}
		return rows;
	}

	/** @return the event of the newest of the given decisions (the one the change is recorded against) */
	public Consent newestSource(Collection<ConsentPurpose> rows) {
		return consentRepository.findByConsentIdIn(rows.stream().map(ConsentPurpose::getConsentId).toList())
				.stream().max(Comparator.comparing(Consent::getServerReceivedAt))
				.orElseThrow(() -> new IllegalStateException("Decision without its consent event"));
	}

	/** @return the guardian link of a GUARDIAN event while it is still valid */
	public Optional<GuardianLink> activeGuardian(Consent source) {
		if (source.getConsentingParty() != ConsentingParty.GUARDIAN) {
			return Optional.empty();
		}
		Instant now = clock.instant();
		return guardianLinkRepository.findFirstByConsentIdAndDeletedFalse(source.getConsentId())
				.filter(link -> link.getValidUntil() == null || !link.getValidUntil().isBefore(now));
	}

	/** @return GUARDIAN while the source event's guardian link is valid, else SELF (R1) */
	public ConsentingParty partyFor(Consent source) {
		return activeGuardian(source).isPresent() ? ConsentingParty.GUARDIAN : ConsentingParty.SELF;
	}

	/**
	 * @return SHA-256 of the guardian's phone, for matching an OTP challenge (T9)
	 * @throws ConsentApiException {@code OTP_INVALID} when the guardian has no phone
	 */
	public String guardianPhoneHash(GuardianLink link) {
		if (link.getGuardianPhoneEncrypted() == null) {
			throw new ConsentApiException(ErrorCode.OTP_INVALID);
		}
		return Sha256.hex(encryptor.decrypt(link.getGuardianPhoneEncrypted()));
	}

	/** Saves the new event, copying notice, language and fingerprint from the source event (R1). */
	public Consent saveEvent(Long benRegId, EventType type, ConsentMode mode, Consent source, ConsentingParty party,
			PrincipalChange change, String requestKey, String requestHash, AuthenticatedUser user) {
		Instant now = clock.instant();
		Consent event = new Consent();
		event.setConsentId(UUID.randomUUID().toString());
		event.setBeneficiaryRegId(benRegId);
		event.setEventType(type);
		event.setConsentingParty(party);
		event.setNoticeVersionId(source.getNoticeVersionId());
		event.setLanguageCode(source.getLanguageCode());
		event.setTextHash(source.getTextHash());
		event.setChannel(Channel.PREFERENCE_CENTRE);
		event.setMode(mode);
		event.setCapturedBy(user.userId());
		event.setProviderServiceMapId(change.providerServiceMapId());
		event.setFacilityId(change.facilityId());
		event.setServiceLine(change.serviceLine());
		event.setDeviceId(change.deviceId() != null ? change.deviceId() : DEFAULT_DEVICE);
		event.setCapturedAt(now);
		event.setServerReceivedAt(now);
		event.setSyncStatus(SyncStatus.SYNCED);
		event.setRequestKey(requestKey);
		event.setRequestHash(requestHash);
		return consentRepository.saveAndFlush(event);
	}

	/** Marks the current decision SUPERSEDED and saves its successor at the next version. */
	public ConsentPurpose replace(ConsentPurpose previous, Consent event, PurposeStatus status,
			Instant validUntil) {
		previous.setCurrent(false);
		previous.setStatus(PurposeStatus.SUPERSEDED);
		purposeRepository.saveAndFlush(previous);
		ConsentPurpose row = new ConsentPurpose();
		row.setConsentId(event.getConsentId());
		row.setBeneficiaryRegId(event.getBeneficiaryRegId());
		row.setPurposeCode(previous.getPurposeCode());
		row.setDecision(status == PurposeStatus.GRANTED ? Decision.GRANT : Decision.DENY);
		row.setStatus(status);
		row.setVersionNo(previous.getVersionNo() + 1);
		row.setCurrent(true);
		row.setValidUntil(validUntil);
		return purposeRepository.save(row);
	}
}
