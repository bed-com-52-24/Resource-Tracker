package com.resourcetracker.project

import com.resourcetracker.department.DepartmentRepository
import com.resourcetracker.common.ListQuery
import com.resourcetracker.resource.ResourceRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.LocalDate

@Service
class ProjectService(
    private val projects: ProjectRepository,
    private val departments: DepartmentRepository,
    private val resources: ResourceRepository
) {
    private val allowedSortFields = setOf("name", "code", "startDate", "endDate", "active", "createdAt")

    @Transactional
    fun create(req: CreateProjectRequest): ProjectResponse {
        val dept = departments.findByIdOrNull(req.departmentId)?.takeIf { it.status == "ACTIVE" }
            ?: throw NotFoundException("Department not found or inactive")
        validateDates(req.startDate, req.endDate)
        val name = req.name.trim()
        if (projects.existsByDepartmentIdAndNameIgnoreCase(dept.id, name))
            throw ConflictException("Project name already exists in this department")
        if (projects.existsByCodeIgnoreCase(req.code))
            throw ConflictException("Project code already in use")

        val saved = projects.save(
            Project(
                name = name, code = req.code, department = dept,
                startDate = req.startDate, endDate = req.endDate
            )
        )
        return saved.toResponse()
    }

    @Transactional
    fun update(id: Int, req: UpdateProjectRequest): ProjectResponse {
        val project = projects.findByIdOrNull(id) ?: throw NotFoundException("Project not found")
        val dept = req.departmentId?.let {
            departments.findByIdOrNull(it)?.takeIf { department -> department.status == "ACTIVE" }
                ?: throw NotFoundException("Department not found or inactive")
        } ?: project.department
        val startDate = req.startDate ?: project.startDate
        val endDate = req.endDate ?: project.endDate
        validateDates(startDate, endDate)
        req.name?.let {
            val name = it.trim()
            if (projects.existsByDepartmentIdAndNameIgnoreCaseAndIdNot(dept.id, name, id))
                throw ConflictException("Project name already exists in this department")
            project.name = name
        }
        req.code?.let {
            if (projects.existsByCodeIgnoreCaseAndIdNot(it, id))
                throw ConflictException("Project code already in use")
            project.code = it
        }
        project.department = dept
        project.startDate = startDate
        req.endDate?.let { project.endDate = it }
        req.active?.let { applyActive(project, it) }
        project.updatedAt = Instant.now()
        return project.toResponse()
    }

    @Transactional
    fun deactivate(id: Int): ProjectResponse {
        val project = projects.findByIdOrNull(id) ?: throw NotFoundException("Project not found")
        applyActive(project, false)
        project.updatedAt = Instant.now()
        return project.toResponse()
    }

    @Transactional(readOnly = true)
    fun get(id: Int): ProjectResponse =
        (projects.findByIdOrNull(id) ?: throw NotFoundException("Project not found")).toResponse()

    @Transactional(readOnly = true)
    fun list(
        departmentId: Int?,
        active: Boolean?,
        startFrom: LocalDate?,
        startTo: LocalDate?,
        keyword: String?,
        page: Int,
        size: Int,
        sort: List<String>
    ): Page<ProjectResponse> {
        if (keyword != null && keyword.length > 200)
            throw BadRequestException("Keyword must not exceed 200 characters")
        if (page < 0 || size < 1 || size > 100)
            throw BadRequestException("page must be >= 0 and size must be between 1 and 100")

        val spec = ProjectSpecifications.filter(departmentId, active, startFrom, startTo, keyword)
        val pageable = PageRequest.of(page, size, try {
            ListQuery.sort(sort, allowedSortFields, "name")
        } catch (e: IllegalArgumentException) {
            throw BadRequestException(e.message ?: "Invalid sort")
        })
        return projects.findAll(spec, pageable).map { it.toResponse() }
    }

    private fun applyActive(project: Project, active: Boolean) {
        if (project.active == active) return

        if (!active && hasActiveRequests(project.id))
            throw ConflictException("Project has active resource requests and cannot be deactivated")

        project.active = active
    }

    private fun hasActiveRequests(projectId: Int): Boolean = false

    private fun validateDates(start: LocalDate, end: LocalDate?) {
        if (end != null && end.isBefore(start))
            throw BadRequestException("End date must not be before start date")
    }

}
