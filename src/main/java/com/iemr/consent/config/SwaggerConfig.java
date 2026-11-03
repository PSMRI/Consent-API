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

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springdoc.core.customizers.GlobalOpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.iemr.consent.service.version.VersionService;
import com.iemr.consent.utils.exception.ErrorCode;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.BooleanSchema;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

/**
 * API documentation (FR-021, SC-007, research R11), matching
 * {@code specs/001-service-skeleton/contracts/consent-api-skeleton.openapi.yaml}.
 *
 * <ul>
 * <li>Three login schemes so Swagger UI's "Authorize" works: {@code JwtToken} header,
 * {@code Jwttoken} cookie and the {@code Authorization} session key.</li>
 * <li>The {@code ApiResponse}, {@code ErrorBody}, {@code FieldErrorDetail} and {@code ErrorCode}
 * schemas; the {@code ErrorCode} enum is built from {@link ErrorCode#values()}, so the published
 * list cannot drift from the code.</li>
 * <li>Standard 401, 403, 500 and 503 responses on every operation; {@code /health} and
 * {@code /version} are public (no security).</li>
 * </ul>
 * Docs are served only where {@code springdoc.api-docs.enabled=true}.
 */
@Configuration
public class SwaggerConfig {

	static final String JWT_HEADER_SCHEME = "JwtTokenHeader";

	static final String JWT_COOKIE_SCHEME = "JwtTokenCookie";

	static final String SESSION_SCHEME = "SessionAuthorization";

	static final Set<String> PUBLIC_PATHS = Set.of("/health", "/version");

	private static final String SCHEMAS = "#/components/schemas/";

	private static final Map<String, String> STANDARD_ERRORS = Map.of("401", "Not logged in or session expired",
			"403", "Origin not allowed, access denied or no consent", "500", "Unexpected error", "503",
			"Required dependency unavailable");

	@Bean
	public OpenAPI consentOpenApi(VersionService versionService) {
		Info info = new Info().title("Consent-API")
				.description("AMRIT DPDPA consent manager. "
						+ "Every response uses the standard ApiResponse body.")
				.version(versionService.getBuildInfo().version());
		Components components = new Components()
				.addSecuritySchemes(JWT_HEADER_SCHEME, apiKey(SecurityScheme.In.HEADER, "JwtToken"))
				.addSecuritySchemes(JWT_COOKIE_SCHEME, apiKey(SecurityScheme.In.COOKIE, "Jwttoken"))
				.addSecuritySchemes(SESSION_SCHEME, apiKey(SecurityScheme.In.HEADER, "Authorization"));
		return new OpenAPI().info(info).components(components)
				.addSecurityItem(new SecurityRequirement().addList(JWT_HEADER_SCHEME))
				.addSecurityItem(new SecurityRequirement().addList(JWT_COOKIE_SCHEME))
				.addSecurityItem(new SecurityRequirement().addList(SESSION_SCHEME));
	}

	@Bean
	public GlobalOpenApiCustomizer standardResponsesCustomizer() {
		return SwaggerConfig::customize;
	}

	static void customize(OpenAPI openApi) {
		if (openApi.getComponents() == null) {
			openApi.setComponents(new Components());
		}
		openApi.getComponents().addSchemas("ErrorCode", errorCodeSchema())
				.addSchemas("FieldErrorDetail", fieldErrorDetailSchema())
				.addSchemas("ErrorBody", errorBodySchema())
				.addSchemas("ApiResponse", apiResponseSchema());
		if (openApi.getPaths() == null) {
			return;
		}
		openApi.getPaths().forEach((path, item) -> customize(path, item));
	}

	private static void customize(String path, PathItem item) {
		for (Operation operation : item.readOperations()) {
			if (operation.getResponses() == null) {
				operation.setResponses(new ApiResponses());
			}
			STANDARD_ERRORS.forEach((status, description) -> {
				if (!operation.getResponses().containsKey(status)) {
					operation.getResponses().addApiResponse(status, errorResponse(description));
				}
			});
			if (PUBLIC_PATHS.contains(path)) {
				operation.setSecurity(List.of());
			}
		}
	}

	private static SecurityScheme apiKey(SecurityScheme.In in, String name) {
		return new SecurityScheme().type(SecurityScheme.Type.APIKEY).in(in).name(name);
	}

	private static ApiResponse errorResponse(String description) {
		return new ApiResponse().description(description).content(new Content().addMediaType("application/json",
				new MediaType().schema(new Schema<>().$ref(SCHEMAS + "ApiResponse"))));
	}

	@SuppressWarnings("rawtypes")
	private static Schema errorCodeSchema() {
		StringSchema schema = new StringSchema();
		schema.setDescription("Stable machine-readable error code; additive only, never renamed or reused.");
		schema.setEnum(Arrays.stream(ErrorCode.values()).map(Enum::name).toList());
		return schema;
	}

	@SuppressWarnings("rawtypes")
	private static Schema fieldErrorDetailSchema() {
		return new ObjectSchema().addProperty("field", new StringSchema())
				.addProperty("message", new StringSchema()).required(List.of("field", "message"));
	}

	@SuppressWarnings("rawtypes")
	private static Schema errorBodySchema() {
		return new ObjectSchema().addProperty("code", new Schema<>().$ref(SCHEMAS + "ErrorCode"))
				.addProperty("purposeCode", new StringSchema().nullable(true))
				.addProperty("details", detailsSchema())
				.required(List.of("code"));
	}

	@SuppressWarnings("rawtypes")
	private static Schema detailsSchema() {
		Schema<?> item = new Schema<>().$ref(SCHEMAS + "FieldErrorDetail");
		return new ArraySchema().items(item).nullable(true);
	}

	@SuppressWarnings("rawtypes")
	private static Schema apiResponseSchema() {
		return new ObjectSchema().addProperty("success", new BooleanSchema())
				.addProperty("statusCode",
						new IntegerSchema().description("Always equals the HTTP status."))
				.addProperty("message", new StringSchema())
				.addProperty("data", new ObjectSchema().nullable(true))
				.addProperty("error", new Schema<>().$ref(SCHEMAS + "ErrorBody").nullable(true))
				.addProperty("timestamp", new StringSchema().format("date-time"))
				.addProperty("traceId",
						new StringSchema().description("Same value as the X-Trace-Id header."))
				.required(List.of("success", "statusCode", "message", "timestamp", "traceId"));
	}
}
