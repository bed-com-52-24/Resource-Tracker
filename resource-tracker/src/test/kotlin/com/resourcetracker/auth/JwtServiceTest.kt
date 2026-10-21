package com.resourcetracker.auth

import com.resourcetracker.config.SecurityProps
import com.resourcetracker.department.Department
import com.resourcetracker.user.Role
import com.resourcetracker.user.User
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

class JwtServiceTest {
    @Test
    fun `default access token lifetime is thirty minutes and role remains signed in jwt`() {
        val props = SecurityProps(jwtSecret = "0123456789abcdef0123456789abcdef")
        val now = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS)
        val service = JwtService(props, Clock.fixed(now, ZoneOffset.UTC))
        val user = User(
            id = 17,
            username = "team-lead",
            email = "lead@example.com",
            fullName = "Team Lead",
            passwordHash = "hash",
            role = Role.TEAM_LEAD,
            department = Department(id = 4, name = "Engineering"),
        )

        val issued = service.issueAccessToken(user)
        val claims = requireNotNull(service.parse(issued.token))

        assertEquals(Duration.ofMinutes(30).seconds, props.accessTtl.seconds)
        assertEquals(1800L, claims.expiration.toInstant().epochSecond - claims.issuedAt.toInstant().epochSecond)
        assertEquals("TEAM_LEAD", claims["role"])
    }
}
