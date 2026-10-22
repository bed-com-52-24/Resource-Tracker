package com.resourcetracker.team

import com.resourcetracker.security.RequiresRoles
import com.resourcetracker.user.Role
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.data.domain.Page
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/teams")
@Tag(name = "Teams", description = "Create, search, update, and deactivate teams")
@SecurityRequirement(name = "bearerAuth")
class TeamController(private val service: TeamService) {

    @GetMapping("/{id}/members")
    @RequiresRoles(Role.ADMIN)
    @Operation(summary = "List team members", description = "Admin only. Returns a paginated list of team members with optional filters.")
    fun members(
        @PathVariable id: Int,
        @RequestParam(required = false) role: Role?,
        @RequestParam(required = false) active: Boolean?,
        @RequestParam(required = false) search: String?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): Page<TeamMemberResponse> =
        service.members(id, page, size, role, active, search).map { user ->
            TeamMemberResponse(
                id = user.id,
                username = user.username,
                email = user.email,
                fullName = user.fullName,
                role = user.role,
                active = user.active,
                teamIds = user.teams.map { it.id }.sorted(),
            )
        }

    @PostMapping("/{id}/members")
    @RequiresRoles(Role.ADMIN)
    @Operation(summary = "Add members to a team", description = "Admin only. Adds existing active users to the given team.")
    fun addMembers(@PathVariable id: Int, @Valid @RequestBody body: AddTeamMembersRequest): List<TeamMemberResponse> =
        service.addMembers(id, body.userIds).map { user ->
            TeamMemberResponse(
                id = user.id,
                username = user.username,
                email = user.email,
                fullName = user.fullName,
                role = user.role,
                active = user.active,
                teamIds = user.teams.map { it.id }.sorted(),
            )
        }

    @PutMapping("/{id}/members/{userId}")
    @RequiresRoles(Role.ADMIN)
    @Operation(summary = "Transfer a team member", description = "Admin only. Moves a member from one team to another and records a history row.")
    fun transfer(@PathVariable id: Int, @PathVariable userId: Int, @Valid @RequestBody body: TransferTeamMemberRequest): TeamMemberResponse {
        val user = service.transfer(id, userId, body.toTeamId)
        return TeamMemberResponse(
            id = user.id,
            username = user.username,
            email = user.email,
            fullName = user.fullName,
            role = user.role,
            active = user.active,
            teamIds = user.teams.map { it.id }.sorted(),
        )
    }

    @DeleteMapping("/{id}/members/{userId}")
    @RequiresRoles(Role.ADMIN)
    @Operation(summary = "Remove a member from a team", description = "Admin only. Removes a user from the team and records the removal event.")
    fun removeMember(@PathVariable id: Int, @PathVariable userId: Int): TeamMemberResponse {
        val user = service.removeMember(id, userId)
        return TeamMemberResponse(
            id = user.id,
            username = user.username,
            email = user.email,
            fullName = user.fullName,
            role = user.role,
            active = user.active,
            teamIds = user.teams.map { it.id }.sorted(),
        )
    }

    @GetMapping("/{id}/members/history")
    @RequiresRoles(Role.ADMIN)
    @Operation(summary = "List team member history", description = "Admin only. Returns the audit trail for a team.")
    fun history(@PathVariable id: Int): List<TeamMemberHistory> = service.history(id)

    @PostMapping
    @RequiresRoles(Role.ADMIN)
    @Operation(summary = "Create a team", description = "Admin only. Returns 201, 400, 401, 403, 404 or 409.")
    fun create(@Valid @RequestBody body: TeamRequest): ResponseEntity<TeamResponse> =
        ResponseEntity.status(HttpStatus.CREATED).body(service.create(body))

    @PutMapping("/{id}")
    @RequiresRoles(Role.ADMIN)
    @Operation(
        summary = "Update a team",
        description = "Admin only. All fields are optional; null or omitted fields preserve their current values. " +
                "Updates name, department, manager and projects. " +
                "If `isActive` is false, the team is deactivated; if true, it is reactivated. " +
                "Omit `isActive` to leave the value unchanged. Returns 200, 400, 401, 403, 404 or 409."
    )
    fun update(@PathVariable id: Int, @Valid @RequestBody body: UpdateTeamRequest): TeamResponse =
        service.update(id, body)

    @GetMapping("/{id}")
    @Operation(summary = "Get a team", description = "Any authenticated user.")
    fun get(@PathVariable id: Int): TeamResponse = service.get(id)

    @GetMapping
    @Operation(
        summary = "List teams",
        description = "Any authenticated user. Lists all teams by default, with optional filters, keyword search, pagination, and one-field sorting."
    )
    fun list(
        @Parameter(description = "Filter by department ID", example = "1")
        @RequestParam(required = false) departmentId: Int?,
        @Parameter(description = "Filter by manager ID", example = "7")
        @RequestParam(required = false) managerId: Int?,
        @Parameter(description = "Filter by project ID")
        @RequestParam(required = false) projectId: Int?,
        @Parameter(description = "Filter by active state. Omit to return both active and inactive teams.", example = "true")
        @RequestParam(required = false) isActive: Boolean?,
        @Parameter(description = "Search team names", example = "alpha")
        @RequestParam(required = false) search: String?,
        @Parameter(description = "Zero-based page number", example = "0")
        @RequestParam(defaultValue = "0") page: Int,
        @Parameter(description = "Page size, from 1 to 100", example = "20")
        @RequestParam(defaultValue = "20") size: Int,
        @Parameter(description = "Sort field: name, isActive, or createdAt", example = "name")
        @RequestParam(defaultValue = "name") sort: String,
        @Parameter(description = "Sort direction: asc or desc", example = "asc")
        @RequestParam(defaultValue = "asc") direction: String
    ): Page<TeamResponse> =
        service.list(
            departmentId, managerId, projectId, isActive, search, page, size,
            listOf(
                "${sort.takeIf { it in ALLOWED_SORT_FIELDS } ?: "name"}:" +
                    if (direction.equals("desc", ignoreCase = true)) "desc" else "asc"
            )
        )

    companion object {
        private val ALLOWED_SORT_FIELDS = setOf("name", "isActive", "createdAt")
    }
}
