package com.resourcetracker.approval

import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.server.ResponseStatusException

/**
 * Runs for every approved INTERNAL line, inside the approval transaction.
 * If it throws, the status change and everything else rolls back with it.
 */
@Component
class InternalReservationHandler(
    private val inventory: InventoryPort,
    private val allocations: ResourceAllocationRepository,
    private val auditRepository: ApprovalAuditRepository
) : InternalApprovalHandler {

    override fun handle(decision: LineDecision) {
        if (!decision.approved) return          // a rejected line reserves nothing

        val line = decision.line
        val resourceId = line.resourceId ?: throw ResponseStatusException(
            HttpStatus.BAD_REQUEST, "Internal line ${line.id} is not linked to an inventory item"
        )
        val before = inventory.availability(resourceId) ?: throw ResponseStatusException(
            HttpStatus.NOT_FOUND, "Inventory item $resourceId not found"
        )

        when {
            inventory.tryReserve(resourceId, line.quantity) -> {
                allocations.save(ResourceAllocation(
                    requestId = decision.requestId, lineId = line.id,
                    resourceId = resourceId, quantity = line.quantity, status = "RESERVED"
                ))
                val after = inventory.availability(resourceId)
                audit(decision, "RESOURCE_RESERVED", "RESERVED",
                    "Line ${line.id}, item $resourceId: reserved ${before.reserved} -> ${after?.reserved}, " +
                            "available ${before.available} -> ${after?.available}")
            }
            decision.waitlist -> {
                // Waitlist hand-off to RT-19: record the allocation as WAITLISTED.
                allocations.save(ResourceAllocation(
                    requestId = decision.requestId, lineId = line.id,
                    resourceId = resourceId, quantity = line.quantity, status = "WAITLISTED"
                ))
                audit(decision, "RESOURCE_WAITLISTED", "WAITLISTED",
                    "Line ${line.id}, item $resourceId: wanted ${line.quantity}, available ${before.available}")
            }
            else -> throw ResponseStatusException(
                HttpStatus.CONFLICT,
                "Insufficient stock for line ${line.id}: requested ${line.quantity}, " +
                        "available ${before.available}, shortfall ${line.quantity - before.available}"
            )
        }
    }

    private fun audit(decision: LineDecision, action: String, newState: String, comment: String) {
        auditRepository.save(ApprovalAudit(
            requestId = decision.requestId, actorId = decision.actorId,
            previousState = null, newState = newState, action = action, comment = comment
        ))
    }
}