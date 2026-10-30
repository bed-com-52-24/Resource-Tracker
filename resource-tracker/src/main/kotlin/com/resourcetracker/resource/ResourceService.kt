package com.resourcetracker.resource

import com.resourcetracker.audit.AuditAction
import com.resourcetracker.audit.AuditRecord
import com.resourcetracker.audit.AuditService
import com.resourcetracker.common.BadRequestException
import com.resourcetracker.common.BusinessRuleException
import com.resourcetracker.common.ResourceNotFoundException
import com.resourcetracker.department.DepartmentReference
import com.resourcetracker.department.DepartmentRepository
import com.resourcetracker.vendor.VendorReference
import com.resourcetracker.vendor.VendorRepository
import com.resourcetracker.request.service.StockAvailabilityService
import com.resourcetracker.request.entity.MovementType
import com.resourcetracker.request.entity.StockMovement
import com.resourcetracker.request.repository.StockMovementRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ResourceService(
    private val resourceRepository: ResourceRepository,
    private val resourceCodeGenerator: ResourceCodeGenerator,
    private val departmentRepository: DepartmentRepository,
    private val vendorRepository: VendorRepository,
    private val audit: AuditService,
    private val availability: StockAvailabilityService,
    private val movements: StockMovementRepository,
) {
    @Transactional
    fun create(request: CreateResourceRequest, actorId: Int, scopeDepartmentId: Int? = null): ResourceResponse {
        validateTypeRules(request.type, request.departmentId, request.preferredVendorId, request.quantity)
        if (scopeDepartmentId != null && request.type == ResourceType.INTERNAL && request.departmentId != scopeDepartmentId) {
            throw ResourceNotFoundException("Resource not found")
        }
        val unitOfMeasure = ResourceUnits.normalize(request.category, request.unitOfMeasure)
        val resource = Resource(
            code = resourceCodeGenerator.next(),
            name = request.name,
            type = request.type,
            category = request.category,
            unitOfMeasure = unitOfMeasure,
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
        if (saved.type == ResourceType.INTERNAL && (saved.quantity ?: 0) > 0) {
            movements.save(StockMovement(resourceId = saved.id, type = MovementType.OPENING, quantity = saved.quantity!!, actorId = actorId, reason = "Initial stock on resource creation"))
        }
        return saved.toResponse()
    }

    @Transactional
    fun update(id: Int, request: UpdateResourceRequest, scopeDepartmentId: Int? = null): ResourceResponse {
        validateTypeRules(request.type, request.departmentId, request.preferredVendorId, request.quantity)
        val unitOfMeasure = ResourceUnits.normalize(request.category, request.unitOfMeasure)
        val resource = resourceRepository.findById(id)
            .orElseThrow { ResourceNotFoundException("Resource not found with id $id") }
        requireInScope(resource, scopeDepartmentId)
        if (scopeDepartmentId != null && request.type == ResourceType.INTERNAL && request.departmentId != scopeDepartmentId) {
            throw ResourceNotFoundException("Resource not found")
        }
        val before = snapshot(resource)
        if (request.type != resource.type) {
            throw BusinessRuleException("Resource type cannot be changed; create a new resource")
        }

        resource.name = request.name
        resource.category = request.category
        resource.unitOfMeasure = unitOfMeasure
        resource.description = request.description
        resource.department = request.departmentId?.let(::findDepartment)
        resource.preferredVendor = request.preferredVendorId?.let(::findVendor)
        if (request.quantity != null && request.quantity != resource.quantity) {
            throw BusinessRuleException("Physical quantity can only change through stock operations")
        }
        resource.update(request.type, resource.quantity, request.location, request.reorderLevel)
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
        availableOnly: Boolean,
        keyword: String?,
        pageable: Pageable,
        scopeDepartmentId: Int? = null,
    ): Page<ResourceResponse> {
        if (keyword != null && keyword.length > 200) {
            throw BadRequestException("Keyword must not exceed 200 characters")
        }

        if (scopeDepartmentId != null && departmentId != null && departmentId != scopeDepartmentId) {
            return Page.empty(pageable)
        }
        val specification = ResourceSpecifications.filter(active, type, category, status, departmentId, keyword, scopeDepartmentId)
        if (availableOnly) return listAvailable(specification, pageable)

        val page = resourceRepository.findAll(specification, pageable)
        val reservedById = availability.reservedQuantities(page.content.filter { it.type == ResourceType.INTERNAL }.map { it.id })
        return PageImpl(
            page.content.map { it.toResponse(reservedById[it.id] ?: 0) },
            pageable,
            page.totalElements,
        )
    }

    @Transactional(readOnly = true)
    fun get(id: Int, scopeDepartmentId: Int? = null): ResourceResponse {
        val resource = resourceRepository.findWithRelationsById(id)
            ?: throw ResourceNotFoundException("Resource not found with id $id")
        requireInScope(resource, scopeDepartmentId)
        val reserved = if (resource.type == ResourceType.INTERNAL) availability.reservedQuantity(resource.id) else 0
        return resource.toResponse(reserved)
    }

    private fun listAvailable(specification: org.springframework.data.jpa.domain.Specification<Resource>, pageable: Pageable): Page<ResourceResponse> {
        val selected = mutableListOf<Pair<Resource, Int>>()
        val windowStart = pageable.offset
        val windowEnd = windowStart + pageable.pageSize
        var availableCount = 0L
        var candidatePageNumber = 0
        val batchSize = 200

        while (true) {
            val candidates = resourceRepository.findAll(
                specification,
                PageRequest.of(candidatePageNumber, batchSize, pageable.sort),
            )
            val internalResources = candidates.content.filter { it.type == ResourceType.INTERNAL }
            val reservedById = availability.reservedQuantities(internalResources.map { it.id })
            candidates.content.forEach { resource ->
                val reserved = reservedById[resource.id] ?: 0
                if (resource.type == ResourceType.INTERNAL && availability.availableQuantity(resource, knownReservedQuantity = reserved) > 0) {
                    if (availableCount >= windowStart && availableCount < windowEnd) selected += resource to reserved
                    availableCount++
                }
            }
            if (!candidates.hasNext()) break
            candidatePageNumber++
        }

        return PageImpl(selected.map { (resource, reserved) -> resource.toResponse(reserved) }, pageable, availableCount)
    }

    private fun requireInScope(resource: Resource, scopeDepartmentId: Int?) {
        if (scopeDepartmentId != null && resource.type == ResourceType.INTERNAL && resource.department?.id != scopeDepartmentId) {
            throw ResourceNotFoundException("Resource not found")
        }
    }

    private fun applyActive(resource: Resource, active: Boolean) {
        resource.active = active
    }

    private fun validateTypeRules(type: ResourceType, departmentId: Int?, vendorId: Int?, quantity: Int?) {
        when (type) {
            ResourceType.INTERNAL -> {
                if (departmentId == null) {
                    throw BadRequestException("departmentId is required for INTERNAL resources")
                }
                if (vendorId != null) {
                    throw BadRequestException("preferredVendorId is not allowed for INTERNAL resources")
                }
                if (quantity == null) {
                    throw BadRequestException("quantity is required for INTERNAL resources")
                }
            }
            ResourceType.EXTERNAL -> {
                if (departmentId != null) {
                    throw BadRequestException("departmentId is not allowed for EXTERNAL resources")
                }
                if (quantity != null) {
                    throw BadRequestException("quantity is not allowed for EXTERNAL resources")
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

    private fun Resource.toResponse(reservedQuantity: Int? = null): ResourceResponse {
        val reserved = if (type == ResourceType.INTERNAL) reservedQuantity ?: availability.reservedQuantity(id) else 0
        return ResourceResponse(
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
            department = department?.let { DepartmentReference(it.id, it.name) },
            preferredVendor = preferredVendor?.let { VendorReference(it.id, it.name) },
            reservedQuantity = reserved,
            unavailableQuantity = if (type == ResourceType.INTERNAL) unavailableQuantity else 0,
            availableQuantity = if (type == ResourceType.INTERNAL) availability.availableQuantity(this, knownReservedQuantity = reserved) else null,
        )
    }

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
