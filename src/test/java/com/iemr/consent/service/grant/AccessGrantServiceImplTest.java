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

package com.iemr.consent.service.grant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import com.iemr.consent.data.consent.Consent;
import com.iemr.consent.data.consent.ConsentingParty;
import com.iemr.consent.data.consent.GuardianLink;
import com.iemr.consent.data.consent.PurposeStatus;
import com.iemr.consent.data.grant.AccessGrant;
import com.iemr.consent.data.grant.GrantStatus;
import com.iemr.consent.data.grant.GrantType;
import com.iemr.consent.data.grant.RevokeReason;
import com.iemr.consent.model.grant.AccessGrantView;
import com.iemr.consent.model.grant.AccessRequest;
import com.iemr.consent.model.grant.ApproveRequest;
import com.iemr.consent.model.otp.OtpReason;
import com.iemr.consent.repository.consent.ConsentPurposeRepository;
import com.iemr.consent.repository.consent.ConsentRepository;
import com.iemr.consent.repository.consent.GuardianLinkRepository;
import com.iemr.consent.repository.grant.AccessGrantRepository;
import com.iemr.consent.repository.notice.PurposeRepository;
import com.iemr.consent.service.audit.AuditLogService;
import com.iemr.consent.service.grant.GrantReplayStore.Replay;
import com.iemr.consent.service.otp.OtpChallenge;
import com.iemr.consent.service.otp.OtpService;
import com.iemr.consent.service.principal.EffectiveStatus;
import com.iemr.consent.service.principal.PrincipalEventFactory;
import com.iemr.consent.support.ConsentFixtures;
import com.iemr.consent.support.PrincipalFixtures;
import com.iemr.consent.support.TestFixtures;
import com.iemr.consent.utils.crypto.FieldEncryptor;
import com.iemr.consent.utils.crypto.Sha256;
import com.iemr.consent.utils.exception.ConsentApiException;
import com.iemr.consent.utils.exception.ErrorCode;

class AccessGrantServiceImplTest {

	private static final Long BEN = PrincipalFixtures.BEN;

	private static final String IP = "10.0.0.1";

	private static final String KEY = "key-1";

	private static final UUID OTP_ID = UUID.fromString("3f2504e0-4f89-11d3-9a0c-0305e82c3301");

	private final FieldEncryptor encryptor = new FieldEncryptor(TestFixtures.consentProperties());

	private AccessGrantRepository repository;

	private AccessGrantWriter writer;

	private GrantReplayStore replays;

	private PurposeRepository purposes;

	private ConsentPurposeRepository decisions;

	private ConsentRepository consents;

	private GuardianLinkRepository links;

	private OtpService otp;

	private AuditLogService audit;

	private AccessGrantServiceImpl service;

	@BeforeEach
	void setUp() {
		repository = mock(AccessGrantRepository.class);
		writer = mock(AccessGrantWriter.class);
		replays = mock(GrantReplayStore.class);
		purposes = mock(PurposeRepository.class);
		decisions = mock(ConsentPurposeRepository.class);
		consents = mock(ConsentRepository.class);
		links = mock(GuardianLinkRepository.class);
		otp = mock(OtpService.class);
		audit = mock(AuditLogService.class);
		when(purposes.findByPurposeCodeInAndActiveTrueAndDeletedFalse(any())).thenReturn(ConsentFixtures.purposes());
		when(otp.requireUsableFor(anyString(), any(), anyString(), any(), any())).thenReturn(
				new OtpChallenge(OTP_ID.toString(), OtpReason.ACCESS_GRANT, "enc", "h", "******3210", "g-1", 42L,
						TestFixtures.NOW, TestFixtures.NOW, false, 0));
		PrincipalEventFactory events = new PrincipalEventFactory(consents, decisions, links, encryptor,
				TestFixtures.CLOCK);
		service = new AccessGrantServiceImpl(repository, writer, replays, purposes, decisions, events, otp,
				new EffectiveStatus(TestFixtures.CLOCK), audit, TestFixtures.CLOCK, TestFixtures.consentProperties());
	}

	private AccessGrant stored(AccessGrant grant) {
		when(repository.findLockedByGrantId(grant.getGrantId())).thenReturn(Optional.of(grant));
		when(repository.findById(grant.getGrantId())).thenReturn(Optional.of(grant));
		return grant;
	}

	private static AccessRequest request(int days, String... codes) {
		return new AccessRequest(BEN, List.of(codes), days, "Referral follow-up", 5);
	}

	private static void assertCode(Runnable call, ErrorCode code) {
		assertThatThrownBy(call::run).isInstanceOf(ConsentApiException.class).extracting("errorCode")
				.isEqualTo(code);
	}

	private AccessGrantView approve(String grantId, String hash) {
		return service.approve(grantId, new ApproveRequest(OTP_ID), KEY, hash, ConsentFixtures.WORKER, IP);
	}

	@Test
	void aRequestCreatesAPendingGrant() {
		AccessRequest request = request(7, "P-REM");
		when(writer.create(request, KEY, "hash-1", ConsentFixtures.WORKER, IP))
				.thenReturn(PrincipalFixtures.grant("g-1", GrantStatus.PENDING, "P-REM"));

		AccessGrantView view = service.request(request, KEY, "hash-1", ConsentFixtures.WORKER, IP);

		assertThat(view.grantId()).isEqualTo("g-1");
		assertThat(view.status()).isEqualTo(GrantStatus.PENDING);
		assertThat(view.grantee().userId()).isEqualTo(77L);
		assertThat(view.grantee().name()).isEqualTo("doctor77");
		assertThat(view.grantee().facilityId()).isEqualTo(5);
		assertThat(view.purposes()).containsExactly("P-REM");
		assertThat(view.days()).isEqualTo(7);
	}

	@Test
	void aRequestNeedsActivePurposesAndAtMostTheMaximumDays() {
		assertCode(() -> service.request(request(31, "P-REM"), KEY, "h", ConsentFixtures.WORKER, IP),
				ErrorCode.VALIDATION_FAILED);
		assertCode(() -> service.request(request(30, "P-REM", "P-XYZ"), KEY, "h", ConsentFixtures.WORKER, IP),
				ErrorCode.VALIDATION_FAILED);
		verify(writer, never()).create(any(), anyString(), anyString(), any(), anyString());
	}

	@Test
	@DisplayName("C18: a repeated request returns the original grant; another request with the key is a conflict")
	void repeatedRequestReplays() {
		AccessGrant existing = PrincipalFixtures.grant("g-1", GrantStatus.PENDING, "P-REM");
		existing.setRequestHash("hash-1");
		when(repository.findByRequestedByAndRequestKey(42L, KEY)).thenReturn(Optional.of(existing));

		assertThat(service.request(request(7, "P-REM"), KEY, "hash-1", ConsentFixtures.WORKER, IP).grantId())
				.isEqualTo("g-1");
		assertCode(() -> service.request(request(7, "P-REM"), KEY, "hash-2", ConsentFixtures.WORKER, IP),
				ErrorCode.STATE_CONFLICT);
		verify(writer, never()).create(any(), anyString(), anyString(), any(), anyString());
	}

	@Test
	void aLostRaceReplaysTheWinnerOrFails() {
		AccessGrant winner = PrincipalFixtures.grant("g-1", GrantStatus.PENDING, "P-REM");
		winner.setRequestHash("hash-1");
		when(writer.create(any(), anyString(), anyString(), any(), anyString()))
				.thenThrow(new DataIntegrityViolationException("dup"));
		when(repository.findByRequestedByAndRequestKey(42L, KEY)).thenReturn(Optional.empty(), Optional.of(winner));
		assertThat(service.request(request(7, "P-REM"), KEY, "hash-1", ConsentFixtures.WORKER, IP).grantId())
				.isEqualTo("g-1");

		when(repository.findByRequestedByAndRequestKey(42L, KEY)).thenReturn(Optional.empty(), Optional.empty());
		assertCode(() -> service.request(request(7, "P-REM"), KEY, "hash-1", ConsentFixtures.WORKER, IP),
				ErrorCode.STATE_CONFLICT);
	}

	@Test
	void listShowsEffectiveStatusAndFiltersByIt() {
		AccessGrant active = PrincipalFixtures.grant("g-1", GrantStatus.ACTIVE, "P-REM");
		active.setValidUntil(TestFixtures.NOW.plusSeconds(60));
		AccessGrant lapsed = PrincipalFixtures.grant("g-2", GrantStatus.ACTIVE, "P-REM");
		lapsed.setValidUntil(TestFixtures.NOW.minusSeconds(60));
		AccessGrant emergency = PrincipalFixtures.grant("g-3", GrantStatus.PENDING, "P-TRT");
		emergency.setGrantType(GrantType.BREAK_GLASS);
		when(repository.findByBeneficiaryRegIdAndDeletedFalseOrderByCreatedDateDesc(BEN))
				.thenReturn(List.of(active, lapsed, emergency));

		assertThat(service.list(BEN, Set.of())).extracting(AccessGrantView::status)
				.containsExactly(GrantStatus.ACTIVE, GrantStatus.EXPIRED, GrantStatus.PENDING);
		assertThat(service.list(BEN, Set.of(GrantStatus.ACTIVE, GrantStatus.PENDING)))
				.extracting(AccessGrantView::grantId).containsExactly("g-1", "g-3");
		assertThat(service.list(BEN, Set.of(GrantStatus.EXPIRED))).extracting(AccessGrantView::type)
				.containsExactly(GrantType.CONSENTED);
	}

	@Test
	@DisplayName("H6 / T3: an approved grant is active for the requested days from approval")
	void approvalActivatesForTheRequestedDays() {
		AccessGrant grant = stored(PrincipalFixtures.grant("g-1", GrantStatus.PENDING, "P-REM"));

		AccessGrantView view = approve("g-1", "hash-1");

		verify(otp).requireUsableFor(OTP_ID.toString(), Set.of(OtpReason.ACCESS_GRANT), "g-1",
				ConsentFixtures.WORKER, null);
		assertThat(view.status()).isEqualTo(GrantStatus.ACTIVE);
		assertThat(view.approvedAt()).isEqualTo(TestFixtures.NOW);
		assertThat(view.validUntil()).isEqualTo(TestFixtures.NOW.plus(Duration.ofDays(7)));
		assertThat(grant.getApprovedBy()).isEqualTo(42L);
		assertThat(grant.getApprovalMaskedPhone()).isEqualTo("******3210");
		assertThat(grant.getModifiedBy()).isEqualTo("worker42");
		verify(repository).save(grant);
		verify(audit).append(AuditLogService.ACCESS_APPROVED, null, BEN, "grant:g-1", "ACTIVE", 42L, IP);
		verify(otp).markUsed(OTP_ID.toString());
		verify(replays).save(42L, KEY, new Replay("hash-1", "g-1"));
	}

	@Test
	@DisplayName("T9: when a guardian's consent is current, the OTP must come from the guardian's phone")
	void approvalNeedsTheGuardiansPhoneWhileAGuardiansConsentIsCurrent() {
		stored(PrincipalFixtures.grant("g-1", GrantStatus.PENDING, "P-REM"));
		Consent source = PrincipalFixtures.event("c-1", TestFixtures.NOW);
		source.setConsentingParty(ConsentingParty.GUARDIAN);
		when(decisions.findByBeneficiaryRegIdAndCurrentTrue(BEN))
				.thenReturn(List.of(PrincipalFixtures.row("c-1", "P-REM", PurposeStatus.GRANTED, 1, null)));
		when(consents.findByConsentIdIn(List.of("c-1"))).thenReturn(List.of(source));
		GuardianLink link = new GuardianLink();
		link.setGuardianPhoneEncrypted(encryptor.encrypt(ConsentFixtures.GUARDIAN_PHONE));
		when(links.findFirstByConsentIdAndDeletedFalse("c-1")).thenReturn(Optional.of(link));

		approve("g-1", "hash-1");

		verify(otp).requireUsableFor(eq(OTP_ID.toString()), eq(Set.of(OtpReason.ACCESS_GRANT)), eq("g-1"), any(),
				eq(Sha256.hex(ConsentFixtures.GUARDIAN_PHONE)));
	}

	@Test
	void onlyAPendingConsentedGrantCanBeApproved() {
		stored(PrincipalFixtures.grant("g-1", GrantStatus.ACTIVE, "P-REM"));
		assertCode(() -> approve("g-1", "h"), ErrorCode.STATE_CONFLICT);
		AccessGrant emergency = stored(PrincipalFixtures.grant("g-2", GrantStatus.PENDING, "P-REM"));
		emergency.setGrantType(GrantType.BREAK_GLASS);
		assertCode(() -> approve("g-2", "h"), ErrorCode.STATE_CONFLICT);
		assertCode(() -> approve("g-404", "h"), ErrorCode.NOT_FOUND);
		verify(otp, never()).requireUsableFor(anyString(), any(), anyString(), any(), any());
	}

	@Test
	void anUnusableOtpLeavesTheGrantPending() {
		AccessGrant grant = stored(PrincipalFixtures.grant("g-1", GrantStatus.PENDING, "P-REM"));
		when(otp.requireUsableFor(anyString(), any(), anyString(), any(), any()))
				.thenThrow(new ConsentApiException(ErrorCode.OTP_INVALID));
		assertCode(() -> approve("g-1", "h"), ErrorCode.OTP_INVALID);
		assertThat(grant.getStatus()).isEqualTo(GrantStatus.PENDING);
		verify(repository, never()).save(any());
	}

	@Test
	@DisplayName("C18 / I1: a repeated approve returns the grant as it is; the key for another grant is a conflict")
	void repeatedApproveReplays() {
		AccessGrant grant = stored(PrincipalFixtures.grant("g-1", GrantStatus.ACTIVE, "P-REM"));
		when(replays.find(42L, KEY)).thenReturn(Optional.of(new Replay("hash-1", "g-1")));

		assertThat(approve("g-1", "hash-1").status()).isEqualTo(GrantStatus.ACTIVE);
		assertCode(() -> approve("g-2", "hash-of-g-2"), ErrorCode.STATE_CONFLICT);
		verify(repository, never()).findLockedByGrantId(anyString());
		assertThat(grant.getApprovedAt()).isNull();

		when(replays.find(42L, KEY)).thenReturn(Optional.of(new Replay("hash-1", "g-gone")));
		assertCode(() -> approve("g-gone", "hash-1"), ErrorCode.NOT_FOUND);
	}

	@Test
	@DisplayName("T3: the patient can cancel a pending or active grant at any time")
	void revokeEndsAPendingOrActiveGrant() {
		AccessGrant active = stored(PrincipalFixtures.grant("g-1", GrantStatus.ACTIVE, "P-REM"));
		active.setValidUntil(TestFixtures.NOW.plusSeconds(60));

		AccessGrantView view = service.revoke("g-1", KEY, "hash-1", ConsentFixtures.WORKER, IP);

		assertThat(view.status()).isEqualTo(GrantStatus.REVOKED);
		assertThat(view.revokeReason()).isEqualTo(RevokeReason.PATIENT_REQUEST);
		assertThat(view.revokedAt()).isEqualTo(TestFixtures.NOW);
		assertThat(active.getReviewedBy()).isEqualTo(42L);
		verify(audit).append(AuditLogService.ACCESS_REVOKED, null, BEN, "grant:g-1", "REVOKED", 42L, IP);
		verify(replays).save(42L, KEY, new Replay("hash-1", "g-1"));

		stored(PrincipalFixtures.grant("g-2", GrantStatus.PENDING, "P-REM"));
		assertThat(service.revoke("g-2", "key-2", "h", ConsentFixtures.WORKER, IP).status())
				.isEqualTo(GrantStatus.REVOKED);
	}

	@Test
	void revokingAnEndedGrantIsAConflict() {
		stored(PrincipalFixtures.grant("g-1", GrantStatus.REVOKED, "P-REM"));
		assertCode(() -> service.revoke("g-1", KEY, "h", ConsentFixtures.WORKER, IP), ErrorCode.STATE_CONFLICT);
		AccessGrant lapsed = stored(PrincipalFixtures.grant("g-2", GrantStatus.ACTIVE, "P-REM"));
		lapsed.setValidUntil(TestFixtures.NOW.minusSeconds(1));
		assertCode(() -> service.revoke("g-2", KEY, "h", ConsentFixtures.WORKER, IP), ErrorCode.STATE_CONFLICT);
		assertCode(() -> service.revoke("g-404", KEY, "h", ConsentFixtures.WORKER, IP), ErrorCode.NOT_FOUND);
	}

	@Test
	@DisplayName("T5 / C11: withdrawal ends the grants covering a withdrawn purpose, not others or expired ones")
	void withdrawalRevokesCoveringGrants() {
		AccessGrant covering = PrincipalFixtures.grant("g-1", GrantStatus.ACTIVE, "P-REM", "P-TRT");
		AccessGrant other = PrincipalFixtures.grant("g-2", GrantStatus.PENDING, "P-TRT");
		AccessGrant lapsed = PrincipalFixtures.grant("g-3", GrantStatus.ACTIVE, "P-REM");
		lapsed.setValidUntil(TestFixtures.NOW.minusSeconds(1));

		service.revokeForWithdrawal(List.of(covering, other, lapsed), Set.of("P-REM"), ConsentFixtures.WORKER, IP);

		assertThat(covering.getStatus()).isEqualTo(GrantStatus.REVOKED);
		assertThat(covering.getRevokeReason()).isEqualTo(RevokeReason.WITHDRAWAL);
		assertThat(other.getStatus()).isEqualTo(GrantStatus.PENDING);
		assertThat(lapsed.getStatus()).isEqualTo(GrantStatus.ACTIVE);
		verify(audit).append(AuditLogService.ACCESS_REVOKED, null, BEN, "grant:g-1", "REVOKED", 42L, IP);
		verify(repository).save(covering);
		verify(repository, never()).save(other);
	}
}
