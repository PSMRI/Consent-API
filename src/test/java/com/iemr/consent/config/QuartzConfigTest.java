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

import org.junit.jupiter.api.Test;
import org.quartz.CronTrigger;
import org.quartz.JobDetail;
import org.quartz.Trigger;

import com.iemr.consent.jobs.EvidenceCleanupJob;
import com.iemr.consent.support.TestFixtures;

/** Schedule of the evidence clean-up job (feature 004). */
class QuartzConfigTest {

	@Test
	void cleanupJobRunsOnTheConfiguredCron() {
		QuartzConfig config = new QuartzConfig();
		JobDetail job = config.evidenceCleanupJobDetail();
		Trigger trigger = config.evidenceCleanupTrigger(job, TestFixtures.consentProperties());

		assertThat(job.getJobClass()).isEqualTo(EvidenceCleanupJob.class);
		assertThat(job.isDurable()).isTrue();
		assertThat(trigger.getJobKey()).isEqualTo(job.getKey());
		assertThat(((CronTrigger) trigger).getCronExpression()).isEqualTo("0 0 * * * ?");
	}
}
