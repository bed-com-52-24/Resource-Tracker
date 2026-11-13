package com.resourcetracker.team

import com.resourcetracker.common.DepartmentReference
import com.resourcetracker.common.ProjectReference
import com.resourcetracker.common.UserReference
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

data class TeamRequest(
    @field:NotBlank @field:Size(min = 2, max = 100)
    val name: String,

    val departmentId: Int,

    val managerId: Int,

    val projectIds: Set<Int> = emptySet(),

    @field:Schema(description = "Optional description of the team", example = "Handles customer support operations", nullable = true)
    @field:Size(max = 500)
    val description: String? = null,

    @field:Schema(
        description = "Optional usernames of existing active users to add to the team when it is created. Omit or send an empty list to create the team without members.",
        example = "[\"jdoe\", \"asmith\"]",
    )
    val memberUsernames: Set<String> = emptySet(),
)

data class UpdateTeamRequest(
    @field:Size(min = 2, max = 100)
    @field:Pattern(regexp = "^.*\\S.*$", message = "Team name cannot be blank")
    @field:Schema(description = "Optional team name. Null or omitted preserves the current name.")
    val name: String? = null,

    @field:Schema(description = "Optional department ID. Null or omitted preserves the current department.")
    val departmentId: Int? = null,

    @field:Schema(description = "Optional manager ID. Null or omitted preserves the current manager.")
    val managerId: Int? = null,

    @field:Schema(description = "Optional project IDs. Null or omitted preserves the current project assignments.")
    val projectIds: Set<Int>? = null,

    @field:Schema(
        description = "Optional usernames of existing active users to add to the team. Null or an empty list leaves membership unchanged; users omitted from this list are not removed.",
        example = "[\"jdoe\", \"asmith\"]",
        nullable = true,
    )
    val memberUsernames: Set<String>? = null,

    @field:Schema(
        description = "Set to false to deactivate the team, true to reactivate it. Omit to leave unchanged.",
        nullable = true
    )
    val isActive: Boolean? = null,
)

data class TeamResponse(
    val id: Int,
    val name: String,
    val description: String?,
    val department: DepartmentReference,
    val manager: UserReference?,
    val projects: List<ProjectReference>,
    val isActive: Boolean
)

data class AddTeamMembersRequest(
    val usernames: Set<String>
)

data class TransferTeamMemberRequest(
    val toTeamId: Int
)

data class TeamMemberResponse(
    val id: Int,
    val username: String,
    val email: String,
    val fullName: String,
    val role: com.resourcetracker.user.Role,
    val active: Boolean,
    val teams: List<com.resourcetracker.common.TeamReference>,
)

data class TeamMemberHistoryResponse(
    val id: Int,
    val user: UserReference,
    val fromTeam: com.resourcetracker.common.TeamReference?,
    val toTeam: com.resourcetracker.common.TeamReference?,
    val action: TeamMemberAction,
    val performedBy: UserReference,
    val createdAt: java.time.Instant,
)

fun Team.toResponse() = TeamResponse(
    id = id,
    name = name,
    description = description,
    department = DepartmentReference(department.id, department.name),
    manager = manager?.let { UserReference(it.id, it.fullName ?: it.username) },
    projects = projects.map { ProjectReference(it.id, it.name, it.code) }.sortedBy { it.name },
    isActive = isActive
)
