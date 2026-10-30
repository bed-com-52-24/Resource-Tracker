package com.resourcetracker.team

import com.resourcetracker.audit.AuditService
import com.resourcetracker.audit.AuditAction
import com.resourcetracker.audit.AuditRecord
import com.resourcetracker.common.BusinessRuleException
import com.resourcetracker.common.DuplicateResourceException
import com.resourcetracker.common.ResourceNotFoundException
import com.resourcetracker.department.Department
import com.resourcetracker.department.DepartmentRepository
import com.resourcetracker.project.ProjectRepository
import com.resourcetracker.security.AuthenticatedUser
import com.resourcetracker.user.Role
import com.resourcetracker.user.User
import com.resourcetracker.user.UserRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.verify
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import java.time.Instant
import java.util.Optional

class TeamServiceTest {

    private val teams = mock(TeamRepository::class.java)
    private val departments = mock(DepartmentRepository::class.java)
    private val users = mock(UserRepository::class.java)
    private val projects = mock(ProjectRepository::class.java)
    private val audit = mock(AuditService::class.java)
    private val memberHistory = mock(TeamMemberHistoryRepository::class.java)
    private val service = TeamService(teams, departments, users, projects, audit, memberHistory)

    private val dept = Department(id = 1, name = "Engineering")

    private fun user(role: Role = Role.DEPARTMENT_MANAGER, active: Boolean = true) = User(
        id = 7, username = "mgr", email = "mgr@example.com", fullName = "Manager One",
        passwordHash = "hash", role = role, department = dept, active = active
    )

    private fun request() = TeamRequest(name = "Alpha", departmentId = 1, managerId = 7)

    @Test
    fun `create fails when department does not exist`() {
        `when`(departments.findById(1)).thenReturn(Optional.empty())
        assertThrows<ResourceNotFoundException> { service.create(request()) }
    }

    @Test
    fun `create fails when department is inactive`() {
        dept.status = "INACTIVE"
        `when`(departments.findById(1)).thenReturn(Optional.of(dept))
        assertThrows<ResourceNotFoundException> { service.create(request()) }
    }

    @Test
    fun `create fails on duplicate team name in same department`() {
        `when`(departments.findById(1)).thenReturn(Optional.of(dept))
        `when`(teams.existsByDepartmentIdAndNameIgnoreCase(1, "Alpha")).thenReturn(true)
        assertThrows<DuplicateResourceException> { service.create(request()) }
    }

    @Test
    fun `create fails when manager does not exist`() {
        `when`(departments.findById(1)).thenReturn(Optional.of(dept))
        `when`(users.findById(7)).thenReturn(Optional.empty())
        assertThrows<ResourceNotFoundException> { service.create(request()) }
    }

    @Test
    fun `create fails when user is not a department manager`() {
        `when`(departments.findById(1)).thenReturn(Optional.of(dept))
        `when`(users.findById(7)).thenReturn(Optional.of(user(role = Role.TEAM_LEAD)))
        assertThrows<InvalidTeamRequestException> { service.create(request()) }
    }

    @Test
    fun `create fails when manager is inactive`() {
        `when`(departments.findById(1)).thenReturn(Optional.of(dept))
        `when`(users.findById(7)).thenReturn(Optional.of(user(active = false)))
        assertThrows<InvalidTeamRequestException> { service.create(request()) }
    }

    @Test
    fun `create succeeds with valid data`() {
        `when`(departments.findById(1)).thenReturn(Optional.of(dept))
        `when`(users.findById(7)).thenReturn(Optional.of(user()))
        `when`(teams.save(any(Team::class.java))).thenAnswer { it.getArgument<Team>(0) }

        val response = service.create(request())

        assertEquals("Alpha", response.name)
        assertEquals(7, response.manager?.id)
        assertEquals(true, response.isActive)
        val auditRecord = argumentCaptor<AuditRecord>()
        verify(audit).record(auditRecord.capture())
        assertEquals(AuditAction.TEAM_CREATED, auditRecord.firstValue.action)
        assertEquals("TEAM", auditRecord.firstValue.itemType)
    }

    @Test
    fun `deactivate marks the team inactive and keeps the record`() {
        val team = Team(id = 3, name = "Alpha", department = dept, manager = user())
        `when`(teams.findById(3)).thenReturn(Optional.of(team))

        service.deactivate(3)

        assertEquals(false, team.isActive)
    }

    @Test
    fun `deactivate fails when team does not exist`() {
        `when`(teams.findById(3)).thenReturn(Optional.empty())
        assertThrows<ResourceNotFoundException> { service.deactivate(3) }
    }

    @Test
    fun `list rejects more than three sort fields`() {
        assertThrows<InvalidTeamRequestException> {
            service.list(null, null, null, null, null, 0, 20,
                listOf("name", "isActive", "createdAt", "name"))
        }
    }

    @Test
    fun `list rejects an invalid sort field`() {
        assertThrows<InvalidTeamRequestException> {
            service.list(null, null, null, null, null, 0, 20, listOf("password"))
        }
    }

    @Test
    fun `add members rejects a duplicate membership`() {
        val team = Team(id = 2, name = "Ops", department = dept, manager = user())
        val member = user(role = Role.MEMBER)
        member.teams.add(team)
        `when`(teams.findById(2)).thenReturn(Optional.of(team))
        `when`(users.findByUsername("mgr")).thenReturn(member)
        asAdmin()

        assertThrows<BusinessRuleException> { service.addMembers(2, setOf("mgr")) }
    }

    @Test
    fun `transfer moves a user between teams and writes history`() {
        val source = Team(id = 2, name = "Ops", department = dept, manager = user())
        val target = Team(id = 3, name = "Support", department = dept, manager = user())
        val member = user(role = Role.MEMBER)
        member.teams.add(source)
        `when`(teams.findById(2)).thenReturn(Optional.of(source))
        `when`(teams.findById(3)).thenReturn(Optional.of(target))
        `when`(users.findByUsername("mgr")).thenReturn(member)
        `when`(memberHistory.save(any(TeamMemberHistory::class.java))).thenAnswer { it.getArgument(0) }
        asAdmin()

        service.transfer(2, "mgr", 3)

        assertTrue(member.teams.contains(target))
        assertTrue(!member.teams.contains(source))
    }

    @Test
    fun `transfer rejects when user is not in the source team`() {
        val source = Team(id = 2, name = "Ops", department = dept, manager = user())
        val target = Team(id = 3, name = "Support", department = dept, manager = user())
        val member = user(role = Role.MEMBER)
        `when`(teams.findById(2)).thenReturn(Optional.of(source))
        `when`(teams.findById(3)).thenReturn(Optional.of(target))
        `when`(users.findByUsername("mgr")).thenReturn(member)
        asAdmin()

        assertThrows<BusinessRuleException> { service.transfer(2, "mgr", 3) }
    }

    @Test
    fun `remove member rejects removing the last team from a member`() {
        val team = Team(id = 2, name = "Ops", department = dept, manager = user())
        val member = user(role = Role.MEMBER)
        member.teams.add(team)
        `when`(teams.findById(2)).thenReturn(Optional.of(team))
        `when`(users.findByUsername("mgr")).thenReturn(member)
        asAdmin()

        assertThrows<BusinessRuleException> { service.removeMember(2, "mgr") }
    }

    private fun asAdmin() {
        val principal = AuthenticatedUser(
            id = 1,
            username = "admin",
            role = Role.ADMIN,
            jti = "jwt-1",
            tokenExpiresAt = Instant.now().plusSeconds(3600)
        )
        `when`(users.findById(1)).thenReturn(Optional.of(
            User(
                id = 1,
                username = "admin",
                email = "admin@example.com",
                fullName = "System Admin",
                passwordHash = "hash",
                role = Role.ADMIN,
                department = dept,
                active = true,
            )
        ))
        val auth = UsernamePasswordAuthenticationToken(
            principal,
            null,
            listOf(SimpleGrantedAuthority("ROLE_ADMIN"))
        )
        SecurityContextHolder.getContext().authentication = auth
    }
}
