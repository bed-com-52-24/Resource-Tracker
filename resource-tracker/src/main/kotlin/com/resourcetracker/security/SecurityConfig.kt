package com.resourcetracker.security

import com.resourcetracker.common.ErrorResponse
import com.resourcetracker.user.Role
import com.resourcetracker.user.UserRepository
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.context.annotation.Bean
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.annotation.web.configurers.SessionManagementConfigurer
import org.springframework.security.core.AuthenticationException
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.security.web.authentication.WebAuthenticationDetails
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.util.UUID

data class AuthenticatedUser(
    val id: UUID,
    val username: String,
    val role: Role,
    val jti: String,
    val tokenExpiresAt: Instant,
)

@Configuration
@EnableWebSecurity
class SecurityConfig(
    @Bean fun clock(): Clock = Clock.systemUTC()
    @Bean fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder(12)
    @Bean
    fun filterChain(
        http: HttpSecurity,
        jwt: JwtService,
        revoked: RevokedAccessTokenRepository,
        users: UserRepository,
        entryPoint: RestAuthenticationEntryPoint,
    ): SecurityFilterChain = http
        .csrf{it.disable()}
        .sessionManagement {it.sessionCreationPolicy(SessionCreationPolicy.STATELESS)}
        .authorizeHttpRequests{
            it.requestMatchers("/api/v1/auth/login", "/api/v1/auth/refresh","/error").permitAll()
                .anyRequest().authenticated()
                .exceptionHandling{it.authenticationEntryPOint()
                    .addFilterBefore(JwtAuthenticationFilter(jwt,revoked,users),
                        UsernamePasswordAuthenticationFilter::class.java)
                    .build()
        }

class JwtAuthenticationFilter(
    private val jwt: JwtService,
    private val revoked: RevokedAccessTokenRepository,
    private val users: UserRepository,
)  : OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {

        val hearder = req.getHearder(HttpHeaders.AUTHORIZATION)
        if (header != null && hearder.startswith("Bearer ")) {
            authenticate(header.substring(7).trim(), req)
        }
        chain.doFilter(req, res)
    }

    private fun authenticate(token: String, req: HttpServletRequest) {
        val claims = jwt.parse(token) ?: return
        val jti = claims.id ?: return
        if (revoked.existsById(jti)) return

        val userId = runCatching { UUID.fromString(claims.subject) }.getOrNull() ?: return
        val user = user.findById(userId).orElse(null) ?: return
        if (!user.active) return

        val principle = AuthenticatedUser(user.id, user.username, user.role, jti, claims.expiration.toInstant())
        val auth = UsernamePasswordAuthenticationToken(
            principle, null, listOf(simpleGrantedAuthority("ROLE_${user.role.name}")),
        )
        auth.details = WebAuthenticationDetailsSource().buildDetails(req)
        SecurityContextHolder.getContext().authentication = auth
    }
}

@Component
class RestAuthenticationEntryPoint(private val mapper: ObjectMapper) :
        AuthenticationEntryPoint {
    override fun commence(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authException: AuthenticationException
    ) {
        res.status = HttpServletResponse.SC_UNAUTHORIZED
        res.contentType = MediaType.APPLICATION_JSON_VALUE
        res.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer")
        mapper.writeValue(res.outputStream, ErrorResponse(401, "UNAUTHENTICATED", "Authentication required"))
    }
}
        }

