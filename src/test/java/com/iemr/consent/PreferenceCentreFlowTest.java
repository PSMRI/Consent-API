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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.iemr.consent.config.ConsentProperties;
import com.iemr.consent.config.SecurityProperties;
import com.iemr.consent.controller.grant.AccessGrantController;
import com.iemr.consent.controller.principal.PrincipalConsentController;
import com.iemr.consent.data.audit.AuditLogEntry;
import com.iemr.consent.data.consent.Consent;
import com.iemr.consent.data.consent.ConsentPurpose;
import com.iemr.consent.data.consent.EventType;
import com.iemr.consent.data.consent.PurposeStatus;
import com.iemr.consent.data.grant.AccessGrant;
import com.iemr.consent.data.notice.Notice;
import com.iemr.consent.data.notice.NoticeContext;
import com.iemr.consent.data.notice.NoticePurpose;
import com.iemr.consent.data.notice.NoticeStatus;
import com.iemr.consent.data.notice.Purpose;
import com.iemr.consent.model.otp.OtpReason;
import com.iemr.consent.repository.audit.AuditLogRepository;
import com.iemr.consent.repository.consent.ConsentPurposeRepository;
import com.iemr.consent.repository.consent.ConsentRepository;
import com.iemr.consent.repository.consent.GuardianLinkRepository;
import com.iemr.consent.repository.evidence.ConsentEvidenceRepository;
import com.iemr.consent.repository.grant.AccessGrantRepository;
import com.iemr.consent.repository.notice.NoticePurposeRepository;
import com.iemr.consent.repository.notice.NoticeRepository;
import com.iemr.consent.repository.notice.PurposeRepository;
import com.iemr.consent.service.audit.AuditLogServiceImpl;
import com.iemr.consent.service.grant.AccessGrantServiceImpl;
import com.iemr.consent.service.grant.AccessGrantWriter;
import com.iemr.consent.service.grant.GrantReplayStore;
import com.iemr.consent.service.otp.CommonApiOtpClient;
import com.iemr.consent.service.otp.OtpChallenge;
import com.iemr.consent.service.otp.OtpChallengeStore;
import com.iemr.consent.service.otp.OtpServiceImpl;
import com.iemr.consent.service.principal.ConsentViewMapper;
import com.iemr.consent.service.principal.EffectiveStatus;
import com.iemr.consent.service.principal.PendingActionServiceImpl;
import com.iemr.consent.service.principal.PreferenceChangeWriter;
import com.iemr.consent.service.principal.PrincipalConsentServiceImpl;
import com.iemr.consent.service.principal.PrincipalEventFactory;
import com.iemr.consent.service.principal.WithdrawalWriter;
import com.iemr.consent.support.ConsentFixtures;
import com.iemr.consent.support.LogCapture;
import com.iemr.consent.support.PrincipalFixtures;
import com.iemr.consent.support.TestFixtures;
import com.iemr.consent.support.TestTokens;
import com.iemr.consent.utils.auth.JwtValidator;
import com.iemr.consent.utils.auth.PublicPaths;
import com.iemr.consent.utils.auth.SessionValidator;
import com.iemr.consent.utils.auth.TokenDenylist;
import com.iemr.consent.utils.cors.OriginPolicy;
import com.iemr.consent.utils.crypto.FieldEncryptor;
import com.iemr.consent.utils.exception.ErrorResponseWriter;
import com.iemr.consent.utils.idempotency.RequestHasher;

/**
 * Preference centre end to end (quickstart scenarios 1 – 15): the real filter chain, controllers and services
 * of feature 003 over in-memory repositories, Redis and OTP store. Scenario ids are in the display names.
 */
@WebMvcTest(controllers = { PrincipalConsentController.class, AccessGrantController.class },
		properties = { "jwt.secret=" + TestTokens.SECRET, "cors.allowed-origins=" + TestFixtures.ALLOWED_ORIGINS,
			"consent.evidence.storage-dir=" + TestFixtures.STORAGE_DIR,
			"consent.common-api.base-url=" + TestFixtures.COMMON_API_URL,
			"consent.encryption.key=" + TestFixtures.ENCRYPTION_KEY })
@Import({ ErrorResponseWriter.class, OriginPolicy.class, PublicPaths.class, JwtValidator.class,
	SessionValidator.class, SecurityProperties.class, RequestHasher.class, FieldEncryptor.class,
	PrincipalConsentServiceImpl.class, PendingActionServiceImpl.class, PreferenceChangeWriter.class,
	WithdrawalWriter.class, PrincipalEventFactory.class, EffectiveStatus.class, ConsentViewMapper.class,
	AccessGrantServiceImpl.class, AccessGrantWriter.class, GrantReplayStore.class, OtpServiceImpl.class,
	AuditLogServiceImpl.class, PreferenceCentreFlowTest.SliceConfig.class })
class PreferenceCentreFlowTest {

	private static final String WORKER = TestTokens.valid("42", "worker42", "jti-42");

	private static final String DOCTOR = TestTokens.valid("77", "doctor77", "jti-77");

	private static final String BASE = "/consent/v1/principals/1001/";

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
	private ObjectMapper json;

	@MockBean
	private TokenDenylist tokenDenylist;

	@MockBean
	private StringRedisTemplate redis;

	@MockBean
	private NoticeRepository noticeRepository;

	@MockBean
	private NoticePurposeRepository noticePurposeRepository;

	@MockBean
	private PurposeRepository purposeRepository;

	@MockBean
	private ConsentRepository consentRepository;

	@MockBean
	private ConsentPurposeRepository decisionRepository;

	@MockBean
	private GuardianLinkRepository guardianLinkRepository;

	@MockBean
	private ConsentEvidenceRepository evidenceRepository;

	@MockBean
	private AccessGrantRepository grantRepository;

	@MockBean
	private AuditLogRepository auditLogRepository;

	@MockBean
	private OtpChallengeStore otpStore;

	@MockBean
	private CommonApiOtpClient commonApi;

	private final Map<String, Consent> events = new HashMap<>();

	private final List<ConsentPurpose> rows = new ArrayList<>();

	private final Map<String, AccessGrant> grants = new HashMap<>();

	private final Map<String, String> redisValues = new HashMap<>();

	private final Map<String, OtpChallenge> challenges = new HashMap<>();

	@BeforeEach
	void setUp() {
		notices();
		consents();
		accessGrants();
		otpAndRedis();
		AuditLogEntry genesis = new AuditLogEntry();
		genesis.setEntryHash("0".repeat(64));
		when(auditLogRepository.findTopByOrderByIdDesc()).thenReturn(Optional.of(genesis));
		Consent first = PrincipalFixtures.event("c-1", TestFixtures.NOW.minusSeconds(86400));
		events.put("c-1", first);
		rows.add(PrincipalFixtures.row("c-1", "P-REG", PurposeStatus.GRANTED, 1, null));
		rows.add(PrincipalFixtures.row("c-1", "P-REM", PurposeStatus.GRANTED, 1,
				TestFixtures.NOW.plus(Duration.ofDays(300))));
		rows.add(PrincipalFixtures.row("c-1", "P-FAM", PurposeStatus.DENIED, 1, null));
	}

	private void notices() {
		Notice notice = ConsentFixtures.notice(NoticeStatus.PUBLISHED);
		when(noticeRepository.findFirstByContextAndStatusAndDeletedFalseOrderByVersionNoDesc(
				NoticeContext.REGISTRATION, NoticeStatus.PUBLISHED)).thenReturn(Optional.of(notice));
		when(noticeRepository.findById(ConsentFixtures.NOTICE_ID)).thenReturn(Optional.of(notice));
		when(noticePurposeRepository.findByNoticeVersionIdAndDeletedFalseOrderByDisplayRankAsc(
				ConsentFixtures.NOTICE_ID)).thenReturn(List.of(noticePurpose("P-REG"), noticePurpose("P-REM")));
		List<Purpose> catalogue = new ArrayList<>(ConsentFixtures.purposes());
		catalogue.add(ConsentFixtures.purpose("P-FAM", false, 180, 1));
		when(purposeRepository.findByPurposeCodeInAndActiveTrueAndDeletedFalse(any())).thenReturn(catalogue);
	}

	private static NoticePurpose noticePurpose(String code) {
		NoticePurpose purpose = new NoticePurpose();
		purpose.setPurposeCode(code);
		return purpose;
	}

	private void consents() {
		when(consentRepository.saveAndFlush(any())).thenAnswer(i -> {
			Consent c = i.getArgument(0);
			events.put(c.getConsentId(), c);
			return c;
		});
		when(consentRepository.findByCapturedByAndRequestKey(any(), anyString())).thenAnswer(i -> events.values()
				.stream().filter(c -> c.getCapturedBy().equals(i.getArgument(0))
						&& c.getRequestKey().equals(i.getArgument(1)))
				.findFirst());
		when(consentRepository.findByBeneficiaryRegIdOrderByServerReceivedAtDesc(any())).thenAnswer(i -> events
				.values().stream().sorted(Comparator.comparing(Consent::getServerReceivedAt).reversed()).toList());
		when(consentRepository.findByConsentIdIn(any())).thenAnswer(
				i -> events.values().stream().filter(c -> i.getArgument(0, List.class).contains(c.getConsentId()))
						.toList());
		when(decisionRepository.findCurrent(any(), anyString())).thenAnswer(i -> rows.stream()
				.filter(r -> r.isCurrent() && r.getPurposeCode().equals(i.getArgument(1))).findFirst());
		when(decisionRepository.findByBeneficiaryRegIdAndCurrentTrue(any()))
				.thenAnswer(i -> rows.stream().filter(ConsentPurpose::isCurrent).toList());
		when(decisionRepository.findByConsentIdInOrderByConsentPurposeIdAsc(any())).thenAnswer(i -> List.copyOf(rows));
		when(decisionRepository.save(any())).thenAnswer(i -> {
			rows.add(i.getArgument(0));
			return i.getArgument(0);
		});
	}

	private void accessGrants() {
		when(grantRepository.saveAndFlush(any())).thenAnswer(i -> {
			AccessGrant g = i.getArgument(0);
			grants.put(g.getGrantId(), g);
			return g;
		});
		when(grantRepository.findByRequestedByAndRequestKey(any(), anyString())).thenAnswer(i -> grants.values()
				.stream().filter(g -> g.getRequestedBy().equals(i.getArgument(0))
						&& g.getRequestKey().equals(i.getArgument(1)))
				.findFirst());
		when(grantRepository.findLockedByGrantId(anyString()))
				.thenAnswer(i -> Optional.ofNullable(grants.get(i.getArgument(0, String.class))));
		when(grantRepository.findById(anyString()))
				.thenAnswer(i -> Optional.ofNullable(grants.get(i.getArgument(0, String.class))));
		when(grantRepository.findByBeneficiaryRegIdAndDeletedFalseOrderByCreatedDateDesc(any()))
				.thenAnswer(i -> List.copyOf(grants.values()));
		when(grantRepository.findLockedByBeneficiaryRegIdAndStatusIn(any(), any())).thenAnswer(i -> grants.values()
				.stream().filter(g -> i.getArgument(1, List.class).contains(g.getStatus())).toList());
	}

	@SuppressWarnings("unchecked")
	private void otpAndRedis() {
		when(otpStore.find(anyString()))
				.thenAnswer(i -> Optional.ofNullable(challenges.get(i.getArgument(0, String.class))));
		ValueOperations<String, String> values = mock(ValueOperations.class);
		when(redis.opsForValue()).thenReturn(values);
		when(values.get(anyString())).thenAnswer(i -> redisValues.get(i.getArgument(0, String.class)));
		doAnswer(i -> redisValues.put(i.getArgument(0), i.getArgument(1))).when(values)
				.set(anyString(), anyString(), any(Duration.class));
	}

	private String verifiedOtp(OtpReason reason, String refId) {
		String id = UUID.randomUUID().toString();
		challenges.put(id, new OtpChallenge(id, reason, "enc", "hash", "******3210", refId, 42L,
				TestFixtures.NOW.plusSeconds(300), TestFixtures.NOW, false, 0));
		return id;
	}

	private static MockHttpServletRequestBuilder write(String token, String path, String key, String body) {
		return post(path).header("JwtToken", token).header("Idempotency-Key", key)
				.contentType(MediaType.APPLICATION_JSON).content(body);
	}

	private JsonNode data(MockHttpServletRequestBuilder request, int status) throws Exception {
		String body = mvc.perform(request).andExpect(status().is(status)).andReturn().getResponse()
				.getContentAsString();
		return json.readTree(body).get("data");
	}

	private String requestAccess(String key) throws Exception {
		return data(write(DOCTOR, "/consent/v1/access-grants", key, "{\"beneficiaryRegId\":1001,"
				+ "\"purposeCodes\":[\"P-REM\"],\"days\":7,\"reason\":\"Referral follow-up\"}"), 201)
				.get("grantId").asText();
	}

	@Test
	@DisplayName("H3 / C4 / C14: pending actions for a consented patient, a new patient and a material change")
	void pendingActions() throws Exception {
		mvc.perform(get(BASE + "pending-actions").header("JwtToken", WORKER)).andExpect(status().isOk())
				.andExpect(jsonPath("$.data").isEmpty());

		rows.clear();
		mvc.perform(get(BASE + "pending-actions").header("JwtToken", WORKER)).andExpect(status().isOk())
				.andExpect(jsonPath("$.data[0].reason").value("CONSENT_MISSING"))
				.andExpect(jsonPath("$.data[0].purposeCodes.length()").value(2));

		rows.add(PrincipalFixtures.row("c-1", "P-REG", PurposeStatus.GRANTED, 1, null));
		rows.add(PrincipalFixtures.row("c-1", "P-REM", PurposeStatus.DENIED, 1, null));
		Notice newer = ConsentFixtures.notice(NoticeStatus.PUBLISHED);
		newer.setPublishedAt(TestFixtures.NOW.minusSeconds(60));
		when(noticeRepository.findNewerMaterial("MMU-REGISTRATION", 1)).thenReturn(List.of(newer));
		mvc.perform(get(BASE + "pending-actions").header("JwtToken", WORKER)).andExpect(status().isOk())
				.andExpect(jsonPath("$.data[0].reason").value("RECONSENT_REQUIRED"))
				.andExpect(jsonPath("$.data[0].dueSince").value("2026-10-29T09:59:00Z"));
	}

	@Test
	@DisplayName("H6 / T3 / C11 / T5 / C18 / C20 / C12: approve access, withdraw, repeat, stale and refused")
	void accessApprovalThenWithdrawalEndsIt() throws Exception {
		mvc.perform(get(BASE + "consents").header("JwtToken", WORKER)).andExpect(status().isOk())
				.andExpect(jsonPath("$.data.current.length()").value(3))
				.andExpect(jsonPath("$.data.history[0].consentId").value("c-1"));

		String grantId = requestAccess(UUID.randomUUID().toString());
		assertThat(grants.get(grantId).getGranteeUserId()).isEqualTo(77L);
		mvc.perform(get(BASE + "access-grants").param("status", "PENDING").header("JwtToken", WORKER))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data[0].grantId").value(grantId));
		String otp = verifiedOtp(OtpReason.ACCESS_GRANT, grantId);
		JsonNode approved = data(write(WORKER, "/consent/v1/access-grants/" + grantId + "/approve",
				UUID.randomUUID().toString(), "{\"otpChallengeId\":\"" + otp + "\"}"), 200);
		assertThat(approved.get("status").asText()).isEqualTo("ACTIVE");
		assertThat(approved.get("validUntil").asText()).isEqualTo("2026-11-05T10:00:00Z");
		verify(otpStore).update(challenges.get(otp).markedUsed());

		String key = UUID.randomUUID().toString();
		String withdrawal = "{\"purposeCodes\":[\"P-REM\"],\"versions\":{\"P-REM\":1}}";
		JsonNode first = data(write(WORKER, BASE + "withdrawals", key, withdrawal), 200);
		assertThat(first.at("/current/2/purposeCode").asText()).isEqualTo("P-REM");
		assertThat(first.at("/current/2/status").asText()).isEqualTo("WITHDRAWN");
		assertThat(first.at("/current/2/versionNo").asInt()).isEqualTo(2);
		assertThat(grants.get(grantId).getStatus().name()).isEqualTo("REVOKED");
		assertThat(grants.get(grantId).getRevokeReason().name()).isEqualTo("WITHDRAWAL");

		data(write(WORKER, BASE + "withdrawals", key, withdrawal), 200);
		assertThat(events.values()).filteredOn(e -> e.getEventType() == EventType.WITHDRAWAL).hasSize(1);
		mvc.perform(write(WORKER, "/consent/v1/principals/2002/withdrawals", key, withdrawal))
				.andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("STATE_CONFLICT"));

		mvc.perform(write(WORKER, BASE + "withdrawals", UUID.randomUUID().toString(), withdrawal))
				.andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("STALE_VERSION"));
		mvc.perform(write(WORKER, BASE + "withdrawals", UUID.randomUUID().toString(),
				"{\"purposeCodes\":[\"P-REG\"],\"versions\":{\"P-REG\":1}}")).andExpect(status().isUnprocessableEntity())
				.andExpect(jsonPath("$.error.code").value("PURPOSE_NOT_WITHDRAWABLE"))
				.andExpect(jsonPath("$.error.purposeCode").value("P-REG"));
		logs.assertNoneContain("Referral follow-up", "******3210", WORKER);
	}

	@Test
	@DisplayName("C10 / C11: grant a purpose with an OTP, then ALL withdraws what it can")
	void grantWithOtpThenWithdrawAll() throws Exception {
		String otp = verifiedOtp(OtpReason.CONSENT, "1001");
		JsonNode granted = data(write(WORKER, BASE + "preferences", UUID.randomUUID().toString(),
				"{\"purposeCodes\":[\"P-FAM\"],\"decision\":\"GRANT\",\"versions\":{\"P-FAM\":1},"
						+ "\"otpChallengeId\":\"" + otp + "\"}"), 200);
		assertThat(granted.at("/current/0/purposeCode").asText()).isEqualTo("P-FAM");
		assertThat(granted.at("/current/0/status").asText()).isEqualTo("GRANTED");
		assertThat(granted.at("/history/0/mode").asText()).isEqualTo("OTP");
		verify(evidenceRepository).save(any());

		JsonNode all = data(write(WORKER, BASE + "withdrawals", UUID.randomUUID().toString(),
				"{\"purposeCodes\":\"ALL\",\"versions\":{\"P-FAM\":2,\"P-REM\":1}}"), 200);
		assertThat(all.get("notWithdrawn").get(0).asText()).isEqualTo("P-REG");
		assertThat(all.at("/current/0/status").asText()).isEqualTo("WITHDRAWN");
		assertThat(all.at("/current/2/status").asText()).isEqualTo("WITHDRAWN");
	}

	@Test
	@DisplayName("T3: the patient cancels a pending request; a second cancel is a conflict")
	void cancelAPendingRequest() throws Exception {
		String grantId = requestAccess(UUID.randomUUID().toString());
		JsonNode revoked = data(write(WORKER, "/consent/v1/access-grants/" + grantId + "/revoke",
				UUID.randomUUID().toString(), "{}"), 200);
		assertThat(revoked.get("status").asText()).isEqualTo("REVOKED");
		assertThat(revoked.get("revokeReason").asText()).isEqualTo("PATIENT_REQUEST");
		mvc.perform(write(WORKER, "/consent/v1/access-grants/" + grantId + "/revoke", UUID.randomUUID().toString(),
				"{}")).andExpect(status().isConflict());
	}

	@Test
	@DisplayName("API Response Standard: every new endpoint's errors carry statusCode = HTTP status and a code")
	void everyNewEndpointErrorFollowsTheResponseContract() throws Exception {
		String grant = "/consent/v1/access-grants/" + UUID.randomUUID();
		List<MockHttpServletRequestBuilder> refused = List.of(
				get("/consent/v1/principals/0/consents").header("JwtToken", WORKER),
				get(BASE + "pending-actions").param("context", "OTHER").header("JwtToken", WORKER),
				get(BASE + "access-grants").param("status", "BAD").header("JwtToken", WORKER),
				write(WORKER, BASE + "preferences", UUID.randomUUID().toString(), "{}"),
				write(WORKER, BASE + "withdrawals", UUID.randomUUID().toString(), "{}"),
				write(DOCTOR, "/consent/v1/access-grants", UUID.randomUUID().toString(), "{}"),
				write(WORKER, grant + "/approve", UUID.randomUUID().toString(), "{}"),
				write(WORKER, grant + "/revoke", "not-a-key", "{}"));
		for (MockHttpServletRequestBuilder request : refused) {
			String body = mvc.perform(request).andExpect(status().isBadRequest()).andReturn().getResponse()
					.getContentAsString();
			JsonNode response = json.readTree(body);
			assertThat(response.get("statusCode").asInt()).isEqualTo(400);
			assertThat(response.get("success").asBoolean()).isFalse();
			assertThat(response.at("/error/code").asText()).isIn("VALIDATION_FAILED", "MALFORMED_REQUEST");
		}
	}

	@Test
	void everyEndpointNeedsTheLogin() throws Exception {
		mvc.perform(get(BASE + "consents")).andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));
		mvc.perform(post(BASE + "withdrawals").contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isUnauthorized());
	}
}
