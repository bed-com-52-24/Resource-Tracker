package com.resourcetracker.auth

import com.resourcetracker.security.AuthenticatedUser
import com.resourcetracker.user.UserRepository
import com.resourcetracker.common.UnauthenticatedException
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

data class LoginRequest(
    @field:NotBlank @field:Size(max = 50) @field:Schema(description = "Account username", example = "admin") val username: String,
    @field:NotBlank @field:Size(max = 72) @field:Schema(description = "Account password", example = "Admin@2026", format = "password") val password: String,
){
    override fun toString(): String = "LoginRequest(username=$username, password=***)"
}

data class RefreshRequest(@field:NotBlank @field:Schema(description = "Refresh token returned by login or refresh", example = "eyJ...refresh") val refreshToken: String){
    override fun toString() = "RefreshRequest(refreshToken=***)"
}

data class LogoutRequest(@field:NotBlank @field:Schema(description = "Refresh token to revoke", example = "eyJ...refresh") val refreshToken: String){
    override fun toString() = "LogoutRequest(refreshToken=***)"
}

data class UserSummary(val id: String, val username: String, val role: String, val department: String)

data class TokenPair(
    val accessToken: String,
    val refreshToken: String,
    val tokenType: String = "Bearer",
    val accessTokenExpiresIn: Long,
    val refreshTokenExpiresIn: Long,
){
    override fun toString() = "TokenPair(***)"
}

data class LoginResponse(val user: UserSummary, val tokens: TokenPair)

data class ProfileResponse(
    val id: String, val username:String, val email: String, val fullName: String,
    val role: String, val department: String, val teams: List<String>, val active: Boolean,
)

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication", description = "Login, token refresh, logout, and current profile")
class AuthController(private val auth: AuthService, private val users: UserRepository){

    @PostMapping("/login")
    @Operation(summary = "Log in", description = "Authenticate with username and password. Returns an access JWT and refresh token.")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Login successful", content = [Content(schema = Schema(implementation = LoginResponse::class))]),
        ApiResponse(responseCode = "400", description = "Invalid request body", content = [Content(schema = Schema(implementation = com.resourcetracker.common.ErrorResponse::class))]),
        ApiResponse(responseCode = "401", description = "Invalid credentials", content = [Content(schema = Schema(implementation = com.resourcetracker.common.ErrorResponse::class))]),
        ApiResponse(responseCode = "423", description = "Account temporarily locked", content = [Content(schema = Schema(implementation = com.resourcetracker.common.ErrorResponse::class))]),
    ])
    fun login(@Valid @RequestBody req: LoginRequest): LoginResponse = auth.login(req)

    @PostMapping("/refresh")
    @Operation(summary = "Refresh tokens", description = "Rotate a valid refresh token and return a new access token and refresh token.")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Tokens rotated", content = [Content(schema = Schema(implementation = TokenPair::class))]),
        ApiResponse(responseCode = "400", description = "Invalid request body", content = [Content(schema = Schema(implementation = com.resourcetracker.common.ErrorResponse::class))]),
        ApiResponse(responseCode = "401", description = "Refresh token invalid or expired", content = [Content(schema = Schema(implementation = com.resourcetracker.common.ErrorResponse::class))]),
    ])
    fun refresh(@Valid @RequestBody req: RefreshRequest): TokenPair = auth.refresh(req.refreshToken)

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Log out", description = "Revoke the supplied refresh token and current access token.", security = [SecurityRequirement(name = "bearerAuth")])
    @ApiResponses(value = [
        ApiResponse(responseCode = "204", description = "Logged out"),
        ApiResponse(responseCode = "401", description = "Missing or invalid access token", content = [Content(schema = Schema(implementation = com.resourcetracker.common.ErrorResponse::class))]),
    ])
    fun logout(@AuthenticationPrincipal caller: AuthenticatedUser, @Valid @RequestBody req: LogoutRequest) = auth.logout(caller, req.refreshToken)

    @GetMapping("/me")
    @Operation(summary = "Get current profile", description = "Return the profile associated with the authenticated access token.", security = [SecurityRequirement(name = "bearerAuth")])
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Current profile", content = [Content(schema = Schema(implementation = ProfileResponse::class))]),
        ApiResponse(responseCode = "401", description = "Missing or invalid access token", content = [Content(schema = Schema(implementation = com.resourcetracker.common.ErrorResponse::class))]),
    ])
    fun me(@AuthenticationPrincipal caller: AuthenticatedUser) : ProfileResponse{
        val u = users.findById(caller.id).orElseThrow{ UnauthenticatedException() }
        return ProfileResponse(
            u.id.toString(), u.username, u.email, u.fullName,
            u.role.name, u.department.name, u.teams.map { it.name }.sorted(), u.active,
        )
    }
}
