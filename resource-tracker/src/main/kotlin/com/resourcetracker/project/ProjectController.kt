package com.resourcetracker.project

import com.resourcetracker.common.ErrorResponse
import io.swagger.v3.oas.annotations.Operation
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
    @ApiResponses(value = [
        ApiResponse(responseCode = "201", description = "Project created", content = [Content(schema = Schema(implementation = ProjectResponse::class))]),
        ApiResponse(responseCode = "400", description = "Invalid request body", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ApiResponse(responseCode = "401", description = "Missing or invalid access token", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ApiResponse(responseCode = "403", description = "Admin role required", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ApiResponse(responseCode = "409", description = "Project conflicts with an existing resource", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
    ])
    fun create(@Valid @RequestBody body: CreateProjectRequest): ResponseEntity<ProjectResponse> =
        ResponseEntity.status(HttpStatus.CREATED).body(service.create(body))

    @PutMapping("/{id}")
    @Operation(summary = "Update a project", description = "Update an existing project.")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Project updated", content = [Content(schema = Schema(implementation = ProjectResponse::class))]),
        ApiResponse(responseCode = "400", description = "Invalid request body", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ApiResponse(responseCode = "401", description = "Missing or invalid access token", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ApiResponse(responseCode = "403", description = "Admin role required", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ApiResponse(responseCode = "404", description = "Project not found", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ApiResponse(responseCode = "409", description = "Project conflicts with an existing resource", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
    ])
    fun update(@PathVariable id: UUID, @Valid @RequestBody body: UpdateProjectRequest): ProjectResponse =
        service.update(id, body)

    @PostMapping("/{id}/deactivate")
    @Operation(summary = "Deactivate a project", description = "Mark a project as inactive.")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Project deactivated", content = [Content(schema = Schema(implementation = ProjectResponse::class))]),
        ApiResponse(responseCode = "401", description = "Missing or invalid access token", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ApiResponse(responseCode = "403", description = "Admin role required", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ApiResponse(responseCode = "404", description = "Project not found", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
    ])
    fun deactivate(@PathVariable id: UUID): ProjectResponse = service.deactivate(id)

    @GetMapping("/{id}")
    @Operation(summary = "Get a project", description = "Return a project by its UUID.")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Project returned", content = [Content(schema = Schema(implementation = ProjectResponse::class))]),
        ApiResponse(responseCode = "401", description = "Missing or invalid access token", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ApiResponse(responseCode = "403", description = "Admin role required", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ApiResponse(responseCode = "404", description = "Project not found", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
    ])
    fun get(@PathVariable id: UUID): ProjectResponse = service.get(id)

    // Sort format: ?sort=name:asc&sort=startDate:desc (max 3)
    @GetMapping
    @Operation(summary = "List projects", description = "Filter projects by department, active status, start-date range, or search text with pagination and sorting.")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Projects returned"),
        ApiResponse(responseCode = "400", description = "Invalid filter or pagination parameters", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ApiResponse(responseCode = "401", description = "Missing or invalid access token", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ApiResponse(responseCode = "403", description = "Admin role required", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
    ])
    fun list(
        @RequestParam(required = false) departmentId: Int?,
        @RequestParam(required = false) active: Boolean?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) startFrom: LocalDate?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) startTo: LocalDate?,
        @RequestParam(required = false) search: String?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        @RequestParam(defaultValue = "name:asc") sort: List<String>
    ): Page<ProjectResponse> =
        service.list(departmentId, active, startFrom, startTo, search, page, size, sort)

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
