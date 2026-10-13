package com.resourcetracker.resource

import com.resourcetracker.common.ErrorResponse
import com.resourcetracker.security.RequiresRoles
import com.resourcetracker.user.Role
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/resources")
@RequiresRoles(Role.ADMIN)
@Tag(name = "Resources", description = "Create, update, and deactivate resources")
@SecurityRequirement(name = "bearerAuth")
class ResourceController(private val resources: ResourceService) {

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a resource", description = "Creates a new active resource.")
    @ApiResponses(value = [
        ApiResponse(responseCode = "201", description = "Resource created", content = [Content(schema = Schema(implementation = ResourceResponse::class))]),
        ApiResponse(responseCode = "400", description = "Invalid request body", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ApiResponse(responseCode = "401", description = "Authentication required", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ApiResponse(responseCode = "403", description = "Admin role required", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ApiResponse(responseCode = "404", description = "Department or vendor not found", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ApiResponse(responseCode = "409", description = "Resource type rules were violated", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
    ])
    fun create(@Valid @RequestBody request: CreateResourceRequest): ResourceResponse =
        resources.create(request)

    @PutMapping("/{id}")
    @Operation(summary = "Update a resource", description = "Updates an existing resource.")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Resource updated", content = [Content(schema = Schema(implementation = ResourceResponse::class))]),
        ApiResponse(responseCode = "400", description = "Invalid request body", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ApiResponse(responseCode = "401", description = "Authentication required", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ApiResponse(responseCode = "403", description = "Admin role required", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ApiResponse(responseCode = "404", description = "Resource, department, or vendor not found", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ApiResponse(responseCode = "409", description = "Resource type rules were violated", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
    ])
    fun update(
        @PathVariable id: UUID,
        @Valid @RequestBody request: UpdateResourceRequest,
    ): ResourceResponse = resources.update(id, request)

    @PatchMapping("/{id}/deactivate")
    @Operation(summary = "Deactivate a resource", description = "Marks a resource as inactive without deleting it.")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Resource deactivated", content = [Content(schema = Schema(implementation = ResourceResponse::class))]),
        ApiResponse(responseCode = "401", description = "Authentication required", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ApiResponse(responseCode = "403", description = "Admin role required", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ApiResponse(responseCode = "404", description = "Resource not found", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
    ])
    fun deactivate(@PathVariable id: UUID): ResourceResponse =
        resources.deactivate(id)
}
