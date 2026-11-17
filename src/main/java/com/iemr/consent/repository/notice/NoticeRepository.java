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

package com.iemr.consent.repository.notice;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.iemr.consent.data.notice.Notice;
import com.iemr.consent.data.notice.NoticeContext;
import com.iemr.consent.data.notice.NoticeStatus;

/** Notice versions ({@code cm_notice}). */
@Repository
public interface NoticeRepository extends JpaRepository<Notice, Long> {

	/** The active central notice: the highest published version for a context with no project (feature 004). */
	Optional<Notice> findFirstByContextAndProjectIdIsNullAndStatusAndDeletedFalseOrderByVersionNoDesc(
			NoticeContext context, NoticeStatus status);

	/** A project's own active notice: the highest published version for a context and project (feature 004). */
	Optional<Notice> findFirstByContextAndProjectIdAndStatusAndDeletedFalseOrderByVersionNoDesc(
			NoticeContext context, Integer projectId, NoticeStatus status);

	/**
	 * Material versions of a notice newer than the given one that were ever published, newest first (C14). A
	 * version retired by a later publish still counts (feature 006, research R8); a draft never does.
	 */
	@Query("select n from Notice n where n.noticeCode = :code and n.versionNo > :version"
			+ " and n.status in (com.iemr.consent.data.notice.NoticeStatus.PUBLISHED,"
			+ " com.iemr.consent.data.notice.NoticeStatus.RETIRED)"
			+ " and n.publishedAt is not null and n.material = true"
			+ " and n.deleted = false order by n.versionNo desc")
	List<Notice> findNewerMaterial(@Param("code") String noticeCode, @Param("version") Integer versionNo);

	/** A project's notice in one status for a context: its draft or its published version (feature 006). */
	Optional<Notice> findFirstByProjectIdAndContextAndStatusAndDeletedFalse(Integer projectId,
			NoticeContext context, NoticeStatus status);

	/** A project's latest notice for a context in any status: gives its notice code and next version. */
	Optional<Notice> findFirstByProjectIdAndContextAndDeletedFalseOrderByVersionNoDesc(Integer projectId,
			NoticeContext context);

	/** Publishes a draft only if it is still a draft; 0 means someone else published it first (FR-013). */
	@Modifying
	@Query("update Notice n set n.status = com.iemr.consent.data.notice.NoticeStatus.PUBLISHED,"
			+ " n.approvedBy = :approvedBy, n.publishedAt = :publishedAt, n.material = :material,"
			+ " n.modifiedBy = :approvedBy where n.noticeVersionId = :id"
			+ " and n.status = com.iemr.consent.data.notice.NoticeStatus.DRAFT")
	int publishDraft(@Param("id") Long noticeVersionId, @Param("approvedBy") String approvedBy,
			@Param("publishedAt") Instant publishedAt, @Param("material") boolean material);

	/** Retires a project's other published versions for a context after a publish. */
	@Modifying
	@Query("update Notice n set n.status = com.iemr.consent.data.notice.NoticeStatus.RETIRED,"
			+ " n.modifiedBy = :modifiedBy where n.projectId = :projectId and n.context = :context"
			+ " and n.noticeVersionId <> :keepId"
			+ " and n.status = com.iemr.consent.data.notice.NoticeStatus.PUBLISHED")
	int retireOthers(@Param("projectId") Integer projectId, @Param("context") NoticeContext context,
			@Param("keepId") Long keepId, @Param("modifiedBy") String modifiedBy);
}
