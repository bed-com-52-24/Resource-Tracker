package com.resourcetracker.auth

import com.resourcetracker.common.ApiException
import com.resourcetracker.common.InvalidCredentialsException
import com.resourcetracker.config.SecurityProps
import com.resourcetracker.department.Department
import com.resourcetracker.user.ForgotPasswordRequest
import com.resourcetracker.user.PasswordSetRequest
import com.resourcetracker.user.Role
import com.resourcetracker.user.User
import com.resourcetracker.user.UserRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.mock
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.security.crypto.password.PasswordEncoder
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Optional

class PasswordManagementServiceTest {
    private val users = mock(UserRepository::class.java)
    private val refreshTokens = mock(RefreshTokenRepository::class.java)
    private val revokedTokens = mock(RevokedAccessTokenRepository::class.java)
    private val encoder = mock(PasswordEncoder::class.java)
    private val jwt = mock(JwtService::class.java)
    private val passwordTokens = mock(PasswordTokenRepository::class.java)
    private val tokenService = mock(PasswordTokenService::class.java)
    private val now = Instant.parse("2026-10-22T00:00:00Z")
    private val props = SecurityProps(jwtSecret = "0123456789abcdef0123456789abcdef")

    private fun service(at: Instant = now): AuthService {
        whenever(encoder.encode(any())).thenReturn("dummy-hash")
        return AuthService(
            users,
            refreshTokens,
            revokedTokens,
            encoder,
            jwt,
            props,
            Clock.fixed(at, ZoneOffset.UTC),
            passwordTokens,
            tokenService,
        )
    }

    private fun user() = User(
        id = 11,
        username = "jdoe",
        email = "jdoe@example.com",
        role = Role.MEMBER,
        department = Department(id = 1, name = "Engineering"),
        active = true,
    )

    @Test
    fun `setup token without expiry sets password even a year later`() {
        val token = PasswordToken(
            userId = 11,
            tokenHash = "hash",
            purpose = PasswordTokenPurpose.SETUP,
        )
        val user = user()
        whenever(tokenService.hash("raw-token")).thenReturn("hash")
        whenever(passwordTokens.findForUpdateByTokenHash("hash")).thenReturn(token)
        whenever(users.findById(11)).thenReturn(Optional.of(user))
        val auth = service(now.plusSeconds(365L * 24 * 60 * 60))
        whenever(encoder.encode("StrongPass1!")).thenReturn("encoded-password")

        val result = auth.setPassword(PasswordSetRequest("raw-token", "StrongPass1!"))

        assertEquals(true, result.passwordSet)
        assertEquals("encoded-password", user.passwordHash)
        assertNotNull(token.usedAt)
        verify(refreshTokens).revokeAllByUserId(11)
    }

    @Test
    fun `expired reset token is rejected as invalid`() {
        val token = PasswordToken(
            userId = 11,
            tokenHash = "hash",
            purpose = PasswordTokenPurpose.RESET,
            expiresAt = now.minusSeconds(1),
        )
        whenever(tokenService.hash("raw-token")).thenReturn("hash")
        whenever(passwordTokens.findForUpdateByTokenHash("hash")).thenReturn(token)

        val error = assertThrows<ApiException> {
            service().setPassword(PasswordSetRequest("raw-token", "StrongPass1!"))
        }

        assertEquals("INVALID_TOKEN", error.code)
        assertEquals(null, token.usedAt)
    }

    @Test
    fun `setup token cannot be reused`() {
        val token = PasswordToken(
            userId = 11,
            tokenHash = "hash",
            purpose = PasswordTokenPurpose.SETUP,
        )
        val user = user()
        whenever(tokenService.hash("raw-token")).thenReturn("hash")
        whenever(passwordTokens.findForUpdateByTokenHash("hash")).thenReturn(token)
        whenever(users.findById(11)).thenReturn(Optional.of(user))
        val auth = service()
        whenever(encoder.encode("StrongPass1!")).thenReturn("encoded-password")

        auth.setPassword(PasswordSetRequest("raw-token", "StrongPass1!"))
        val error = assertThrows<ApiException> {
            auth.setPassword(PasswordSetRequest("raw-token", "StrongPass1!"))
        }

        assertEquals("INVALID_TOKEN", error.code)
    }

    @Test
    fun `login without password compares dummy hash then rejects credentials`() {
        val user = user()
        whenever(users.findForUpdateByUsername("jdoe")).thenReturn(user)
        whenever(encoder.matches("anything", "dummy-hash")).thenReturn(true)

        assertThrows<InvalidCredentialsException> {
            service().login(LoginRequest("jdoe", "anything"))
        }

        verify(encoder).matches("anything", "dummy-hash")
    }

    @Test
    fun `forgot password returns the same response for unknown and known emails`() {
        val user = user()
        whenever(users.findByEmailIgnoreCase("missing@example.com")).thenReturn(null)
        whenever(users.findByEmailIgnoreCase("jdoe@example.com")).thenReturn(user)

        val auth = service()
        val unknown = auth.forgotPassword(ForgotPasswordRequest("missing@example.com"))
        val known = auth.forgotPassword(ForgotPasswordRequest("jdoe@example.com"))

        assertEquals(unknown, known)
        assertNull(user.passwordHash)
        verify(tokenService).issue(eq(user), eq(PasswordTokenPurpose.RESET), any())
    }
}
