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

package com.iemr.consent.service.notify;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.iemr.consent.utils.http.ForwardedAuth;

/**
 * Hands the consent SMS to Common-API's existing {@code POST /sms/sendSMS} (Principle I; feature 004, R5). Common-API
 * stores it and its scheduler sends it, so success means queued. Free text ({@code smsText}) and the number
 * ({@code alternateNo}) are sent; no template row is needed. The phone and text are never logged.
 */
@Component
public class CommonApiSmsClient {

	static final String SEND_PATH = "/sms/sendSMS";

	private static final Logger LOGGER = LoggerFactory.getLogger(CommonApiSmsClient.class);

	private static final int OK = 200;

	private final RestClient restClient;

	public CommonApiSmsClient(@Qualifier("commonApiSmsRestClient") RestClient restClient) {
		this.restClient = restClient;
	}

	/** Common-API's legacy response; only {@code statusCode} is used. */
	@JsonIgnoreProperties(ignoreUnknown = true)
	record LegacyResponse(Integer statusCode) {
	}

	/**
	 * @return {@code true} when Common-API accepted (queued) the SMS; {@code false} on any error or time-out
	 */
	public boolean send(String text, String phone, Long beneficiaryRegId, Integer providerServiceMapId,
			String createdBy, ForwardedAuth auth) {
		Map<String, Object> sms = new HashMap<>();
		sms.put("smsText", text);
		sms.put("alternateNo", phone);
		sms.put("beneficiaryRegID", beneficiaryRegId);
		sms.put("providerServiceMapID", providerServiceMapId);
		sms.put("createdBy", createdBy);
		sms.put("is1097", false);
		try {
			RestClient.RequestBodySpec call = restClient.post().uri(SEND_PATH)
					.contentType(MediaType.APPLICATION_JSON).headers(auth::applyTo);
			LegacyResponse response = call.body(List.of(sms)).retrieve().body(LegacyResponse.class);
			Integer status = response == null ? null : response.statusCode();
			if (status == null || status != OK) {
				LOGGER.warn("Common-API SMS not accepted (statusCode {})", status);
				return false;
			}
			return true;
		} catch (RestClientException e) {
			LOGGER.warn("Common-API SMS call failed ({})", e.getClass().getSimpleName());
			return false;
		}
	}
}
