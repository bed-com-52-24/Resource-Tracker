package com.resourcetracker.user

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Information required to create a user")
data class CreateUserRequest(
    @field:NotBlank @field:Size(min = 3, max = 50) @field:Schema(example = "jdoe") val username: String,
    @field:NotBlank @field:Email @field:Size(max = 150) @field:Schema(example = "jdoe@example.com") val email: String,
    @field:NotBlank @field:Size(max = 255) @field:Schema(example = "Jane Doe") val fullName: String,
    @field:NotBlank @field:Schema(example = "StrongPass1!", format = "password", description = "At least 8 characters, with uppercase, digit, and special character") val password: String,
    @field:Schema(example = "TEAM_LEAD", allowableValues = ["TEAM_LEAD", "DEPARTMENT_MANAGER", "ADMIN", "MEMBER"]) val role: Role,
    @field:Schema(description = "Existing active department ID. The department must exist.", example = "1")
    val departmentId: Int,
    @field:Schema(
        description = "Existing active team IDs. Each team must exist and be active. " +
                "Required for MEMBER users; optional for ADMIN and DEPARTMENT_MANAGER users.",
        example = "[3, 4]",
        nullable = true,
    )
    val teamIds: Set<Int>? = null,
)

@Schema(description = "Editable profile fields for a user")
data class UpdateUserRequest(
    @field:NotBlank @field:Email @field:Size(max = 150) @field:Schema(example = "jjester@example.com")     val email: String? = null,
    @field:Size(max = 255) @field:Schema(example = "Jeckman Jester") val fullName: String? = null,
    
    @field:Schema(description = "Set to false to deactivate the account (revokes refresh tokens). Null or omitted leaves the current value unchanged.", nullable = true)
    val active: Boolean? = null,
    
    @field:Schema(description = "New role. Optional; requires Admin. Omit to leave the role unchanged.", nullable = true)
    val role: Role? = null,
    @field:Schema(description = "Existing active department ID. Null or omitted leaves the current department unchanged.", example = "1", nullable = true)
    val departmentId: Int? = null,
    @field:Schema(description = "Existing active team IDs. Null or omitted leaves the current team assignments unchanged.", example = "[3, 4]", nullable = true)
    val teamIds: Set<Int>? = null,
)
data class UserResponse(
    val id: Int,
    val username: String,
    val email: String,
    val fullName: String,
    val role: Role,
    val departmentId: Int,
    val departmentName: String,
    val teamIds: List<Int>,
    val teamNames: List<String>,
    val active: Boolean,
)
