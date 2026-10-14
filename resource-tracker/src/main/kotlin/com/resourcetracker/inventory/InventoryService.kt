package com.resourcetracker.inventory

import com.resourcetracker.common.BusinessRuleException
import com.resourcetracker.common.ResourceNotFoundException
import com.resourcetracker.resource.Resource
import com.resourcetracker.resource.ResourceRepository
import com.resourcetracker.resource.ResourceType
import com.resourcetracker.security.AuthenticatedUser
import com.resourcetracker.user.User
import com.resourcetracker.user.UserRepository
import org.springframework.context.ApplicationEventPublisher
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class InventoryService(
    private val inventories: InventoryRepository,
    private val audits: InventoryAuditLogRepository,
    private val resources: ResourceRepository,
    private val users: UserRepository,
    private val events: ApplicationEventPublisher,
) {
    @Transactional
    fun create(resourceId: UUID, request: CreateInventoryRequest, caller: AuthenticatedUser): InventoryResponse {
        val resource = internalResource(resourceId)
        requireManagerScope(resource, caller)
        if (inventories.findAllByResourceIdOrderByLocation(resourceId).any { it.location == request.location }) {
            throw BusinessRuleException("Inventory already exists for this resource and location")
        }
        val inventory = inventories.save(
            Inventory(resource = resource, location = request.location.trim(), totalQuantity = request.totalQuantity, reorderLevel = request.reorderLevel),
        )
        audit(inventory, "CREATE", request.reason, Snapshot(0, 0, 0), currentUser(caller))
        publishLowStock(inventory)
        return inventory.toResponse()
    }

    @Transactional
    fun adjust(resourceId: UUID, location: String, request: AdjustInventoryRequest, caller: AuthenticatedUser): InventoryResponse {
        val inventory = locked(resourceId, location)
        internalResource(resourceId)
        requireManagerScope(inventory.resource, caller)
        if (request.totalQuantity < inventory.reservedQuantity + inventory.issuedQuantity) {
            throw BusinessRuleException("Total quantity cannot be below reserved plus issued quantity")
        }
        val before = snapshot(inventory)
        inventory.totalQuantity = request.totalQuantity
        inventory.reorderLevel = request.reorderLevel
        inventories.save(inventory)
        audit(inventory, "ADJUST", request.reason, before, currentUser(caller))
        publishLowStock(inventory)
        return inventory.toResponse()
    }

    @Transactional
    fun reserve(resourceId: UUID, quantity: Int): InventoryResponse = mutate(resourceId, quantity, "RESERVE", "Reservation", null) {
        if (it.availableQuantity < quantity) throw BusinessRuleException("Insufficient available inventory")
        it.reservedQuantity += quantity
    }

    @Transactional
    fun releaseReservation(resourceId: UUID, quantity: Int): InventoryResponse = mutate(resourceId, quantity, "RELEASE_RESERVATION", "Reservation release", null) {
        if (it.reservedQuantity < quantity) throw BusinessRuleException("Cannot release more than the reserved quantity")
        it.reservedQuantity -= quantity
    }

    @Transactional
    fun issue(resourceId: UUID, quantity: Int): InventoryResponse = mutate(resourceId, quantity, "ISSUE", "Issue", null) {
        if (it.reservedQuantity < quantity) throw BusinessRuleException("Cannot issue more than the reserved quantity")
        it.reservedQuantity -= quantity
        it.issuedQuantity += quantity
    }

    @Transactional
    fun returnStock(resourceId: UUID, quantity: Int): InventoryResponse = mutate(resourceId, quantity, "RETURN", "Stock return", null) {
        if (it.issuedQuantity < quantity) throw BusinessRuleException("Cannot return more than the issued quantity")
        it.issuedQuantity -= quantity
    }

    @Transactional(readOnly = true)
    fun available(resourceId: UUID): Int =
        inventories.findAllByResourceIdOrderByLocation(resourceId).sumOf { it.availableQuantity }

    @Transactional(readOnly = true)
    fun list(resourceId: UUID): List<InventoryResponse> =
        inventories.findAllByResourceIdOrderByLocation(resourceId).map { it.toResponse() }

    private fun mutate(
        resourceId: UUID,
        quantity: Int,
        action: String,
        defaultReason: String,
        location: String?,
        change: (Inventory) -> Unit,
    ): InventoryResponse {
        if (quantity <= 0) throw BusinessRuleException("Quantity must be positive")
        val target = if (location == null) {
            val matches = inventories.findAllByResourceIdOrderByLocation(resourceId)
            if (matches.size != 1) throw BusinessRuleException("A location is required when a resource has multiple inventory locations")
            matches.single().location
        } else location
        val inventory = locked(resourceId, target)
        val before = snapshot(inventory)
        change(inventory)
        inventories.save(inventory)
        audit(inventory, action, defaultReason, before, currentUser())
        publishLowStock(inventory)
        return inventory.toResponse()
    }

    private fun locked(resourceId: UUID, location: String): Inventory =
        inventories.findForUpdate(resourceId, location)
            ?: throw ResourceNotFoundException("Inventory not found for resource $resourceId at $location")

    private fun internalResource(id: UUID): Resource {
        val resource = resources.findById(id).orElseThrow { ResourceNotFoundException("Resource not found with id $id") }
        if (resource.type != ResourceType.INTERNAL) throw BusinessRuleException("Only INTERNAL resources can have inventory")
        return resource
    }

    private fun requireManagerScope(resource: Resource, caller: AuthenticatedUser) {
        if (caller.role.name == "ADMIN") return
        val user = users.findById(caller.id).orElseThrow { ResourceNotFoundException("User not found") }
        if (caller.role.name != "DEPARTMENT_MANAGER" || user.department != resource.department?.name) {
            throw BusinessRuleException("Department manager does not own this resource's department")
        }
    }

    private fun currentUser(caller: AuthenticatedUser? = null): User? {
        val principal = caller ?: SecurityContextHolder.getContext().authentication?.principal as? AuthenticatedUser ?: return null
        return users.findById(principal.id).orElse(null)
    }

    private data class Snapshot(val total: Int, val reserved: Int, val issued: Int)
    private fun snapshot(i: Inventory) = Snapshot(i.totalQuantity, i.reservedQuantity, i.issuedQuantity)

    private fun audit(i: Inventory, action: String, reason: String, before: Snapshot, actor: User?) {
        audits.save(InventoryAuditLog(
            inventory = i, actor = actor,
            action = action, reason = reason,
            previousTotalQuantity = before.total, previousReservedQuantity = before.reserved, previousIssuedQuantity = before.issued,
            newTotalQuantity = i.totalQuantity, newReservedQuantity = i.reservedQuantity, newIssuedQuantity = i.issuedQuantity,
        ))
    }

    private fun publishLowStock(i: Inventory) {
        if (i.availableQuantity <= i.reorderLevel) {
            events.publishEvent(InventoryLowStockEvent(i.resource.id, i.location, i.availableQuantity, i.reorderLevel))
        }
    }

    private fun Inventory.toResponse() = InventoryResponse(id, resource.id, location, totalQuantity, reservedQuantity, issuedQuantity, availableQuantity, reorderLevel, lastUpdated)
}
