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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.iemr.consent.service.version.BuildInfo;
import com.iemr.consent.service.version.VersionService;
import com.iemr.consent.utils.exception.ErrorCode;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityScheme;

class SwaggerConfigTest {

	private final SwaggerConfig config = new SwaggerConfig();

	private OpenAPI openApi() {
		VersionService versionService = mock(VersionService.class);
		when(versionService.getBuildInfo()).thenReturn(new BuildInfo("0.1.0", "t", "b", "c"));
		return config.consentOpenApi(versionService);
	}

	@Test
	void declaresTheThreeLoginSchemes() {
		OpenAPI openApi = openApi();

		assertThat(openApi.getInfo().getTitle()).isEqualTo("Consent-API");
		assertThat(openApi.getInfo().getVersion()).isEqualTo("0.1.0");
		SecurityScheme header = openApi.getComponents().getSecuritySchemes().get("JwtTokenHeader");
		SecurityScheme cookie = openApi.getComponents().getSecuritySchemes().get("JwtTokenCookie");
		SecurityScheme session = openApi.getComponents().getSecuritySchemes().get("SessionAuthorization");
		assertThat(header.getIn()).isEqualTo(SecurityScheme.In.HEADER);
		assertThat(header.getName()).isEqualTo("JwtToken");
		assertThat(cookie.getIn()).isEqualTo(SecurityScheme.In.COOKIE);
		assertThat(cookie.getName()).isEqualTo("Jwttoken");
		assertThat(session.getName()).isEqualTo("Authorization");
		assertThat(header.getType()).isEqualTo(SecurityScheme.Type.APIKEY);
		assertThat(openApi.getSecurity()).hasSize(3);
	}

	@Test
	void errorCodeSchemaEqualsTheEnumSoDocsCannotDrift() {
		OpenAPI openApi = openApi();
		config.standardResponsesCustomizer().customise(openApi);

		assertThat(openApi.getComponents().getSchemas().get("ErrorCode").getEnum())
				.isEqualTo(Arrays.stream(ErrorCode.values()).map(Enum::name).toList()).hasSize(26);
		assertThat(openApi.getComponents().getSchemas()).containsKeys("ApiResponse", "ErrorBody", "FieldErrorDetail");
	}

	@Test
	void addsStandardErrorsToEveryOperationAndClearsSecurityOnPublicPaths() {
		Operation health = new Operation();
		Operation business = new Operation().responses(new ApiResponses().addApiResponse("403",
				new ApiResponse().description("custom")));
		Operation noResponses = new Operation();
		noResponses.setResponses(null);
		OpenAPI openApi = new OpenAPI().paths(new Paths().addPathItem("/health", new PathItem().get(health))
				.addPathItem("/version", new PathItem().get(noResponses))
				.addPathItem("/consent/v1/x", new PathItem().post(business)));

		SwaggerConfig.customize(openApi);

		assertThat(health.getResponses()).containsKeys("401", "403", "500", "503");
		assertThat(health.getSecurity()).isEmpty();
		assertThat(noResponses.getResponses()).containsKeys("401", "403", "500", "503");
		assertThat(business.getSecurity()).isNull();
		assertThat(business.getResponses().get("403").getDescription()).isEqualTo("custom");
		assertThat(business.getResponses().get("503").getContent().get("application/json").getSchema().get$ref())
				.isEqualTo("#/components/schemas/ApiResponse");
	}

	@Test
	void documentWithoutPathsOrComponentsStillGetsTheSchemas() {
		OpenAPI openApi = new OpenAPI();
		SwaggerConfig.customize(openApi);

		assertThat(openApi.getComponents().getSchemas()).containsKeys("ErrorCode", "ApiResponse");
		assertThat(List.copyOf(openApi.getComponents().getSchemas().keySet())).hasSize(4);
	}
}
