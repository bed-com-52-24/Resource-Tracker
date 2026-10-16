package com.resourcetracker.team

import com.resourcetracker.security.RequiresRoles
import com.resourcetracker.user.Role
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.data.domain.Page
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/v1/teams")
@Tag(name = "Teams", description = "Create, search, update, and deactivate teams")
@SecurityRequirement(name = "bearerAuth")
class TeamController(private val service: TeamService) {

    @PostMapping
    @RequiresRoles(Role.ADMIN)
    @Operation(summary = "Create a team", description = "Admin only. Returns 201, 400, 401, 403, 404 or 409.")
    fun create(@Valid @RequestBody body: TeamRequest): ResponseEntity<TeamResponse> =
        ResponseEntity.status(HttpStatus.CREATED).body(service.create(body))

    @PutMapping("/{id}")
    @RequiresRoles(Role.ADMIN)
    @Operation(summary = "Update a team", description = "Admin only. Returns 200, 400, 401, 403, 404 or 409.")
    fun update(@PathVariable id: Int, @Valid @RequestBody body: TeamRequest): TeamResponse =
        service.update(id, body)

    @PostMapping("/{id}/deactivate")
    @RequiresRoles(Role.ADMIN)
    @Operation(summary = "Deactivate a team", description = "Admin only. The team is kept and marked INACTIVE.")
    fun deactivate(@PathVariable id: Int): TeamResponse = service.deactivate(id)

    @GetMapping("/{id}")
    @Operation(summary = "Get a team", description = "Any authenticated user.")
    fun get(@PathVariable id: Int): TeamResponse = service.get(id)

    // Sort format: ?sort=name:asc&sort=createdAt:desc (max 3)
    @GetMapping
    @Operation(summary = "List teams", description = "Any authenticated user. Filters, keyword search, sorting and pagination.")
    fun list(
        @RequestParam(required = false) departmentId: Int?,
        @RequestParam(required = false) managerId: Int?,
        @RequestParam(required = false) projectId: UUID?,
        @RequestParam(required = false) status: String?,
        @RequestParam(required = false) search: String?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        @RequestParam(defaultValue = "name:asc") sort: List<String>
    ): Page<TeamResponse> =
        service.list(departmentId, managerId, projectId, status, search, page, size, sort)
}
