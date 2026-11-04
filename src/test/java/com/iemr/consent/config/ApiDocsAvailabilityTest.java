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

package com.iemr.consent.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import javax.sql.DataSource;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.JsonNode;
import com.iemr.consent.repository.notify.ConsentNotificationRepository;
import com.iemr.consent.repository.audit.AuditLogRepository;
import com.iemr.consent.repository.consent.ConsentPurposeRepository;
import com.iemr.consent.repository.consent.ConsentRepository;
import com.iemr.consent.repository.consent.GuardianLinkRepository;
import com.iemr.consent.repository.evidence.ConsentEvidenceRepository;
import com.iemr.consent.repository.grant.AccessGrantRepository;
import com.iemr.consent.repository.notice.NoticePurposeRepository;
import com.iemr.consent.repository.notice.NoticeRequestRepository;
import com.iemr.consent.repository.notice.NoticeRepository;
import com.iemr.consent.repository.notice.NoticeTranslationRepository;
import com.iemr.consent.repository.notice.PurposeRepository;
import com.iemr.consent.support.TestFixtures;
import com.iemr.consent.support.TestTokens;

import jakarta.persistence.EntityManager;

/**
 * User Story 4 on the full application context (database and Redis mocked): docs list exactly
 * the deployed endpoints, and are not served where configuration disables them.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK, properties = {
	"jwt.secret=" + TestTokens.SECRET, "spring.datasource.url=jdbc:mysql://localhost:3306/db_identity",
	"spring.datasource.username=test", "spring.redis.host=localhost",
	"consent.evidence.storage-dir=" + TestFixtures.STORAGE_DIR,
	"consent.common-api.base-url=" + TestFixtures.COMMON_API_URL,
	"consent.encryption.key=" + TestFixtures.ENCRYPTION_KEY,
	"spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
			+ "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,"
			+ "org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration,"
			+ "org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration,"
			+ "org.springframework.boot.autoconfigure.data.redis.RedisReactiveAutoConfiguration" })
@AutoConfigureMockMvc
class ApiDocsAvailabilityTest {

	@MockBean
	DataSource dataSource;

	@MockBean
	RedisConnectionFactory redisConnectionFactory;

	@MockBean
	NoticeRepository noticeRepository;

	@MockBean
	NoticeTranslationRepository noticeTranslationRepository;

	@MockBean
	NoticePurposeRepository noticePurposeRepository;

	@MockBean
	PurposeRepository purposeRepository;

	@MockBean
	ConsentRepository consentRepository;

	@MockBean
	ConsentPurposeRepository consentPurposeRepository;

	@MockBean
	GuardianLinkRepository guardianLinkRepository;

	@MockBean
	ConsentEvidenceRepository consentEvidenceRepository;

	@MockBean
	AuditLogRepository auditLogRepository;

	@MockBean
	AccessGrantRepository accessGrantRepository;

	@MockBean
	ConsentNotificationRepository consentNotificationRepository;

	@MockBean
	PlatformTransactionManager transactionManager;

	@MockBean
	NoticeRequestRepository noticeRequestRepository;

	@MockBean
	EntityManager entityManager;

	@Nested
	@TestPropertySource(properties = { "springdoc.api-docs.enabled=true", "springdoc.swagger-ui.enabled=true" })
	@DisplayName("US4-1: docs enabled")
	class Enabled {

		@Autowired
		MockMvc mvc;

		@Test
		void docsArePublicAndListExactlyTheDeployedEndpoints() throws Exception {
			String json = mvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andReturn().getResponse()
					.getContentAsString();
			JsonNode docs = TestFixtures.objectMapper().readTree(json);

			List<String> paths = new java.util.ArrayList<>();
			docs.get("paths").fieldNames().forEachRemaining(paths::add);
			assertThat(paths).containsExactlyInAnyOrder("/health", "/version", "/consent/v1/notices/active",
					"/consent/v1/notices/active/languages", "/consent/v1/otp/challenges",
					"/consent/v1/otp/challenges/{challengeId}/verify", "/consent/v1/evidence", "/consent/v1/consents",
					"/consent/v1/principals/{benRegId}/consents", "/consent/v1/principals/{benRegId}/pending-actions",
					"/consent/v1/principals/{benRegId}/preferences", "/consent/v1/principals/{benRegId}/withdrawals",
					"/consent/v1/principals/pending-actions/batch",
					"/consent/v1/principals/{benRegId}/access-grants", "/consent/v1/access-grants",
					"/consent/v1/access-grants/{grantId}/approve", "/consent/v1/access-grants/{grantId}/revoke",
					"/consent/v1/admin/projects/{projectId}/notices",
					"/consent/v1/admin/projects/{projectId}/notices/{context}/draft",
					"/consent/v1/admin/projects/{projectId}/notices/{context}/publish");
			assertThat(json).doesNotContain("_test").doesNotContain("/error");
			assertThat(docs.at("/components/schemas/ErrorCode/enum").size()).isEqualTo(26);
			assertThat(docs.at("/components/securitySchemes").size()).isEqualTo(3);
			assertThat(docs.at("/paths/~1health/get/responses").has("503")).isTrue();
		}
	}

	@Nested
	@TestPropertySource(properties = { "springdoc.api-docs.enabled=false", "springdoc.swagger-ui.enabled=false" })
	@DisplayName("US4-2: docs disabled")
	class Disabled {

		@Autowired
		MockMvc mvc;

		@Test
		void docsAreNotServed() throws Exception {
			mvc.perform(get("/v3/api-docs")).andExpect(status().isUnauthorized())
					.andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));
			mvc.perform(get("/swagger-ui.html")).andExpect(status().isUnauthorized());
		}

		@Test
		void versionStillAnswersPublicly() throws Exception {
			mvc.perform(get("/version")).andExpect(status().isOk()).andExpect(jsonPath("$.statusCode").value(200));
		}
	}

}
