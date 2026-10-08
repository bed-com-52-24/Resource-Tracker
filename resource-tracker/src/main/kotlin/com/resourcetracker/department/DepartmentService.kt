package com.resourcetracker.department

import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable

@Service
class DepartmentService(private val departmentRepository: DepartmentRepository) {

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
    fun search(status: String?, keyword: String?, pageable: Pageable): Page<DepartmentResponse> =
        departmentRepository
            .search(status?.trim()?.ifBlank { null }, cleanKeyword(keyword), pageable)
            .map { it.toResponse() }

    private fun cleanKeyword(keyword: String?): String? {
        val trimmed = keyword?.trim() ?: return null
        if (trimmed.length > 200) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Keyword must be at most 200 characters")
        }
        return if (trimmed.length < 2) null else trimmed
    }

    fun getById(id: Int): DepartmentResponse = findOrThrow(id).toResponse()

    @Transactional
    fun update(id: Int, request: DepartmentRequest): DepartmentResponse {
        val department = findOrThrow(id)
        val name = request.name.trim()
        if (!department.name.equals(name, ignoreCase = true) &&
            departmentRepository.existsByNameIgnoreCase(name)
        ) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "A department with this name already exists")
        }
        department.name = name
        department.description = request.description
        return departmentRepository.save(department).toResponse()
    }

    @Transactional
    fun deactivate(id: Int): DepartmentResponse {
        val department = findOrThrow(id)
        // TODO (SRS 2.4.2.3): refuse if active projects or requests use this department
        department.status = "INACTIVE"
        return departmentRepository.save(department).toResponse()
    }

    private fun findOrThrow(id: Int): Department =
        departmentRepository.findById(id).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Department not found")
        }

    private fun Department.toResponse() =
        DepartmentResponse(id, name, description, status)
}