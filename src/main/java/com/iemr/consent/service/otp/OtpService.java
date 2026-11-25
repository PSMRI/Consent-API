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

import java.util.Set;

import com.iemr.consent.model.otp.OtpChallengeView;
import com.iemr.consent.model.otp.OtpReason;
import com.iemr.consent.model.otp.OtpSendRequest;
import com.iemr.consent.model.otp.OtpVerifyResult;
import com.iemr.consent.utils.auth.AuthenticatedUser;
import com.iemr.consent.utils.http.ForwardedAuth;

/** OTP challenges for consent and guardian proof (FR-004 – FR-006). */
public interface OtpService {

	OtpChallengeView send(OtpSendRequest request, String requestKey, String requestHash, AuthenticatedUser user,
			ForwardedAuth auth);

	OtpVerifyResult verify(String challengeId, String otp, AuthenticatedUser user, ForwardedAuth auth);

	/** A verified, unused challenge of this reason created by this user; else {@code OTP_INVALID}. */
	OtpChallenge requireUsable(String challengeId, OtpReason reason, AuthenticatedUser user);

	/**
	 * A verified, unused challenge created by this user, with one of {@code reasons}, a {@code refId} equal to
	 * {@code refId} and, when {@code expectedPhoneHash} is not null, that phone; else {@code OTP_INVALID}.
	 */
	OtpChallenge requireUsableFor(String challengeId, Set<OtpReason> reasons, String refId, AuthenticatedUser user,
			String expectedPhoneHash);

	void markUsed(String challengeId);
}
