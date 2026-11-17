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

package com.iemr.consent.support;

import java.time.Instant;
import java.util.List;

import com.iemr.consent.data.consent.Channel;
import com.iemr.consent.data.consent.Consent;
import com.iemr.consent.data.consent.ConsentMode;
import com.iemr.consent.data.consent.ConsentPurpose;
import com.iemr.consent.data.consent.ConsentingParty;
import com.iemr.consent.data.consent.Decision;
import com.iemr.consent.data.consent.EventType;
import com.iemr.consent.data.consent.PurposeStatus;
import com.iemr.consent.data.consent.SyncStatus;
import com.iemr.consent.data.grant.AccessGrant;
import com.iemr.consent.data.grant.GrantStatus;
import com.iemr.consent.data.grant.GrantType;

/** Synthetic stored decisions, events and access grants for patient-level (feature 003) tests. */
public final class PrincipalFixtures {

	public static final long BEN = 1001L;

	private PrincipalFixtures() {
	}

	/** A decision row for {@link #BEN}; current unless SUPERSEDED. */
	public static ConsentPurpose row(String consentId, String code, PurposeStatus status, int version,
			Instant validUntil) {
		ConsentPurpose row = new ConsentPurpose();
		row.setConsentId(consentId);
		row.setBeneficiaryRegId(BEN);
		row.setPurposeCode(code);
		row.setDecision(status == PurposeStatus.GRANTED ? Decision.GRANT : Decision.DENY);
		row.setStatus(status);
		row.setVersionNo(version);
		row.setCurrent(status != PurposeStatus.SUPERSEDED);
		row.setValidUntil(validUntil);
		return row;
	}

	/** A self-read consent event for {@link #BEN} on notice {@link ConsentFixtures#NOTICE_ID}. */
	public static Consent event(String consentId, Instant receivedAt) {
		Consent consent = new Consent();
		consent.setConsentId(consentId);
		consent.setBeneficiaryRegId(BEN);
		consent.setEventType(EventType.NEW);
		consent.setConsentingParty(ConsentingParty.SELF);
		consent.setNoticeVersionId(ConsentFixtures.NOTICE_ID);
		consent.setLanguageCode("hi");
		consent.setTextHash(ConsentFixtures.TEXT_HASH);
		consent.setChannel(Channel.FACILITY);
		consent.setMode(ConsentMode.SELF_READ);
		consent.setCapturedBy(42L);
		consent.setDeviceId("van-07");
		consent.setServiceLine("MMU");
		consent.setCapturedAt(receivedAt);
		consent.setServerReceivedAt(receivedAt);
		consent.setSyncStatus(SyncStatus.SYNCED);
		consent.setRequestKey("k-" + consentId);
		consent.setRequestHash("h-" + consentId);
		return consent;
	}

	/** A CONSENTED grant for {@link #BEN} requested by user 77 for 7 days. */
	public static AccessGrant grant(String grantId, GrantStatus status, String... purposes) {
		AccessGrant grant = new AccessGrant();
		grant.setGrantId(grantId);
		grant.setBeneficiaryRegId(BEN);
		grant.setGrantType(GrantType.CONSENTED);
		grant.setGranteeUserId(77L);
		grant.setGranteeName("doctor77");
		grant.setGranteeFacilityId(5);
		grant.setPurposeList(List.of(purposes));
		grant.setReason("Referral follow-up");
		grant.setDays(7);
		grant.setStatus(status);
		grant.setRequestedBy(77L);
		grant.setRequestKey("k-" + grantId);
		grant.setRequestHash("h-" + grantId);
		grant.setCreatedBy("doctor77");
		return grant;
	}
}
