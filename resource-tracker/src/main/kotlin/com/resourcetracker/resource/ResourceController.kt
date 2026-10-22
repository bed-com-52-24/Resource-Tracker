package com.resourcetracker.resource

import com.resourcetracker.common.ErrorResponse
import com.resourcetracker.security.RequiresRoles
import com.resourcetracker.security.AuthenticatedUser
import com.resourcetracker.user.Role
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import jakarta.validation.constraints.Min
import org.springframework.data.domain.Page
import org.springframework.http.HttpStatus
import org.springframework.validation.annotation.Validated
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@Validated
@RequestMapping("/api/v1/resources")
@Tag(name = "Resources", description = "Create, update, and deactivate resources")
@SecurityRequirement(name = "bearerAuth")
class ResourceController(private val resources: ResourceService) {

    @GetMapping
    @Operation(
        summary = "List resources",
        description = "Lists resources with optional active-state, type, category, and name/code search filters."
    )
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Resources returned"),
        ApiResponse(responseCode = "400", description = "Invalid filter or pagination parameters", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ApiResponse(responseCode = "401", description = "Authentication required", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ApiResponse(responseCode = "403", description = "Admin role required", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
    ])
    fun list(
        @RequestParam(required = false) active: Boolean?,
        @RequestParam(required = false) type: ResourceType?,
        @RequestParam(required = false) category: ResourceCategory?,
        @RequestParam(required = false) status: ResourceStatus?,
        @RequestParam(defaultValue = "false") availableOnly: Boolean,
        @RequestParam(required = false) departmentId: Int?,
        @RequestParam(required = false) search: String?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        @RequestParam(defaultValue = "name") sort: String,
        @RequestParam(defaultValue = "asc") direction: String,
    ): Page<ResourceResponse> = resources.list(
        active = active,
        type = type,
        category = category,
        status = status,
        departmentId = departmentId,
        availableOnly = availableOnly,
        keyword = search,
        page = page,
        size = size,
        sort = listOf(
            "${sort.takeIf { it in ALLOWED_SORT_FIELDS } ?: "name"}:" +
                    if (direction.equals("desc", ignoreCase = true)) "desc" else "asc"
        ),
    )

    @PostMapping
    @RequiresRoles(Role.ADMIN)
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
    fun create(@Valid @RequestBody request: CreateResourceRequest, @AuthenticationPrincipal caller: AuthenticatedUser): ResourceResponse =
        resources.create(request, caller.id)

    @PutMapping("/{id}")
    @RequiresRoles(Role.ADMIN)
    @Operation(summary = "Update a resource", description = "Updates an existing resource. Set active to true or false to change its state; omit it to leave the state unchanged.")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Resource updated", content = [Content(schema = Schema(implementation = ResourceResponse::class))]),
        ApiResponse(responseCode = "400", description = "Invalid request body", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ApiResponse(responseCode = "401", description = "Authentication required", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ApiResponse(responseCode = "403", description = "Admin role required", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ApiResponse(responseCode = "404", description = "Resource, department, or vendor not found", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ApiResponse(responseCode = "409", description = "Resource type rules were violated", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
    ])
    fun update(
        @PathVariable @Min(1) id: Int,
        @Valid @RequestBody request: UpdateResourceRequest,
    ): ResourceResponse = resources.update(id, request)

    @GetMapping("/{id}")
    fun get(@PathVariable @Min(1) id: Int): ResourceResponse = resources.get(id)

    companion object {
        private val ALLOWED_SORT_FIELDS = setOf("name", "code", "type", "category", "active", "createdAt", "quantity", "status")
    }
}
