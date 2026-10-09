package com.resourcetracker.department

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class DepartmentRequest(
    @field:NotBlank(message = "Department name is required")
    @field:Size(min = 2, max = 100, message = "Department name must be 2 to 100 characters")
    val name: String,

    @field:Size(max = 255, message = "Description must be at most 255 characters")
    val description: String? = null
)

data class DepartmentResponse(
    val id: Int,
    val name: String,
    val description: String?,
    val status: String
)
