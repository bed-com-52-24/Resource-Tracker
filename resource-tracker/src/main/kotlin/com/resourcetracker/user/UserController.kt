package com.resourcetracker.user

import com.resourcetracker.common.ErrorResponse
import com.resourcetracker.security.RequiresRoles
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import jakarta.validation.constraints.Min
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.http.HttpStatus
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@Validated
@RestController
@RequestMapping("/api/v1/users")
@RequiresRoles(Role.ADMIN)
@Tag(name = "Users", description = "Create and manage Resource Tracker user accounts")
@SecurityRequirement(name = "bearerAuth")
class UserController(private val users: UserService) {

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a user", description = "Creates an account. MANAGING_DIRECTOR is reserved and cannot be assigned through this endpoint.")
    @ApiResponses(value = [
        ApiResponse(responseCode = "201", description = "User created", content = [Content(schema = Schema(implementation = UserResponse::class))]),
        ApiResponse(responseCode = "400", description = "Invalid input or weak password", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ApiResponse(responseCode = "401", description = "Authentication required", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ApiResponse(responseCode = "403", description = "Admin role required", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ApiResponse(responseCode = "409", description = "Username or email already exists", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
    ])
    fun create(@Valid @RequestBody request: CreateUserRequest): UserResponse = users.create(request)

    @PutMapping("/{id}")
    @Operation(summary = "Update a user's profile", description = "Updates email, full name, department, and teams. Username and role are unchanged.")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "User updated", content = [Content(schema = Schema(implementation = UserResponse::class))]),
        ApiResponse(responseCode = "400", description = "Invalid input", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ApiResponse(responseCode = "401", description = "Authentication required", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ApiResponse(responseCode = "403", description = "Admin role required", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ApiResponse(responseCode = "404", description = "User not found", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ApiResponse(responseCode = "409", description = "Email already exists", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
    ])
    fun update(
        @Parameter(description = "User ID", example = "42") @PathVariable @Min(1) id: Int,
        @Valid @RequestBody request: UpdateUserRequest,
    ): UserResponse = users.update(id, request)

    @PutMapping("/{id}/role")
    @Operation(summary = "Change a user's role", description = "Changes the role. The final active Admin cannot be demoted.")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Role changed", content = [Content(schema = Schema(implementation = UserResponse::class))]),
        ApiResponse(responseCode = "400", description = "Invalid or reserved role", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ApiResponse(responseCode = "401", description = "Authentication required", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ApiResponse(responseCode = "403", description = "Admin role required", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ApiResponse(responseCode = "404", description = "User not found", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ApiResponse(responseCode = "409", description = "Cannot demote the final active Admin", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
    ])
    fun changeRole(
        @Parameter(description = "User ID", example = "42") @PathVariable @Min(1) id: Int,
        @Valid @RequestBody request: ChangeRoleRequest,
    ): UserResponse = users.changeRole(id, request.role)

    @PostMapping("/{id}/deactivate")
    @Operation(summary = "Deactivate a user", description = "Disables the account and revokes its refresh tokens. The final active Admin cannot be deactivated.")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "User deactivated", content = [Content(schema = Schema(implementation = UserResponse::class))]),
        ApiResponse(responseCode = "401", description = "Authentication required", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ApiResponse(responseCode = "403", description = "Admin role required", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ApiResponse(responseCode = "404", description = "User not found", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ApiResponse(responseCode = "409", description = "Cannot deactivate the final active Admin", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
    ])
    fun deactivate(
        @Parameter(description = "User ID", example = "42") @PathVariable @Min(1) id: Int,
    ): UserResponse = users.deactivate(id)

    @GetMapping
    @Operation(summary = "List users", description = "Returns a paginated user list. Search matches username, email, or full name. Page size is capped at 100.")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "User page", content = [Content(schema = Schema(implementation = UserPageResponse::class))]),
        ApiResponse(responseCode = "401", description = "Authentication required", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ApiResponse(responseCode = "403", description = "Admin role required", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
    ])
    fun list(
        @Parameter(description = "Search username, email, or full name", example = "jane")
        @RequestParam(required = false) search: String?,
        @Parameter(description = "Zero-based page number", example = "0")
        @RequestParam(defaultValue = "0") @Min(0) page: Int,
        @Parameter(description = "Page size, from 1 to 100", example = "20")
        @RequestParam(defaultValue = "20") @Min(1) size: Int,
        @Parameter(description = "Sort field: username, email, fullName, role, department, or active", example = "username")
        @RequestParam(defaultValue = "username") sort: String,
        @Parameter(description = "Sort direction: asc or desc", example = "asc")
        @RequestParam(defaultValue = "asc") direction: String,
    ): UserPageResponse {
        val sortField = sort.takeIf { it in ALLOWED_SORT_FIELDS } ?: "username"
        val sortDirection = if (direction.equals("desc", ignoreCase = true)) Sort.Direction.DESC else Sort.Direction.ASC
        val result: Page<UserResponse> = users.list(
            search,
            PageRequest.of(page, size.coerceAtMost(MAX_PAGE_SIZE), Sort.by(sortDirection, sortField)),
        )
        return UserPageResponse(
            content = result.content,
            page = result.number,
            size = result.size,
            totalElements = result.totalElements,
            totalPages = result.totalPages,
        )
    }

    companion object {
        private const val MAX_PAGE_SIZE = 100
        private val ALLOWED_SORT_FIELDS = setOf("username", "email", "fullName", "role", "department", "active")
    }
}

@Schema(description = "Paginated users")
data class UserPageResponse(
    val content: List<UserResponse>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
)
