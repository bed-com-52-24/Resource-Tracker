package com.resourcetracker.user

import com.resourcetracker.audit.AuditAction
import com.resourcetracker.audit.AuditRecord
import com.resourcetracker.audit.AuditService
import com.resourcetracker.department.Department
import com.resourcetracker.department.DepartmentRepository
import com.resourcetracker.team.TeamRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.verify
import org.springframework.security.crypto.password.PasswordEncoder
import java.util.Optional

class UserServiceTest {
    private val users = mock(UserRepository::class.java)
    private val refreshTokens = mock(com.resourcetracker.auth.RefreshTokenRepository::class.java)
    private val encoder = mock(PasswordEncoder::class.java)
    private val departments = mock(DepartmentRepository::class.java)
    private val teams = mock(TeamRepository::class.java)
    private val audit = mock(AuditService::class.java)
    private val service = UserService(users, refreshTokens, encoder, departments, teams, audit)

    @Test
    fun `create audits safe user state without password data`() {
        val department = Department(id = 3, name = "Engineering")
        `when`(departments.findById(3)).thenReturn(Optional.of(department))
        `when`(encoder.encode("StrongPass1!")).thenReturn("hashed-secret")
        `when`(users.saveAndFlush(any<User>())).thenAnswer { it.getArgument<User>(0) }

        service.create(
            CreateUserRequest(
                username = "jdoe",
                email = "jdoe@example.com",
                fullName = "Jane Doe",
                password = "StrongPass1!",
                role = Role.TEAM_LEAD,
                departmentId = department.id,
            ),
        )

        val record = argumentCaptor<AuditRecord>()
        verify(audit).record(record.capture())
        assertEquals(AuditAction.USER_CREATED, record.firstValue.action)
        assertEquals("USER", record.firstValue.itemType)
        assertEquals(listOf("username", "email", "fullName", "role", "departmentId", "teamIds", "active"), record.firstValue.changedFields)
        assertEquals(false, (record.firstValue.newState as Map<*, *>).containsKey("password"))
        assertEquals(false, (record.firstValue.newState as Map<*, *>).containsKey("passwordHash"))
    }
}
