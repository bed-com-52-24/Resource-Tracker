package com.resourcetracker.team

import com.resourcetracker.department.DepartmentReference
import com.resourcetracker.user.UserReference
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class TeamRequest(
    @field:NotBlank @field:Size(min = 2, max = 100)
    val name: String,

    val departmentId: Int,

    val managerId: Int,

    val projectIds: Set<Int> = emptySet()
)

data class ProjectSummary(val id: Int, val name: String, val code: String)

data class UpdateTeamRequest(
    @field:Schema(description = "Optional team name. Null or omitted preserves the current name.")
    @field:NotBlank @field:Size(min = 2, max = 100)
    val name: String? = null,

    @field:Schema(description = "Optional department ID. Null or omitted preserves the current department.")
    val departmentId: Int? = null,

    @field:Schema(description = "Optional manager ID. Null or omitted preserves the current manager.")
    val managerId: Int? = null,

    @field:Schema(description = "Optional project IDs. Null or omitted preserves the current project assignments.")
    val projectIds: Set<Int>? = null,

    @field:Schema(
        description = "Set to false to deactivate the team, true to reactivate it. Omit to leave unchanged.",
        nullable = true
    )
    val isActive: Boolean? = null,
)

data class TeamResponse(
    val id: Int,
    val name: String,
    val department: DepartmentReference,
    val manager: UserReference?,
    val projects: List<ProjectSummary>,
    val isActive: Boolean,
    val active: Boolean = isActive,
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
    val teamIds: List<Int>,
)

fun Team.toResponse() = TeamResponse(
    id = id,
    name = name,
    department = DepartmentReference(department.id, department.name),
    manager = manager?.let { UserReference(it.id, it.fullName ?: it.username) },
    projects = projects.map { ProjectSummary(it.id, it.name, it.code) }.sortedBy { it.name },
    isActive = isActive
)
