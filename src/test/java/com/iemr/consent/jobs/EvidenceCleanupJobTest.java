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

package com.iemr.consent.jobs;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.quartz.JobExecutionContext;

import com.iemr.consent.service.evidence.EvidenceCleanupService;

/** The Quartz job only runs the clean-up service (feature 004). */
class EvidenceCleanupJobTest {

	@Test
	void runsTheCleanup() {
		EvidenceCleanupService service = mock(EvidenceCleanupService.class);
		when(service.purgeExpired()).thenReturn(3);

		new EvidenceCleanupJob(service).executeInternal(mock(JobExecutionContext.class));

		verify(service).purgeExpired();
	}
}
