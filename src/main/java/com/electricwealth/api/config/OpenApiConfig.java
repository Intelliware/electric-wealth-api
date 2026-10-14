package com.electricwealth.api.config;

import java.util.Map;

import com.electricwealth.api.error.ErrorEnvelope;

import io.swagger.v3.core.converter.AnnotatedType;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;

import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** The OpenAPI spec served at /openapi.json (Swagger UI at /docs), generated from the controllers by springdoc. */
@Configuration
public class OpenApiConfig {

    public static final String BEARER_AUTH = "bearerAuth";

    private static final Map<Integer, String> ERROR_DESCRIPTIONS = Map.of(
            400, "Validation error",
            401, "Missing, expired or not MFA-verified access token",
            404, "Resource not found",
            409, "Conflicts with the current state (e.g. the slot was just taken)",
            422, "The details did not match our records",
            429, "Rate limited");

    @Bean
    OpenAPI openApi(@Value("${server.port}") int port) {
        return new OpenAPI()
                .info(new Info()
                        .title("Electric Wealth API")
                        .version("0.1.0")
                        .description("REST API for the Electric Wealth web app. Money values are integers in minor "
                                + "units (cents). Errors use the ErrorEnvelope shape. Demo sign-in: any persona "
                                + "email, password `Demo123!`, MFA code `123456`."))
                .addServersItem(new Server().url("http://localhost:" + port).description("Local development"))
                .components(new Components().addSecuritySchemes(BEARER_AUTH, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .description("Access token from POST /api/auth/token, after POST /api/auth/mfa/verify.")));
    }

    /** Adds the error responses listed in {@link ApiErrors} to each operation. */
    @Bean
    OperationCustomizer apiErrorsCustomizer() {
        return (operation, handlerMethod) -> {
            ApiErrors errors = handlerMethod.getMethodAnnotation(ApiErrors.class);
            if (errors != null) {
                for (int code : errors.value()) {
                    var content = new Content().addMediaType("application/json",
                            new MediaType().schema(new Schema<>().$ref("#/components/schemas/ErrorEnvelope")));
                    operation.getResponses().addApiResponse(String.valueOf(code), new ApiResponse()
                            .description(ERROR_DESCRIPTIONS.getOrDefault(code, "Error"))
                            .content(content));
                }
            }
            return operation;
        };
    }

    /** Registers the ErrorEnvelope schema that the {@link ApiErrors} responses refer to. */
    @Bean
    OpenApiCustomizer errorEnvelopeSchema() {
        return openApi -> ModelConverters.getInstance(true)
                .readAll(new AnnotatedType(ErrorEnvelope.class))
                .forEach(openApi.getComponents()::addSchemas);
    }
}
