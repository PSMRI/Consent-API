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

import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobExecutionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.quartz.QuartzJobBean;

import com.iemr.consent.service.evidence.EvidenceCleanupService;

/** Quartz job: removes unlinked evidence older than the retention window (feature 004, R8). */
@DisallowConcurrentExecution
public class EvidenceCleanupJob extends QuartzJobBean {

	private static final Logger LOGGER = LoggerFactory.getLogger(EvidenceCleanupJob.class);

	private final EvidenceCleanupService cleanupService;

	public EvidenceCleanupJob(EvidenceCleanupService cleanupService) {
		this.cleanupService = cleanupService;
	}

	@Override
	protected void executeInternal(JobExecutionContext context) {
		LOGGER.info("Evidence clean-up removed {} unlinked file(s)", cleanupService.purgeExpired());
	}
}
