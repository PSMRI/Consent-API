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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.ArgumentCaptor;

import com.iemr.consent.model.otp.OtpChallengeView;
import com.iemr.consent.model.otp.OtpReason;
import com.iemr.consent.model.otp.OtpSendRequest;
import com.iemr.consent.service.otp.OtpChallengeStore.SendReplay;
import com.iemr.consent.support.ConsentFixtures;
import com.iemr.consent.support.LogCapture;
import com.iemr.consent.support.TestFixtures;
import com.iemr.consent.utils.auth.AuthenticatedUser;
import com.iemr.consent.utils.auth.ClientType;
import com.iemr.consent.utils.crypto.FieldEncryptor;
import com.iemr.consent.utils.crypto.Sha256;
import com.iemr.consent.utils.exception.ConsentApiException;
import com.iemr.consent.utils.exception.ErrorCode;
import com.iemr.consent.utils.http.ForwardedAuth;

class OtpServiceImplTest {

	private static final String PHONE = "9876543210";

	private static final ForwardedAuth AUTH = new ForwardedAuth("jwt", null, null);

	private static final AuthenticatedUser OTHER = new AuthenticatedUser(7L, "other", ClientType.WEB);

	@RegisterExtension
	final LogCapture logs = new LogCapture();

	private final FieldEncryptor encryptor = new FieldEncryptor(TestFixtures.consentProperties());

	private OtpChallengeStore store;

	private CommonApiOtpClient client;

	private OtpServiceImpl service;

	@BeforeEach
	void setUp() {
		store = mock(OtpChallengeStore.class);
		client = mock(CommonApiOtpClient.class);
		service = service(TestFixtures.CLOCK);
		when(store.findSendReplay(any(), anyString())).thenReturn(Optional.empty());
	}

	private OtpServiceImpl service(Clock clock) {
		return new OtpServiceImpl(store, client, encryptor, clock, TestFixtures.consentProperties());
	}

	private OtpChallenge challenge(Instant verifiedAt, boolean used, int attempts, OtpReason reason) {
		return new OtpChallenge("c1", reason, encryptor.encrypt(PHONE), Sha256.hex(PHONE), "******3210", null, 42L,
				TestFixtures.NOW.plusSeconds(300), verifiedAt, used, attempts);
	}

	private void stored(OtpChallenge challenge) {
		when(store.find("c1")).thenReturn(Optional.of(challenge));
	}

	private static void assertCode(Runnable call, ErrorCode code) {
		assertThatThrownBy(call::run).isInstanceOf(ConsentApiException.class).extracting("errorCode")
				.isEqualTo(code);
	}

	@Test
	void sendStoresAProtectedChallengeAndCallsCommonApi() {
		OtpChallengeView view = service.send(new OtpSendRequest(OtpReason.GUARDIAN, PHONE, null), "k", "h",
				ConsentFixtures.WORKER, AUTH);

		ArgumentCaptor<OtpChallenge> created = ArgumentCaptor.forClass(OtpChallenge.class);
		verify(store).create(created.capture());
		OtpChallenge challenge = created.getValue();
		assertThat(challenge.phoneEnc()).doesNotContain(PHONE);
		assertThat(encryptor.decrypt(challenge.phoneEnc())).isEqualTo(PHONE);
		assertThat(challenge.phoneHash()).isEqualTo(Sha256.hex(PHONE));
		assertThat(challenge.createdBy()).isEqualTo(42L);
		assertThat(view.maskedPhone()).isEqualTo("******3210");
		assertThat(view.expiresAt()).isEqualTo(TestFixtures.NOW.plusSeconds(300));
		verify(client).send(PHONE, AUTH);
		verify(store).saveSendReplay(42L, "k", new SendReplay("h", view));
		logs.assertNoneContain(PHONE);
	}

	@Test
	@DisplayName("C18: a repeated send replays the first answer and sends no second SMS")
	void sendReplay() {
		OtpChallengeView first = new OtpChallengeView("c1", "******3210", TestFixtures.NOW);
		when(store.findSendReplay(42L, "k")).thenReturn(Optional.of(new SendReplay("h", first)));

		assertThat(service.send(new OtpSendRequest(OtpReason.CONSENT, PHONE, null), "k", "h", ConsentFixtures.WORKER,
				AUTH)).isEqualTo(first);
		assertCode(() -> service.send(new OtpSendRequest(OtpReason.CONSENT, PHONE, null), "k", "other",
				ConsentFixtures.WORKER, AUTH), ErrorCode.STATE_CONFLICT);
		verify(client, never()).send(anyString(), any());
	}

	@Test
	void verifyRightAndWrongCodes() {
		OtpChallenge fresh = challenge(null, false, 0, OtpReason.CONSENT);
		stored(fresh);
		when(client.validate(PHONE, "123456", AUTH)).thenReturn(true);
		when(client.validate(PHONE, "000000", AUTH)).thenReturn(false);

		assertThat(service.verify("c1", "123456", ConsentFixtures.WORKER, AUTH).verified()).isTrue();
		verify(store).update(fresh.verified(TestFixtures.NOW));
		assertThat(service.verify("c1", "000000", ConsentFixtures.WORKER, AUTH).verified()).isFalse();
		verify(store).update(fresh.failedAttempt());
		logs.assertNoneContain("123456", PHONE);
	}

	@Test
	void verifyAfterExpiryIsOtpExpiredWithoutCallingCommonApi() {
		stored(challenge(null, false, 0, OtpReason.CONSENT));
		Clock late = Clock.fixed(TestFixtures.NOW.plus(Duration.ofMinutes(6)), ZoneOffset.UTC);

		assertCode(() -> service(late).verify("c1", "123456", ConsentFixtures.WORKER, AUTH), ErrorCode.OTP_EXPIRED);
		verify(client, never()).validate(anyString(), anyString(), any());
	}

	@Test
	void wrongCodeLimitLocksTheChallenge() {
		stored(challenge(null, false, 5, OtpReason.CONSENT));

		assertCode(() -> service.verify("c1", "123456", ConsentFixtures.WORKER, AUTH), ErrorCode.RATE_LIMITED);
		verify(client, never()).validate(anyString(), anyString(), any());
	}

	@Test
	void verifyErrors() {
		assertCode(() -> service.verify("c1", "123456", ConsentFixtures.WORKER, AUTH), ErrorCode.NOT_FOUND);
		stored(challenge(null, false, 0, OtpReason.CONSENT));
		assertCode(() -> service.verify("c1", "123456", OTHER, AUTH), ErrorCode.NOT_FOUND);
		stored(challenge(TestFixtures.NOW, false, 0, OtpReason.CONSENT));
		assertCode(() -> service.verify("c1", "123456", ConsentFixtures.WORKER, AUTH), ErrorCode.STATE_CONFLICT);
		stored(challenge(null, true, 0, OtpReason.CONSENT));
		assertCode(() -> service.verify("c1", "123456", ConsentFixtures.WORKER, AUTH), ErrorCode.STATE_CONFLICT);
	}

	@Test
	@DisplayName("A late save still finds a challenge verified near its expiry")
	void requireUsableAcceptsOnlyVerifiedUnusedOwnChallengesOfTheReason() {
		Clock tenMinutesLater = Clock.fixed(TestFixtures.NOW.plus(Duration.ofMinutes(10)), ZoneOffset.UTC);
		stored(challenge(TestFixtures.NOW, false, 0, OtpReason.GUARDIAN));
		assertThat(service(tenMinutesLater).requireUsable("c1", OtpReason.GUARDIAN, ConsentFixtures.WORKER)
				.challengeId()).isEqualTo("c1");

		assertCode(() -> service.requireUsable("c1", OtpReason.CONSENT, ConsentFixtures.WORKER),
				ErrorCode.OTP_INVALID);
		assertCode(() -> service.requireUsable("c1", OtpReason.GUARDIAN, OTHER), ErrorCode.OTP_INVALID);
		stored(challenge(null, false, 0, OtpReason.GUARDIAN));
		assertCode(() -> service.requireUsable("c1", OtpReason.GUARDIAN, ConsentFixtures.WORKER),
				ErrorCode.OTP_INVALID);
		stored(challenge(TestFixtures.NOW, true, 0, OtpReason.GUARDIAN));
		assertCode(() -> service.requireUsable("c1", OtpReason.GUARDIAN, ConsentFixtures.WORKER),
				ErrorCode.OTP_INVALID);
		assertCode(() -> service.requireUsable("missing", OtpReason.GUARDIAN, ConsentFixtures.WORKER),
				ErrorCode.NOT_FOUND);
	}

	private OtpChallenge refChallenge(OtpReason reason, String refId, Instant verifiedAt, boolean used) {
		return new OtpChallenge("c1", reason, encryptor.encrypt(PHONE), Sha256.hex(PHONE), "******3210", refId, 42L,
				TestFixtures.NOW.plusSeconds(300), verifiedAt, used, 0);
	}

	@Test
	@DisplayName("T9: requireUsableFor binds the challenge to its reason, reference and (guardian) phone")
	void requireUsableForChecksReasonRefIdAndPhone() {
		Set<OtpReason> consentOrGuardian = Set.of(OtpReason.CONSENT, OtpReason.GUARDIAN);
		stored(refChallenge(OtpReason.GUARDIAN, "1001", TestFixtures.NOW, false));
		assertThat(service.requireUsableFor("c1", consentOrGuardian, "1001", ConsentFixtures.WORKER, null)
				.challengeId()).isEqualTo("c1");
		assertThat(service.requireUsableFor("c1", consentOrGuardian, "1001", ConsentFixtures.WORKER,
				Sha256.hex(PHONE)).challengeId()).isEqualTo("c1");

		assertCode(() -> service.requireUsableFor("c1", Set.of(OtpReason.ACCESS_GRANT), "1001",
				ConsentFixtures.WORKER, null), ErrorCode.OTP_INVALID);
		assertCode(() -> service.requireUsableFor("c1", consentOrGuardian, "2002", ConsentFixtures.WORKER, null),
				ErrorCode.OTP_INVALID);
		assertCode(() -> service.requireUsableFor("c1", consentOrGuardian, "1001", ConsentFixtures.WORKER,
				Sha256.hex("9000000000")), ErrorCode.OTP_INVALID);
		assertCode(() -> service.requireUsableFor("c1", consentOrGuardian, "1001", OTHER, null),
				ErrorCode.OTP_INVALID);
		assertCode(() -> service.requireUsableFor("missing", consentOrGuardian, "1001", ConsentFixtures.WORKER,
				null), ErrorCode.OTP_INVALID);
	}

	@Test
	void requireUsableForRefusesUnverifiedUsedOrUnreferencedChallenges() {
		Set<OtpReason> grant = Set.of(OtpReason.ACCESS_GRANT);
		stored(refChallenge(OtpReason.ACCESS_GRANT, "g-1", null, false));
		assertCode(() -> service.requireUsableFor("c1", grant, "g-1", ConsentFixtures.WORKER, null),
				ErrorCode.OTP_INVALID);
		stored(refChallenge(OtpReason.ACCESS_GRANT, "g-1", TestFixtures.NOW, true));
		assertCode(() -> service.requireUsableFor("c1", grant, "g-1", ConsentFixtures.WORKER, null),
				ErrorCode.OTP_INVALID);
		stored(refChallenge(OtpReason.ACCESS_GRANT, null, TestFixtures.NOW, false));
		assertCode(() -> service.requireUsableFor("c1", grant, "g-1", ConsentFixtures.WORKER, null),
				ErrorCode.OTP_INVALID);
	}

	@Test
	void markUsed() {
		OtpChallenge verified = challenge(TestFixtures.NOW, false, 0, OtpReason.CONSENT);
		stored(verified);
		service.markUsed("c1");
		service.markUsed("missing");
		verify(store).update(eq(verified.markedUsed()));
	}
}
