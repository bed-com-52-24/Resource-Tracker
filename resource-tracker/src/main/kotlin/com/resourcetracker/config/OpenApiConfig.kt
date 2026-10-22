package com.resourcetracker.config

import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.media.Content
import io.swagger.v3.oas.models.media.MediaType
import io.swagger.v3.oas.models.media.Schema
import io.swagger.v3.oas.models.responses.ApiResponse
import io.swagger.v3.oas.models.responses.ApiResponses
import io.swagger.v3.oas.models.security.SecurityScheme
import org.springdoc.core.customizers.OpenApiCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class OpenApiConfig {
    @Bean
    fun resourceTrackerOpenApi(): OpenAPI = OpenAPI()
        .info(
            Info()
                .title("Resource Tracker API")
                .version("v1")
                .description("REST API for managing Resource Tracker users, resources, projects, requests, and workflows."),
        )
        .components(
            Components()
                .addSecuritySchemes(
                    "bearerAuth",
                    SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT"),
                )
                .addSchemas("ErrorResponse", errorResponseSchema()),
        )

    @Bean
    fun commonErrorResponses(): OpenApiCustomizer = OpenApiCustomizer { openApi ->
        val errorContent = Content().addMediaType(
            "application/json",
            MediaType().schema(Schema<Any>().`$ref`("#/components/schemas/ErrorResponse")),
        )
        val responses = mapOf(
            "400" to "Invalid request",
            "401" to "Authentication required",
            "403" to "Caller does not have the required role",
            "404" to "Requested resource was not found",
            "409" to "Request conflicts with the current state",
            "500" to "Unexpected server error",
        )

        openApi.paths?.values?.flatMap { it.readOperations() }?.forEach { operation ->
            val operationResponses = operation.responses ?: ApiResponses().also { operation.responses = it }
            responses.forEach { (status, description) ->
                if (!operationResponses.containsKey(status)) {
                    operationResponses.addApiResponse(
                        status,
                        ApiResponse().description(description).content(errorContent),
                    )
                }
            }
        }
    }

    private fun errorResponseSchema(): Schema<Any> = Schema<Any>()
        .type("object")
        .description("Standard API error response")
        .addProperty("timestamp", Schema<String>().type("string").format("date-time"))
        .addProperty("status", Schema<Int>().type("integer").format("int32"))
        .addProperty("error", Schema<String>().type("string"))
        .addProperty("code", Schema<String>().type("string"))
        .addProperty("message", Schema<String>().type("string"))
        .addProperty("path", Schema<String>().type("string"))
        .addProperty("correlationId", Schema<String>().type("string"))
        .addProperty("details", Schema<Any>().type("array").items(Schema<String>().type("string")))
        .required(
            listOf("timestamp", "status", "error", "code", "message", "path", "correlationId", "details"),
        )
}
