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

import java.time.Clock;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.iemr.consent.config.ConsentProperties;
import com.iemr.consent.model.otp.OtpChallengeView;
import com.iemr.consent.model.otp.OtpReason;
import com.iemr.consent.model.otp.OtpSendRequest;
import com.iemr.consent.model.otp.OtpVerifyResult;
import com.iemr.consent.service.otp.OtpChallengeStore.SendReplay;
import com.iemr.consent.utils.auth.AuthenticatedUser;
import com.iemr.consent.utils.crypto.FieldEncryptor;
import com.iemr.consent.utils.crypto.PhoneMasker;
import com.iemr.consent.utils.crypto.Sha256;
import com.iemr.consent.utils.exception.ConsentApiException;
import com.iemr.consent.utils.exception.ErrorCode;
import com.iemr.consent.utils.http.ForwardedAuth;

/**
 * OTP challenges wrapping Common-API's consent OTP (research R1).
 *
 * <ul>
 * <li>Send: a repeat with the same request key replays the first answer and sends no second SMS
 * (C18).</li>
 * <li>Verify: the code expires after {@code consent.otp.ttl-seconds} ({@code OTP_EXPIRED}); after
 * {@code consent.otp.max-attempts} wrong codes the challenge is locked ({@code RATE_LIMITED})
 * without calling Common-API; a challenge is verified at most once.</li>
 * <li>A challenge belongs to the user who sent it; others get {@code NOT_FOUND}.</li>
 * </ul>
 */
@Service
public class OtpServiceImpl implements OtpService {

	private final OtpChallengeStore store;

	private final CommonApiOtpClient client;

	private final FieldEncryptor encryptor;

	private final Clock clock;

	private final ConsentProperties.Otp settings;

	public OtpServiceImpl(OtpChallengeStore store, CommonApiOtpClient client, FieldEncryptor encryptor, Clock clock,
			ConsentProperties properties) {
		this.store = store;
		this.client = client;
		this.encryptor = encryptor;
		this.clock = clock;
		this.settings = properties.otp();
	}

	@Override
	public OtpChallengeView send(OtpSendRequest request, String requestKey, String requestHash,
			AuthenticatedUser user, ForwardedAuth auth) {
		SendReplay replay = store.findSendReplay(user.userId(), requestKey).orElse(null);
		if (replay != null) {
			if (!replay.requestHash().equals(requestHash)) {
				throw new ConsentApiException(ErrorCode.STATE_CONFLICT);
			}
			return replay.response();
		}
		String phone = request.phone();
		OtpChallenge challenge = new OtpChallenge(UUID.randomUUID().toString(), request.purpose(),
				encryptor.encrypt(phone), Sha256.hex(phone), PhoneMasker.mask(phone), request.refId(),
				user.userId(), clock.instant().plusSeconds(settings.ttlSeconds()), null, false, 0);
		store.create(challenge);
		client.send(phone, auth);
		OtpChallengeView view = new OtpChallengeView(challenge.challengeId(), challenge.maskedPhone(),
				challenge.expiresAt());
		store.saveSendReplay(user.userId(), requestKey, new SendReplay(requestHash, view));
		return view;
	}

	@Override
	public OtpVerifyResult verify(String challengeId, String otp, AuthenticatedUser user, ForwardedAuth auth) {
		OtpChallenge challenge = owned(challengeId, user);
		if (challenge.verifiedAt() != null || challenge.used()) {
			throw new ConsentApiException(ErrorCode.STATE_CONFLICT);
		}
		if (challenge.attempts() >= settings.maxAttempts()) {
			throw new ConsentApiException(ErrorCode.RATE_LIMITED);
		}
		Instant now = clock.instant();
		if (now.isAfter(challenge.expiresAt())) {
			throw new ConsentApiException(ErrorCode.OTP_EXPIRED);
		}
		boolean verified = client.validate(encryptor.decrypt(challenge.phoneEnc()), otp, auth);
		store.update(verified ? challenge.verified(now) : challenge.failedAttempt());
		return new OtpVerifyResult(verified);
	}

	@Override
	public OtpChallenge requireUsable(String challengeId, OtpReason reason, AuthenticatedUser user) {
		OtpChallenge challenge = store.find(challengeId)
				.orElseThrow(() -> new ConsentApiException(ErrorCode.NOT_FOUND));
		boolean usable = challenge.reason() == reason && challenge.verifiedAt() != null && !challenge.used()
				&& challenge.createdBy().equals(user.userId());
		if (!usable) {
			throw new ConsentApiException(ErrorCode.OTP_INVALID);
		}
		return challenge;
	}

	@Override
	public OtpChallenge requireUsableFor(String challengeId, Set<OtpReason> reasons, String refId,
			AuthenticatedUser user, String expectedPhoneHash) {
		OtpChallenge challenge = store.find(challengeId)
				.orElseThrow(() -> new ConsentApiException(ErrorCode.OTP_INVALID));
		boolean usable = reasons.contains(challenge.reason()) && challenge.verifiedAt() != null
				&& !challenge.used() && challenge.createdBy().equals(user.userId())
				&& refId.equals(challenge.refId())
				&& (expectedPhoneHash == null || expectedPhoneHash.equals(challenge.phoneHash()));
		if (!usable) {
			throw new ConsentApiException(ErrorCode.OTP_INVALID);
		}
		return challenge;
	}

	@Override
	public void markUsed(String challengeId) {
		store.find(challengeId).ifPresent(challenge -> store.update(challenge.markedUsed()));
	}

	private OtpChallenge owned(String challengeId, AuthenticatedUser user) {
		return store.find(challengeId).filter(c -> c.createdBy().equals(user.userId()))
				.orElseThrow(() -> new ConsentApiException(ErrorCode.NOT_FOUND));
	}
}
