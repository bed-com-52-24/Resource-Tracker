package com.resourcetracker.department

import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import com.resourcetracker.project.ProjectRepository

@Service
class DepartmentService(
    private val departmentRepository: DepartmentRepository,
    private val projectRepository: ProjectRepository, ) {

    fun create(request: DepartmentRequest): DepartmentResponse {
        val name = request.name.trim()
        if (departmentRepository.existsByNameIgnoreCase(name)) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "A department with this name already exists")
        }
        val saved = departmentRepository.save(
            Department(name = name, description = request.description)
        )
        return saved.toResponse()
    }

    fun getAll(): () -> Unit = {}
    fun search(active: Boolean?, keyword: String?, pageable: Pageable): Page<DepartmentResponse> =
        departmentRepository
            .search(active, cleanKeyword(keyword), validateSort(pageable))
            .map { it.toResponse() }

    private fun validateSort(pageable: Pageable): Pageable {
        val allowedFields = setOf("id", "name", "status")
        pageable.sort.forEach { order ->
            if (order.property !in allowedFields) {
                throw ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid sort field '${order.property}'. Allowed: ${allowedFields.joinToString()}"
                )
            }
        }
        return pageable
    }

    private fun cleanKeyword(keyword: String?): String? {
        val trimmed = keyword?.trim() ?: return null
        if (trimmed.length > 200) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Keyword must be at most 200 characters")
        }
        return if (trimmed.length < 2) null else trimmed
    }

    fun getById(id: Int): DepartmentResponse = findOrThrow(id).toResponse()


    @Transactional
    fun update(id: Int, request: UpdateDepartmentRequest): DepartmentResponse {
        val department = findOrThrow(id)
        request.name?.let {
            val name = it.trim()
            if (!department.name.equals(name, ignoreCase = true) &&
                departmentRepository.existsByNameIgnoreCase(name)
            ) {
                throw ResponseStatusException(HttpStatus.CONFLICT, "A department with this name already exists")
            }
            department.name = name
        }
        request.active?.let { applyActive(department, it) }
        request.description?.let { department.description = it }
        return departmentRepository.save(department).toResponse()
    }

    private fun applyActive(department: Department, active: Boolean) {
        val target = if (active) "ACTIVE" else "INACTIVE"
        if (department.status == target) return

        if (!active) {
            ensureNoActiveProjects(department)
        }
        department.status = target
    }

    private fun ensureNoActiveProjects(department: Department) {
        if (projectRepository.existsByDepartmentIdAndActiveTrue(department.id!!)) {
            throw ResponseStatusException(
                HttpStatus.CONFLICT,
                "Cannot deactivate '${department.name}': it is still used by active projects"
            )
        }
    }

    private fun findOrThrow(id: Int): Department =
        departmentRepository.findById(id).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Department not found")
        }

    private fun Department.toResponse() =
        DepartmentResponse(id, name, description, status)

    private companion object {
        val BLOCKING_PROJECT_STATUSES = listOf("ACTIVE")
    }
}
