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

package com.iemr.consent.utils.cors;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.iemr.consent.support.TestFixtures;

class OriginPolicyTest {

	private static OriginPolicy policy(String origins) {
		return new OriginPolicy(TestFixtures.securityProperties(origins, false, true));
	}

	@Test
	void exactMatch() {
		OriginPolicy policy = policy("http://localhost:4200");

		assertThat(policy.isAllowed("http://localhost:4200")).isTrue();
		assertThat(policy.isAllowed("http://localhost:4201")).isFalse();
	}

	@Test
	void wildcardMatches() {
		OriginPolicy policy = policy("http://localhost:*, https://*.piramalswasthya.org");

		assertThat(policy.isAllowed("http://localhost:8080")).isTrue();
		assertThat(policy.isAllowed("https://amrit.piramalswasthya.org")).isTrue();
		assertThat(policy.isAllowed("https://piramalswasthya.org.evil.example")).isFalse();
		assertThat(policy.isAllowed("http://evil.example")).isFalse();
	}

	@Test
	void regexMetacharactersAreLiteral() {
		OriginPolicy policy = policy("https://a+b.example.org");

		assertThat(policy.isAllowed("https://a+b.example.org")).isTrue();
		assertThat(policy.isAllowed("https://aab.example.org")).isFalse();
		assertThat(policy("https://x.example.org").isAllowed("https://xxexample.org")).isFalse();
	}

	@Test
	void allowedOriginsListEmptyOrNotConfiguredRefusesAllBrowserOrigins() {
		assertThat(policy("").isAllowed("http://localhost:4200")).isFalse();
		assertThat(policy(null).isAllowed("http://localhost:4200")).isFalse();
	}

	@Test
	void nullOriginIsNeverAllowed() {
		assertThat(policy("*").isAllowed(null)).isFalse();
		assertThat(policy("*").isAllowed("anything")).isTrue();
	}
}
