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

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.iemr.consent.utils.exception.ConsentApiException;
import com.iemr.consent.utils.exception.ErrorCode;
import com.iemr.consent.utils.http.ForwardedAuth;

/**
 * Common-API's consent OTP ({@code /beneficiaryConsent/sendConsent}, {@code /validateConsent}),
 * which has the consent SMS template and the per-number rate limit (research R1, constitution IX).
 *
 * <p>Common-API answers with its legacy body ({@code statusCode} inside HTTP 200). A wrong code
 * is HTTP 200 with a non-200 {@code statusCode}; an HTTP or network error is
 * {@code DEPENDENCY_UNAVAILABLE}. Phones, codes and bodies are never logged.</p>
 */
@Component
public class CommonApiOtpClient {

	static final String SEND_PATH = "/beneficiaryConsent/sendConsent";

	static final String VALIDATE_PATH = "/beneficiaryConsent/validateConsent";

	private static final Logger LOGGER = LoggerFactory.getLogger(CommonApiOtpClient.class);

	private static final int OK = 200;

	private static final int TOO_MANY = 429;

	private final RestClient restClient;

	public CommonApiOtpClient(RestClient commonApiRestClient) {
		this.restClient = commonApiRestClient;
	}

	/** Common-API's legacy response; only {@code statusCode} is used. */
	@JsonIgnoreProperties(ignoreUnknown = true)
	record LegacyResponse(Integer statusCode) {
	}

	/** Sends the OTP SMS. */
	public void send(String phone, ForwardedAuth auth) {
		int status = call(SEND_PATH, Map.of("mobNo", phone), auth);
		if (status == TOO_MANY) {
			throw new ConsentApiException(ErrorCode.RATE_LIMITED);
		}
		if (status != OK) {
			LOGGER.warn("Common-API OTP send failed (statusCode {})", status);
			throw new ConsentApiException(ErrorCode.DEPENDENCY_UNAVAILABLE);
		}
	}

	/** @return {@code true} when Common-API accepts the code */
	public boolean validate(String phone, String otp, ForwardedAuth auth) {
		return call(VALIDATE_PATH, Map.of("mobNo", phone, "otp", Integer.parseInt(otp)), auth) == OK;
	}

	private int call(String path, Map<String, Object> body, ForwardedAuth auth) {
		try {
			LegacyResponse response = restClient.post().uri(path).contentType(MediaType.APPLICATION_JSON)
					.headers(auth::applyTo).body(body).retrieve().body(LegacyResponse.class);
			return response == null || response.statusCode() == null ? -1 : response.statusCode();
		} catch (RestClientException e) {
			LOGGER.warn("Common-API OTP call failed ({})", e.getClass().getSimpleName());
			throw new ConsentApiException(ErrorCode.DEPENDENCY_UNAVAILABLE);
		}
	}
}
