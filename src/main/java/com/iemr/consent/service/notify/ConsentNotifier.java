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

import java.time.Clock;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.iemr.consent.config.ConsentProperties;
import com.iemr.consent.data.notice.Notice;
import com.iemr.consent.data.notify.ConsentNotification;
import com.iemr.consent.data.notify.NotificationStatus;
import com.iemr.consent.model.consent.NotificationResult;
import com.iemr.consent.model.consent.NotifyInput;
import com.iemr.consent.model.consent.SaveConsentRequest;
import com.iemr.consent.repository.notify.ConsentNotificationRepository;
import com.iemr.consent.utils.auth.AuthenticatedUser;
import com.iemr.consent.utils.crypto.FieldEncryptor;
import com.iemr.consent.utils.crypto.PhoneMasker;
import com.iemr.consent.utils.http.ForwardedAuth;

/**
 * The SMS sent after a consent save (feature 004, FR-015 to FR-018, research R6). The row is inserted as FAILED inside
 * the save transaction ({@link #record}); after commit {@link #send} hands the SMS to Common-API and marks it QUEUED on
 * success. A replay reads the stored row ({@link #stored}) and never sends again. The phone is stored encrypted only;
 * the masked form is derived on read. Nothing here ever fails the save.
 */
@Service
public class ConsentNotifier {

	private static final Logger LOGGER = LoggerFactory.getLogger(ConsentNotifier.class);

	private final ConsentNotificationRepository repository;

	private final CommonApiSmsClient smsClient;

	private final FieldEncryptor encryptor;

	private final Clock clock;

	private final String template;

	public ConsentNotifier(ConsentNotificationRepository repository, CommonApiSmsClient smsClient,
			FieldEncryptor encryptor, Clock clock, ConsentProperties properties) {
		this.repository = repository;
		this.smsClient = smsClient;
		this.encryptor = encryptor;
		this.clock = clock;
		this.template = properties.sms().template();
	}

	/** Inside the save transaction: stores the requested SMS as FAILED (nothing handed over yet). */
	public void record(String consentId, NotifyInput notify, AuthenticatedUser user) {
		if (notify == null) {
			return;
		}
		ConsentNotification row = new ConsentNotification();
		row.setConsentId(consentId);
		row.setChannel(notify.channel());
		row.setPhoneEncrypted(encryptor.encrypt(notify.phone()));
		row.setStatus(NotificationStatus.FAILED);
		row.setCreatedBy(user.auditName());
		repository.save(row);
	}

	/** After commit: sends the SMS and returns its outcome; never throws. */
	public NotificationResult send(String consentId, SaveConsentRequest request, Notice notice, ForwardedAuth auth,
			AuthenticatedUser user) {
		NotifyInput notify = request.notifyRequest();
		if (notify == null) {
			return NotificationResult.notRequested();
		}
		NotificationStatus status = NotificationStatus.FAILED;
		try {
			String text = text(consentId, notice);
			boolean queued = smsClient.send(text, notify.phone(), request.beneficiaryRegId(),
					request.providerServiceMapId(), user.auditName(), auth);
			status = queued ? NotificationStatus.QUEUED : NotificationStatus.FAILED;
			update(consentId, status, user);
		} catch (RuntimeException e) {
			LOGGER.warn("Consent SMS outcome not stored ({}), consentId={}", e.getClass().getSimpleName(),
					consentId);
		}
		return new NotificationResult(status, PhoneMasker.mask(notify.phone()));
	}

	/** For a replay: the stored outcome, or NOT_REQUESTED when the save had no notify. */
	public NotificationResult stored(String consentId) {
		return repository.findByConsentId(consentId).map(this::result)
				.orElseGet(NotificationResult::notRequested);
	}

	private NotificationResult result(ConsentNotification row) {
		String phone = encryptor.decrypt(row.getPhoneEncrypted());
		return new NotificationResult(row.getStatus(), PhoneMasker.mask(phone));
	}

	private void update(String consentId, NotificationStatus status, AuthenticatedUser user) {
		repository.findByConsentId(consentId).ifPresent(row -> {
			row.setStatus(status);
			row.setAttemptedAt(clock.instant());
			row.setModifiedBy(user.auditName());
			repository.save(row);
		});
	}

	/** The template with the Consent ID and the grievance officer's name and phone (or nothing). */
	String text(String consentId, Notice notice) {
		String grievance = Stream.of(notice.getGrievanceOfficerName(), notice.getGrievancePhone())
				.filter(Objects::nonNull).collect(Collectors.joining(" "));
		return template.replace("{consentId}", consentId).replace("{grievance}", grievance);
	}
}
