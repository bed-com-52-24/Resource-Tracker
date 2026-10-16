package com.resourcetracker.approval

import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException

data class LineAvailability(
    val lineId: Int,
    val description: String,
    val resourceId: Int?,
    val requested: Int,
    val total: Int?,
    val available: Int?,
    val reserved: Int?,
    val issued: Int?,
    /** FULL, PARTIAL, NONE or UNKNOWN (no inventory link). */
    val fulfilment: String,
    val expectedReturnDates: List<String>
)

@Service
class AvailabilityService(
    private val requestRepository: ApprovalRequestRepository,
    private val lineRepository: ApprovalLineItemRepository,
    private val inventory: InventoryPort
) {

    /** What the assigned manager sees before deciding: stock for each internal line. */
    @Transactional(readOnly = true)
    fun review(requestId: Int, actorId: Int): List<LineAvailability> {
        val request = requestRepository.findById(requestId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Request not found")
        }
        if (request.assignedManagerId != actorId) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Only the assigned manager can review this request")
        }

        return lineRepository.findByRequestId(requestId)
            .filter { it.source == "INTERNAL" }
            .map { line ->
                val stock = line.resourceId?.let { inventory.availability(it) }
                val fulfilment = when {
                    stock == null -> "UNKNOWN"
                    stock.available >= line.quantity -> "FULL"
                    stock.available > 0 -> "PARTIAL"
                    else -> "NONE"
                }
                LineAvailability(
                    lineId = line.id, description = line.description,
                    resourceId = line.resourceId, requested = line.quantity,
                    total = stock?.total, available = stock?.available,
                    reserved = stock?.reserved, issued = stock?.issued,
                    fulfilment = fulfilment,
                    expectedReturnDates = line.resourceId?.let { inventory.expectedReturnDates(it) } ?: emptyList()
                )
            }
    }
}