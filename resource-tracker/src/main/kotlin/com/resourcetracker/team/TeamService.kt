package com.resourcetracker.team

import com.resourcetracker.common.DuplicateResourceException
import com.resourcetracker.common.ResourceNotFoundException
import com.resourcetracker.department.Department
import com.resourcetracker.department.DepartmentRepository
import com.resourcetracker.project.Project
import com.resourcetracker.project.ProjectRepository
import com.resourcetracker.user.Role
import com.resourcetracker.user.User
import com.resourcetracker.user.UserRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
class TeamService(
    private val teams: TeamRepository,
    private val departments: DepartmentRepository,
    private val users: UserRepository,
    private val projects: ProjectRepository
) {
    private val allowedSortFields = setOf("name", "status", "createdAt")

    @Transactional
    fun create(req: TeamRequest): TeamResponse {
        val dept = activeDepartment(req.departmentId)
        val name = req.name.trim()
        if (teams.existsByDepartmentIdAndNameIgnoreCase(dept.id, name))
            throw DuplicateResourceException("Team name already exists in this department")
        val manager = validManager(req.managerId)
        val teamProjects = activeProjects(req.projectIds)

        val saved = teams.save(
            Team(name = name, department = dept, manager = manager, projects = teamProjects.toMutableSet())
        )
        return saved.toResponse()
    }

    @Transactional
    fun update(id: Int, req: TeamRequest): TeamResponse {
        val team = teams.findByIdOrNull(id) ?: throw ResourceNotFoundException("Team not found")
        val dept = activeDepartment(req.departmentId)
        val name = req.name.trim()
        if (teams.existsByDepartmentIdAndNameIgnoreCaseAndIdNot(dept.id, name, id))
            throw DuplicateResourceException("Team name already exists in this department")
        val manager = validManager(req.managerId)
        val teamProjects = activeProjects(req.projectIds)

        team.name = name
        team.department = dept
        team.manager = manager
        team.projects.clear()
        team.projects.addAll(teamProjects)
        team.updatedAt = Instant.now()
        return team.toResponse()
    }

    @Transactional
    fun deactivate(id: Int): TeamResponse {
        val team = teams.findByIdOrNull(id) ?: throw ResourceNotFoundException("Team not found")
        team.status = "INACTIVE"
        team.updatedAt = Instant.now()
        return team.toResponse()
    }

    @Transactional(readOnly = true)
    fun get(id: Int): TeamResponse =
        (teams.findByIdOrNull(id) ?: throw ResourceNotFoundException("Team not found")).toResponse()

    @Transactional(readOnly = true)
    fun list(
        departmentId: Int?,
        managerId: Int?,
        projectId: UUID?,
        status: String?,
        keyword: String?,
        page: Int,
        size: Int,
        sort: List<String>
    ): Page<TeamResponse> {
        if (keyword != null && keyword.length > 200)
            throw InvalidTeamRequestException("Keyword must not exceed 200 characters")
        if (page < 0 || size < 1 || size > 100)
            throw InvalidTeamRequestException("page must be >= 0 and size must be between 1 and 100")

        val spec = TeamSpecifications.filter(departmentId, managerId, projectId, status, keyword)
        val pageable = PageRequest.of(page, size, buildSort(sort))
        return teams.findAll(spec, pageable).map { it.toResponse() }
    }

    private fun activeDepartment(id: Int): Department =
        departments.findByIdOrNull(id)?.takeIf { it.status.equals("ACTIVE", ignoreCase = true) }
            ?: throw ResourceNotFoundException("Department not found or inactive")

    private fun validManager(id: Int): User {
        val manager = users.findByIdOrNull(id) ?: throw ResourceNotFoundException("Manager not found")
        if (!manager.active || manager.role != Role.DEPARTMENT_MANAGER)
            throw InvalidTeamRequestException("Manager must be an active user with the Department Manager role")
        return manager
    }

    private fun activeProjects(ids: Set<UUID>): List<Project> {
        if (ids.isEmpty()) return emptyList()
        val found = projects.findAllById(ids)
        if (found.size != ids.size) throw ResourceNotFoundException("One or more projects not found")
        if (found.any { !it.active }) throw InvalidTeamRequestException("Inactive projects cannot be assigned to a team")
        return found
    }

    private fun buildSort(sort: List<String>): Sort {
        if (sort.size > 3) throw InvalidTeamRequestException("At most 3 sort fields are allowed")
        val orders = sort.map { s ->
            val parts = s.split(":")
            val field = parts[0].trim()
            if (field !in allowedSortFields)
                throw InvalidTeamRequestException("Invalid sort field '" + field + "'. Allowed: " + allowedSortFields.joinToString())
            val dir = parts.getOrNull(1)?.trim()?.lowercase() ?: "asc"
            if (dir != "asc" && dir != "desc")
                throw InvalidTeamRequestException("Invalid sort direction '" + dir + "'. Use asc or desc")
            if (dir == "desc") Sort.Order.desc(field) else Sort.Order.asc(field)
        }
        return Sort.by(orders)
    }
}
