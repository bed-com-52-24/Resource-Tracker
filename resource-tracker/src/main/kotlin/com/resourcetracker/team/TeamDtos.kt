package com.resourcetracker.team

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
    val departmentId: Int,
    val departmentName: String,
    val managerId: Int?,
    val managerName: String?,
    val projects: List<ProjectSummary>,
    val isActive: Boolean
)

fun Team.toResponse() = TeamResponse(
    id = id,
    name = name,
    departmentId = department.id,
    departmentName = department.name,
    managerId = manager?.id,
    managerName = manager?.fullName,
    projects = projects.map { ProjectSummary(it.id, it.name, it.code) }.sortedBy { it.name },
    isActive = isActive
)
