package com.resourcetracker.project

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import java.time.LocalDate
import java.util.UUID

data class CreateProjectRequest(
    @field:NotBlank @field:Size(min = 2, max = 100)
    val name: String,

    @field:NotBlank @field:Pattern(
        regexp = "^[A-Z0-9]{2,20}$",
        message = "Code must be 2-20 uppercase letters or digits"
    )
    val code: String,

    @field:NotNull
    val departmentId: Int,

    @field:NotNull
    val startDate: LocalDate,

    val endDate: LocalDate? = null
)

data class UpdateProjectRequest(
    @field:Schema(description = "Optional project name. Null or omitted preserves the current name.")
    @field:NotBlank @field:Size(min = 2, max = 100)
    val name: String? = null,

    @field:Schema(description = "Optional project code. Null or omitted preserves the current code.")
    @field:NotBlank @field:Pattern(
        regexp = "^[A-Z0-9]{2,20}$",
        message = "Code must be 2-20 uppercase letters or digits"
    )
    val code: String? = null,

    @field:Schema(description = "Optional department ID. Null or omitted preserves the current department.")
    @field:NotNull
    val departmentId: Int? = null,

    @field:Schema(description = "Optional start date. Null or omitted preserves the current start date.")
    @field:NotNull
    val startDate: LocalDate? = null,

    @field:Schema(description = "Optional end date. Null or omitted preserves the current end date.")
    val endDate: LocalDate? = null,

    @field:Schema(description = "Set to false to deactivate the project, true to reactivate it. Omit to leave unchanged.",nullable = true)
    val active: Boolean? = null,
)

data class ProjectResponse(
    val id: UUID,
    val name: String,
    val code: String,
    val departmentId: Int,
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
