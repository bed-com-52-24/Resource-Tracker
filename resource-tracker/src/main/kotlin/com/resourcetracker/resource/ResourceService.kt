package com.resourcetracker.resource

import com.resourcetracker.common.BusinessRuleException
import com.resourcetracker.common.ResourceNotFoundException
import com.resourcetracker.department.DepartmentRepository
import com.resourcetracker.vendor.VendorRepository
import com.resourcetracker.common.ListQuery
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ResourceService(
    private val resourceRepository: ResourceRepository,
    private val resourceCodeGenerator: ResourceCodeGenerator,
    private val departmentRepository: DepartmentRepository,
    private val vendorRepository: VendorRepository,
) {
    @Transactional
    fun create(request: CreateResourceRequest): ResourceResponse {
        validateTypeRules(request.type, request.departmentId, request.preferredVendorId)
        val resource = Resource(
            code = resourceCodeGenerator.next(),
            name = request.name,
            type = request.type,
            category = request.category,
            unitOfMeasure = request.unitOfMeasure,
            description = request.description,
            department = request.departmentId?.let(::findDepartment),
            preferredVendor = request.preferredVendorId?.let(::findVendor),
        )
        return resourceRepository.save(resource).toResponse()
    }

    @Transactional
    fun update(id: Int, request: UpdateResourceRequest): ResourceResponse {
        validateTypeRules(request.type, request.departmentId, request.preferredVendorId)
        val resource = resourceRepository.findById(id)
            .orElseThrow { ResourceNotFoundException("Resource not found with id $id") }

        resource.name = request.name
        resource.type = request.type
        resource.category = request.category
        resource.unitOfMeasure = request.unitOfMeasure
        resource.description = request.description
        resource.department = request.departmentId?.let(::findDepartment)
        resource.preferredVendor = request.preferredVendorId?.let(::findVendor)
        request.active?.let { applyActive(resource, it) }
        return resourceRepository.save(resource).toResponse()
    }

    @Transactional(readOnly = true)
    fun list(
        active: Boolean?,
        type: ResourceType?,
        category: ResourceCategory?,
        keyword: String?,
        page: Int,
        size: Int,
        sort: List<String>,
    ): Page<ResourceResponse> {
        if (keyword != null && keyword.length > 200) {
            throw BusinessRuleException("Keyword must not exceed 200 characters")
        }
        if (page < 0 || size < 1 || size > 100) {
            throw BusinessRuleException("page must be >= 0 and size must be between 1 and 100")
        }

        val pageable = PageRequest.of(page, size, ListQuery.sort(sort, ALLOWED_SORT_FIELDS, "name"))
        return resourceRepository.findAll(
            ResourceSpecifications.filter(active, type, category, keyword),
            pageable,
        ).map { it.toResponse() }
    }

    private fun applyActive(resource: Resource, active: Boolean) {
        resource.active = active
    }

    private fun validateTypeRules(type: ResourceType, departmentId: Int?, vendorId: Int?) {
        when (type) {
            ResourceType.INTERNAL -> {
                if (departmentId == null) {
                    throw BusinessRuleException("departmentId is required for INTERNAL resources")
                }
                if (vendorId != null) {
                    throw BusinessRuleException("preferredVendorId is not allowed for INTERNAL resources")
                }
            }
            ResourceType.EXTERNAL -> {
                if (departmentId != null) {
                    throw BusinessRuleException("departmentId is not allowed for EXTERNAL resources")
                }
            }
        }
    }

    private fun findDepartment(id: Int) =
        departmentRepository.findById(id)
            .orElseThrow { ResourceNotFoundException("Department not found with id $id") }

    private fun findVendor(id: Int) =
        vendorRepository.findById(id)
            .orElseThrow { ResourceNotFoundException("Vendor not found with id $id") }

    private fun Resource.toResponse() = ResourceResponse(
        id = id,
        code = code,
        name = name,
        type = type,
        category = category,
        unitOfMeasure = unitOfMeasure,
        description = description,
        active = active,
        departmentId = department?.id,
        preferredVendorId = preferredVendor?.id,
    )

    companion object {
        private val ALLOWED_SORT_FIELDS = setOf("name", "code", "type", "category", "active", "createdAt")
    }
}
