package com.resourcetracker.resource

import com.resourcetracker.audit.AuditAction
import com.resourcetracker.audit.AuditRecord
import com.resourcetracker.audit.AuditService
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
    private val audit: AuditService,
) {
    @Transactional
    fun create(request: CreateResourceRequest): ResourceResponse {
        validateTypeRules(request.type, request.departmentId, request.preferredVendorId, request.quantity)
        val resource = Resource(
            code = resourceCodeGenerator.next(),
            name = request.name,
            type = request.type,
            category = request.category,
            unitOfMeasure = request.unitOfMeasure,
            description = request.description,
            department = request.departmentId?.let(::findDepartment),
            preferredVendor = request.preferredVendorId?.let(::findVendor),
            quantity = request.quantity,
            location = request.location,
            reorderLevel = request.reorderLevel,
        )
        resource.create()
        val saved = resourceRepository.save(resource)
        audit.record(
            AuditRecord.currentActor(
                if (saved.type == ResourceType.INTERNAL) AuditAction.INVENTORY_CREATED else AuditAction.RESOURCE_CREATED,
                if (saved.type == ResourceType.INTERNAL) "INVENTORY_ITEM" else "RESOURCE",
                saved.id.toString(),
                newState = snapshot(saved),
                changedFields = listOf("code", "name", "type", "category", "departmentId", "unitOfMeasure", "quantity", "location", "reorderLevel"),
            ),
        )
        return saved.toResponse()
    }

    @Transactional
    fun update(id: Int, request: UpdateResourceRequest): ResourceResponse {
        validateTypeRules(request.type, request.departmentId, request.preferredVendorId, request.quantity)
        val resource = resourceRepository.findById(id)
            .orElseThrow { ResourceNotFoundException("Resource not found with id $id") }
        val before = snapshot(resource)

        resource.name = request.name
        resource.category = request.category
        resource.unitOfMeasure = request.unitOfMeasure
        resource.description = request.description
        resource.department = request.departmentId?.let(::findDepartment)
        resource.preferredVendor = request.preferredVendorId?.let(::findVendor)
        resource.update(request.type, request.quantity, request.location, request.reorderLevel)
        request.active?.let { applyActive(resource, it) }
        val saved = resourceRepository.save(resource)
        val after = snapshot(saved)
        val changed = before.keys.filter { before[it] != after[it] }
        if (changed.isNotEmpty()) {
            val action = when {
                "quantity" in changed -> AuditAction.INVENTORY_ADJUSTED
                "active" in changed && saved.active -> AuditAction.RESOURCE_RESTORED
                "active" in changed -> AuditAction.RESOURCE_ARCHIVED
                saved.type == ResourceType.INTERNAL -> AuditAction.INVENTORY_UPDATED
                else -> AuditAction.RESOURCE_UPDATED
            }
            audit.record(
                AuditRecord.currentActor(
                    action,
                    if (saved.type == ResourceType.INTERNAL) "INVENTORY_ITEM" else "RESOURCE",
                    saved.id.toString(),
                    previousState = before,
                    newState = after,
                    changedFields = changed,
                ),
            )
        }
        return saved.toResponse()
    }

    @Transactional(readOnly = true)
    fun list(
        active: Boolean?,
        type: ResourceType?,
        category: ResourceCategory?,
        status: ResourceStatus?,
        departmentId: Int?,
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
            ResourceSpecifications.filter(active, type, category, status, departmentId, keyword),
            pageable,
        ).map { it.toResponse() }
    }

    @Transactional(readOnly = true)
    fun get(id: Int): ResourceResponse =
        resourceRepository.findWithRelationsById(id)
            ?.toResponse()
            ?: throw ResourceNotFoundException("Resource not found with id $id")

    private fun applyActive(resource: Resource, active: Boolean) {
        resource.active = active
    }

    private fun validateTypeRules(type: ResourceType, departmentId: Int?, vendorId: Int?, quantity: Int?) {
        when (type) {
            ResourceType.INTERNAL -> {
                if (departmentId == null) {
                    throw BusinessRuleException("departmentId is required for INTERNAL resources")
                }
                if (vendorId != null) {
                    throw BusinessRuleException("preferredVendorId is not allowed for INTERNAL resources")
                }
                if (quantity == null) {
                    throw BusinessRuleException("quantity is required for INTERNAL resources")
                }
            }
            ResourceType.EXTERNAL -> {
                if (departmentId != null) {
                    throw BusinessRuleException("departmentId is not allowed for EXTERNAL resources")
                }
                if (quantity != null) {
                    throw BusinessRuleException("quantity is not allowed for EXTERNAL resources")
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
        quantity = quantity,
        location = location,
        reorderLevel = reorderLevel,
        status = status,
        departmentId = department?.id,
        preferredVendorId = preferredVendor?.id,
    )

    private fun snapshot(resource: Resource) = mapOf(
        "code" to resource.code,
        "name" to resource.name,
        "type" to resource.type.name,
        "category" to resource.category.name,
        "description" to resource.description,
        "departmentId" to resource.department?.id,
        "preferredVendorId" to resource.preferredVendor?.id,
        "unitOfMeasure" to resource.unitOfMeasure,
        "quantity" to resource.quantity,
        "location" to resource.location,
        "reorderLevel" to resource.reorderLevel,
        "status" to resource.status?.name,
        "active" to resource.active,
    )

    companion object {
        private val ALLOWED_SORT_FIELDS = setOf("name", "code", "type", "category", "active", "createdAt", "quantity", "status")
    }
}
