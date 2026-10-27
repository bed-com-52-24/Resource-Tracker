package com.resourcetracker.user

import com.resourcetracker.audit.AuditAction
import com.resourcetracker.audit.AuditRecord
import com.resourcetracker.audit.AuditService
import com.resourcetracker.auth.PasswordTokenRepository
import com.resourcetracker.auth.PasswordTokenService
import com.resourcetracker.auth.RefreshTokenRepository
import com.resourcetracker.department.Department
import com.resourcetracker.department.DepartmentRepository
import com.resourcetracker.team.TeamRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.Optional

class UserServiceTest {
    private val users = mock(UserRepository::class.java)
    private val refreshTokens = mock(RefreshTokenRepository::class.java)
    private val departments = mock(DepartmentRepository::class.java)
    private val teams = mock(TeamRepository::class.java)
    private val audit = mock(AuditService::class.java)
    private val passwordTokens = mock(PasswordTokenRepository::class.java)
    private val passwordTokenService = mock(PasswordTokenService::class.java)
    private val service = UserService(users, refreshTokens, departments, teams, audit, passwordTokens, passwordTokenService)

    @Test
    fun `role change audits safe user state without password data`() {
        val user = User(
            id = 11,
            username = "jdoe",
            email = "jdoe@example.com",
            passwordHash = "hashed-secret",
            role = Role.MEMBER,
            department = Department(id = 3, name = "Engineering"),
        )
        whenever(users.findById(11)).thenReturn(Optional.of(user))
        whenever(users.saveAndFlush(any<User>())).thenAnswer { it.getArgument<User>(0) }

        service.update(11, UpdateUserRequest(role = Role.ADMIN))

        val record = argumentCaptor<AuditRecord>()
        verify(audit).record(record.capture())
        assertEquals(AuditAction.USER_ROLE_UPDATED, record.firstValue.action)
        assertEquals("USER", record.firstValue.itemType)
        assertEquals(listOf("role"), record.firstValue.changedFields)
        assertFalse((record.firstValue.newState as Map<*, *>).containsKey("password"))
        assertFalse((record.firstValue.newState as Map<*, *>).containsKey("passwordHash"))
    }
}
