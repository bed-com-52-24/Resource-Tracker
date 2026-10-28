package com.resourcetracker.request.service

import com.resourcetracker.request.entity.FulfillmentPreference
import com.resourcetracker.request.repository.RequestLineItemRepository
import com.resourcetracker.request.repository.ResourceRequestRepository
import com.resourcetracker.request.repository.StockIssueRepository
import com.resourcetracker.resource.ResourceRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.time.Clock
import java.time.LocalDate

data class LineAvailability(
    val lineId: Int,
    val description: String,
    val resourceId: Int?,
    val requested: Int,
    val total: Int?,
    val available: Int?,
    val reserved: Int?,
    val issued: Int?,
    val fulfilment: String,
    val expectedReturnDates: List<String>,
)

@Service
class AvailabilityService(
    private val requestRepository: ResourceRequestRepository,
    private val lineRepository: RequestLineItemRepository,
    private val resources: ResourceRepository,
    private val stockAvailability: StockAvailabilityService,
    private val issues: StockIssueRepository,
    private val clock: Clock,
) {
    @Transactional(readOnly = true)
    fun review(requestId: Int, actorId: Int): List<LineAvailability> {
        val request = requestRepository.findById(requestId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Request not found")
        }
        if (request.assignedManagerId != actorId) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Only the assigned manager can review this request")
        }

        return lineRepository.findAllByRequestId(requestId)
            .filter { it.fulfillmentPreference == FulfillmentPreference.INTERNAL.name }
            .map { line ->
                val resource = line.resourceId?.let { resources.findById(it).orElse(null) }
                val reserved = resource?.let { stockAvailability.reservedQuantity(it.id) }
                val available = resource?.let { stockAvailability.availableQuantity(it) }
                val issued = line.lineItemId?.let { lineId ->
                    issues.findAllByRequestIdAndLineItemId(requestId, lineId).sumOf { it.quantity }
                }
                val fulfilment = when {
                    resource == null -> "UNKNOWN"
                    available!! >= line.quantity -> "FULL"
                    available > 0 -> "PARTIAL"
                    else -> "NONE"
                }
                val expectedReturns = resource?.let {
                    issues.findExpectedReturns(listOf(it.id), LocalDate.now(clock)).map { row -> row.expectedDate.toString() }
                } ?: emptyList()

                LineAvailability(
                    lineId = requireNotNull(line.lineItemId),
                    description = line.description,
                    resourceId = line.resourceId,
                    requested = line.quantity,
                    total = resource?.quantity,
                    available = available,
                    reserved = reserved,
                    issued = issued,
                    fulfilment = fulfilment,
                    expectedReturnDates = expectedReturns,
                )
            }
    }
}
