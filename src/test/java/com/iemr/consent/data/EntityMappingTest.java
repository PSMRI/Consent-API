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

package com.iemr.consent.data;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.junit.jupiter.api.Test;

import com.iemr.consent.data.audit.AuditLogEntry;
import com.iemr.consent.data.consent.Consent;
import com.iemr.consent.data.consent.ConsentPurpose;
import com.iemr.consent.data.consent.GuardianLink;
import com.iemr.consent.data.evidence.ConsentEvidence;
import com.iemr.consent.data.grant.AccessGrant;
import com.iemr.consent.data.evidence.EvidenceType;
import com.iemr.consent.data.notice.Notice;
import com.iemr.consent.data.notify.ConsentNotification;
import com.iemr.consent.data.notice.NoticePurpose;
import com.iemr.consent.data.notice.NoticePurposeId;
import com.iemr.consent.data.notice.NoticeTranslation;
import com.iemr.consent.data.notice.Purpose;

import jakarta.persistence.Column;
import jakarta.persistence.Table;

/**
 * Entities must map exactly the columns of AMRIT-DB {@code V30__Consent_Manager_Capture.sql}
 * (data-model.md); checked by reflection, no database needed.
 */
class EntityMappingTest {

	private static final Set<String> AUDIT = Set.of("CreatedBy", "CreatedDate", "ModifiedBy", "LastModDate",
			"Deleted");

	private static final Map<Class<?>, List<String>> EXPECTED = Map.of(
			Purpose.class, List.of("cm_purpose", "purpose_id", "purpose_code", "version", "name", "description",
					"legal_basis", "is_mandatory", "is_withdrawable", "validity_days", "is_active"),
			Notice.class, List.of("cm_notice", "notice_version_id", "notice_code", "version_no", "context",
					"is_material", "status", "approved_by", "published_at", "project_id", "grievance_officer_name",
					"grievance_phone", "grievance_email", "grievance_address", "grievance_office_hours"),
			NoticeTranslation.class, List.of("cm_notice_translation", "translation_id", "notice_version_id",
					"language_code", "language_name", "body", "data_items", "retention", "recipients",
					"withdrawal_info", "grievance_info", "text_hash", "audio_ref", "helpline_script", "declaration",
					"representative_declaration"),
			NoticePurpose.class, List.of("cm_notice_purpose", "notice_version_id", "purpose_code", "display_rank"),
			Consent.class, List.of("cm_consent", "consent_id", "beneficiary_reg_id", "event_type",
					"consenting_party", "notice_version_id", "language_code", "text_hash", "channel", "mode",
					"captured_by", "provider_service_map_id", "facility_id", "service_point_id", "service_line",
					"device_id", "call_id", "captured_at", "server_received_at", "capture_time_flagged",
					"sync_status", "project_id", "config_version", "request_key", "request_hash"),
			ConsentPurpose.class, List.of("cm_consent_purpose", "consent_purpose_id", "consent_id",
					"beneficiary_reg_id", "purpose_code", "decision", "status", "version_no", "is_current",
					"valid_until", "current_key"),
			ConsentEvidence.class, List.of("cm_consent_evidence", "evidence_id", "consent_id", "type", "file_ref",
					"sha256", "content_type", "size_bytes", "duration_sec", "witness_name", "witness_relationship",
					"otp_status", "masked_phone", "call_id", "uploaded_by", "request_key", "request_hash",
					"created_at", "declaration_text", "declaration_wording", "declaration_app_default"),
			GuardianLink.class, List.of("cm_guardian_link", "guardian_link_id", "consent_id", "beneficiary_reg_id",
					"guardian_type", "guardian_name", "guardian_phone", "relationship", "verification_method",
					"verified_at", "follow_up_required", "valid_until"),
			AuditLogEntry.class, List.of("cm_audit_log", "id", "action", "consent_id", "beneficiary_reg_id",
					"purpose", "status", "initiator", "source_ip", "created_at", "prev_hash", "entry_hash"),
			AccessGrant.class, List.of("cm_access_grant", "grant_id", "beneficiary_reg_id", "grant_type",
					"grantee_user_id", "grantee_name", "grantee_facility_id", "purposes", "reason", "days", "status",
					"valid_until", "approved_at", "approved_by", "approval_masked_phone", "revoked_at", "reviewed_by",
					"revoke_reason", "requested_by", "request_key", "request_hash"));

	private static final Set<Class<?>> APPEND_ONLY = Set.of(Consent.class, ConsentPurpose.class,
			AuditLogEntry.class);

	@Test
	void everyEntityMapsExactlyTheMigrationColumns() {
		EXPECTED.forEach((entity, expected) -> {
			assertThat(entity.getAnnotation(Table.class).name()).as(entity.getSimpleName())
					.isEqualTo(expected.get(0));
			Set<String> want = new TreeSet<>(expected.subList(1, expected.size()));
			if (!APPEND_ONLY.contains(entity)) {
				want.addAll(AUDIT);
			}
			assertThat(columns(entity)).as(entity.getSimpleName()).isEqualTo(want);
		});
	}

	@Test
	void notificationMapsTheV35ColumnsWithoutAMaskedPhone() {
		Set<String> want = new TreeSet<>(List.of("notification_id", "consent_id", "channel", "phone", "status",
				"attempted_at"));
		want.addAll(AUDIT);
		assertThat(ConsentNotification.class.getAnnotation(Table.class).name()).isEqualTo("cm_consent_notification");
		assertThat(columns(ConsentNotification.class)).isEqualTo(want);
	}

	@Test
	void appendOnlyTablesHaveNoSoftDeleteColumns() {
		for (Class<?> entity : APPEND_ONLY) {
			assertThat(AuditColumns.class.isAssignableFrom(entity)).as(entity.getSimpleName()).isFalse();
		}
	}

	@Test
	void accessGrantStoresPurposesSortedAndUnique() {
		AccessGrant grant = new AccessGrant();
		grant.setGrantId("g-1");
		grant.setPurposeList(List.of("P-REM", "P-FAM", "P-REM"));
		assertThat(grant.getPurposes()).isEqualTo("P-FAM,P-REM");
		assertThat(grant.purposeList()).containsExactly("P-FAM", "P-REM");
		assertThat(grant).hasToString("AccessGrant[grantId=g-1, status=null]");
	}

	@Test
	void evidenceTypesKnowWhichAreFilesAndUploadable() {
		assertThat(EvidenceType.VOICE_CLIP.isFile()).isTrue();
		assertThat(EvidenceType.WITNESS.isFile()).isFalse();
		assertThat(EvidenceType.DECLARATION.isFile()).isFalse();
		assertThat(EvidenceType.PATIENT_PHOTO.isFile()).isTrue();
		assertThat(EvidenceType.VOICE_CLIP.isUploadable()).isTrue();
		assertThat(EvidenceType.SIGNED_FORM_PHOTO.isUploadable()).isTrue();
		assertThat(EvidenceType.PATIENT_PHOTO.isUploadable()).isFalse();
		assertThat(EvidenceType.DECLARATION.isUploadable()).isFalse();
		assertThat(new NoticePurposeId(1L, "P-REG")).isEqualTo(new NoticePurposeId(1L, "P-REG"));
	}

	private static Set<String> columns(Class<?> entity) {
		Set<String> names = new TreeSet<>();
		List<Field> fields = new ArrayList<>();
		for (Class<?> type = entity; type != Object.class; type = type.getSuperclass()) {
			fields.addAll(List.of(type.getDeclaredFields()));
		}
		for (Field field : fields) {
			Column column = field.getAnnotation(Column.class);
			if (column != null) {
				names.add(column.name());
			}
		}
		return names;
	}
}
