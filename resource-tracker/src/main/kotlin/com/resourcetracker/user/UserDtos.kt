package com.resourcetracker.user

import com.resourcetracker.common.DepartmentReference
import com.resourcetracker.common.TeamReference
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Information required to create a user")
data class CreateUserRequest(
    @field:NotBlank @field:Size(min = 3, max = 50) @field:Schema(example = "jdoe") val username: String,
    @field:NotBlank @field:Email @field:Size(max = 150) @field:Schema(example = "jdoe@example.com") val email: String,
    @field:Schema(example = "MANAGING_DIRECTOR", allowableValues = ["MEMBER", "TEAM_LEAD", "DEPARTMENT_MANAGER", "ADMIN", "MANAGING_DIRECTOR"]) val role: Role,
    @field:NotBlank @field:Size(max = 100) @field:Schema(example = "Jane") val firstName: String,
    @field:NotBlank @field:Size(max = 100) @field:Schema(example = "Doe") val lastName: String,
    @field:Size(max = 100) val middleName: String? = null,
    @field:Size(max = 150) val position: String? = null,
    @field:Size(max = 30) val phone: String? = null,
    @field:Size(max = 500) val address: String? = null,
    @field:Size(max = 50) val title: String? = null,
    @field:Schema(description = "Existing active department ID. Required for MEMBER, TEAM_LEAD, and ADMIN users; optional for DEPARTMENT_MANAGER and MANAGING_DIRECTOR users.", example = "1", nullable = true)
    val departmentId: Int? = null,
    @field:Schema(
        description = "Optional existing active team IDs. Omit or send an empty set to create the user without team assignment; a department manager can assign the user later. " +
                "Each supplied team must exist, be active, and belong to the selected department. Managing Directors cannot be assigned to teams.",
        example = "[3, 4]",
        nullable = true,
    )
    val teamIds: Set<Int>? = null,
)

@Schema(description = "Profile, role, and account status fields that an administrator may update")
data class UpdateUserRequest(
    @field:Pattern(regexp = "^.*\\S.*$", message = "First name cannot be blank")
    @field:Size(max = 100) val firstName: String? = null,
    @field:Pattern(regexp = "^.*\\S.*$", message = "Last name cannot be blank")
    @field:Size(max = 100) val lastName: String? = null,
    @field:Size(max = 100) val middleName: String? = null,
    @field:Size(max = 150) val position: String? = null,
    @field:Size(max = 30) val phone: String? = null,
    @field:Size(max = 500) val address: String? = null,
    @field:Size(max = 50) val title: String? = null,
    @field:Schema(description = "Set to false to deactivate the account (revokes refresh tokens). Null or omitted leaves the current value unchanged.", nullable = true)
    val active: Boolean? = null,
    @field:Schema(description = "New role. Optional; requires Admin or Managing Director. Omit to leave the role unchanged.", nullable = true)
    val role: Role? = null,
)

@Schema(description = "Profile fields the authenticated user may update")
data class UpdateOwnProfileRequest(
    @field:Pattern(regexp = "^.*\\S.*$", message = "First name cannot be blank")
    @field:Size(max = 100) val firstName: String? = null,
    @field:Pattern(regexp = "^.*\\S.*$", message = "Last name cannot be blank")
    @field:Size(max = 100) val lastName: String? = null,
    @field:Size(max = 100) val middleName: String? = null,
    @field:Size(max = 150) val position: String? = null,
    @field:Size(max = 30) val phone: String? = null,
    @field:Size(max = 500) val address: String? = null,
    @field:Size(max = 50) val title: String? = null,
)

data class ChangePasswordRequest(
    @field:Size(min = 43, max = 64) val token: String? = null,
    @field:Size(max = 72) val currentPassword: String? = null,
    @field:NotBlank @field:Size(min = 8, max = 72) val newPassword: String,
) {
    override fun toString() = "ChangePasswordRequest(token=***, currentPassword=***, newPassword=***)"
}

@Schema(description = "Assign an existing active department to a department manager")
data class AssignDepartmentRequest(
    @field:Schema(description = "Existing active department ID", example = "1")
    val departmentId: Int,
)

data class PasswordSetRequest(
    @field:NotBlank @field:Size(min = 43, max = 64) val token: String,
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
    val firstName: String,
    val lastName: String,
    val middleName: String?,
    val position: String?,
    val phone: String?,
    val address: String?,
    val title: String?,
    val role: Role,
    val department: DepartmentReference?,
    val teams: List<TeamReference>,
    val active: Boolean,
    val passwordSet: Boolean,
)
