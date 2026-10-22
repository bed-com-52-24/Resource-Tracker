package com.resourcetracker.user

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Information required to create a user")
data class CreateUserRequest(
    @field:NotBlank @field:Size(min = 3, max = 50) @field:Schema(example = "jdoe") val username: String,
    @field:NotBlank @field:Email @field:Size(max = 150) @field:Schema(example = "jdoe@example.com") val email: String,
    @field:Schema(example = "MEMBER", allowableValues = ["MEMBER", "TEAM_LEAD", "DEPARTMENT_MANAGER", "ADMIN"]) val role: Role,
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

@Schema(description = "Role and account status fields that an administrator may update")
data class UpdateUserRequest(
    @field:Schema(description = "Set to false to deactivate the account (revokes refresh tokens). Null or omitted leaves the current value unchanged.", nullable = true)
    val active: Boolean? = null,
    @field:Schema(description = "New role. Optional; requires Admin. Omit to leave the role unchanged.", nullable = true)
    val role: Role? = null,
)

data class PasswordSetRequest(
    @field:NotBlank val token: String,
    @field:NotBlank val password: String,
) {
    override fun toString() = "PasswordSetRequest(token=***, password=***)"
}

data class ForgotPasswordRequest(
    @field:NotBlank @field:Email @field:Size(max = 150) val email: String,
)

data class PasswordSetResponse(val passwordSet: Boolean)

data class PasswordResetResponse(val message: String)

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
    val passwordSet: Boolean,
)
