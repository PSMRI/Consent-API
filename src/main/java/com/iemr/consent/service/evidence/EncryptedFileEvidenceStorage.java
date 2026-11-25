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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.iemr.consent.config.ConsentProperties;
import com.iemr.consent.utils.crypto.FieldEncryptor;
import com.iemr.consent.utils.crypto.Sha256;
import com.iemr.consent.utils.exception.ConsentApiException;
import com.iemr.consent.utils.exception.ErrorCode;

/**
 * Evidence files encrypted with AES-256-GCM ({@link FieldEncryptor}) in
 * {@code consent.evidence.storage-dir}, one file per evidence id (research R2). Common-API's KM
 * file manager is not used: it logs the file body, uses {@code db_iemr} and does not encrypt.
 */
@Component
public class EncryptedFileEvidenceStorage implements EvidenceStorage {

	private static final Logger LOGGER = LoggerFactory.getLogger(EncryptedFileEvidenceStorage.class);

	private static final String SUFFIX = ".enc";

	private final Path directory;

	private final FieldEncryptor encryptor;

	public EncryptedFileEvidenceStorage(ConsentProperties properties, FieldEncryptor encryptor) {
		this.directory = Path.of(properties.evidence().storageDir());
		this.encryptor = encryptor;
		try {
			Files.createDirectories(directory);
		} catch (IOException e) {
			throw new IllegalStateException("Missing required setting: consent.evidence.storage-dir");
		}
	}

	@Override
	public String store(String evidenceId, byte[] content) {
		String fileRef = evidenceId + SUFFIX;
		try {
			Files.write(directory.resolve(fileRef), encryptor.encrypt(content));
			return fileRef;
		} catch (IOException e) {
			LOGGER.error("Evidence file could not be written ({})", e.getClass().getSimpleName());
			throw new ConsentApiException(ErrorCode.DEPENDENCY_UNAVAILABLE);
		}
	}

	@Override
	public byte[] read(String fileRef, String expectedSha256) {
		byte[] content;
		try {
			content = encryptor.decrypt(Files.readAllBytes(directory.resolve(fileRef)));
		} catch (IOException e) {
			throw new ConsentApiException(ErrorCode.DEPENDENCY_UNAVAILABLE);
		}
		if (!Sha256.hex(content).equals(expectedSha256)) {
			throw new IllegalStateException("Evidence file does not match its hash");
		}
		return content;
	}

	@Override
	public void delete(String fileRef) {
		try {
			Files.deleteIfExists(directory.resolve(fileRef));
		} catch (IOException e) {
			LOGGER.error("Evidence file could not be deleted ({})", e.getClass().getSimpleName());
		}
	}
}
