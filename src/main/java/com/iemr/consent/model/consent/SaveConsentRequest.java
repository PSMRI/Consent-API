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

package com.iemr.consent.model.consent;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.iemr.consent.data.consent.Channel;
import com.iemr.consent.data.consent.ConsentMode;
import com.iemr.consent.data.consent.ConsentingParty;
import com.iemr.consent.data.consent.EventType;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Save consent (front-end API 7, contract {@code SaveConsentRequest}). {@code capturedBy} is
 * accepted for compatibility but ignored: the logged-in user is recorded (FR-015).
 *
 * @param beneficiaryRegId     patient, or {@code null} before registration (stored unlinked)
 * @param eventType            NEW, UPDATE or RENEWAL
 * @param consentingParty      SELF or GUARDIAN
 * @param noticeVersionId      from the active notice
 * @param languageCode         language shown
 * @param textHash             fingerprint of the text shown
 * @param channel              FACILITY
 * @param mode                 how consent was taken; {@code null} only when every decision is DENY
 * @param capturedBy           ignored
 * @param providerServiceMapId context
 * @param facilityId           context
 * @param servicePointId       context
 * @param serviceLine          e.g. {@code MMU}
 * @param deviceId             capturing device
 * @param capturedAt           device time of capture
 * @param decisions            one per notice purpose
 * @param evidenceIds          uploaded proof files
 * @param proof                mode proof details
 * @param guardian             for GUARDIAN
 * @param projectId            configuration in force (deviation 2)
 * @param configVersion        configuration version
 * @param declaration          declaration agreed to; required on an accept, absent on a decline (feature 004)
 * @param notifyRequest        JSON {@code notify}: where to send the Consent ID by SMS, or {@code null} (004)
 */
public record SaveConsentRequest(Long beneficiaryRegId, @NotNull EventType eventType,
		@NotNull ConsentingParty consentingParty, @NotNull Long noticeVersionId,
		@NotNull @Pattern(regexp = "^[a-z]{2,3}$") String languageCode,
		@NotNull @Pattern(regexp = "^[0-9a-f]{64}$") String textHash, @NotNull Channel channel,
		ConsentMode mode,
		Long capturedBy, Integer providerServiceMapId, Integer facilityId, Integer servicePointId,
		@Size(max = 30) @Pattern(regexp = ConsentPatterns.CODE) String serviceLine,
		@NotBlank @Size(max = 100) @Pattern(regexp = ConsentPatterns.CODE) String deviceId,
		@NotNull Instant capturedAt, @NotEmpty List<@Valid @NotNull DecisionItem> decisions,
		List<@NotNull UUID> evidenceIds, @Valid Proof proof, @Valid GuardianInfo guardian, Integer projectId,
		Integer configVersion, @Valid DeclarationInput declaration,
		@JsonProperty("notify") @Valid NotifyInput notifyRequest) {

	@Override
	public String toString() {
		return "SaveConsentRequest[noticeVersionId=" + noticeVersionId + ", mode=" + mode + "]";
	}
}
