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

package com.iemr.consent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;

class ConsentApiApplicationTest {

	@Test
	void mainRunsSpringApplication() {
		try (MockedStatic<SpringApplication> spring = mockStatic(SpringApplication.class)) {
			String[] args = { "--x=1" };
			ConsentApiApplication.main(args);
			spring.verify(() -> SpringApplication.run(eq(ConsentApiApplication.class), eq(args)));
		}
	}

	@Test
	void configureRegistersTheApplicationForWarDeployment() {
		SpringApplicationBuilder builder = mock(SpringApplicationBuilder.class);
		when(builder.sources(any(Class[].class))).thenReturn(builder);

		assertThat(new ConsentApiApplication().configure(builder)).isSameAs(builder);
		verify(builder).sources(ConsentApiApplication.class);
	}
}
