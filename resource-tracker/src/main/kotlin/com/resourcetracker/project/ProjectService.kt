package com.resourcetracker.project

import com.resourcetracker.department.DepartmentRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@Service
class ProjectService(
    private val projects: ProjectRepository,
    private val departments: DepartmentRepository
    // TODO(#16): inject ResourceRequestRepository once it exists, for the deactivation check
) {
    private val allowedSortFields = setOf("name", "code", "startDate", "endDate", "active", "createdAt")

    @Transactional
    fun create(req: CreateProjectRequest): ProjectResponse {
        val dept = departments.findByIdOrNull(req.departmentId)?.takeIf { it.active }
            ?: throw NotFoundException("Department not found or inactive")
        validateDates(req.startDate, req.endDate)
        val name = req.name.trim()
        if (projects.existsByDepartmentIdAndNameIgnoreCase(dept.id!!, name))
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
    fun update(id: UUID, req: UpdateProjectRequest): ProjectResponse {
        val project = projects.findByIdOrNull(id) ?: throw NotFoundException("Project not found")
        val dept = departments.findByIdOrNull(req.departmentId)?.takeIf { it.active }
            ?: throw NotFoundException("Department not found or inactive")
        validateDates(req.startDate, req.endDate)
        val name = req.name.trim()
        if (projects.existsByDepartmentIdAndNameIgnoreCaseAndIdNot(dept.id!!, name, id))
            throw ConflictException("Project name already exists in this department")
        if (projects.existsByCodeIgnoreCaseAndIdNot(req.code, id))
            throw ConflictException("Project code already in use")

        project.name = name
        project.code = req.code
        project.department = dept
        project.startDate = req.startDate
        project.endDate = req.endDate
        project.updatedAt = Instant.now()
        return project.toResponse()
    }

    @Transactional
    fun deactivate(id: UUID): ProjectResponse {
        val project = projects.findByIdOrNull(id) ?: throw NotFoundException("Project not found")
        if (hasActiveRequests(id))
            throw ConflictException("Project has active resource requests and cannot be deactivated")
        project.active = false
        project.updatedAt = Instant.now()
        return project.toResponse()
    }

    @Transactional(readOnly = true)
    fun get(id: UUID): ProjectResponse =
        (projects.findByIdOrNull(id) ?: throw NotFoundException("Project not found")).toResponse()

    @Transactional(readOnly = true)
    fun list(
        departmentId: UUID?,
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
        val pageable = PageRequest.of(page, size, buildSort(sort))
        return projects.findAll(spec, pageable).map { it.toResponse() }
    }

    // TODO(#16): replace with a real check, e.g.
    // requests.existsByProjectIdAndStatusIn(id, listOf(PENDING, QUERIED))
    private fun hasActiveRequests(projectId: UUID): Boolean = false

    private fun validateDates(start: LocalDate, end: LocalDate?) {
        if (end != null && end.isBefore(start))
            throw BadRequestException("End date must not be before start date")
    }

    private fun buildSort(sort: List<String>): Sort {
        if (sort.size > 3) throw BadRequestException("At most 3 sort fields are allowed")
        val orders = sort.map { s ->
            val parts = s.split(":")
            val field = parts[0].trim()
            if (field !in allowedSortFields)
                throw BadRequestException("Invalid sort field '" + field + "'. Allowed: " + allowedSortFields.joinToString())
            val dir = parts.getOrNull(1)?.trim()?.lowercase() ?: "asc"
            if (dir != "asc" && dir != "desc")
                throw BadRequestException("Invalid sort direction '" + dir + "'. Use asc or desc")
            if (dir == "desc") Sort.Order.desc(field) else Sort.Order.asc(field)
        }
        return Sort.by(orders)
    }
}
