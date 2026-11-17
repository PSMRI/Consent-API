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

package com.iemr.consent.service.version;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

class VersionServiceImplTest {

	@Test
	void mapsAllFourGitProperties() {
		String properties = "git.build.version=0.1.0\ngit.build.time=2026-10-29T10:00:00+0530\n"
				+ "git.branch=001-service-skeleton\ngit.commit.id.abbrev=af44d98\n";
		Resource resource = new ByteArrayResource(properties.getBytes(StandardCharsets.UTF_8));

		assertThat(new VersionServiceImpl(resource).getBuildInfo()).isEqualTo(
				new BuildInfo("0.1.0", "2026-10-29T10:00:00+0530", "001-service-skeleton", "af44d98"));
	}

	@Test
	void missingKeysAreUnknown() {
		Resource resource = new ByteArrayResource("git.branch=main\n".getBytes(StandardCharsets.UTF_8));

		assertThat(new VersionServiceImpl(resource).getBuildInfo())
				.isEqualTo(new BuildInfo("unknown", "unknown", "main", "unknown"));
	}

	@Test
	void missingFileIsUnknown() {
		assertThat(new VersionServiceImpl(new ClassPathResource("does-not-exist.properties")).getBuildInfo())
				.isEqualTo(new BuildInfo("unknown", "unknown", "unknown", "unknown"));
	}

	@Test
	void readErrorIsUnknown() throws Exception {
		Resource resource = mock(Resource.class);
		when(resource.exists()).thenReturn(true);
		when(resource.getInputStream()).thenThrow(new IOException("disk"));

		assertThat(new VersionServiceImpl(resource).getBuildInfo().version()).isEqualTo("unknown");
	}
}
