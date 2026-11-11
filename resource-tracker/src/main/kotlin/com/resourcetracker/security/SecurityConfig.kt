package com.resourcetracker.security

import com.resourcetracker.auth.JwtService
import com.resourcetracker.auth.RevokedAccessTokenRepository
import com.resourcetracker.common.ErrorResponse
import com.resourcetracker.user.Role
import com.resourcetracker.user.UserRepository
import tools.jackson.databind.ObjectMapper
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.AuthenticationException
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.time.Clock

data class AuthenticatedUser(
    val id: Int,
    val username: String,
    val role: Role,
    val jti: String,
    val tokenExpiresAt: java.time.Instant,
)

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
class SecurityConfig {
    @Bean
    fun clock(): Clock = Clock.systemUTC()

    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder(12)

    @Bean
    fun filterChain(
        http: HttpSecurity,
        jwt: JwtService,
        revoked: RevokedAccessTokenRepository,
        users: UserRepository,
        entryPoint: RestAuthenticationEntryPoint,
    ): SecurityFilterChain = http
        .csrf { it.disable() }
        .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
        .exceptionHandling { it.authenticationEntryPoint(entryPoint) }
        .authorizeHttpRequests {
            it.requestMatchers(
                "/api/v1/auth/login", "/api/v1/auth/refresh", "/api/v1/auth/password",
                "/api/v1/auth/forgot-password", "/api/v1/health", "/error",
                "/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**",
            ).permitAll()
                .anyRequest().authenticated()
        }
        .addFilterBefore(
            JwtAuthenticationFilter(jwt, revoked, users),
            UsernamePasswordAuthenticationFilter::class.java,
        )
        .build()
}

class JwtAuthenticationFilter(
    private val jwt: JwtService,
    private val revoked: RevokedAccessTokenRepository,
    private val users: UserRepository,
) : OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val header = request.getHeader(HttpHeaders.AUTHORIZATION)
        if (header != null && header.startsWith("Bearer ")) {
            authenticate(header.substring(7).trim(), request)
        }
        filterChain.doFilter(request, response)
    }

    private fun authenticate(token: String, request: HttpServletRequest) {
        val claims = jwt.parse(token) ?: return
        val jti = claims.id ?: return
        if (revoked.existsById(jti)) return

        val userId = claims.subject.toIntOrNull() ?: return
        val user = users.findById(userId).orElse(null) ?: return
        if (!user.active) return

        val principal = AuthenticatedUser(
            user.id,
            user.username,
            user.role,
            jti,
            claims.expiration.toInstant(),
        )
        val auth = UsernamePasswordAuthenticationToken(
            principal,
            null,
            listOf(SimpleGrantedAuthority("ROLE_${user.role.name}")),
        )
        auth.details = WebAuthenticationDetailsSource().buildDetails(request)
        SecurityContextHolder.getContext().authentication = auth
    }
}

@Component
class RestAuthenticationEntryPoint(private val mapper: ObjectMapper) : AuthenticationEntryPoint {
    override fun commence(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authException: AuthenticationException
    ) {
        response.status = HttpServletResponse.SC_UNAUTHORIZED
        response.contentType = MediaType.APPLICATION_JSON_VALUE
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer")

        val correlationId = request.getHeader("X-Correlation-ID")
            ?.takeIf { it.matches(Regex("[A-Za-z0-9._-]{1,100}")) }
            ?: java.util.UUID.randomUUID().toString()

        response.setHeader("X-Correlation-ID", correlationId)

        mapper.writeValue(
            response.outputStream,
            ErrorResponse(
                timestamp = java.time.Instant.now(),
                status = 401,
                error = "Unauthorized",
                code = "UNAUTHENTICATED",
                message = "Authentication required",
                path = request.requestURI,
                correlationId = correlationId,
                details = emptyList(),
                action = "Sign in and try again.",
            )
        )
    }
}
