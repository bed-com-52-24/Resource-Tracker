package com.resourcetracker.inventory

import com.resourcetracker.common.BusinessRuleException
import com.resourcetracker.common.ResourceNotFoundException
import com.resourcetracker.resource.Resource
import com.resourcetracker.resource.ResourceRepository
import com.resourcetracker.resource.ResourceType
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

    @Transactional
    fun adjust(
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
        val inventory =
                mutate(resourceId, request.quantity, action, reason, request.location, caller) { inv
                    ->
                    when (request.type) {
                        InventoryTransactionType.RESERVE -> {
                            if (inv.availableQuantity < request.quantity) {
                                throw BusinessRuleException("Insufficient available inventory")
                            }
                            inv.reservedQuantity += request.quantity
                        }
                        InventoryTransactionType.RELEASE -> {
                            if (inv.reservedQuantity < request.quantity) {
                                throw BusinessRuleException(
                                        "Cannot release more than the reserved quantity"
                                )
                            }
                            inv.reservedQuantity -= request.quantity
                        }
                        InventoryTransactionType.ISSUE -> {
                            if (inv.reservedQuantity < request.quantity) {
                                throw BusinessRuleException(
                                        "Cannot issue more than the reserved quantity"
                                )
                            }
                            inv.reservedQuantity -= request.quantity
                            inv.issuedQuantity += request.quantity
                        }
                        InventoryTransactionType.RETURN -> {
                            if (inv.issuedQuantity < request.quantity) {
                                throw BusinessRuleException(
                                        "Cannot return more than the issued quantity"
                                )
                            }
                            inv.issuedQuantity -= request.quantity
                        }
                    }
                }
        return InventoryTransactionResponse(
                resourceId = resourceId,
                type = request.type,
                quantity = request.quantity,
                inventory = inventory.toResponse(),
        )
    }

    @Transactional(readOnly = true)
    fun available(resourceId: UUID): Int =
            inventories.findAllByResourceIdOrderByLocation(resourceId).sumOf {
                it.availableQuantity
            }

    @Transactional(readOnly = true)
    fun list(resourceId: UUID): List<InventoryResponse> =
            inventories.findAllByResourceIdOrderByLocation(resourceId).map { it.toResponse() }

    private fun defaultReasonFor(type: InventoryTransactionType): String =
            when (type) {
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
        if (caller.role.name != "DEPARTMENT_MANAGER" || user.department != resource.department?.name
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
