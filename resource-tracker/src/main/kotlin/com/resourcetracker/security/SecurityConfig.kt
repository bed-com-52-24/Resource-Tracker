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
import org.springframework.security.web.access.AccessDeniedHandler
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
        accessDeniedHandler: RestAccessDeniedHandler,
    ): SecurityFilterChain = http
        .csrf { it.disable() }
        .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
        .exceptionHandling {
            it.authenticationEntryPoint(entryPoint)
                .accessDeniedHandler(accessDeniedHandler)
        }
        .authorizeHttpRequests {
            it.requestMatchers(
                "/api/v1/auth/login", "/api/v1/auth/refresh", "/api/v1/auth/set-password",
                "/api/v1/auth/forgot-password", "/api/v1/health", "/error",
                "/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**",
            ).permitAll()
                .requestMatchers(
                    "/api/v1/departments/**",
                    "/api/v1/vendors/**",
                    "/api/v1/projects/**",
                ).hasRole("ADMIN")
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
        authException: AuthenticationException,
    ) {
        response.status = HttpServletResponse.SC_UNAUTHORIZED
        response.contentType = MediaType.APPLICATION_JSON_VALUE
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer")
        mapper.writeValue(
            response.outputStream,
            ErrorResponse.from(
                request,
                org.springframework.http.HttpStatus.UNAUTHORIZED,
                "UNAUTHENTICATED",
                "Authentication required",
            ),
        )
    }
}

@Component
class RestAccessDeniedHandler(private val mapper: ObjectMapper) : AccessDeniedHandler {
    override fun handle(
        request: HttpServletRequest,
        response: HttpServletResponse,
        accessDeniedException: org.springframework.security.access.AccessDeniedException,
    ) {
        val actualRole = (SecurityContextHolder.getContext().authentication?.principal as? AuthenticatedUser)
            ?.role?.name ?: "UNKNOWN"
        response.status = HttpServletResponse.SC_FORBIDDEN
        response.contentType = MediaType.APPLICATION_JSON_VALUE
        mapper.writeValue(
            response.outputStream,
            ErrorResponse.from(
                request,
                org.springframework.http.HttpStatus.FORBIDDEN,
                "INSUFFICIENT_ROLE",
                "Access requires ADMIN; caller role is $actualRole",
                listOf("Required role: ADMIN", "Caller role: $actualRole"),
            ),
        )
    }
}
