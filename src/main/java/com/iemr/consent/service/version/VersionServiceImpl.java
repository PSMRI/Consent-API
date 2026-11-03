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

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

/**
 * Reads {@code git.properties}, written at build time by {@code git-commit-id-maven-plugin}, as
 * Common-API's {@code controller/version/VersionController} does (research R8). A missing file,
 * key or read error gives {@code "unknown"}.
 */
@Service
public class VersionServiceImpl implements VersionService {

	static final String UNKNOWN = "unknown";

	private final Resource gitProperties;

	public VersionServiceImpl(@Value("classpath:git.properties") Resource gitProperties) {
		this.gitProperties = gitProperties;
	}

	@Override
	public BuildInfo getBuildInfo() {
		Properties properties = load();
		return new BuildInfo(properties.getProperty("git.build.version", UNKNOWN),
				properties.getProperty("git.build.time", UNKNOWN),
				properties.getProperty("git.branch", UNKNOWN),
				properties.getProperty("git.commit.id.abbrev", UNKNOWN));
	}

	private Properties load() {
		Properties properties = new Properties();
		if (!gitProperties.exists()) {
			return properties;
		}
		try (InputStream input = gitProperties.getInputStream()) {
			properties.load(input);
		} catch (IOException e) {
			return new Properties();
		}
		return properties;
	}
}
