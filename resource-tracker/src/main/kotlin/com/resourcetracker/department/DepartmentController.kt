package com.resourcetracker.department

import com.resourcetracker.common.ErrorResponse
import com.resourcetracker.common.ListQuery
import com.resourcetracker.common.PagedResponse
import com.resourcetracker.security.RequiresRoles
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
import org.springframework.http.HttpStatus
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*


@RestController
@Validated
@RequestMapping("/api/v1/departments")
@RequiresRoles(Role.ADMIN, Role.MEMBER, Role.TEAM_LEAD, Role.DEPARTMENT_MANAGER, Role.MANAGING_DIRECTOR)
@Tag(name = "Departments", description = "Create, search, update, and deactivate departments")
@SecurityRequirement(name = "bearerAuth")
class DepartmentController(private val departmentService: DepartmentService) {

    @PostMapping
    @RequiresRoles(Role.MANAGING_DIRECTOR)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a department", description = "Create a new active department.")
    @ApiResponses(value = [
        ApiResponse(responseCode = "201", description = "Department created", content = [Content(schema = Schema(implementation = DepartmentResponse::class))]),
        ApiResponse(responseCode = "400", description = "Invalid request body", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ApiResponse(responseCode = "401", description = "Missing or invalid access token", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ApiResponse(responseCode = "403", description = "Managing Director role required", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ApiResponse(responseCode = "409", description = "A department with this name already exists", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
    ])
    fun create(@Valid @RequestBody request: DepartmentRequest) =
        departmentService.create(request)

    @GetMapping
    @RequiresRoles(Role.ADMIN, Role.MEMBER, Role.TEAM_LEAD, Role.DEPARTMENT_MANAGER, Role.MANAGING_DIRECTOR)
    @Operation(summary = "List departments", description = "Returns a paginated department list. Search matches department names; active status is an optional filter. Page size is capped at 100.")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Departments returned"),
        ApiResponse(responseCode = "400", description = "Invalid search parameters", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ApiResponse(responseCode = "401", description = "Missing or invalid access token", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ApiResponse(responseCode = "403", description = "Caller role is not permitted for department access", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
    ])
    fun list(
        @Parameter(description = "Search department names (optional)")
        @RequestParam(required = false) search: String?,
        @Parameter(description = "Zero-based page number", example = "0")
        @RequestParam(defaultValue = "0") @Min(0) page: Int,
        @Parameter(description = "Page size, from 1 to 100", example = "20")
        @RequestParam(defaultValue = "20") @Min(1) size: Int,
        @Parameter(description = "Sort field: id, name, or status", example = "name")
        @RequestParam(defaultValue = "name") sort: String,
        @Parameter(description = "Sort direction: asc or desc", example = "asc")
        @RequestParam(defaultValue = "asc") direction: String,
        @Parameter(description = "Filter by active state. Omit to return both active and inactive departments.", example = "true")
        @RequestParam(required = false) active: Boolean?,
    ): PagedResponse<DepartmentResponse> = PagedResponse.from(departmentService.search(
        active,
        search,
        ListQuery.pageable(page, size, sort, direction, ALLOWED_SORT_FIELDS, "name")
    ))

    @GetMapping("/{id}")
    @RequiresRoles(Role.ADMIN, Role.MEMBER, Role.TEAM_LEAD, Role.DEPARTMENT_MANAGER, Role.MANAGING_DIRECTOR)
    @Operation(summary = "Get a department", description = "Return a department by its numeric identifier.")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Department returned", content = [Content(schema = Schema(implementation = DepartmentResponse::class))]),
        ApiResponse(responseCode = "401", description = "Missing or invalid access token", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ApiResponse(responseCode = "403", description = "Caller role is not permitted for department access", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ApiResponse(responseCode = "404", description = "Department not found", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
    ])
    fun get(@PathVariable id: Int) = departmentService.getById(id)

    @PutMapping("/{id}")
    @RequiresRoles(Role.MANAGING_DIRECTOR)
    @Operation(summary = "Update a department", description = "All fields are optional; null or omitted fields preserve their current values. Updates name, description, status, and manager. If `active` is false the department is deactivated, if true it is reactivated. If `managerId` is supplied, that active Department Manager becomes the department's manager and any previous manager is unassigned."
    )
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Department updated", content = [Content(schema = Schema(implementation = DepartmentResponse::class))]),
        ApiResponse(responseCode = "400", description = "Invalid request body, user is not active Department Manager, or department is inactive", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ApiResponse(responseCode = "401", description = "Missing or invalid access token", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ApiResponse(responseCode = "403", description = "Managing Director role required", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ApiResponse(responseCode = "404", description = "Department not found or manager user not found", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ApiResponse(responseCode = "409", description = "Name already exists, or department is in use and cannot be deactivated", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
    ])
    fun update(@PathVariable id: Int, @Valid @RequestBody request: UpdateDepartmentRequest) =
        departmentService.update(id, request)

    companion object { private val ALLOWED_SORT_FIELDS = setOf("id", "name", "status") }
}
