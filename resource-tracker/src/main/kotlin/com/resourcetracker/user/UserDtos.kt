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
    @field:Schema(example = "TEAM_LEAD", allowableValues = ["TEAM_LEAD", "DEPARTMENT_MANAGER", "ADMIN"]) val role: Role,
    val department: String? = null,
    val teams: Set<String> = emptySet(),
)

@Schema(description = "Editable profile fields for a user")
data class UpdateUserRequest(
    @field:NotBlank @field:Email @field:Size(max = 150) @field:Schema(example = "jjester@example.com") val email: String,
    @field:NotBlank @field:Size(max = 255) @field:Schema(example = "Jeckman Jester") val fullName: String,
    
    @field:Schema(description = "Set to false to deactivate the account (revokes refresh tokens). Omit to leave unchanged.",nullable = true)
    val active: Boolean? = null,
    
    @field:Schema(description = "New role. Optional; requires Admin. Omit to leave the role unchanged.", nullable = true)
    val role: Role? = null,
    val department: String? = null,
    val teams: Set<String> = emptySet(),
)
data class UserResponse(
    val id: Int,
    val username: String,
    val email: String,
    val fullName: String,
    val role: Role,
    val department: String?,
    val teams: List<String>,
    val active: Boolean,
)
