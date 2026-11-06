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

package com.iemr.consent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.iemr.consent.data.notice.Notice;
import com.iemr.consent.data.notice.NoticeTranslation;
import com.iemr.consent.data.notify.ConsentNotification;
import com.iemr.consent.data.notify.NotificationStatus;
import com.iemr.consent.config.ConsentProperties;
import com.iemr.consent.config.SecurityProperties;
import com.iemr.consent.controller.consent.ConsentController;
import com.iemr.consent.controller.evidence.EvidenceController;
import com.iemr.consent.controller.notice.NoticeController;
import com.iemr.consent.controller.otp.OtpController;
import com.iemr.consent.data.audit.AuditLogEntry;
import com.iemr.consent.data.consent.Consent;
import com.iemr.consent.data.consent.ConsentPurpose;
import com.iemr.consent.data.consent.GuardianLink;
import com.iemr.consent.data.evidence.ConsentEvidence;
import com.iemr.consent.data.notice.NoticeContext;
import com.iemr.consent.data.notice.NoticePurpose;
import com.iemr.consent.data.notice.NoticeStatus;
import com.iemr.consent.model.otp.OtpReason;
import com.iemr.consent.repository.audit.AuditLogRepository;
import com.iemr.consent.repository.consent.ConsentPurposeRepository;
import com.iemr.consent.repository.consent.ConsentRepository;
import com.iemr.consent.repository.consent.GuardianLinkRepository;
import com.iemr.consent.repository.evidence.ConsentEvidenceRepository;
import com.iemr.consent.repository.notice.NoticePurposeRepository;
import com.iemr.consent.repository.notice.NoticeRepository;
import com.iemr.consent.repository.notice.NoticeTranslationRepository;
import com.iemr.consent.repository.notice.PurposeRepository;
import com.iemr.consent.service.audit.AuditLogServiceImpl;
import com.iemr.consent.service.consent.ConsentServiceImpl;
import com.iemr.consent.service.consent.ConsentDecisionWriter;
import com.iemr.consent.service.consent.ConsentValidator;
import com.iemr.consent.service.consent.ConsentWriter;
import com.iemr.consent.repository.notify.ConsentNotificationRepository;
import com.iemr.consent.service.consent.DeclarationProofWriter;
import com.iemr.consent.service.consent.DeclarationRule;
import com.iemr.consent.service.consent.EvidenceProofWriter;
import com.iemr.consent.service.consent.GuardianProofWriter;
import com.iemr.consent.service.consent.GuardianRule;
import com.iemr.consent.service.consent.OtpProofRule;
import com.iemr.consent.service.consent.OtpProofWriter;
import com.iemr.consent.service.consent.ReadAloudProofRule;
import com.iemr.consent.service.consent.SignaturePhotoProofRule;
import com.iemr.consent.service.consent.RecordingRule;
import com.iemr.consent.service.consent.SelfReadProofRule;
import com.iemr.consent.service.consent.WitnessedProofRule;
import com.iemr.consent.service.evidence.EvidenceServiceImpl;
import com.iemr.consent.service.evidence.EvidenceStorage;
import com.iemr.consent.service.notify.CommonApiSmsClient;
import com.iemr.consent.service.notify.ConsentNotifier;
import com.iemr.consent.service.notice.NoticeServiceImpl;
import com.iemr.consent.service.otp.CommonApiOtpClient;
import com.iemr.consent.service.otp.OtpChallenge;
import com.iemr.consent.service.otp.OtpChallengeStore;
import com.iemr.consent.service.otp.OtpServiceImpl;
import com.iemr.consent.support.ConsentFixtures;
import com.iemr.consent.support.LogCapture;
import com.iemr.consent.support.TestFixtures;
import com.iemr.consent.support.TestTokens;
import com.iemr.consent.utils.auth.JwtValidator;
import com.iemr.consent.utils.auth.PublicPaths;
import com.iemr.consent.utils.auth.SessionValidator;
import com.iemr.consent.utils.auth.TokenDenylist;
import com.iemr.consent.utils.cors.OriginPolicy;
import com.iemr.consent.utils.crypto.FieldEncryptor;
import com.iemr.consent.utils.crypto.Sha256;
import com.iemr.consent.utils.exception.ErrorResponseWriter;
import com.iemr.consent.utils.idempotency.RequestHasher;

/**
 * The MMU consent dialog end to end (feature 002): real filters, controllers, services, validator,
 * rules and writers; repositories, Redis, Common-API and file storage mocked. Named by scenario.
 */
@WebMvcTest(controllers = { NoticeController.class, ConsentController.class, OtpController.class,
	EvidenceController.class }, properties = { "jwt.secret=" + TestTokens.SECRET,
		"cors.allowed-origins=" + TestFixtures.ALLOWED_ORIGINS,
		"consent.evidence.storage-dir=" + TestFixtures.STORAGE_DIR,
		"consent.common-api.base-url=" + TestFixtures.COMMON_API_URL,
		"consent.encryption.key=" + TestFixtures.ENCRYPTION_KEY })
@Import({ ErrorResponseWriter.class, OriginPolicy.class, PublicPaths.class, JwtValidator.class,
	SessionValidator.class, SecurityProperties.class, RequestHasher.class, FieldEncryptor.class,
	NoticeServiceImpl.class, ConsentServiceImpl.class, ConsentValidator.class, ConsentWriter.class,
	ConsentDecisionWriter.class,
	GuardianRule.class, OtpProofRule.class, SelfReadProofRule.class, RecordingRule.class, SignaturePhotoProofRule.class,
	WitnessedProofRule.class, ReadAloudProofRule.class, EvidenceProofWriter.class, OtpProofWriter.class,
	GuardianProofWriter.class, DeclarationRule.class, DeclarationProofWriter.class, ConsentNotifier.class,
	OtpServiceImpl.class, EvidenceServiceImpl.class, AuditLogServiceImpl.class,
	CaptureFlowTest.SliceConfig.class })
class CaptureFlowTest {

	private static final String TOKEN = TestTokens.valid("42", "worker42", "jti-42");

	@TestConfiguration
	@EnableConfigurationProperties(ConsentProperties.class)
	static class SliceConfig {

		@Bean
		Clock clock() {
			return TestFixtures.CLOCK;
		}
	}

	@RegisterExtension
	final LogCapture logs = new LogCapture();

	@Autowired
	private MockMvc mvc;

	@Autowired
	private FieldEncryptor encryptor;

	@MockBean
	private TokenDenylist tokenDenylist;

	@MockBean
	private StringRedisTemplate stringRedisTemplate;

	@MockBean
	private NoticeRepository noticeRepository;

	@MockBean
	private NoticeTranslationRepository translationRepository;

	@MockBean
	private NoticePurposeRepository noticePurposeRepository;

	@MockBean
	private PurposeRepository purposeRepository;

	@MockBean
	private ConsentRepository consentRepository;

	@MockBean
	private ConsentPurposeRepository consentPurposeRepository;

	@MockBean
	private GuardianLinkRepository guardianLinkRepository;

	@MockBean
	private ConsentEvidenceRepository evidenceRepository;

	@MockBean
	private AuditLogRepository auditLogRepository;

	@MockBean
	private OtpChallengeStore otpStore;

	@MockBean
	private CommonApiOtpClient commonApi;

	@MockBean
	private EvidenceStorage evidenceStorage;

	@MockBean
	private ConsentNotificationRepository notificationRepository;

	@MockBean
	private CommonApiSmsClient smsClient;

	private final Map<String, Consent> consents = new HashMap<>();

	private final List<ConsentPurpose> purposeRows = new ArrayList<>();

	private final Map<String, ConsentEvidence> evidence = new HashMap<>();

	@BeforeEach
	void setUp() {
		var notice = ConsentFixtures.notice(NoticeStatus.PUBLISHED);
		when(noticeRepository.findFirstByContextAndProjectIdIsNullAndStatusAndDeletedFalseOrderByVersionNoDesc(
				NoticeContext.REGISTRATION, NoticeStatus.PUBLISHED)).thenReturn(Optional.of(notice));
		when(noticeRepository.findById(ConsentFixtures.NOTICE_ID)).thenReturn(Optional.of(notice));
		when(translationRepository.findByNoticeVersionIdAndDeletedFalseOrderByLanguageCodeAsc(ConsentFixtures.NOTICE_ID))
				.thenReturn(List.of(ConsentFixtures.translation("en", "a.mp3"), ConsentFixtures.translation("hi", null)));
		when(translationRepository.findByNoticeVersionIdAndLanguageCodeAndDeletedFalse(ConsentFixtures.NOTICE_ID,
				"hi")).thenReturn(Optional.of(ConsentFixtures.translation("hi", null)));
		NoticePurpose reg = new NoticePurpose();
		reg.setPurposeCode("P-REG");
		reg.setDisplayRank(1);
		NoticePurpose rem = new NoticePurpose();
		rem.setPurposeCode("P-REM");
		rem.setDisplayRank(2);
		when(noticePurposeRepository.findByNoticeVersionIdAndDeletedFalseOrderByDisplayRankAsc(
				ConsentFixtures.NOTICE_ID)).thenReturn(List.of(reg, rem));
		when(purposeRepository.findByPurposeCodeInAndActiveTrueAndDeletedFalse(any()))
				.thenReturn(ConsentFixtures.purposes());
		AuditLogEntry genesis = new AuditLogEntry();
		genesis.setEntryHash("0".repeat(64));
		when(auditLogRepository.findTopByOrderByIdDesc()).thenReturn(Optional.of(genesis));
		when(consentRepository.saveAndFlush(any())).thenAnswer(i -> {
			Consent c = i.getArgument(0);
			consents.put(c.getRequestKey(), c);
			return c;
		});
		when(consentRepository.findByCapturedByAndRequestKey(any(), anyString()))
				.thenAnswer(i -> Optional.ofNullable(consents.get(i.getArgument(1, String.class))));
		when(consentPurposeRepository.save(any())).thenAnswer(i -> {
			purposeRows.add(i.getArgument(0));
			return i.getArgument(0);
		});
		when(consentPurposeRepository.findCurrent(any(), anyString())).thenReturn(Optional.empty());
		when(consentPurposeRepository.findByConsentIdOrderByConsentPurposeIdAsc(anyString())).thenAnswer(
				i -> purposeRows.stream().filter(r -> r.getConsentId().equals(i.getArgument(0))).toList());
		when(evidenceRepository.saveAndFlush(any())).thenAnswer(i -> {
			ConsentEvidence e = i.getArgument(0);
			evidence.put(e.getEvidenceId(), e);
			return e;
		});
		when(evidenceRepository.findById(anyString()))
				.thenAnswer(i -> Optional.ofNullable(evidence.get(i.getArgument(0, String.class))));
		when(evidenceRepository.findByUploadedByAndRequestKey(any(), anyString())).thenReturn(Optional.empty());
		when(evidenceRepository.linkToConsent(anyString(), anyString())).thenReturn(1);
		when(evidenceStorage.store(anyString(), any())).thenAnswer(i -> i.getArgument(0) + ".enc");
	}

	private static MockHttpServletRequestBuilder save(String body) {
		return post("/consent/v1/consents").header("JwtToken", TOKEN)
				.header("Idempotency-Key", UUID.randomUUID().toString()).contentType(MediaType.APPLICATION_JSON)
				.content(body);
	}

	private static final String SELF = "\"SELF\"";

	private static final String GUARDIAN = "\"GUARDIAN\"";

	private static final String PATIENT = "\"PATIENT\"";

	private static final String REPRESENTATIVE = "\"REPRESENTATIVE\"";

	/** 004: every accept carries the declaration shown (the app's default wording; the fixture notice has none). */
	private static final String DECLARATION = ",\"declaration\":{\"text\":\"" + ConsentFixtures.DEFAULT_DECLARATION
			+ "\",\"languageCode\":\"hi\",\"noticeVersionId\":11,\"wording\":\"PATIENT\"}";

	private static String body(String mode, String reg, String rem, String extra) {
		return "{\"beneficiaryRegId\":1001,\"eventType\":\"NEW\",\"consentingParty\":\"SELF\","
				+ "\"noticeVersionId\":11,\"languageCode\":\"hi\",\"textHash\":\"" + ConsentFixtures.TEXT_HASH + "\","
				+ "\"channel\":\"FACILITY\",\"mode\":" + mode + ",\"capturedBy\":999,\"deviceId\":\"van-07\","
				+ "\"capturedAt\":\"2026-10-29T09:59:00Z\",\"decisions\":[{\"purposeCode\":\"P-REG\",\"decision\":\""
				+ reg + "\"},{\"purposeCode\":\"P-REM\",\"decision\":\"" + rem + "\"}]"
				+ ("GRANT".equals(reg) || "GRANT".equals(rem) ? DECLARATION : "") + extra + "}";
	}

	@Test
	@DisplayName("C1 / H1: language, notice, self-read accept of everything")
	void c1AcceptAll() throws Exception {
		mvc.perform(get("/consent/v1/notices/active/languages").param("context", "REGISTRATION").header("JwtToken",
				TOKEN)).andExpect(status().isOk()).andExpect(jsonPath("$.data[0].languageCode").value("en"));
		mvc.perform(get("/consent/v1/notices/active").param("context", "REGISTRATION").param("lang", "hi")
				.header("JwtToken", TOKEN)).andExpect(status().isOk())
				.andExpect(jsonPath("$.data.purposes[0].purposeCode").value("P-REG"));

		mvc.perform(save(body("\"SELF_READ\"", "GRANT", "GRANT", ""))).andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.purposes[0].status").value("GRANTED"))
				.andExpect(jsonPath("$.data.purposes[1].status").value("GRANTED"))
				.andExpect(jsonPath("$.data.syncStatus").value("SYNCED"));

		assertThat(consents.values()).singleElement().satisfies(c -> assertThat(c.getCapturedBy()).isEqualTo(42L));
		verify(auditLogRepository).save(any());
		logs.assertNoneContain("notice body", TOKEN);
	}

	@Test
	@DisplayName("C2: partial consent")
	void c2Partial() throws Exception {
		mvc.perform(save(body("\"SELF_READ\"", "GRANT", "DENY", ""))).andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.purposes[1].status").value("DENIED"));
	}

	@Test
	@DisplayName("C3: decline with no mode")
	void c3Decline() throws Exception {
		mvc.perform(save(body("null", "DENY", "DENY", ""))).andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.purposes[0].status").value("DENIED"));
	}

	@Test
	@DisplayName("C4: closing the dialog saves nothing")
	void c4NothingSavedWithoutAnExplicitSave() throws Exception {
		mvc.perform(get("/consent/v1/notices/active").param("context", "REGISTRATION").param("lang", "hi")
				.header("JwtToken", TOKEN)).andExpect(status().isOk());
		verify(consentRepository, never()).saveAndFlush(any());
	}

	@Test
	@DisplayName("C17: the save must name the text actually shown")
	void c17LanguageAndTextMustMatch() throws Exception {
		mvc.perform(save(body("\"SELF_READ\"", "GRANT", "GRANT", "").replace("\"hi\"", "\"en\"")))
				.andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("STALE_VERSION"));
	}

	@Test
	@DisplayName("C18: a retry with the same key saves once and answers the same")
	void c18Retry() throws Exception {
		String key = UUID.randomUUID().toString();
		String body = body("\"SELF_READ\"", "GRANT", "DENY", "");
		String first = mvc.perform(post("/consent/v1/consents").header("JwtToken", TOKEN).header("Idempotency-Key", key)
				.contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isCreated()).andReturn()
				.getResponse().getContentAsString();
		mvc.perform(post("/consent/v1/consents").header("JwtToken", TOKEN).header("Idempotency-Key", key)
				.contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.consentId").value(containsString(consents.get(key).getConsentId())));
		mvc.perform(post("/consent/v1/consents").header("JwtToken", TOKEN).header("Idempotency-Key", key)
				.contentType(MediaType.APPLICATION_JSON).content(body.replace("DENY", "GRANT")))
				.andExpect(status().isConflict());
		assertThat(consents).hasSize(1);
		assertThat(first).contains(consents.get(key).getConsentId());
	}

	@Test
	@DisplayName("C18: the browser may send Idempotency-Key")
	void preflightAllowsTheRequestKey() throws Exception {
		mvc.perform(options("/consent/v1/consents").header("Origin", "http://localhost:4200")
				.header("Access-Control-Request-Method", "POST")
				.header("Access-Control-Request-Headers", "idempotency-key,content-type")).andExpect(status().isOk())
				.andExpect(header().string("Access-Control-Allow-Headers", containsString("Idempotency-Key")));
	}

	private void verifiedChallenge(String id, OtpReason reason, String phone) {
		when(otpStore.find(id)).thenReturn(Optional.of(new OtpChallenge(id, reason, encryptor.encrypt(phone),
				Sha256.hex(phone), "******" + phone.substring(6), null, 42L, TestFixtures.NOW.plusSeconds(300),
				TestFixtures.NOW, false, 0)));
	}

	@Test
	@DisplayName("C6: guardian consents with a verified OTP")
	void c6GuardianOtp() throws Exception {
		String challenge = UUID.randomUUID().toString();
		verifiedChallenge(challenge, OtpReason.GUARDIAN, ConsentFixtures.GUARDIAN_PHONE);
		String guardian = ",\"guardian\":{\"guardianType\":\"PARENT\",\"name\":\"Sita Devi\",\"relationship\":"
				+ "\"Mother\",\"phone\":\"9876543210\",\"verification\":\"OTP\",\"otpChallengeId\":\"" + challenge
				+ "\"}";

		mvc.perform(save(body("\"SELF_READ\"", "GRANT", "GRANT", guardian).replace(SELF, GUARDIAN).replace(PATIENT, REPRESENTATIVE)))
				.andExpect(status().isCreated());

		ArgumentCaptor<GuardianLink> link = ArgumentCaptor.forClass(GuardianLink.class);
		verify(guardianLinkRepository).save(link.capture());
		assertThat(link.getValue().getGuardianPhoneEncrypted()).doesNotContain("9876543210");
		assertThat(encryptor.decrypt(link.getValue().getGuardianNameEncrypted())).isEqualTo("Sita Devi");
		verify(otpStore).update(any());
		logs.assertNoneContain("9876543210", "Sita Devi");
	}

	@Test
	@DisplayName("C7 / C8: guardian without OTP is flagged for follow-up")
	void c7GuardianFallback() throws Exception {
		String guardian = ",\"guardian\":{\"guardianType\":\"DISABILITY_GUARDIAN\",\"name\":\"Ravi Kumar\","
				+ "\"relationship\":\"Brother\",\"verification\":\"ID_SIGHTED\",\"witnessName\":\"Gita Bai\"}";

		mvc.perform(save(body("\"SELF_READ\"", "GRANT", "GRANT", guardian).replace(SELF, GUARDIAN).replace(PATIENT, REPRESENTATIVE)))
				.andExpect(status().isCreated());

		ArgumentCaptor<GuardianLink> link = ArgumentCaptor.forClass(GuardianLink.class);
		verify(guardianLinkRepository).save(link.capture());
		assertThat(link.getValue().isFollowUpRequired()).isTrue();
	}

	@Test
	@DisplayName("OTP mode: a challenge proves one consent only")
	void otpMode() throws Exception {
		String challenge = UUID.randomUUID().toString();
		verifiedChallenge(challenge, OtpReason.CONSENT, "9123456789");
		String proof = ",\"proof\":{\"otpChallengeId\":\"" + challenge + "\"}";

		mvc.perform(save(body("\"OTP\"", "GRANT", "GRANT", proof))).andExpect(status().isCreated());
		verify(otpStore).update(any());
	}

	@Test
	@DisplayName("M3: read aloud with the patient's recorded agreement, uploaded then linked")
	void m3VoiceClip() throws Exception {
		byte[] clip = "synthetic-clip".getBytes();
		String evidenceId = new com.fasterxml.jackson.databind.ObjectMapper().readTree(mvc.perform(
				multipart("/consent/v1/evidence").file(new MockMultipartFile("file", "c.webm", "audio/webm", clip))
						.param("type", "VOICE_CLIP").param("sha256", Sha256.hex(clip)).param("durationSec", "35")
						.header("JwtToken", TOKEN).header("Idempotency-Key", UUID.randomUUID().toString()))
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString())
				.at("/data/evidenceId").asText();

		// 004: reviewed list - the recording is part of READ_ALOUD, not a VOICE_CLIP mode
		mvc.perform(save(body("\"READ_ALOUD\"", "GRANT", "GRANT",
				",\"proof\":{\"workerConfirmed\":true},\"evidenceIds\":[\"" + evidenceId + "\"]")))
				.andExpect(status().isCreated());
		verify(evidenceRepository).linkToConsent(anyString(), anyString());
	}

	@Test
	@DisplayName("M3: witnessed mode without a witness is refused")
	void m3WitnessRequired() throws Exception {
		mvc.perform(save(body("\"WITNESSED\"", "GRANT", "GRANT", ""))).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.details[0].field").value("proof.witnessName"));
	}

	@Test
	@DisplayName("H2: read aloud with the worker's confirmation")
	void h2ReadAloud() throws Exception {
		mvc.perform(save(body("\"READ_ALOUD\"", "GRANT", "GRANT", ",\"proof\":{\"workerConfirmed\":true}")))
				.andExpect(status().isCreated());
	}

	@Test
	void everyCaptureEndpointNeedsALogin() throws Exception {
		mvc.perform(get("/consent/v1/notices/active").param("context", "REGISTRATION").param("lang", "hi"))
				.andExpect(status().isUnauthorized());
		mvc.perform(post("/consent/v1/consents").contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isUnauthorized());
	}

	// ---- Feature 004: front-end review changes (T047; kept here to reuse this slice's set-up) ----

	@Test
	@DisplayName("C1 (004): accept with notify answers 201 with the SMS QUEUED and the phone masked")
	void c1NotifyIsQueued() throws Exception {
		List<ConsentNotification> rows = new ArrayList<>();
		when(notificationRepository.save(any())).thenAnswer(i -> {
			rows.add(i.getArgument(0));
			return i.getArgument(0);
		});
		when(notificationRepository.findByConsentId(anyString())).thenAnswer(i -> rows.stream()
				.filter(r -> r.getConsentId().equals(i.getArgument(0))).findFirst());
		when(smsClient.send(anyString(), anyString(), any(), any(), anyString(), any())).thenReturn(true);

		mvc.perform(save(body("\"SELF_READ\"", "GRANT", "GRANT",
				",\"notify\":{\"channel\":\"SMS\",\"phone\":\"9876543210\"}")))
				.andExpect(status().isCreated()).andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.statusCode").value(201))
				.andExpect(jsonPath("$.data.notification.status").value("QUEUED"))
				.andExpect(jsonPath("$.data.notification.maskedPhone").value("******3210"));

		assertThat(rows).as("inserted in the save, updated after commit").hasSize(2).containsOnly(rows.get(0));
		assertThat(rows.get(0).getStatus()).isEqualTo(NotificationStatus.QUEUED);
		assertThat(rows.get(0).getPhoneEncrypted()).doesNotContain("9876");
		logs.assertNoneContain("9876543210");
	}

	@Test
	@DisplayName("C1 (004): a failing SMS never fails the save")
	void c1FailedSmsStillSaves() throws Exception {
		when(smsClient.send(anyString(), anyString(), any(), any(), anyString(), any())).thenReturn(false);

		mvc.perform(save(body("\"SELF_READ\"", "GRANT", "GRANT",
				",\"notify\":{\"channel\":\"SMS\",\"phone\":\"9876543210\"}")))
				.andExpect(status().isCreated()).andExpect(jsonPath("$.data.notification.status").value("FAILED"));
		assertThat(consents).hasSize(1);
	}

	@Test
	@DisplayName("C3 (004): decline without a declaration answers NOT_REQUESTED; with one it is refused")
	void c3DeclineHasNoDeclaration() throws Exception {
		mvc.perform(save(body("null", "DENY", "DENY", ""))).andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.notification.status").value("NOT_REQUESTED"));
		mvc.perform(save(body("null", "DENY", "DENY", DECLARATION))).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
				.andExpect(jsonPath("$.error.details[0].field").value("declaration"));
	}

	@Test
	@DisplayName("C2 (004): a declaration that differs from the published text is stale")
	void c2StaleDeclarationIs409() throws Exception {
		NoticeTranslation published = ConsentFixtures.translation("hi", null);
		published.setDeclaration("Published declaration");
		when(translationRepository.findByNoticeVersionIdAndLanguageCodeAndDeletedFalse(ConsentFixtures.NOTICE_ID,
				"hi")).thenReturn(Optional.of(published));

		mvc.perform(save(body("\"SELF_READ\"", "GRANT", "GRANT", ""))).andExpect(status().isConflict())
				.andExpect(jsonPath("$.statusCode").value(409))
				.andExpect(jsonPath("$.error.code").value("STALE_VERSION"));
		assertThat(consents).isEmpty();
	}

	@Test
	@DisplayName("C6 (004): a guardian agrees as representative in WITNESSED mode")
	void c6GuardianWitnessed() throws Exception {
		String challenge = UUID.randomUUID().toString();
		verifiedChallenge(challenge, OtpReason.GUARDIAN, ConsentFixtures.GUARDIAN_PHONE);
		String extra = ",\"proof\":{\"witnessName\":\"Sita Devi\",\"witnessRelationship\":\"Mother\"}"
				+ ",\"guardian\":{\"guardianType\":\"PARENT\",\"name\":\"Sita Devi\",\"relationship\":"
				+ "\"Mother\",\"phone\":\"9876543210\",\"verification\":\"OTP\",\"otpChallengeId\":\""
				+ challenge + "\"}";

		mvc.perform(save(body("\"WITNESSED\"", "GRANT", "GRANT", extra).replace(SELF, GUARDIAN)
				.replace(PATIENT, REPRESENTATIVE))).andExpect(status().isCreated());
		mvc.perform(save(body("\"WITNESSED\"", "GRANT", "GRANT", extra).replace(SELF, GUARDIAN)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.details[0].field").value("declaration.wording"));
	}

	@Test
	@DisplayName("US3 (004): mode VOICE_CLIP is refused")
	void voiceClipModeIsRefused() throws Exception {
		mvc.perform(save(body("\"VOICE_CLIP\"", "GRANT", "GRANT", ""))).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.success").value(false)).andExpect(jsonPath("$.statusCode").value(400))
				.andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
				.andExpect(jsonPath("$.error.details[0].field").value("mode"));
	}

	@Test
	@DisplayName("US1 (004): the project's notice is returned for projectId, the central one without it")
	void projectNotice() throws Exception {
		Notice project = ConsentFixtures.notice(NoticeStatus.PUBLISHED);
		project.setNoticeVersionId(77L);
		project.setProjectId(3);
		when(noticeRepository.findFirstByContextAndProjectIdAndStatusAndDeletedFalseOrderByVersionNoDesc(
				NoticeContext.REGISTRATION, 3, NoticeStatus.PUBLISHED)).thenReturn(Optional.of(project));
		NoticeTranslation hindi = ConsentFixtures.translation("hi", null);
		hindi.setNoticeVersionId(77L);
		hindi.setDeclaration("Project declaration");
		when(translationRepository.findByNoticeVersionIdAndLanguageCodeAndDeletedFalse(77L, "hi"))
				.thenReturn(Optional.of(hindi));

		mvc.perform(get("/consent/v1/notices/active").param("context", "REGISTRATION").param("lang", "hi")
				.param("projectId", "3").header("JwtToken", TOKEN)).andExpect(status().isOk())
				.andExpect(jsonPath("$.data.noticeVersionId").value(77))
				.andExpect(jsonPath("$.data.declaration").value("Project declaration"));
		mvc.perform(get("/consent/v1/notices/active").param("context", "REGISTRATION").param("lang", "hi")
				.header("JwtToken", TOKEN)).andExpect(status().isOk())
				.andExpect(jsonPath("$.data.noticeVersionId").value(11));
	}
}
