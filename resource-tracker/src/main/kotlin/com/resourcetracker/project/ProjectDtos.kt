package com.resourcetracker.project

import com.resourcetracker.common.DepartmentReference
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.Size
import java.time.LocalDate

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
    @field:Size(min = 2, max = 100)
    @field:Pattern(regexp = "^.*\\S.*$", message = "Name cannot be blank")
    @field:Schema(description = "Optional project name. Null or omitted preserves the current name.")
    val name: String? = null,

    @field:Size(min = 2, max = 20)
    @field:Pattern(
        regexp = "^[A-Z0-9]{2,20}$",
        message = "Code must be 2-20 uppercase letters or digits"
    )
    @field:Schema(description = "Optional project code. Null or omitted preserves the current code.")
    val code: String? = null,

    @field:Positive
    @field:Schema(description = "Optional department ID. Null or omitted preserves the current department.")
    val departmentId: Int? = null,

    @field:Schema(description = "Optional start date. Null or omitted preserves the current start date.")
    val startDate: LocalDate? = null,

    @field:Schema(description = "Optional end date. Null or omitted preserves the current end date.")
    val endDate: LocalDate? = null,

    @field:Schema(description = "Set to false to deactivate the project, true to reactivate it. Omit to leave unchanged.",nullable = true)
    val active: Boolean? = null,
)

data class ProjectResponse(
    val id: Int,
    val name: String,
    val code: String,
    val department: DepartmentReference,
    val startDate: LocalDate,
    val endDate: LocalDate?,
    val active: Boolean
)

fun Project.toResponse() = ProjectResponse(
    id = id,
    name = name,
    code = code,
    department = DepartmentReference(requireNotNull(department.id), department.name),
    startDate = startDate,
    endDate = endDate,
    active = active
)
