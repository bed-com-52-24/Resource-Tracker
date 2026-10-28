package com.resourcetracker.project

import com.resourcetracker.common.ErrorResponse
import com.resourcetracker.common.ListQuery
import com.resourcetracker.common.PagedResponse
import com.resourcetracker.security.RequiresRoles
import com.resourcetracker.audit.Auditable
import com.resourcetracker.audit.AuditAction
import com.resourcetracker.user.Role
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import jakarta.validation.constraints.Min
import org.springframework.data.domain.Page
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*
import java.time.LocalDate

@RestController
@Validated
@RequestMapping("/api/v1/projects")
@RequiresRoles(Role.TEAM_LEAD, Role.DEPARTMENT_MANAGER, Role.MANAGING_DIRECTOR)
@Tag(name = "Projects", description = "Create, search, update, and deactivate projects")
@SecurityRequirement(name = "bearerAuth")
class ProjectController(private val service: ProjectService) {

    @PostMapping
    @RequiresRoles(Role.DEPARTMENT_MANAGER)
    @Auditable(action = AuditAction.PROJECT_CREATED, itemType = "Project", itemIdExpression = "#body.code")
    @Operation(summary = "Create a project", description = "Create a new project.")
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "201",
                description = "Project created",
                content = [Content(schema = Schema(implementation = ProjectResponse::class))]
            ),
            ApiResponse(
                responseCode = "400",
                description = "Invalid request body",
                content = [Content(schema = Schema(implementation = ErrorResponse::class))]
            ),
            ApiResponse(
                responseCode = "401",
                description = "Missing or invalid access token",
                content = [Content(schema = Schema(implementation = ErrorResponse::class))]
            ),
            ApiResponse(
                responseCode = "403",
                description = "Department manager role required",
                content = [Content(schema = Schema(implementation = ErrorResponse::class))]
            ),
            ApiResponse(
                responseCode = "409",
                description = "Project conflicts with an existing resource",
                content = [Content(schema = Schema(implementation = ErrorResponse::class))]
            ),
        ]
    )
    fun create(@Valid @RequestBody body: CreateProjectRequest): ResponseEntity<ProjectResponse> =
        ResponseEntity.status(HttpStatus.CREATED).body(service.create(body))

    @PutMapping("/{id}")
    @RequiresRoles(Role.DEPARTMENT_MANAGER)
    @Auditable(action = AuditAction.PROJECT_UPDATED, itemType = "Project", itemIdExpression = "#id")
    @Operation(
        summary = "Update a project",
        description = "Updates an existing project. All fields are optional; null or omitted fields preserve their current values. " +
                "If `active` is false, the project is deactivated " +
                "(refused while it has active resource requests); if true, it is reactivated. " +
                "Omit `active` to leave the status unchanged."
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Project updated",
                content = [Content(schema = Schema(implementation = ProjectResponse::class))]
            ),
            ApiResponse(
                responseCode = "400",
                description = "Invalid request body",
                content = [Content(schema = Schema(implementation = ErrorResponse::class))]
            ),
            ApiResponse(
                responseCode = "401",
                description = "Missing or invalid access token",
                content = [Content(schema = Schema(implementation = ErrorResponse::class))]
            ),
            ApiResponse(
                responseCode = "403",
                description = "Department manager role required",
                content = [Content(schema = Schema(implementation = ErrorResponse::class))]
            ),
            ApiResponse(
                responseCode = "404",
                description = "Project not found",
                content = [Content(schema = Schema(implementation = ErrorResponse::class))]
            ),
            ApiResponse(
                responseCode = "409",
                description = "Name or code already exists, or project has active resource requests and cannot be deactivated",
                content = [Content(schema = Schema(implementation = ErrorResponse::class))]
            ),
        ]
    )
    fun update(@PathVariable @Min(1) id: Int, @Valid @RequestBody body: UpdateProjectRequest): ProjectResponse =
        service.update(id, body)

    @GetMapping("/{id}")
    @RequiresRoles(Role.TEAM_LEAD, Role.DEPARTMENT_MANAGER, Role.MANAGING_DIRECTOR)
    @Operation(summary = "Get a project", description = "Return a project by its integer ID.")
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Project returned",
                content = [Content(schema = Schema(implementation = ProjectResponse::class))]
            ),
            ApiResponse(
                responseCode = "401",
                description = "Missing or invalid access token",
                content = [Content(schema = Schema(implementation = ErrorResponse::class))]
            ),
            ApiResponse(
                responseCode = "403",
                description = "Admin role is not permitted for project access",
                content = [Content(schema = Schema(implementation = ErrorResponse::class))]
            ),
            ApiResponse(
                responseCode = "404",
                description = "Project not found",
                content = [Content(schema = Schema(implementation = ErrorResponse::class))]
            ),
        ]
    )
    fun get(@PathVariable @Min(1) id: Int): ProjectResponse = service.get(id)

    @GetMapping
    @RequiresRoles(Role.TEAM_LEAD, Role.DEPARTMENT_MANAGER, Role.MANAGING_DIRECTOR)
    @Operation(
        summary = "List projects",
        description = "Lists all projects by default. Optionally filters by department, active status, dates, or search text. Sorting uses one field and direction."
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Projects returned"),
            ApiResponse(
                responseCode = "400",
                description = "Invalid filter or pagination parameters",
                content = [Content(schema = Schema(implementation = ErrorResponse::class))]
            ),
            ApiResponse(
                responseCode = "401",
                description = "Missing or invalid access token",
                content = [Content(schema = Schema(implementation = ErrorResponse::class))]
            ),
            ApiResponse(
                responseCode = "403",
                description = "Admin role is not permitted for project access",
                content = [Content(schema = Schema(implementation = ErrorResponse::class))]
            ),
        ]
    )
    fun list(
        @Parameter(description = "Search project names or codes (optional)")
        @RequestParam(required = false) search: String?,
        @Parameter(description = "Zero-based page number", example = "0")
        @RequestParam(defaultValue = "0") @Min(0) page: Int,
        @Parameter(description = "Page size, from 1 to 100", example = "20")
        @RequestParam(defaultValue = "20") @Min(1) size: Int,
        @Parameter(description = "Sort field: name, code, startDate, endDate, active, or createdAt", example = "name")
        @RequestParam(defaultValue = "name") sort: String,
        @Parameter(description = "Sort direction: asc or desc", example = "asc")
        @RequestParam(defaultValue = "asc") direction: String,
        @Parameter(description = "Filter by department ID", example = "1")
        @RequestParam(required = false) departmentId: Int?,
        @Parameter(description = "Filter by active state. Omit to return both active and inactive projects.", example = "true")
        @RequestParam(required = false) active: Boolean?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) startFrom: LocalDate?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) startTo: LocalDate?,
    ): PagedResponse<ProjectResponse> {
        val pageable = ListQuery.pageable(page, size, sort, direction, ALLOWED_SORT_FIELDS, "name")
        val order = pageable.sort.first()
        val result = service.list(
            departmentId, active, startFrom, startTo, search,
            pageable.pageNumber, pageable.pageSize,
            listOf("${order.property}:${order.direction.name.lowercase()}"),
        )
        return PagedResponse.from(result)
    }

    companion object {
        private val ALLOWED_SORT_FIELDS = setOf("name", "code", "startDate", "endDate", "active", "createdAt")
    }
}
