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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;

import com.iemr.consent.data.evidence.ConsentEvidence;
import com.iemr.consent.data.evidence.EvidenceType;
import com.iemr.consent.model.evidence.EvidenceUploadResult;
import com.iemr.consent.repository.evidence.ConsentEvidenceRepository;
import com.iemr.consent.support.ConsentFixtures;
import com.iemr.consent.support.LogCapture;
import com.iemr.consent.support.TestFixtures;
import com.iemr.consent.utils.crypto.Sha256;
import com.iemr.consent.utils.exception.ConsentApiException;
import com.iemr.consent.utils.exception.ErrorCode;

class EvidenceServiceImplTest {

	private static final byte[] CLIP = "synthetic-voice-clip".getBytes(StandardCharsets.UTF_8);

	private static final String SHA = Sha256.hex(CLIP);

	@RegisterExtension
	final LogCapture logs = new LogCapture();

	private ConsentEvidenceRepository repository;

	private EvidenceStorage storage;

	private EvidenceServiceImpl service;

	@BeforeEach
	void setUp() {
		repository = mock(ConsentEvidenceRepository.class);
		storage = mock(EvidenceStorage.class);
		when(storage.store(anyString(), any())).thenReturn("ref.enc");
		when(repository.findByUploadedByAndRequestKey(42L, "k")).thenReturn(Optional.empty());
		service = new EvidenceServiceImpl(repository, storage, TestFixtures.CLOCK, TestFixtures.consentProperties());
	}

	private static EvidenceUpload voice(BigDecimal seconds) {
		return new EvidenceUpload(EvidenceType.VOICE_CLIP, SHA, seconds, "audio/webm;codecs=opus", CLIP);
	}

	private void assertCode(EvidenceUpload upload, ErrorCode code) {
		assertThatThrownBy(() -> service.upload(upload, "k", ConsentFixtures.WORKER))
				.isInstanceOf(ConsentApiException.class).extracting("errorCode").isEqualTo(code);
		verify(storage, never()).store(anyString(), any());
	}

	@Test
	void storesAndRecordsTheFile() {
		EvidenceUploadResult result = service.upload(voice(BigDecimal.valueOf(35)), "k", ConsentFixtures.WORKER);

		assertThat(result.sha256()).isEqualTo(SHA);
		ArgumentCaptor<ConsentEvidence> saved = ArgumentCaptor.forClass(ConsentEvidence.class);
		verify(repository).saveAndFlush(saved.capture());
		ConsentEvidence row = saved.getValue();
		assertThat(row.getEvidenceId()).isEqualTo(result.evidenceId());
		assertThat(row.getFileRef()).isEqualTo("ref.enc");
		assertThat(row.getContentType()).isEqualTo("audio/webm");
		assertThat(row.getSizeBytes()).isEqualTo(CLIP.length);
		assertThat(row.getUploadedBy()).isEqualTo(42L);
		assertThat(row.getRequestKey()).isEqualTo("k");
		assertThat(row.getRequestHash()).hasSize(64);
		assertThat(row.getConsentId()).isNull();
		assertThat(row.getCreatedAt()).isEqualTo(TestFixtures.NOW);
		logs.assertNoneContain("synthetic-voice-clip");
	}

	@Test
	void photoWithoutDuration() {
		service.upload(new EvidenceUpload(EvidenceType.PATIENT_PHOTO, SHA, null, "image/png", CLIP), "k",
				ConsentFixtures.WORKER);
		verify(storage).store(anyString(), any());
	}

	@Test
	void fingerprintMismatchStoresNothing() {
		assertCode(new EvidenceUpload(EvidenceType.VOICE_CLIP, "0".repeat(64), BigDecimal.valueOf(35), "audio/webm",
				CLIP), ErrorCode.EVIDENCE_HASH_MISMATCH);
	}

	@Test
	void fileChecks() {
		assertCode(new EvidenceUpload(EvidenceType.WITNESS, SHA, null, "image/png", CLIP), ErrorCode.VALIDATION_FAILED);
		assertCode(new EvidenceUpload(null, SHA, null, "image/png", CLIP), ErrorCode.VALIDATION_FAILED);
		assertCode(new EvidenceUpload(EvidenceType.PATIENT_PHOTO, SHA, null, "image/png", new byte[0]),
				ErrorCode.VALIDATION_FAILED);
		assertCode(new EvidenceUpload(EvidenceType.PATIENT_PHOTO, SHA, null, "image/png", new byte[2048]),
				ErrorCode.PAYLOAD_TOO_LARGE);
		assertCode(new EvidenceUpload(EvidenceType.PATIENT_PHOTO, SHA, null, "application/pdf", CLIP),
				ErrorCode.UNSUPPORTED_MEDIA_TYPE);
		assertCode(new EvidenceUpload(EvidenceType.PATIENT_PHOTO, SHA, null, null, CLIP),
				ErrorCode.UNSUPPORTED_MEDIA_TYPE);
		assertCode(new EvidenceUpload(EvidenceType.VOICE_CLIP, SHA, BigDecimal.valueOf(35), "image/png", CLIP),
				ErrorCode.UNSUPPORTED_MEDIA_TYPE);
	}

	@Test
	void voiceClipMustBeThirtyToSixtySeconds() {
		assertCode(voice(null), ErrorCode.VALIDATION_FAILED);
		assertCode(voice(BigDecimal.valueOf(29.9)), ErrorCode.VALIDATION_FAILED);
		assertCode(voice(BigDecimal.valueOf(70)), ErrorCode.VALIDATION_FAILED);
		service.upload(voice(BigDecimal.valueOf(60)), "k", ConsentFixtures.WORKER);
	}

	@Test
	void replayAndConflict() {
		ConsentEvidence existing = new ConsentEvidence();
		existing.setEvidenceId("e1");
		existing.setSha256(SHA);
		ArgumentCaptor<ConsentEvidence> saved = ArgumentCaptor.forClass(ConsentEvidence.class);
		service.upload(voice(BigDecimal.valueOf(35)), "k", ConsentFixtures.WORKER);
		verify(repository).saveAndFlush(saved.capture());
		existing.setRequestHash(saved.getValue().getRequestHash());
		when(repository.findByUploadedByAndRequestKey(42L, "k")).thenReturn(Optional.of(existing));

		assertThat(service.upload(voice(BigDecimal.valueOf(35)), "k", ConsentFixtures.WORKER).evidenceId())
				.isEqualTo("e1");
		assertThatThrownBy(() -> service.upload(voice(BigDecimal.valueOf(40)), "k", ConsentFixtures.WORKER))
				.isInstanceOf(ConsentApiException.class).extracting("errorCode").isEqualTo(ErrorCode.STATE_CONFLICT);
	}

	@Test
	void raceOnTheKeyDeletesTheFileAndReplaysTheWinner() {
		ConsentEvidence winner = new ConsentEvidence();
		winner.setEvidenceId("e-win");
		when(repository.saveAndFlush(any())).thenAnswer(i -> {
			winner.setRequestHash(((ConsentEvidence) i.getArgument(0)).getRequestHash());
			throw new DataIntegrityViolationException("dup");
		});
		when(repository.findByUploadedByAndRequestKey(42L, "k")).thenReturn(Optional.empty(), Optional.of(winner));

		assertThat(service.upload(voice(BigDecimal.valueOf(35)), "k", ConsentFixtures.WORKER).evidenceId())
				.isEqualTo("e-win");
		verify(storage).delete("ref.enc");
	}

	@Test
	void raceWithoutWinnerAndOtherFailuresDeleteTheFile() {
		when(repository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("x"),
				new IllegalStateException("db"));

		assertThatThrownBy(() -> service.upload(voice(BigDecimal.valueOf(35)), "k", ConsentFixtures.WORKER))
				.isInstanceOf(ConsentApiException.class);
		assertThatThrownBy(() -> service.upload(voice(BigDecimal.valueOf(35)), "k", ConsentFixtures.WORKER))
				.isInstanceOf(IllegalStateException.class);
		verify(storage, org.mockito.Mockito.times(2)).delete("ref.enc");
	}

	@Test
	void linkableEvidenceMustExistBelongToTheUserAndBeUnlinked() {
		UUID id = UUID.randomUUID();
		ConsentEvidence clip = new ConsentEvidence();
		clip.setEvidenceId(id.toString());
		clip.setType(EvidenceType.VOICE_CLIP);
		clip.setUploadedBy(42L);
		when(repository.findById(id.toString())).thenReturn(Optional.of(clip));

		assertThat(service.requireLinkable(List.of(id, id), ConsentFixtures.WORKER)).containsExactly(clip);
		assertThatThrownBy(() -> service.requireLinkable(List.of(UUID.randomUUID()), ConsentFixtures.WORKER))
				.isInstanceOf(ConsentApiException.class).extracting("errorCode").isEqualTo(ErrorCode.NOT_FOUND);
		clip.setUploadedBy(7L);
		assertThatThrownBy(() -> service.requireLinkable(List.of(id), ConsentFixtures.WORKER))
				.extracting("errorCode").isEqualTo(ErrorCode.NOT_FOUND);
		clip.setUploadedBy(42L);
		clip.setType(EvidenceType.WITNESS);
		assertThatThrownBy(() -> service.requireLinkable(List.of(id), ConsentFixtures.WORKER))
				.extracting("errorCode").isEqualTo(ErrorCode.NOT_FOUND);
		clip.setType(EvidenceType.VOICE_CLIP);
		clip.setConsentId("c-other");
		assertThatThrownBy(() -> service.requireLinkable(List.of(id), ConsentFixtures.WORKER))
				.extracting("errorCode").isEqualTo(ErrorCode.STATE_CONFLICT);
		assertThat(new EvidenceUpload(EvidenceType.VOICE_CLIP, SHA, null, "audio/webm", CLIP).toString())
				.doesNotContain("synthetic");
	}
}
