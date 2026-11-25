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

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.iemr.consent.data.consent.Channel;
import com.iemr.consent.data.consent.ConsentMode;
import com.iemr.consent.data.consent.ConsentingParty;
import com.iemr.consent.data.consent.Decision;
import com.iemr.consent.data.consent.DeclarationWording;
import com.iemr.consent.data.consent.EventType;
import com.iemr.consent.data.consent.GuardianType;
import com.iemr.consent.data.consent.GuardianVerification;
import com.iemr.consent.data.consent.PurposeStatus;
import com.iemr.consent.data.consent.SyncStatus;
import com.iemr.consent.data.evidence.EvidenceType;
import com.iemr.consent.data.notice.NoticeContext;

/**
 * One consent event in full (front-end API 17, {@code GET /consent/v1/consents/{consentId}}): the patient, what
 * they were told (the notice in the language shown), what they decided per purpose, who consented, how it was
 * captured and proved, and the confirmation SMS. Phones are masked; guardian and witness names are decrypted.
 *
 * @param consentId        the consent id
 * @param beneficiaryRegId the patient
 * @param eventType        NEW, UPDATE, RENEWAL, WITHDRAWAL, LEGACY
 * @param consentingParty  SELF or a representative
 * @param channel          where it was captured
 * @param mode             how it was captured
 * @param languageCode     language the notice was shown in
 * @param textHash         SHA-256 of the notice text shown
 * @param capture          who, when and where
 * @param notice           the notice shown; null if it no longer exists
 * @param purposes         one decision per purpose, in code order
 * @param guardian         the representative; null when the patient consented
 * @param proofs           all proof rows (files, OTP, witness, declaration), oldest first
 * @param notification     the confirmation SMS
 * @param withdrawal       why it was withdrawn; null unless this is a WITHDRAWAL event
 */
public record ConsentDetail(String consentId, Long beneficiaryRegId, EventType eventType,
		ConsentingParty consentingParty, Channel channel, ConsentMode mode, String languageCode,
		String textHash, Capture capture, NoticeShown notice, List<PurposeDecision> purposes, Guardian guardian,
		List<Proof> proofs, NotificationResult notification, Withdrawal withdrawal) {

	/** Why the patient withdrew: the reason code chosen and the remarks (decrypted). */
	public record Withdrawal(String reason, String remarks) {
	}

	/** Who captured the consent, when and where. */
	public record Capture(Long capturedBy, Instant capturedAt, Instant serverReceivedAt,
			boolean captureTimeFlagged, Integer providerServiceMapId, Integer projectId,
			Integer facilityId, Integer servicePointId, String serviceLine, String deviceId,
			String callId, SyncStatus syncStatus) {
	}

	/** The notice version shown and, in the language shown, the information it described. */
	public record NoticeShown(Long noticeVersionId, String noticeCode, Integer versionNo, NoticeContext context,
			Integer projectId, Instant publishedAt, String languageName, String body, String dataItems,
			String recipients, String retention, String withdrawalInfo, String grievanceInfo) {
	}

	/**
	 * The decision for one purpose. {@code status} is as of now (an expired grant shows EXPIRED); {@code current}
	 * is false once a later event replaced it.
	 */
	public record PurposeDecision(String purposeCode, String name, String description, String legalBasis,
			boolean isMandatory, boolean isWithdrawable, Decision decision, PurposeStatus status,
			Integer versionNo, boolean current, Instant validUntil) {
	}

	/** The representative who consented for the patient. */
	public record Guardian(GuardianType guardianType, String name, String maskedPhone, String relationship,
			GuardianVerification verificationMethod, Instant verifiedAt, boolean followUpRequired,
			Instant validUntil) {
	}

	/**
	 * One proof row. Files ({@code isFile}) are played with
	 * {@code GET /consent/v1/evidence/{evidenceId}/content}.
	 */
	public record Proof(String evidenceId, EvidenceType type, boolean isFile, String contentType, Long sizeBytes,
			BigDecimal durationSec, String witnessName, String witnessRelationship, String otpStatus,
			String maskedPhone, String declarationText, DeclarationWording declarationWording,
			String createdBy,
			Instant createdAt) {
	}
}
