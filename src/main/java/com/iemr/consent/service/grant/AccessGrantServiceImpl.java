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

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.iemr.consent.config.ConsentProperties;
import com.iemr.consent.data.consent.ConsentPurpose;
import com.iemr.consent.data.grant.AccessGrant;
import com.iemr.consent.data.grant.GrantStatus;
import com.iemr.consent.data.grant.GrantType;
import com.iemr.consent.data.grant.RevokeReason;
import com.iemr.consent.data.notice.Purpose;
import com.iemr.consent.model.grant.AccessGrantView;
import com.iemr.consent.model.grant.AccessRequest;
import com.iemr.consent.model.grant.ApproveRequest;
import com.iemr.consent.model.grant.GranteeView;
import com.iemr.consent.model.otp.OtpReason;
import com.iemr.consent.repository.consent.ConsentPurposeRepository;
import com.iemr.consent.repository.grant.AccessGrantRepository;
import com.iemr.consent.repository.notice.PurposeRepository;
import com.iemr.consent.service.audit.AuditLogService;
import com.iemr.consent.service.grant.GrantReplayStore.Replay;
import com.iemr.consent.service.otp.OtpChallenge;
import com.iemr.consent.service.otp.OtpService;
import com.iemr.consent.service.principal.EffectiveStatus;
import com.iemr.consent.service.principal.PrincipalEventFactory;
import com.iemr.consent.utils.auth.AuthenticatedUser;
import com.iemr.consent.utils.exception.ConsentApiException;
import com.iemr.consent.utils.exception.ErrorCode;
import com.iemr.consent.utils.http.AfterCommit;
import com.iemr.consent.utils.response.FieldErrorDetail;

/**
 * Doctor access grants (FR-013 – FR-017; research R2, R6, R7, R8). A request is idempotent through the
 * table's request-key unique index; approve and revoke through {@link GrantReplayStore}. Approval needs a
 * verified ACCESS_GRANT OTP challenge whose {@code refId} is the grant, from the guardian's phone while a
 * guardian's consent is current (T9). Every change is audited in the same transaction.
 */
@Service
public class AccessGrantServiceImpl implements AccessGrantService {

	private final AccessGrantRepository repository;

	private final AccessGrantWriter writer;

	private final GrantReplayStore replays;

	private final PurposeRepository purposes;

	private final ConsentPurposeRepository decisions;

	private final PrincipalEventFactory events;

	private final OtpService otpService;

	private final EffectiveStatus effectiveStatus;

	private final AuditLogService auditLogService;

	private final Clock clock;

	private final int maxDays;

	public AccessGrantServiceImpl(AccessGrantRepository repository, AccessGrantWriter writer,
			GrantReplayStore replays, PurposeRepository purposes, ConsentPurposeRepository decisions,
			PrincipalEventFactory events, OtpService otpService, EffectiveStatus effectiveStatus,
			AuditLogService auditLogService, Clock clock, ConsentProperties properties) {
		this.repository = repository;
		this.writer = writer;
		this.replays = replays;
		this.purposes = purposes;
		this.decisions = decisions;
		this.events = events;
		this.otpService = otpService;
		this.effectiveStatus = effectiveStatus;
		this.auditLogService = auditLogService;
		this.clock = clock;
		this.maxDays = properties.accessGrant().maxDays();
	}

	@Override
	public AccessGrantView request(AccessRequest request, String requestKey, String requestHash,
			AuthenticatedUser user, String sourceIp) {
		AccessGrant existing = repository.findByRequestedByAndRequestKey(user.userId(), requestKey)
				.orElse(null);
		if (existing != null) {
			return replay(existing, requestHash);
		}
		if (request.days() > maxDays) {
			throw invalid("days", "must be at most " + maxDays);
		}
		Set<String> known = purposes.findByPurposeCodeInAndActiveTrueAndDeletedFalse(request.purposeCodes())
				.stream().map(Purpose::getPurposeCode).collect(Collectors.toSet());
		if (!known.containsAll(request.purposeCodes())) {
			throw invalid("purposeCodes", "must be active purpose codes");
		}
		try {
			return view(writer.create(request, requestKey, requestHash, user, sourceIp));
		} catch (DataIntegrityViolationException e) {
			AccessGrant winner = repository.findByRequestedByAndRequestKey(user.userId(), requestKey)
					.orElseThrow(() -> new ConsentApiException(ErrorCode.STATE_CONFLICT));
			return replay(winner, requestHash);
		}
	}

	@Override
	@Transactional(readOnly = true)
	public List<AccessGrantView> list(Long benRegId, Set<GrantStatus> statuses) {
		return repository.findByBeneficiaryRegIdAndDeletedFalseOrderByCreatedDateDesc(benRegId).stream()
				.map(this::view).filter(v -> statuses.isEmpty() || statuses.contains(v.status()))
				.toList();
	}

	@Override
	@Transactional
	public AccessGrantView approve(String grantId, ApproveRequest request, String requestKey, String requestHash,
			AuthenticatedUser user, String sourceIp) {
		return changeOnce(grantId, requestKey, requestHash, user, grant -> {
			boolean pending = effectiveStatus.of(grant) == GrantStatus.PENDING;
			if (grant.getGrantType() != GrantType.CONSENTED || !pending) {
				throw new ConsentApiException(ErrorCode.STATE_CONFLICT,
						"This access cannot be approved", null, null);
			}
			String phoneHash = guardianPhoneHash(grant.getBeneficiaryRegId());
			OtpChallenge challenge = otpService.requireUsableFor(request.otpChallengeId().toString(),
					Set.of(OtpReason.ACCESS_GRANT), grantId, user, phoneHash);
			Instant now = clock.instant();
			grant.setStatus(GrantStatus.ACTIVE);
			grant.setApprovedAt(now);
			grant.setApprovedBy(user.userId());
			grant.setValidUntil(now.plus(Duration.ofDays(grant.getDays())));
			grant.setApprovalMaskedPhone(challenge.maskedPhone());
			save(grant, AuditLogService.ACCESS_APPROVED, user, sourceIp);
			AfterCommit.run(() -> otpService.markUsed(challenge.challengeId()));
		});
	}

	@Override
	@Transactional
	public AccessGrantView revoke(String grantId, String requestKey, String requestHash, AuthenticatedUser user,
			String sourceIp) {
		return changeOnce(grantId, requestKey, requestHash, user, grant -> {
			GrantStatus status = effectiveStatus.of(grant);
			if (status != GrantStatus.PENDING && status != GrantStatus.ACTIVE) {
				throw new ConsentApiException(ErrorCode.STATE_CONFLICT, "This access has already ended",
						null,
						null);
			}
			revoke(grant, RevokeReason.PATIENT_REQUEST, user, sourceIp);
		});
	}

	@Override
	@Transactional(propagation = Propagation.MANDATORY)
	public void revokeForWithdrawal(List<AccessGrant> grants, Collection<String> withdrawnCodes,
			AuthenticatedUser user, String sourceIp) {
		for (AccessGrant grant : grants) {
			Set<String> covered = new HashSet<>(grant.purposeList());
			covered.retainAll(withdrawnCodes);
			if (!covered.isEmpty() && effectiveStatus.of(grant) != GrantStatus.EXPIRED) {
				revoke(grant, RevokeReason.WITHDRAWAL, user, sourceIp);
			}
		}
	}

	/** Applies a change to a locked grant once per request key; a repeat returns the grant as it is now. */
	private AccessGrantView changeOnce(String grantId, String requestKey, String requestHash,
			AuthenticatedUser user, Consumer<AccessGrant> change) {
		Replay done = replays.find(user.userId(), requestKey).orElse(null);
		if (done != null) {
			if (!done.requestHash().equals(requestHash)) {
				throw new ConsentApiException(ErrorCode.STATE_CONFLICT);
			}
			return view(repository.findById(done.grantId())
					.orElseThrow(() -> new ConsentApiException(ErrorCode.NOT_FOUND)));
		}
		AccessGrant grant = repository.findLockedByGrantId(grantId)
				.orElseThrow(() -> new ConsentApiException(ErrorCode.NOT_FOUND));
		change.accept(grant);
		AfterCommit.run(() -> replays.save(user.userId(), requestKey, new Replay(requestHash, grantId)));
		return view(grant);
	}

	private void revoke(AccessGrant grant, RevokeReason reason, AuthenticatedUser user, String sourceIp) {
		grant.setStatus(GrantStatus.REVOKED);
		grant.setRevokeReason(reason);
		grant.setReviewedBy(user.userId());
		grant.setRevokedAt(clock.instant());
		save(grant, AuditLogService.ACCESS_REVOKED, user, sourceIp);
	}

	private void save(AccessGrant grant, String action, AuthenticatedUser user, String sourceIp) {
		grant.setModifiedBy(user.auditName());
		repository.save(grant);
		auditLogService.append(action, null, grant.getBeneficiaryRegId(), "grant:" + grant.getGrantId(),
				grant.getStatus().name(), user.userId(), sourceIp);
	}

	/** The guardian's phone hash while the patient's current consent is a guardian's (T9); else null. */
	private String guardianPhoneHash(Long benRegId) {
		List<ConsentPurpose> current = decisions.findByBeneficiaryRegIdAndCurrentTrue(benRegId);
		if (current.isEmpty()) {
			return null;
		}
		return events.activeGuardian(events.newestSource(current)).map(events::guardianPhoneHash).orElse(null);
	}

	private AccessGrantView replay(AccessGrant grant, String requestHash) {
		if (!grant.getRequestHash().equals(requestHash)) {
			throw new ConsentApiException(ErrorCode.STATE_CONFLICT);
		}
		return view(grant);
	}

	private AccessGrantView view(AccessGrant g) {
		GranteeView grantee = new GranteeView(g.getGranteeUserId(), g.getGranteeName(),
				g.getGranteeFacilityId());
		return new AccessGrantView(g.getGrantId(), g.getBeneficiaryRegId(), grantee, g.purposeList(),
				g.getGrantType(), effectiveStatus.of(g), g.getDays(), g.getReason(), g.getValidUntil(),
				g.getApprovedAt(), g.getRevokedAt(), g.getRevokeReason());
	}

	private static ConsentApiException invalid(String field, String message) {
		return new ConsentApiException(ErrorCode.VALIDATION_FAILED, null, null,
				List.of(new FieldErrorDetail(field, message)));
	}
}
