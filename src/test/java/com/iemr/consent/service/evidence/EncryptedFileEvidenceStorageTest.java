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
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.iemr.consent.config.ConsentProperties;
import com.iemr.consent.support.TestFixtures;
import com.iemr.consent.utils.crypto.FieldEncryptor;
import com.iemr.consent.utils.crypto.Sha256;
import com.iemr.consent.utils.exception.ConsentApiException;
import com.iemr.consent.utils.exception.ErrorCode;

class EncryptedFileEvidenceStorageTest {

	private static final byte[] CLIP = "synthetic-voice-clip".getBytes(StandardCharsets.UTF_8);

	@TempDir
	Path dir;

	private EncryptedFileEvidenceStorage storage(Path directory) {
		ConsentProperties properties = TestFixtures.consentProperties(1048576L, directory.toString(), "");
		return new EncryptedFileEvidenceStorage(properties, new FieldEncryptor(properties));
	}

	@Test
	void storesEncryptedAndReadsBackWithHashCheck() throws Exception {
		EncryptedFileEvidenceStorage storage = storage(dir.resolve("evidence"));

		String ref = storage.store("e1", CLIP);

		byte[] onDisk = Files.readAllBytes(dir.resolve("evidence").resolve(ref));
		assertThat(new String(onDisk, StandardCharsets.ISO_8859_1)).doesNotContain("synthetic-voice-clip");
		assertThat(storage.read(ref, Sha256.hex(CLIP))).isEqualTo(CLIP);
		assertThatIllegalStateException().isThrownBy(() -> storage.read(ref, "0".repeat(64)));
		storage.delete(ref);
		assertThat(Files.exists(dir.resolve("evidence").resolve(ref))).isFalse();
		storage.delete(ref);
	}

	@Test
	void unwritableOrMissingFilesAreDependencyUnavailable() throws Exception {
		EncryptedFileEvidenceStorage storage = storage(dir);
		Files.createDirectory(dir.resolve("e2.enc"));
		Files.writeString(dir.resolve("e2.enc").resolve("child"), "x");

		assertThatThrownBy(() -> storage.store("e2", CLIP)).isInstanceOf(ConsentApiException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.DEPENDENCY_UNAVAILABLE);
		assertThatThrownBy(() -> storage.read("missing.enc", "x")).isInstanceOf(ConsentApiException.class);
		storage.delete("e2.enc");
	}

	@Test
	void directoryThatCannotBeCreatedStopsStartUp() throws Exception {
		Path file = Files.writeString(dir.resolve("a-file"), "x");

		assertThatIllegalStateException().isThrownBy(() -> storage(file.resolve("sub")))
				.withMessage("Missing required setting: consent.evidence.storage-dir");
	}
}
