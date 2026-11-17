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

import org.quartz.CronScheduleBuilder;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.iemr.consent.jobs.EvidenceCleanupJob;

/** Quartz schedule of the evidence clean-up job (feature 004, R8); in-memory store, cron from settings. */
@Configuration
public class QuartzConfig {

	static final String CLEANUP_JOB = "evidenceCleanupJob";

	@Bean
	public JobDetail evidenceCleanupJobDetail() {
		return JobBuilder.newJob(EvidenceCleanupJob.class).withIdentity(CLEANUP_JOB).storeDurably().build();
	}

	@Bean
	public Trigger evidenceCleanupTrigger(JobDetail evidenceCleanupJobDetail, ConsentProperties properties) {
		CronScheduleBuilder schedule = CronScheduleBuilder.cronSchedule(properties.evidence().cleanupCron());
		return TriggerBuilder.newTrigger().forJob(evidenceCleanupJobDetail)
				.withIdentity(CLEANUP_JOB + "Trigger").withSchedule(schedule).build();
	}
}
