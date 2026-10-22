package com.resourcetracker.request.service

import com.resourcetracker.notification.NotificationService
import com.resourcetracker.notification.NotificationType
import com.resourcetracker.request.entity.*
import com.resourcetracker.request.repository.*
import com.resourcetracker.user.Role
import com.resourcetracker.user.UserRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.PageImpl
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.time.Clock
import java.time.LocalDateTime

data class ProcurementResponse(
    val id: Int,
    val requestId: Int,
    val lineItemId: Int,
    val vendorId: Int,
    val status: ProcurementStatus,
    val estimatedCost: java.math.BigDecimal?,
    val expectedDelivery: java.time.LocalDate?,
    val receivedQuantity: Int?,
)
data class UpdateProcurementRequest(val status: ProcurementStatus, val receivedQuantity: Int? = null)

@Service
class ProcurementService(
    private val procurements: ProcurementRepository,
    private val requests: ResourceRequestRepository,
    private val lines: RequestLineItemRepository,
    private val history: RequestHistoryRepository,
    private val users: UserRepository,
    private val notifications: NotificationService,
    private val clock: Clock,
) {
    @Transactional
    fun authorize(request: ResourceRequest, lineItems: List<ResourceRequestLineItem>, actorId: Int) {
        val vendorId = request.vendorId ?: throw ResponseStatusException(HttpStatus.CONFLICT, "Vendor is required for external procurement")
        lineItems.filter { it.fulfillmentPreference == FulfillmentPreference.EXTERNAL.name }.forEach { line ->
            val quantity = line.approvedQuantity ?: line.quantity
            val procurement = procurements.save(
                Procurement(
                    requestId = requireNotNull(request.requestId),
                    lineItemId = requireNotNull(line.lineItemId),
                    vendorId = vendorId,
                    estimatedCost = line.unitPrice.multiply(java.math.BigDecimal.valueOf(quantity.toLong())),
                    expectedDelivery = request.requiredDate,
                ),
            )
            history.save(RequestHistory(requestId = request.requestId!!, eventType = "PROCUREMENT_AUTHORIZED", fromStatus = request.status.name, toStatus = request.status.name, actorId = actorId, lineItemId = line.lineItemId, quantity = quantity, comment = "Procurement ${procurement.id} authorized"))
        }
    }

    @Transactional(readOnly = true)
    fun list(actorId: Int, status: ProcurementStatus?, page: Int, size: Int): Page<ProcurementResponse> {
        val actor = users.findById(actorId).orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "User not found") }
        if (actor.role != Role.ADMIN && actor.role != Role.DEPARTMENT_MANAGER) forbidden()
        if (page < 0 || size !in 1..100) throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid pagination")
        val filtered = procurements.findAll().filter { procurement ->
            (status == null || status == procurement.status) &&
                (actor.role == Role.ADMIN || requests.findById(procurement.requestId).orElse(null)?.assignedManagerId == actor.id)
        }
        val pageable = PageRequest.of(page, size)
        val start = minOf(pageable.offset.toInt(), filtered.size)
        val end = minOf(start + size, filtered.size)
        return PageImpl(filtered.subList(start, end).map { it.toResponse() }, pageable, filtered.size.toLong())
    }

    @Transactional
    fun update(id: Int, actorId: Int, dto: UpdateProcurementRequest): ProcurementResponse {
        val actor = users.findById(actorId).orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "User not found") }
        val procurement = procurements.findById(id).orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Procurement not found") }
        val request = requests.findById(procurement.requestId).orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Request not found") }
        if (actor.role != Role.DEPARTMENT_MANAGER || request.assignedManagerId != actor.id) forbidden()
        val valid = when (procurement.status) {
            ProcurementStatus.AUTHORISED -> dto.status == ProcurementStatus.ORDERED
            ProcurementStatus.ORDERED -> dto.status == ProcurementStatus.RECEIVED
            ProcurementStatus.RECEIVED -> dto.status == ProcurementStatus.ACCEPTED
            ProcurementStatus.ACCEPTED, ProcurementStatus.CANCELLED -> false
        }
        if (!valid) throw ResponseStatusException(HttpStatus.CONFLICT, "Invalid procurement transition ${procurement.status} -> ${dto.status}")
        if (dto.status == ProcurementStatus.RECEIVED) {
            val line = lines.findById(procurement.lineItemId).orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Procurement line not found") }
            val quantity = dto.receivedQuantity ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "receivedQuantity is required")
            if (quantity !in 1..(line.approvedQuantity ?: line.quantity)) throw ResponseStatusException(HttpStatus.BAD_REQUEST, "receivedQuantity exceeds authorized quantity")
            procurement.receivedQuantity = quantity
            procurement.receivedBy = actor.id
            procurement.receivedAt = LocalDateTime.now(clock)
        }
        if (dto.status == ProcurementStatus.ACCEPTED) {
            procurement.acceptedBy = actor.id
            procurement.acceptedAt = LocalDateTime.now(clock)
        }
        val from = procurement.status
        procurement.status = dto.status
        val saved = procurements.save(procurement)
        history.save(RequestHistory(requestId = request.requestId!!, eventType = "PROCUREMENT_${dto.status.name}", fromStatus = from.name, toStatus = dto.status.name, actorId = actor.id, lineItemId = procurement.lineItemId, quantity = procurement.receivedQuantity))
        notifications.notify(listOf(request.requesterId), NotificationType.PROCUREMENT, mapOf("requestId" to request.requestId!!))
        return saved.toResponse()
    }

    private fun Procurement.toResponse() = ProcurementResponse(id, requestId, lineItemId, vendorId, status, estimatedCost, expectedDelivery, receivedQuantity)
    private fun forbidden(): Nothing = throw ResponseStatusException(HttpStatus.FORBIDDEN, "Action is not permitted")
}
