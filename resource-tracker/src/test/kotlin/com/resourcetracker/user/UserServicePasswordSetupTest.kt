package com.resourcetracker.user

import com.resourcetracker.auth.PasswordTokenPurpose
import com.resourcetracker.auth.PasswordTokenRepository
import com.resourcetracker.auth.PasswordTokenService
import com.resourcetracker.auth.RefreshTokenRepository
import com.resourcetracker.audit.AuditService
import com.resourcetracker.common.BusinessRuleException
import com.resourcetracker.department.Department
import com.resourcetracker.department.DepartmentRepository
import com.resourcetracker.team.TeamRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.mock
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.Optional

class UserServicePasswordSetupTest {
    private val users = mock(UserRepository::class.java)
    private val refreshTokens = mock(RefreshTokenRepository::class.java)
    private val departments = mock(DepartmentRepository::class.java)
    private val teams = mock(TeamRepository::class.java)
    private val audit = mock(AuditService::class.java)
    private val passwordTokens = mock(PasswordTokenRepository::class.java)
    private val tokenService = mock(PasswordTokenService::class.java)
    private val service = UserService(users, refreshTokens, departments, teams, audit, passwordTokens, tokenService)

    @Test
    fun `creating a user leaves password unset and issues setup link`() {
        val department = Department(id = 3, name = "Engineering")
        val saved = User(
            id = 22,
            username = "jdoe",
            email = "jdoe@example.com",
            role = Role.ADMIN,
            department = department,
        )
        whenever(departments.findById(3)).thenReturn(Optional.of(department))
        whenever(users.saveAndFlush(any())).thenReturn(saved)

        val response = service.create(
            CreateUserRequest(
                username = "jdoe",
                email = "jdoe@example.com",
                role = Role.ADMIN,
                departmentId = 3,
            )
        )

        assertFalse(response.passwordSet)
        assertEquals("jdoe", response.fullName)
        verify(tokenService).issue(eq(saved), eq(PasswordTokenPurpose.SETUP), isNull())
    }

    @Test
    fun `member creation requires a team`() {
        val error = assertThrows<BusinessRuleException> {
            service.create(
                CreateUserRequest(
                    username = "member",
                    email = "member@example.com",
                    role = Role.MEMBER,
                    departmentId = 3,
                )
            )
        }

        assertEquals("MEMBER users must be assigned to at least one team", error.message)
    }

    @Test
    fun `last active admin cannot be demoted`() {
        val department = Department(id = 3, name = "Engineering")
        val admin = User(
            id = 22,
            username = "admin",
            email = "admin@example.com",
            role = Role.ADMIN,
            department = department,
        )
        whenever(users.findById(22)).thenReturn(Optional.of(admin))
        whenever(users.findActiveByRoleForUpdate(Role.ADMIN)).thenReturn(listOf(admin))

        assertThrows<BusinessRuleException> {
            service.update(22, UpdateUserRequest(role = Role.TEAM_LEAD))
        }
    }
}
