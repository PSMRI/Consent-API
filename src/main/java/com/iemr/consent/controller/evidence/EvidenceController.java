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

package com.iemr.consent.controller.evidence;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Clock;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.iemr.consent.data.evidence.EvidenceType;
import com.iemr.consent.model.evidence.EvidenceUploadResult;
import com.iemr.consent.service.evidence.EvidenceService;
import com.iemr.consent.service.evidence.EvidenceUpload;
import com.iemr.consent.utils.auth.AuthenticatedUser;
import com.iemr.consent.utils.auth.CurrentUser;
import com.iemr.consent.utils.exception.ConsentApiException;
import com.iemr.consent.utils.exception.ErrorCode;
import com.iemr.consent.utils.http.TraceIdFilter;
import com.iemr.consent.utils.idempotency.IdempotencyKey;
import com.iemr.consent.utils.response.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Pattern;

/** Proof-file upload for the voice and photo modes (front-end API 6; FR-007, FR-008). */
@RestController
@RequestMapping(value = "/consent/v1/evidence", produces = MediaType.APPLICATION_JSON_VALUE)
public class EvidenceController {

	private final EvidenceService evidenceService;

	private final Clock clock;

	public EvidenceController(EvidenceService evidenceService, Clock clock) {
		this.evidenceService = evidenceService;
		this.clock = clock;
	}

	@Operation(summary = "Upload a proof file (needs Idempotency-Key)")
	@PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public ResponseEntity<ApiResponse<EvidenceUploadResult>> upload(@RequestPart("file") MultipartFile file,
			@RequestParam("type") EvidenceType type,
			@RequestParam("sha256") @Pattern(regexp = "^[0-9a-f]{64}$") String sha256,
			@RequestParam(value = "durationSec", required = false) BigDecimal durationSec,
			@CurrentUser AuthenticatedUser user, HttpServletRequest request) {
		String key = IdempotencyKey.require(request);
		EvidenceUpload upload = new EvidenceUpload(type, sha256, durationSec, file.getContentType(),
				bytes(file));
		EvidenceUploadResult result = evidenceService.upload(upload, key, user);
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(ApiResponse.created(result, TraceIdFilter.currentTraceId(request), clock));
	}

	private static byte[] bytes(MultipartFile file) {
		try {
			return file.getBytes();
		} catch (IOException e) {
			throw new ConsentApiException(ErrorCode.MALFORMED_REQUEST);
		}
	}
}
