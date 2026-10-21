package com.resourcetracker.inventory

import com.resourcetracker.common.BusinessRuleException
import com.resourcetracker.common.ResourceNotFoundException
import com.resourcetracker.resource.Resource
import com.resourcetracker.resource.ResourceRepository
import com.resourcetracker.resource.ResourceType
import com.resourcetracker.resource.ResourceCategory
import com.resourcetracker.security.AuthenticatedUser
import com.resourcetracker.user.User
import com.resourcetracker.user.UserRepository
import java.util.UUID
import org.springframework.context.ApplicationEventPublisher
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class InventoryService(
        private val inventories: InventoryRepository,
        private val audits: InventoryAuditLogRepository,
        private val resources: ResourceRepository,
        private val users: UserRepository,
        private val events: ApplicationEventPublisher,
) {
    @Transactional(readOnly = true)
    fun availability(resourceId: UUID): ResourceAvailabilityResponse {
        val resource = internalResource(resourceId)
        val totals = inventories.totalsForResource(resourceId)
        return availabilityResponse(resource, totals?.getAvailable() ?: 0L,
            totals?.getReserved() ?: 0L, totals?.getIssued() ?: 0L)
    }

    @Transactional(readOnly = true)
    fun availabilityList(departmentId: Int?, category: ResourceCategory?, status: AvailabilityStatus?): List<ResourceAvailabilityResponse> {
        val resources = resources.findActiveForAvailability(ResourceType.INTERNAL, departmentId, category)
        if (resources.isEmpty()) return emptyList()
        val totals = inventories.totalsForResources(resources.map { it.id }).associateBy { it.getResourceId() }
        return resources.map { resource ->
            val row = totals[resource.id]
            availabilityResponse(resource, row?.getAvailable() ?: 0L, row?.getReserved() ?: 0L, row?.getIssued() ?: 0L)
        }.filter { status == null || it.status == status }
    }

    private fun availabilityResponse(resource: Resource, available: Long, reserved: Long, issued: Long) =
        ResourceAvailabilityResponse(resource.id, resource.name, resource.category, resource.department?.name,
            available.toInt(), reserved.toInt(), issued.toInt(), classifyAvailability(available, reserved, issued), null)

    @Transactional
    fun create(
            resourceId: UUID,
            request: CreateInventoryRequest,
            caller: AuthenticatedUser
    ): InventoryResponse {
        val resource = internalResource(resourceId)
        requireManagerScope(resource, caller)
        if (inventories.findAllByResourceIdOrderByLocation(resourceId).any {
                    it.location == request.location
                }
        ) {
            throw BusinessRuleException("Inventory already exists for this resource and location")
        }
        val inventory =
                inventories.save(
                        Inventory(
                                resource = resource,
                                location = request.location.trim(),
                                totalQuantity = request.totalQuantity,
                                reorderLevel = request.reorderLevel,
                        ),
                )
        audit(inventory, "CREATE", request.reason, Snapshot(0, 0, 0), currentUser(caller))
        publishLowStock(inventory)
        return inventory.toResponse()
    }

    private fun adjust(
            resourceId: UUID,
            request: AdjustInventoryRequest,
            caller: AuthenticatedUser
    ): InventoryResponse {
        val inventory = locked(resourceId, request.location)
        internalResource(resourceId)
        requireManagerScope(inventory.resource, caller)
        if (request.totalQuantity < inventory.reservedQuantity + inventory.issuedQuantity) {
            throw BusinessRuleException(
                    "Total quanity cannot be below reserved plus issued quantity"
            )
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
    fun transact(
            resourceId: UUID,
            request: InventoryTransactionRequest,
            caller: AuthenticatedUser,
    ): InventoryTransactionResponse {
        val action = request.type.name
        val reason = request.reason?.takeIf { it.isNotBlank() } ?: defaultReasonFor(request.type)
        if (request.type == InventoryTransactionType.ADJUST) {
            if (request.quantity != null) throw BusinessRuleException("quantity must be omitted for ADJUST")
            val totalQuantity = request.totalQuantity
                ?: throw BusinessRuleException("totalQuantity is required for ADJUST")
            val reorderLevel = request.reorderLevel
                ?: throw BusinessRuleException("reorderLevel is required for ADJUST")
            val location = request.location?.takeIf { it.isNotBlank() }
                ?: throw BusinessRuleException("location is required for ADJUST")
            val updated = adjust(
                resourceId,
                AdjustInventoryRequest(location, totalQuantity, reorderLevel, reason),
                caller,
            )
            return InventoryTransactionResponse(resourceId, request.type, null, updated)
        }
        if (request.totalQuantity != null || request.reorderLevel != null) {
            throw BusinessRuleException("totalQuantity and reorderLevel are only valid for ADJUST")
        }
        val quantity = request.quantity
            ?: throw BusinessRuleException("quantity is required for ${request.type}")
        val inventory =
                mutate(resourceId, quantity, action, reason, request.location, caller) { inv
                    ->
                    when (request.type) {
                        InventoryTransactionType.ADJUST -> error("ADJUST is handled before quantity transactions")
                        InventoryTransactionType.RESERVE -> {
                            if (inv.availableQuantity < quantity) {
                                throw BusinessRuleException("Insufficient available inventory")
                            }
                            inv.reservedQuantity += quantity
                        }
                        InventoryTransactionType.RELEASE -> {
                            if (inv.reservedQuantity < quantity) {
                                throw BusinessRuleException(
                                        "Cannot release more than the reserved quantity"
                                )
                            }
                            inv.reservedQuantity -= quantity
                        }
                        InventoryTransactionType.ISSUE -> {
                            if (inv.reservedQuantity < quantity) {
                                throw BusinessRuleException(
                                        "Cannot issue more than the reserved quantity"
                                )
                            }
                            inv.reservedQuantity -= quantity
                            inv.issuedQuantity += quantity
                        }
                        InventoryTransactionType.RETURN -> {
                            if (inv.issuedQuantity < quantity) {
                                throw BusinessRuleException(
                                        "Cannot return more than the issued quantity"
                                )
                            }
                            inv.issuedQuantity -= quantity
                        }
                    }
                }
        return InventoryTransactionResponse(
                resourceId = resourceId,
                type = request.type,
                quantity = quantity,
                inventory = inventory.toResponse(),
        )
    }

    @Transactional(readOnly = true)
    fun list(resourceId: UUID): InventoryListResponse {
        val items = inventories.findAllByResourceIdOrderByLocation(resourceId).map { it.toResponse() }
        return InventoryListResponse(resourceId, items.sumOf { it.availableQuantity }, items)
    }

    private fun defaultReasonFor(type: InventoryTransactionType): String =
            when (type) {
                InventoryTransactionType.ADJUST -> "Inventory adjustment"
                InventoryTransactionType.RESERVE -> "Reservation"
                InventoryTransactionType.RELEASE -> "Reservation release"
                InventoryTransactionType.ISSUE -> "Issue"
                InventoryTransactionType.RETURN -> "Stock return"
            }

   private fun mutate(
        resourceId: UUID,
        quantity: Int,
        action: String,
        reason: String,
        location: String?,
        caller: AuthenticatedUser,
        change: (Inventory) -> Unit,
    ): Inventory {
        if (quantity <= 0) throw BusinessRuleException("Quantity must be positive")
        val target = location?.takeIf { it.isNotBlank() } ?: run {
            val matches = inventories.findAllByResourceIdOrderByLocation(resourceId)
            if (matches.size != 1) {
                throw BusinessRuleException("A location is required when a resource has multiple inventory locations")
            }
            matches.single().location
        }
        val inventory = locked(resourceId, target)
        val before = snapshot(inventory)
        change(inventory)
        inventories.save(inventory)
        audit(inventory, action, reason, before, currentUser(caller))
        publishLowStock(inventory)
        return inventory
    }

    private fun locked(resourceId: UUID, location: String): Inventory =
            inventories.findForUpdate(resourceId, location)
                    ?: throw ResourceNotFoundException(
                            "Inventory not found for resource $resourceId at $location"
                    )

    private fun internalResource(id: UUID): Resource {
        val resource =
                resources.findById(id).orElseThrow {
                    ResourceNotFoundException("Resource not found with id $id")
                }
        if (resource.type != ResourceType.INTERNAL)
                throw BusinessRuleException("Only INTERNAL resources can have inventory")
        return resource
    }

    private fun requireManagerScope(resource: Resource, caller: AuthenticatedUser) {
        if (caller.role.name == "ADMIN") return
        val user =
                users.findById(caller.id).orElseThrow {
                    ResourceNotFoundException("User not found")
                }
        if (caller.role.name != "DEPARTMENT_MANAGER" || user.department.id != resource.department?.id
        ) {
            throw BusinessRuleException(
                    "Department manager does not own this resource's department"
            )
        }
    }

    private fun currentUser(caller: AuthenticatedUser? = null): User? {
        val principal =
                caller
                        ?: SecurityContextHolder.getContext().authentication?.principal as?
                                AuthenticatedUser
                                ?: return null
        return users.findById(principal.id).orElse(null)
    }

    private data class Snapshot(val total: Int, val reserved: Int, val issued: Int)
    private fun snapshot(i: Inventory) =
            Snapshot(i.totalQuantity, i.reservedQuantity, i.issuedQuantity)

    private fun audit(
            i: Inventory,
            action: String,
            reason: String,
            before: Snapshot,
            actor: User?
    ) {
        audits.save(
                InventoryAuditLog(
                        inventory = i,
                        actor = actor,
                        action = action,
                        reason = reason,
                        previousTotalQuantity = before.total,
                        previousReservedQuantity = before.reserved,
                        previousIssuedQuantity = before.issued,
                        newTotalQuantity = i.totalQuantity,
                        newReservedQuantity = i.reservedQuantity,
                        newIssuedQuantity = i.issuedQuantity,
                )
        )
    }

    private fun publishLowStock(i: Inventory) {
        if (i.availableQuantity <= i.reorderLevel) {
            events.publishEvent(
                    InventoryLowStockEvent(
                            i.resource.id,
                            i.location,
                            i.availableQuantity,
                            i.reorderLevel
                    )
            )
        }
    }

    private fun Inventory.toResponse() =
            InventoryResponse(
                    id,
                    resource.id,
                    location,
                    totalQuantity,
                    reservedQuantity,
                    issuedQuantity,
                    availableQuantity,
                    reorderLevel,
                    lastUpdated
            )
}

/** Stock with free units is AVAILABLE when there are no holds or issues; otherwise PARTIALLY_AVAILABLE.
 * With no free units, a positive reservation yields FULLY_RESERVED; otherwise it is OUT_OF_STOCK.
 * Issued quantity is excluded from free stock and does not count as a reservation.
 */
internal fun classifyAvailability(available: Long, reserved: Long, issued: Long): AvailabilityStatus = when {
    available > 0 && reserved == 0L && issued == 0L -> AvailabilityStatus.AVAILABLE
    available > 0 -> AvailabilityStatus.PARTIALLY_AVAILABLE
    reserved > 0 -> AvailabilityStatus.FULLY_RESERVED
    else -> AvailabilityStatus.OUT_OF_STOCK
}
