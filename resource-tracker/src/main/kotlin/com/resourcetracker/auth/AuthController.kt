package com.resourcetracker.auth

import com.resourcetracker.security.AuthenticatedUser
import com.resourcetracker.user.UserRepository
import com.resourcetracker.common.UnauthenticatedException
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
    @field:NotBlank @field:Size(max = 50) val username: String,
    @field:NotBlank @field:Size(max = 72) val password: String,
){
    override fun toString(): String = "LoginRequest(username=$username, password=***)"
}

data class RefreshRequest(@field:NotBlank val refreshToken: String){
    override fun toString() = "RefreshRequest(refreshToken=***)"
}

data class LogoutRequest(@field:NotBlank val refreshToken: String){
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
class AuthController(private val auth: AuthService, private val users: UserRepository){

    @PostMapping("/login")
    fun login(@Valid @RequestBody req: LoginRequest): LoginResponse = auth.login(req)

    @PostMapping("/refresh")
    fun refresh(@Valid @RequestBody req: RefreshRequest): TokenPair = auth.refresh(req.refreshToken)

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun logout(@AuthenticationPrincipal caller: AuthenticatedUser, @Valid @RequestBody req: LogoutRequest) = auth.logout(caller, req.refreshToken)

    @GetMapping("/me")
    fun me(@AuthenticationPrincipal caller: AuthenticatedUser) : ProfileResponse{
        val u = users.findById(caller.id).orElseThrow{ UnauthenticatedException() }
        return ProfileResponse(
            u.id.toString(), u.username, u.email, u.fullName,
            u.role.name, u.department, u.teams.sorted(), u.active,
        )
    }
}