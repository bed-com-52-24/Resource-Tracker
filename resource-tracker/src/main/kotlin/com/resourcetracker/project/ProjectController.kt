package com.resourcetracker.project

import com.resourcetracker.common.ErrorResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.data.domain.Page
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.*
import java.time.LocalDate
import java.util.UUID

@RestController
@RequestMapping("/api/v1/projects")
@Tag(name = "Projects", description = "Create, search, update, and deactivate projects")
@SecurityRequirement(name = "bearerAuth")
class ProjectController(private val service: ProjectService) {

    @PostMapping
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
                description = "Admin role required",
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
                description = "Admin role required",
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
    fun update(@PathVariable id: UUID, @Valid @RequestBody body: UpdateProjectRequest): ProjectResponse =
        service.update(id, body)

    @GetMapping("/{id}")
    @Operation(summary = "Get a project", description = "Return a project by its UUID.")
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
                description = "Admin role required",
                content = [Content(schema = Schema(implementation = ErrorResponse::class))]
            ),
            ApiResponse(
                responseCode = "404",
                description = "Project not found",
                content = [Content(schema = Schema(implementation = ErrorResponse::class))]
            ),
        ]
    )
    fun get(@PathVariable id: UUID): ProjectResponse = service.get(id)

    @GetMapping
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
                description = "Admin role required",
                content = [Content(schema = Schema(implementation = ErrorResponse::class))]
            ),
        ]
    )
    fun list(
        @Parameter(description = "Filter by department ID", example = "1")
        @RequestParam(required = false) departmentId: Int?,
        @Parameter(description = "Filter by active state. Omit to return both active and inactive projects.", example = "true")
        @RequestParam(required = false) active: Boolean?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) startFrom: LocalDate?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) startTo: LocalDate?,
        @RequestParam(required = false) search: String?,
        @Parameter(description = "Zero-based page number", example = "0")
        @RequestParam(defaultValue = "0") page: Int,
        @Parameter(description = "Page size, from 1 to 100", example = "20")
        @RequestParam(defaultValue = "20") size: Int,
        @Parameter(description = "Sort field: name, code, startDate, endDate, active, or createdAt", example = "name")
        @RequestParam(defaultValue = "name") sort: String,
        @Parameter(description = "Sort direction: asc or desc", example = "asc")
        @RequestParam(defaultValue = "asc") direction: String
    ): Page<ProjectResponse> =
        service.list(
            departmentId, active, startFrom, startTo, search, page, size,
            listOf(
                "${sort.takeIf { it in ALLOWED_SORT_FIELDS } ?: "name"}:" +
                    if (direction.equals("desc", ignoreCase = true)) "desc" else "asc"
            )
        )

    companion object {
        private val ALLOWED_SORT_FIELDS = setOf("name", "code", "startDate", "endDate", "active", "createdAt")
    }

    // ---- Local error handling (move to a shared @RestControllerAdvice when the team adds one) ----
    @ExceptionHandler(NotFoundException::class)
    fun notFound(e: NotFoundException) = error(HttpStatus.NOT_FOUND, e.message)

    @ExceptionHandler(ConflictException::class)
    fun conflict(e: ConflictException) = error(HttpStatus.CONFLICT, e.message)

    @ExceptionHandler(BadRequestException::class)
    fun badRequest(e: BadRequestException) = error(HttpStatus.BAD_REQUEST, e.message)

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun invalid(e: MethodArgumentNotValidException): ResponseEntity<Map<String, Any?>> {
        val details = e.bindingResult.fieldErrors.associate { it.field to it.defaultMessage }
        return ResponseEntity.badRequest()
            .body(mapOf("status" to 400, "error" to "Validation failed", "details" to details))
    }

    private fun error(status: HttpStatus, message: String?): ResponseEntity<Map<String, Any?>> =
        ResponseEntity.status(status).body(mapOf("status" to status.value(), "error" to message))
}
