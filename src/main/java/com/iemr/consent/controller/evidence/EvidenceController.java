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
import java.util.List;
import java.util.UUID;

import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.iemr.consent.data.evidence.EvidenceType;
import com.iemr.consent.model.evidence.EvidenceUploadResult;
import com.iemr.consent.model.evidence.EvidenceView;
import com.iemr.consent.service.evidence.EvidenceAccessService;
import com.iemr.consent.service.evidence.EvidenceFile;
import com.iemr.consent.service.evidence.EvidenceService;
import com.iemr.consent.service.evidence.EvidenceUpload;
import com.iemr.consent.utils.auth.AuthenticatedUser;
import com.iemr.consent.utils.auth.CurrentUser;
import com.iemr.consent.utils.exception.ConsentApiException;
import com.iemr.consent.utils.exception.ErrorCode;
import com.iemr.consent.utils.http.ClientIp;
import com.iemr.consent.utils.http.TraceIdFilter;
import com.iemr.consent.utils.idempotency.IdempotencyKey;
import com.iemr.consent.utils.response.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Pattern;

/**
 * Proof files for the voice and photo modes: upload (front-end API 6; FR-007, FR-008), and listing and retrieving
 * the files of a saved consent for any logged-in user, audited on every retrieval.
 */
@RestController
@RequestMapping(value = "/consent/v1/evidence", produces = MediaType.APPLICATION_JSON_VALUE)
public class EvidenceController {

	private final EvidenceService evidenceService;

	private final EvidenceAccessService accessService;

	private final Clock clock;

	public EvidenceController(EvidenceService evidenceService, EvidenceAccessService accessService, Clock clock) {
		this.evidenceService = evidenceService;
		this.accessService = accessService;
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

	@Operation(summary = "List the proof files of a saved consent (no content)")
	@GetMapping
	public ApiResponse<List<EvidenceView>> list(@RequestParam("consentId") String consentId,
			@CurrentUser AuthenticatedUser user, HttpServletRequest request) {
		return ApiResponse.ok(accessService.listForConsent(uuid(consentId)), TraceIdFilter.currentTraceId(request),
				clock);
	}

	@Operation(summary = "Play (inline) or download a proof file of a saved consent; every call is audited")
	@GetMapping(value = "/{evidenceId}/content", produces = MediaType.ALL_VALUE)
	public ResponseEntity<byte[]> content(@PathVariable String evidenceId,
			@RequestParam(value = "download", defaultValue = "false") boolean download,
			@CurrentUser AuthenticatedUser user, HttpServletRequest request) {
		EvidenceFile file = accessService.open(uuid(evidenceId), download, user, ClientIp.of(request));
		ContentDisposition disposition = (download ? ContentDisposition.attachment() : ContentDisposition.inline())
				.filename(file.fileName()).build();
		return ResponseEntity.ok().contentType(MediaType.parseMediaType(file.contentType()))
				.contentLength(file.content().length)
				.header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
				.header("X-Content-Type-Options", "nosniff")
				.cacheControl(CacheControl.noStore()).body(file.content());
	}

	/** Ids are UUIDs; anything else cannot exist. */
	private static String uuid(String id) {
		try {
			return UUID.fromString(id).toString();
		} catch (IllegalArgumentException e) {
			throw new ConsentApiException(ErrorCode.NOT_FOUND);
		}
	}

	private static byte[] bytes(MultipartFile file) {
		try {
			return file.getBytes();
		} catch (IOException e) {
			throw new ConsentApiException(ErrorCode.MALFORMED_REQUEST);
		}
	}
}
