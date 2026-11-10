package com.resourcetracker.team

import com.resourcetracker.common.ListQuery
import com.resourcetracker.common.PagedResponse
import com.resourcetracker.common.TeamReference
import com.resourcetracker.common.ErrorResponse
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
import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*

@RestController
@Validated
@RequestMapping("/api/v1/teams")
@RequiresRoles(Role.ADMIN, Role.MEMBER, Role.TEAM_LEAD, Role.DEPARTMENT_MANAGER, Role.MANAGING_DIRECTOR)
@Tag(name = "Teams", description = "Create, search, update, and deactivate teams")
@SecurityRequirement(name = "bearerAuth")
class TeamController(private val service: TeamService) {

    @GetMapping("/{id}/members")
    @RequiresRoles(Role.MEMBER, Role.TEAM_LEAD, Role.DEPARTMENT_MANAGER, Role.MANAGING_DIRECTOR)
    @Operation(summary = "List team members", description = "Members, team leads, department managers, and managing directors can view a team's members. Returns a paginated list with optional filters.")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Team members returned"),
        ApiResponse(responseCode = "400", description = "Invalid filters or pagination", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ApiResponse(responseCode = "401", description = "Authentication required", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ApiResponse(responseCode = "403", description = "Caller role is not permitted", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ApiResponse(responseCode = "404", description = "Team not found", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ApiResponse(responseCode = "409", description = "Team is inactive", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
    ])
    fun members(
        @PathVariable id: Int,
        @Parameter(description = "Search username, email, or full name", example = "jane")
        @RequestParam(required = false) search: String?,
        @Parameter(description = "Zero-based page number", example = "0")
        @RequestParam(defaultValue = "0") @Min(0) page: Int,
        @Parameter(description = "Page size, from 1 to 100", example = "20")
        @RequestParam(defaultValue = "20") @Min(1) size: Int,
        @Parameter(description = "Sort field: username, email, fullName, role, or active", example = "username")
        @RequestParam(defaultValue = "username") sort: String,
        @Parameter(description = "Sort direction: asc or desc", example = "asc")
        @RequestParam(defaultValue = "asc") direction: String,
        @RequestParam(required = false) role: Role?,
        @RequestParam(required = false) active: Boolean?,
    ): PagedResponse<TeamMemberResponse> = PagedResponse.from(
        service.members(
            id,
            role,
            active,
            search,
            ListQuery.pageable(
                page, size, sort, direction,
                setOf("username", "email", "fullName", "role", "active"),
                "username",
            ),
        ).map { user ->
            TeamMemberResponse(
                id = user.id,
                username = user.username,
                email = user.email,
                fullName = user.fullName ?: user.username,
                role = user.role,
                active = user.active,
                teams = user.teams.map { TeamReference(it.id, it.name) }.sortedBy { it.name },
            )
        },
    )

    @PostMapping("/{id}/members")
    @RequiresRoles(Role.DEPARTMENT_MANAGER)
    @Operation(summary = "Add members to a team", description = "Department managers may add existing active users only to teams in their own department.")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Members added"),
        ApiResponse(responseCode = "400", description = "Invalid member list", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ApiResponse(responseCode = "401", description = "Authentication required", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ApiResponse(responseCode = "403", description = "Department Manager role required or team is outside the caller's department", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ApiResponse(responseCode = "404", description = "Team or user not found", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ApiResponse(responseCode = "409", description = "Team or user is inactive, or user is already a member", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
    ])
    fun addMembers(@PathVariable id: Int, @Valid @RequestBody body: AddTeamMembersRequest): List<TeamMemberResponse> =
        service.addMembers(id, body.usernames).map { user ->
            TeamMemberResponse(
                id = user.id,
                username = user.username,
                email = user.email,
                fullName = user.fullName ?: user.username,
                role = user.role,
                active = user.active,
                teams = user.teams.map { TeamReference(it.id, it.name) }.sortedBy { it.name },
            )
        }

    @PutMapping("/{id}/members/{username}")
    @RequiresRoles(Role.DEPARTMENT_MANAGER)
    @Operation(summary = "Transfer a team member", description = "Department managers may transfer a member only between teams in their own department. The transfer is recorded in membership history.")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Member transferred"),
        ApiResponse(responseCode = "400", description = "Invalid target team", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ApiResponse(responseCode = "401", description = "Authentication required", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ApiResponse(responseCode = "403", description = "Department Manager role required or source or target team is outside the caller's department", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ApiResponse(responseCode = "404", description = "Team or member not found", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ApiResponse(responseCode = "409", description = "Member transfer is not valid for the current team state", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
    ])
    fun transfer(@PathVariable id: Int, @PathVariable username: String, @Valid @RequestBody body: TransferTeamMemberRequest): TeamMemberResponse {
        val user = service.transfer(id, username, body.toTeamId)
        return TeamMemberResponse(
            id = user.id,
            username = user.username,
            email = user.email,
            fullName = user.fullName ?: user.username,
            role = user.role,
            active = user.active,
            teams = user.teams.map { TeamReference(it.id, it.name) }.sortedBy { it.name },
        )
    }

    @DeleteMapping("/{id}/members/{username}")
    @RequiresRoles(Role.DEPARTMENT_MANAGER)
    @Operation(summary = "Remove a member from a team", description = "Department managers may remove members only from teams in their own department. The removal is recorded in membership history.")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Member removed"),
        ApiResponse(responseCode = "401", description = "Authentication required", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ApiResponse(responseCode = "403", description = "Department Manager role required or team is outside the caller's department", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ApiResponse(responseCode = "404", description = "Team or member not found", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ApiResponse(responseCode = "409", description = "Member cannot be removed from this team", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
    ])
    fun removeMember(@PathVariable id: Int, @PathVariable username: String): TeamMemberResponse {
        val user = service.removeMember(id, username)
        return TeamMemberResponse(
            id = user.id,
            username = user.username,
            email = user.email,
            fullName = user.fullName ?: user.username,
            role = user.role,
            active = user.active,
            teams = user.teams.map { TeamReference(it.id, it.name) }.sortedBy { it.name },
        )
    }

    @GetMapping("/{id}/members/history")
    @RequiresRoles(Role.MEMBER, Role.TEAM_LEAD, Role.DEPARTMENT_MANAGER, Role.MANAGING_DIRECTOR)
    @Operation(summary = "List team member history", description = "Members, team leads, department managers, and managing directors can view the membership history for a team. Admin users are not permitted.")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Team member history returned"),
        ApiResponse(responseCode = "401", description = "Authentication required", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ApiResponse(responseCode = "403", description = "Caller role is not permitted", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ApiResponse(responseCode = "404", description = "Team not found", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
    ])
    fun history(@PathVariable id: Int): List<TeamMemberHistoryResponse> = service.history(id)

    @PostMapping
    @RequiresRoles(Role.DEPARTMENT_MANAGER)
    @Operation(summary = "Create a team", description = "Department managers can create teams only in their own department. Supply memberUsernames to add existing active users during creation; omit it or send an empty list to create a team without members.")
    @ApiResponses(value = [
        ApiResponse(responseCode = "201", description = "Team created"),
        ApiResponse(responseCode = "400", description = "Invalid team data or an inactive manager or project", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ApiResponse(responseCode = "401", description = "Authentication required", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ApiResponse(responseCode = "403", description = "Department Manager role required or requested department is outside the caller's department", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ApiResponse(responseCode = "404", description = "Department, manager, project, or user not found", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ApiResponse(responseCode = "409", description = "Team name already exists in the department, or a listed user is inactive", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
    ])
    fun create(@Valid @RequestBody body: TeamRequest): ResponseEntity<TeamResponse> =
        ResponseEntity.status(HttpStatus.CREATED).body(service.create(body))

    @PutMapping("/{id}")
    @RequiresRoles(Role.DEPARTMENT_MANAGER)
    @Operation(
        summary = "Update a team",
        description = "Department managers may update teams only in their own department. All fields are optional; null or omitted fields preserve their current values. " +
                "Updates name, department, manager and projects. `memberUsernames` adds existing active users; omitted users are not removed, and an empty list makes no membership changes. " +
                "If `isActive` is false, the team is deactivated; if true, it is reactivated. " +
                "Omit `isActive` to leave the value unchanged. Returns 200, 400, 401, 403, 404 or 409."
    )
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Team updated"),
        ApiResponse(responseCode = "400", description = "Invalid team data or inactive department, manager, or project", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ApiResponse(responseCode = "401", description = "Authentication required", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ApiResponse(responseCode = "403", description = "Department Manager role required or team or target department is outside the caller's department", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ApiResponse(responseCode = "404", description = "Team, department, manager, project, or user not found", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ApiResponse(responseCode = "409", description = "Team name already exists, team is inactive while members are being added, or a listed user is inactive or already a member", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
    ])
    fun update(@PathVariable id: Int, @Valid @RequestBody body: UpdateTeamRequest): TeamResponse =
        service.update(id, body)

    @GetMapping("/{id}")
    @RequiresRoles(Role.ADMIN, Role.MEMBER, Role.TEAM_LEAD, Role.DEPARTMENT_MANAGER, Role.MANAGING_DIRECTOR)
    @Operation(summary = "Get a team", description = "Admins can retrieve team details for user assignment. Members, team leads, department managers, and managing directors can also retrieve a team.")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Team returned"),
        ApiResponse(responseCode = "401", description = "Authentication required", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ApiResponse(responseCode = "403", description = "Caller role is not permitted", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ApiResponse(responseCode = "404", description = "Team not found", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
    ])
    fun get(@PathVariable id: Int): TeamResponse = service.get(id)

    @GetMapping
    @RequiresRoles(Role.ADMIN, Role.MEMBER, Role.TEAM_LEAD, Role.DEPARTMENT_MANAGER, Role.MANAGING_DIRECTOR)
    @Operation(
        summary = "List teams",
        description = "Admins can list teams for user assignment. Members, team leads, department managers, and managing directors can also list teams. Lists all teams by default, with optional filters, keyword search, pagination, and one-field sorting."
    )
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Teams returned"),
        ApiResponse(responseCode = "400", description = "Invalid filters, sort, or pagination", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ApiResponse(responseCode = "401", description = "Authentication required", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ApiResponse(responseCode = "403", description = "Caller role is not permitted", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
    ])
    fun list(
        @Parameter(description = "Search team names")
        @RequestParam(required = false) search: String?,
        @Parameter(description = "Zero-based page number", example = "0")
        @RequestParam(defaultValue = "0") @Min(0) page: Int,
        @Parameter(description = "Page size, from 1 to 100", example = "20")
        @RequestParam(defaultValue = "20") @Min(1) size: Int,
        @Parameter(description = "Sort field: name, isActive, or createdAt", example = "name")
        @RequestParam(defaultValue = "name") sort: String,
        @Parameter(description = "Sort direction: asc or desc", example = "asc")
        @RequestParam(defaultValue = "asc") direction: String,
        @Parameter(description = "Filter by department ID")
        @RequestParam(required = false) departmentId: Int?,
        @Parameter(description = "Filter by manager ID")
        @RequestParam(required = false) managerId: Int?,
        @Parameter(description = "Filter by project ID")
        @RequestParam(required = false) projectId: Int?,
        @Parameter(description = "Filter by active state. Omit to return both active and inactive teams.", example = "true")
        @RequestParam(required = false) isActive: Boolean?,
    ): PagedResponse<TeamResponse> =
        ListQuery.pageable(page, size, sort, direction, ALLOWED_SORT_FIELDS, "name").let { pageable ->
            val order = pageable.sort.first()
            PagedResponse.from(service.list(
                departmentId, managerId, projectId, isActive, search,
                pageable.pageNumber, pageable.pageSize,
                listOf("${order.property}:${order.direction.name.lowercase()}"),
            ))
        }

    companion object {
        private val ALLOWED_SORT_FIELDS = setOf("name", "isActive", "createdAt")
    }
}
