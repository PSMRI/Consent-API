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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.ArgumentCaptor;

import com.iemr.consent.data.notice.Notice;
import com.iemr.consent.data.notify.ConsentNotification;
import com.iemr.consent.data.notify.NotificationStatus;
import com.iemr.consent.data.notify.NotifyChannel;
import com.iemr.consent.model.consent.NotificationResult;
import com.iemr.consent.model.consent.NotifyInput;
import com.iemr.consent.model.consent.SaveConsentRequest;
import com.iemr.consent.repository.notify.ConsentNotificationRepository;
import com.iemr.consent.support.ConsentFixtures;
import com.iemr.consent.support.LogCapture;
import com.iemr.consent.support.TestFixtures;
import com.iemr.consent.utils.crypto.FieldEncryptor;
import com.iemr.consent.utils.http.ForwardedAuth;

/** The consent SMS: recorded in the save, sent after commit, replayed from the stored row (feature 004, US5). */
class ConsentNotifierTest {

	private static final String PHONE = "9876543210";

	private static final ForwardedAuth AUTH = new ForwardedAuth("jwt", null, null);

	@RegisterExtension
	final LogCapture logs = new LogCapture();

	private final FieldEncryptor encryptor = new FieldEncryptor(TestFixtures.consentProperties());

	private ConsentNotificationRepository repository;

	private CommonApiSmsClient smsClient;

	private ConsentNotifier notifier;

	private ConsentNotification row;

	@BeforeEach
	void setUp() {
		repository = mock(ConsentNotificationRepository.class);
		smsClient = mock(CommonApiSmsClient.class);
		notifier = new ConsentNotifier(repository, smsClient, encryptor, TestFixtures.CLOCK,
				TestFixtures.consentProperties());
		row = new ConsentNotification();
		row.setConsentId("c-1");
		row.setPhoneEncrypted(encryptor.encrypt(PHONE));
		row.setStatus(NotificationStatus.FAILED);
		when(repository.findByConsentId("c-1")).thenReturn(Optional.of(row));
	}

	private static SaveConsentRequest withNotify() {
		return ConsentFixtures.request().notifyBy(new NotifyInput(NotifyChannel.SMS, PHONE)).build();
	}

	private static Notice notice(String officer, String phone) {
		Notice notice = ConsentFixtures.notice(com.iemr.consent.data.notice.NoticeStatus.PUBLISHED);
		notice.setGrievanceOfficerName(officer);
		notice.setGrievancePhone(phone);
		return notice;
	}

	@Test
	@DisplayName("C1: the requested SMS is recorded as FAILED with the phone encrypted, no masked copy")
	void recordStoresFailedWithEncryptedPhone() {
		notifier.record("c-1", new NotifyInput(NotifyChannel.SMS, PHONE), ConsentFixtures.WORKER);

		ArgumentCaptor<ConsentNotification> saved = ArgumentCaptor.forClass(ConsentNotification.class);
		verify(repository).save(saved.capture());
		assertThat(saved.getValue().getStatus()).isEqualTo(NotificationStatus.FAILED);
		assertThat(saved.getValue().getChannel()).isEqualTo(NotifyChannel.SMS);
		assertThat(saved.getValue().getPhoneEncrypted()).isNotEqualTo(PHONE);
		assertThat(encryptor.decrypt(saved.getValue().getPhoneEncrypted())).isEqualTo(PHONE);
		assertThat(saved.getValue().getCreatedBy()).isEqualTo("worker42");
	}

	@Test
	void noNotifyRecordsNothing() {
		notifier.record("c-1", null, ConsentFixtures.WORKER);
		verify(repository, never()).save(any());
	}

	@Test
	@DisplayName("C1: Common-API accepts → QUEUED, stored with the attempt time, phone masked in the answer")
	void acceptedIsQueued() {
		when(smsClient.send(anyString(), anyString(), any(), any(), anyString(), any())).thenReturn(true);

		NotificationResult result = notifier.send("c-1", withNotify(), notice("Officer", "1800"), AUTH,
				ConsentFixtures.WORKER);

		assertThat(result).isEqualTo(new NotificationResult(NotificationStatus.QUEUED, "******3210"));
		assertThat(row.getStatus()).isEqualTo(NotificationStatus.QUEUED);
		assertThat(row.getAttemptedAt()).isEqualTo(TestFixtures.NOW);
		assertThat(row.getModifiedBy()).isEqualTo("worker42");
		verify(smsClient).send(TestFixtures.SMS_TEMPLATE.replace("{consentId}", "c-1")
				.replace("{grievance}", "Officer 1800"), PHONE, 1001L, 1, "worker42", AUTH);
		logs.assertNoneContain(PHONE);
	}

	@Test
	@DisplayName("C1: Common-API refuses → FAILED, the save is not affected")
	void refusedIsFailed() {
		when(smsClient.send(anyString(), anyString(), any(), any(), anyString(), any())).thenReturn(false);

		assertThat(notifier.send("c-1", withNotify(), notice(null, null), AUTH, ConsentFixtures.WORKER).status())
				.isEqualTo(NotificationStatus.FAILED);
		assertThat(row.getStatus()).isEqualTo(NotificationStatus.FAILED);
		assertThat(row.getAttemptedAt()).isEqualTo(TestFixtures.NOW);
	}

	@Test
	@DisplayName("C1: any error while sending or storing the outcome never throws")
	void errorsNeverThrow() {
		when(smsClient.send(anyString(), anyString(), any(), any(), anyString(), any()))
				.thenThrow(new IllegalStateException(PHONE));

		NotificationResult result = notifier.send("c-1", withNotify(), notice(null, "1800"), AUTH,
				ConsentFixtures.WORKER);

		assertThat(result.status()).isEqualTo(NotificationStatus.FAILED);
		assertThat(logs.messages()).anyMatch(m -> m.contains("IllegalStateException"));
		logs.assertNoneContain(PHONE);
	}

	@Test
	void sendWithoutNotifyIsNotRequested() {
		assertThat(notifier.send("c-1", ConsentFixtures.request().build(), notice(null, null), AUTH,
				ConsentFixtures.WORKER)).isEqualTo(NotificationResult.notRequested());
		verify(smsClient, never()).send(any(), any(), any(), any(), any(), any());
	}

	@Test
	void missingRowStillAnswersTheSendOutcome() {
		when(repository.findByConsentId("c-2")).thenReturn(Optional.empty());
		when(smsClient.send(anyString(), anyString(), any(), any(), anyString(), any())).thenReturn(true);

		assertThat(notifier.send("c-2", withNotify(), notice(null, null), AUTH, ConsentFixtures.WORKER).status())
				.isEqualTo(NotificationStatus.QUEUED);
		verify(repository, never()).save(any());
	}

	@Test
	@DisplayName("C18: a replay answers the stored outcome with the mask derived from the encrypted phone")
	void storedOutcome() {
		row.setStatus(NotificationStatus.QUEUED);

		assertThat(notifier.stored("c-1")).isEqualTo(new NotificationResult(NotificationStatus.QUEUED, "******3210"));
		when(repository.findByConsentId("c-3")).thenReturn(Optional.empty());
		assertThat(notifier.stored("c-3")).isEqualTo(NotificationResult.notRequested());
	}

	@Test
	void grievanceTextUsesWhatIsPublished() {
		assertThat(notifier.text("c-1", notice("Officer", null))).endsWith("Grievance: Officer");
		assertThat(notifier.text("c-1", notice(null, null))).endsWith("Grievance: ");
	}
}
