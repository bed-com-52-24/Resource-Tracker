package com.resourcetracker.project

import com.resourcetracker.department.Department
import com.resourcetracker.department.DepartmentRepository
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.time.LocalDate
import java.util.Optional
import java.util.UUID

class ProjectServiceTest {

    private val projects = mock(ProjectRepository::class.java)
    private val departments = mock(DepartmentRepository::class.java)
    private val service = ProjectService(projects, departments)

    private val deptId = 1
    private val dept = Department(id = deptId, name = "Engineering")

    private fun request(
        name: String = "Data Pipeline",
        code: String = "DP1",
        end: LocalDate? = null
    ) = CreateProjectRequest(
        name = name, code = code, departmentId = deptId,
        startDate = LocalDate.of(2026, 1, 1), endDate = end
    )

    @Test
    fun `create fails when department does not exist`() {
        `when`(departments.findById(deptId)).thenReturn(Optional.empty())
        assertThrows<NotFoundException> { service.create(request()) }
    }

    @Test
    fun `create fails when department is inactive`() {
        dept.status = "INACTIVE"
        `when`(departments.findById(deptId)).thenReturn(Optional.of(dept))
        assertThrows<NotFoundException> { service.create(request()) }
    }

    @Test
    fun `create fails when end date is before start date`() {
        `when`(departments.findById(deptId)).thenReturn(Optional.of(dept))
        assertThrows<BadRequestException> { service.create(request(end = LocalDate.of(2025, 12, 31))) }
    }

    @Test
    fun `create fails on duplicate name in same department`() {
        `when`(departments.findById(deptId)).thenReturn(Optional.of(dept))
        `when`(projects.existsByDepartmentIdAndNameIgnoreCase(deptId, "Data Pipeline")).thenReturn(true)
        assertThrows<ConflictException> { service.create(request()) }
    }

    @Test
    fun `create fails on duplicate code`() {
        `when`(departments.findById(deptId)).thenReturn(Optional.of(dept))
        `when`(projects.existsByCodeIgnoreCase("DP1")).thenReturn(true)
        assertThrows<ConflictException> { service.create(request()) }
    }

    @Test
    fun `deactivate sets active to false and keeps the record`() {
        val id = UUID.randomUUID()
        val project = Project(id = id, name = "Data Pipeline", code = "DP1", department = dept,
            startDate = LocalDate.of(2026, 1, 1))
        `when`(projects.findById(id)).thenReturn(Optional.of(project))

        assertTrue(project.active)
        service.deactivate(id)
        assertFalse(project.active)
    }

    @Test
    fun `deactivate fails when project does not exist`() {
        val id = UUID.randomUUID()
        `when`(projects.findById(id)).thenReturn(Optional.empty())
        assertThrows<NotFoundException> { service.deactivate(id) }
    }

    @Test
    fun `list rejects more than three sort fields`() {
        assertThrows<BadRequestException> {
            service.list(null, null, null, null, null, 0, 20,
                listOf("name", "code", "startDate", "active"))
        }
    }

    @Test
    fun `list rejects an invalid sort field`() {
        assertThrows<BadRequestException> {
            service.list(null, null, null, null, null, 0, 20, listOf("password"))
        }
    }
}
