package com.resourcetracker.team

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.util.UUID

data class TeamRequest(
    @field:NotBlank @field:Size(min = 2, max = 100)
    val name: String,

    val departmentId: Int,

    val managerId: Int,

    val projectIds: Set<UUID> = emptySet()
)

data class ProjectSummary(val id: UUID, val name: String, val code: String)

data class TeamResponse(
    val id: Int,
    val name: String,
    val departmentId: Int,
    val departmentName: String,
    val managerId: Int?,
    val managerName: String?,
    val projects: List<ProjectSummary>,
    val status: String
)

fun Team.toResponse() = TeamResponse(
    id = id,
    name = name,
    departmentId = department.id,
    departmentName = department.name,
    managerId = manager?.id,
    managerName = manager?.fullName,
    projects = projects.map { ProjectSummary(it.id!!, it.name, it.code) }.sortedBy { it.name },
    status = status
)
