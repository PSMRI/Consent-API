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

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.iemr.consent.data.notice.Notice;
import com.iemr.consent.data.notice.NoticeContext;
import com.iemr.consent.data.notice.NoticeStatus;

/** Notice versions ({@code cm_notice}). */
@Repository
public interface NoticeRepository extends JpaRepository<Notice, Long> {

	/** The active notice: the highest published version for a context. */
	Optional<Notice> findFirstByContextAndStatusAndDeletedFalseOrderByVersionNoDesc(NoticeContext context,
			NoticeStatus status);

	/** Published material versions of a notice newer than the given one, newest first (C14). */
	@Query("select n from Notice n where n.noticeCode = :code and n.versionNo > :version"
			+ " and n.status = com.iemr.consent.data.notice.NoticeStatus.PUBLISHED and n.material = true"
			+ " and n.deleted = false order by n.versionNo desc")
	List<Notice> findNewerMaterial(@Param("code") String noticeCode, @Param("version") Integer versionNo);
}
