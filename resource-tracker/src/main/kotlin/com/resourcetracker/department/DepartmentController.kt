package com.resourcetracker.department

import com.resourcetracker.common.ErrorResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableDefault
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*


@RestController
@RequestMapping("/api/departments")
@Tag(name = "Departments", description = "Create, search, update, and deactivate departments")
@SecurityRequirement(name = "bearerAuth")
class DepartmentController(private val departmentService: DepartmentService) {

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a department", description = "Create a new active department.")
    @ApiResponses(value = [
        ApiResponse(responseCode = "201", description = "Department created", content = [Content(schema = Schema(implementation = DepartmentResponse::class))]),
        ApiResponse(responseCode = "400", description = "Invalid request body", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ApiResponse(responseCode = "401", description = "Missing or invalid access token", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ApiResponse(responseCode = "403", description = "Admin role required", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ApiResponse(responseCode = "409", description = "A department with this name already exists", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
    ])
    fun create(@Valid @RequestBody request: DepartmentRequest) =
        departmentService.create(request)

    @GetMapping
    @Operation(summary = "List departments", description = "Search departments by status and keyword with pagination and sorting.")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Departments returned"),
        ApiResponse(responseCode = "400", description = "Invalid search parameters", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ApiResponse(responseCode = "401", description = "Missing or invalid access token", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ApiResponse(responseCode = "403", description = "Admin role required", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
    ])
    fun list(
        @RequestParam(required = false) status: String?,
        @RequestParam(required = false) keyword: String?,
        @PageableDefault(size = 20, sort = ["name"]) pageable: Pageable
    ) = departmentService.search(status, keyword, pageable)

    @GetMapping("/{id}")
    @Operation(summary = "Get a department", description = "Return a department by its numeric identifier.")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Department returned", content = [Content(schema = Schema(implementation = DepartmentResponse::class))]),
        ApiResponse(responseCode = "401", description = "Missing or invalid access token", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ApiResponse(responseCode = "403", description = "Admin role required", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ApiResponse(responseCode = "404", description = "Department not found", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
    ])
    fun get(@PathVariable id: Int) = departmentService.getById(id)

    @PutMapping("/{id}")
    @Operation(summary = "Update a department", description = "Update a department's name and description.")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Department updated", content = [Content(schema = Schema(implementation = DepartmentResponse::class))]),
        ApiResponse(responseCode = "400", description = "Invalid request body", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ApiResponse(responseCode = "401", description = "Missing or invalid access token", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ApiResponse(responseCode = "403", description = "Admin role required", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ApiResponse(responseCode = "404", description = "Department not found", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ApiResponse(responseCode = "409", description = "A department with this name already exists", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
    ])
    fun update(@PathVariable id: Int, @Valid @RequestBody request: DepartmentRequest) =
        departmentService.update(id, request)

    @PatchMapping("/{id}/deactivate")
    @Operation(summary = "Deactivate a department", description = "Mark a department as inactive.")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Department deactivated", content = [Content(schema = Schema(implementation = DepartmentResponse::class))]),
        ApiResponse(responseCode = "401", description = "Missing or invalid access token", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ApiResponse(responseCode = "403", description = "Admin role required", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ApiResponse(responseCode = "404", description = "Department not found", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
    ])
    fun deactivate(@PathVariable id: Int) = departmentService.deactivate(id)
}