package com.resourcetracker.department

import com.resourcetracker.common.UserReference
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import jakarta.validation.constraints.Min
import io.swagger.v3.oas.annotations.media.Schema

data class DepartmentRequest(
    @field:NotBlank(message = "Department name is required")
    @field:Size(min = 2, max = 100, message = "Department name must be 2 to 100 characters")
    val name: String,

    @field:Size(max = 255, message = "Description must be at most 255 characters")
    val description: String? = null
)

data class DepartmentReference(
    val id: Int,
    val name: String,
)

data class DepartmentResponse(
    val id: Int,
    val name: String,
    val description: String?,
    val status: String,
    val manager: UserReference? = null,
)

data class UpdateDepartmentRequest(
    @field:Schema(description = "Optional department name. Null or omitted preserves the current name.",
        nullable = true
    )
    //@field:NotBlank(message = "Department name is required")
    @field:Size(min = 2, max = 100, message = "Department name must be 2 to 100 characters")
    val name: String? = null,

    @field:Schema(description = "Optional description. Null or omitted preserves the current description.")
    @field:Size(max = 255, message = "Description must be at most 255 characters")
    val description: String? = null,

    @field:Schema(
        description = "Set to false to deactivate the department, true to reactivate it. Omit to leave unchanged.",
        nullable = true
    )
    val active: Boolean? = null,

    @field:Schema(
        description = "Optional. ID of an active Department Manager to assign to this department." +
                "Any current manager is unassigned. Omit to leave the manager unchanged.",
        nullable = true,
        example = "2"
    )
    @field:Min(value = 1, message = "Manager ID must be positive")
    val managerId: Int? = null,
    )
