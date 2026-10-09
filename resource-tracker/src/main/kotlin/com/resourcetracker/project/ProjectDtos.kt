package com.resourcetracker.project

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import java.time.LocalDate
import java.util.UUID

data class CreateProjectRequest(
    @field:NotBlank @field:Size(min = 2, max = 100)
    val name: String,

    @field:NotBlank @field:Pattern(regexp = "^[A-Z0-9]{2,20}$", message = "Code must be 2-20 uppercase letters or digits")
    val code: String,

    @field:NotNull
    val departmentId: UUID,

    @field:NotNull
    val startDate: LocalDate,

    val endDate: LocalDate? = null
)

data class UpdateProjectRequest(
    @field:NotBlank @field:Size(min = 2, max = 100)
    val name: String,

    @field:NotBlank @field:Pattern(regexp = "^[A-Z0-9]{2,20}$", message = "Code must be 2-20 uppercase letters or digits")
    val code: String,

    @field:NotNull
    val departmentId: UUID,

    @field:NotNull
    val startDate: LocalDate,

    val endDate: LocalDate? = null
)

data class ProjectResponse(
    val id: UUID,
    val name: String,
    val code: String,
    val departmentId: UUID,
    val departmentName: String,
    val startDate: LocalDate,
    val endDate: LocalDate?,
    val active: Boolean
)

fun Project.toResponse() = ProjectResponse(
    id = id!!,
    name = name,
    code = code,
    departmentId = department.id!!,
    departmentName = department.name,
    startDate = startDate,
    endDate = endDate,
    active = active
)
