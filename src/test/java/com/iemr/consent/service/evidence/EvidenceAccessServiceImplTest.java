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
package com.iemr.consent.service.evidence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.iemr.consent.data.consent.Consent;
import com.iemr.consent.data.evidence.ConsentEvidence;
import com.iemr.consent.data.evidence.EvidenceType;
import com.iemr.consent.model.evidence.EvidenceView;
import com.iemr.consent.repository.consent.ConsentRepository;
import com.iemr.consent.repository.evidence.ConsentEvidenceRepository;
import com.iemr.consent.service.audit.AuditLogService;
import com.iemr.consent.support.ConsentFixtures;
import com.iemr.consent.utils.exception.ConsentApiException;
import com.iemr.consent.utils.exception.ErrorCode;

class EvidenceAccessServiceImplTest {

	private static final String CONSENT = "c0000000-0000-0000-0000-000000000001";
	private static final String EVIDENCE = "e0000000-0000-0000-0000-000000000001";
	private static final byte[] CLIP = "clip".getBytes();

	private ConsentEvidenceRepository evidence;
	private ConsentRepository consents;
	private EvidenceStorage storage;
	private AuditLogService auditLog;
	private EvidenceAccessServiceImpl service;

	@BeforeEach
	void setUp() {
		evidence = mock(ConsentEvidenceRepository.class);
		consents = mock(ConsentRepository.class);
		storage = mock(EvidenceStorage.class);
		auditLog = mock(AuditLogService.class);
		service = new EvidenceAccessServiceImpl(evidence, consents, storage, auditLog);
		Consent consent = new Consent();
		consent.setConsentId(CONSENT);
		consent.setBeneficiaryRegId(7001L);
		when(consents.findById(CONSENT)).thenReturn(Optional.of(consent));
		when(consents.existsById(CONSENT)).thenReturn(true);
	}

	private static ConsentEvidence row(String id, EvidenceType type, String consentId, String fileRef) {
		ConsentEvidence e = new ConsentEvidence();
		e.setEvidenceId(id);
		e.setType(type);
		e.setConsentId(consentId);
		e.setFileRef(fileRef);
		e.setSha256("s".repeat(64));
		e.setContentType("audio/webm");
		e.setSizeBytes(4L);
		e.setDurationSec(BigDecimal.valueOf(35));
		e.setCreatedBy("worker42");
		e.setCreatedAt(Instant.parse("2026-11-05T08:00:00Z"));
		return e;
	}

	@Test
	void listsOnlyStoredFiles() {
		when(evidence.findByConsentIdAndDeletedFalseOrderByCreatedAtAsc(CONSENT)).thenReturn(List.of(
				row(EVIDENCE, EvidenceType.VOICE_CLIP, CONSENT, "a.enc"),
				row("w", EvidenceType.WITNESS, CONSENT, null),
				row("d", EvidenceType.DECLARATION, CONSENT, null)));

		List<EvidenceView> views = service.listForConsent(CONSENT);

		assertThat(views).hasSize(1);
		assertThat(views.get(0).evidenceId()).isEqualTo(EVIDENCE);
		assertThat(views.get(0).uploadedBy()).isEqualTo("worker42");
	}

	@Test
	void unknownConsentIsNotFound() {
		assertThatThrownBy(() -> service.listForConsent("c-unknown")).isInstanceOf(ConsentApiException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.NOT_FOUND);
	}

	@Test
	void opensTheFileAndAuditsIt() {
		when(evidence.findById(EVIDENCE)).thenReturn(Optional.of(row(EVIDENCE, EvidenceType.VOICE_CLIP, CONSENT,
				"a.enc")));
		when(storage.read("a.enc", "s".repeat(64))).thenReturn(CLIP);

		EvidenceFile file = service.open(EVIDENCE, false, ConsentFixtures.WORKER, "10.0.0.1");

		assertThat(file.content()).isEqualTo(CLIP);
		assertThat(file.contentType()).isEqualTo("audio/webm");
		assertThat(file.fileName()).isEqualTo("consent-evidence-" + EVIDENCE + ".webm");
		verify(auditLog).append(AuditLogService.EVIDENCE_ACCESSED, CONSENT, 7001L,
				"evidence:" + EVIDENCE + ";type:VOICE_CLIP", "VIEWED", 42L, "10.0.0.1");

		service.open(EVIDENCE, true, ConsentFixtures.WORKER, "10.0.0.1");
		verify(auditLog).append(AuditLogService.EVIDENCE_ACCESSED, CONSENT, 7001L,
				"evidence:" + EVIDENCE + ";type:VOICE_CLIP", "DOWNLOADED", 42L, "10.0.0.1");
	}

	@Test
	void unlinkedPurgedAndNonFileEvidenceIsNotFound() {
		ConsentEvidence purged = row("p", EvidenceType.VOICE_CLIP, CONSENT, "p.enc");
		purged.setDeleted(true);
		when(evidence.findById("u")).thenReturn(Optional.of(row("u", EvidenceType.VOICE_CLIP, null, "u.enc")));
		when(evidence.findById("p")).thenReturn(Optional.of(purged));
		when(evidence.findById("w")).thenReturn(Optional.of(row("w", EvidenceType.WITNESS, CONSENT, null)));
		for (String id : List.of("u", "p", "w", "missing")) {
			assertThatThrownBy(() -> service.open(id, false, ConsentFixtures.WORKER, null))
					.isInstanceOf(ConsentApiException.class).extracting("errorCode").isEqualTo(ErrorCode.NOT_FOUND);
		}
		verifyNoInteractions(storage, auditLog);
	}

	@Test
	void aTamperedFileIsNeverSent() {
		when(evidence.findById(EVIDENCE)).thenReturn(Optional.of(row(EVIDENCE, EvidenceType.VOICE_CLIP, CONSENT,
				"a.enc")));
		when(storage.read(anyString(), anyString())).thenThrow(new IllegalStateException("hash"));

		assertThatThrownBy(() -> service.open(EVIDENCE, false, ConsentFixtures.WORKER, null))
				.isInstanceOf(ConsentApiException.class).extracting("errorCode").isEqualTo(ErrorCode.INTERNAL_ERROR);
		verifyNoInteractions(auditLog);
	}

	@Test
	void unknownMediaTypeGetsABinExtension() {
		ConsentEvidence odd = row(EVIDENCE, EvidenceType.SIGNED_FORM_PHOTO, CONSENT, "a.enc");
		odd.setContentType("image/heic");
		when(evidence.findById(EVIDENCE)).thenReturn(Optional.of(odd));
		when(storage.read(any(), any())).thenReturn(CLIP);
		assertThat(service.open(EVIDENCE, false, ConsentFixtures.WORKER, null).fileName()).endsWith(".bin");
	}
}
