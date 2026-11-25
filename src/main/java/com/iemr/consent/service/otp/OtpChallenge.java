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

package com.iemr.consent.service.otp;

import java.time.Instant;

import com.iemr.consent.model.otp.OtpReason;

/**
 * An OTP challenge held in Redis (data-model.md). The phone is kept only encrypted, as a SHA-256
 * hash and masked; the OTP itself is never stored.
 *
 * @param challengeId id
 * @param reason      CONSENT, GUARDIAN or ACCESS_GRANT
 * @param phoneEnc    {@code FieldEncryptor} output, needed to verify with Common-API
 * @param phoneHash   SHA-256 of the phone, compared with a guardian's phone at save
 * @param maskedPhone e.g. {@code ******3210}
 * @param refId       optional reference
 * @param createdBy   user who sent it
 * @param expiresAt   code expiry
 * @param verifiedAt  set once by a correct verify
 * @param used        set by the save that uses it
 * @param attempts    wrong codes so far
 */
public record OtpChallenge(String challengeId, OtpReason reason, String phoneEnc, String phoneHash,
		String maskedPhone, String refId, Long createdBy, Instant expiresAt, Instant verifiedAt, boolean used,
		int attempts) {

	public OtpChallenge verified(Instant at) {
		return new OtpChallenge(challengeId, reason, phoneEnc, phoneHash, maskedPhone, refId, createdBy,
				expiresAt, at, used, attempts);
	}

	public OtpChallenge markedUsed() {
		return new OtpChallenge(challengeId, reason, phoneEnc, phoneHash, maskedPhone, refId, createdBy,
				expiresAt, verifiedAt, true, attempts);
	}

	public OtpChallenge failedAttempt() {
		return new OtpChallenge(challengeId, reason, phoneEnc, phoneHash, maskedPhone, refId, createdBy,
				expiresAt, verifiedAt, used, attempts + 1);
	}

	@Override
	public String toString() {
		return "OtpChallenge[challengeId=" + challengeId + ", reason=" + reason + "]";
	}
}
