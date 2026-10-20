package com.resourcetracker.team

import com.resourcetracker.common.DuplicateResourceException
import com.resourcetracker.common.ResourceNotFoundException
import com.resourcetracker.department.Department
import com.resourcetracker.department.DepartmentRepository
import com.resourcetracker.project.ProjectRepository
import com.resourcetracker.user.Role
import com.resourcetracker.user.User
import com.resourcetracker.user.UserRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.util.Optional

class TeamServiceTest {

    private val teams = mock(TeamRepository::class.java)
    private val departments = mock(DepartmentRepository::class.java)
    private val users = mock(UserRepository::class.java)
    private val projects = mock(ProjectRepository::class.java)
    private val service = TeamService(teams, departments, users, projects)

    private val dept = Department(id = 1, name = "Engineering")

    private fun user(role: Role = Role.DEPARTMENT_MANAGER, active: Boolean = true) = User(
        id = 7, username = "mgr", email = "mgr@example.com", fullName = "Manager One",
        passwordHash = "hash", role = role, department = "Engineering", active = active
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
        assertEquals(7, response.managerId)
        assertEquals(true, response.isActive)
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
}
