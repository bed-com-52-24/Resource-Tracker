package com.resourcetracker.request.service

import com.resourcetracker.notification.NotificationService
import com.resourcetracker.notification.NotificationType
import com.resourcetracker.common.ListQuery
import com.resourcetracker.request.entity.*
import com.resourcetracker.request.repository.*
import com.resourcetracker.user.Role
import com.resourcetracker.user.UserRepository
import org.springframework.data.domain.Page
import io.swagger.v3.oas.annotations.media.Schema
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
    val authorizedQuantity: Int,
    val vendorId: Int,
    val status: ProcurementStatus,
    val estimatedCost: java.math.BigDecimal?,
    val expectedDelivery: java.time.LocalDate?,
    val receivedQuantity: Int?,
)
data class UpdateProcurementRequest(
    val status: ProcurementStatus,
    @field:Schema(description = "Quantity received on this procurement authorization. The sum across received authorizations cannot exceed the line's approved quantity.", example = "4")
    val receivedQuantity: Int? = null,
)

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
        val externalLines = lineItems.filter {
            it.fulfillmentPreference == FulfillmentPreference.EXTERNAL.name &&
                (it.approvedQuantity ?: it.quantity) > 0
        }
        if (externalLines.isEmpty()) return

        externalLines.forEach { line ->
            val vendorId = line.vendorId ?: throw ResponseStatusException(HttpStatus.CONFLICT, "Vendor is required for external procurement")
            val quantity = line.approvedQuantity ?: line.quantity
            val procurement = procurements.save(
                Procurement(
                    requestId = requireNotNull(request.requestId),
                    lineItemId = requireNotNull(line.lineItemId),
                    vendorId = vendorId,
                    authorizedQuantity = quantity,
                    estimatedCost = line.unitPrice.multiply(java.math.BigDecimal.valueOf(quantity.toLong())),
                    expectedDelivery = request.requiredDate,
                ),
            )
            history.save(RequestHistory(requestId = request.requestId!!, eventType = "PROCUREMENT_AUTHORIZED", fromStatus = request.status.name, toStatus = request.status.name, actorId = actorId, lineItemId = line.lineItemId, quantity = quantity, comment = "Procurement ${procurement.id} authorized"))
        }
    }

    @Transactional
    fun authorizeIncrease(request: ResourceRequest, line: ResourceRequestLineItem, quantity: Int, actorId: Int) {
        if (quantity <= 0 || line.fulfillmentPreference != FulfillmentPreference.EXTERNAL.name) return
        val vendorId = line.vendorId
            ?: throw ResponseStatusException(HttpStatus.CONFLICT, "Vendor is required for external procurement")
        val procurement = procurements.save(
            Procurement(
                requestId = requireNotNull(request.requestId),
                lineItemId = requireNotNull(line.lineItemId),
                vendorId = vendorId,
                authorizedQuantity = quantity,
                estimatedCost = line.unitPrice.multiply(java.math.BigDecimal.valueOf(quantity.toLong())),
                expectedDelivery = request.requiredDate,
            ),
        )
        history.save(
            RequestHistory(
                requestId = request.requestId!!,
                eventType = "PROCUREMENT_AUTHORIZED",
                fromStatus = request.status.name,
                toStatus = request.status.name,
                actorId = actorId,
                lineItemId = line.lineItemId,
                quantity = quantity,
                comment = "Additional procurement ${procurement.id} authorized",
            ),
        )
    }

    @Transactional(readOnly = true)
    fun acceptedQuantity(requestId: Int, lineItemId: Int): Int =
        procurements.findAllByRequestId(requestId)
            .filter { it.lineItemId == lineItemId && it.status == ProcurementStatus.ACCEPTED }
            .sumOf { it.receivedQuantity ?: 0 }

    @Transactional(readOnly = true)
    fun receivedQuantity(requestId: Int, lineItemId: Int): Int =
        procurements.findAllByRequestId(requestId)
            .filter { it.lineItemId == lineItemId && it.status in setOf(ProcurementStatus.RECEIVED, ProcurementStatus.ACCEPTED) }
            .sumOf { it.receivedQuantity ?: 0 }

    @Transactional
    fun cancelForRequest(requestId: Int, actorId: Int, reason: String?) {
        val requestProcurements = procurements.findAllByRequestIdForUpdate(requestId)
        if (requestProcurements.any { it.status == ProcurementStatus.RECEIVED || it.status == ProcurementStatus.ACCEPTED }) {
            throw ResponseStatusException(
                HttpStatus.CONFLICT,
                "A request with received external goods cannot be cancelled; complete or resolve the procurement first",
            )
        }
        requestProcurements
            .filter { it.status == ProcurementStatus.AUTHORISED || it.status == ProcurementStatus.ORDERED }
            .forEach { procurement ->
                val from = procurement.status
                procurement.status = ProcurementStatus.CANCELLED
                procurements.save(procurement)
                history.save(
                    RequestHistory(
                        requestId = requestId,
                        eventType = "PROCUREMENT_CANCELLED",
                        fromStatus = from.name,
                        toStatus = ProcurementStatus.CANCELLED.name,
                        actorId = actorId,
                        lineItemId = procurement.lineItemId,
                        quantity = procurement.authorizedQuantity,
                        comment = reason,
                    ),
                )
            }
    }

    @Transactional
    fun reduceAuthorizedQuantity(
        requestId: Int,
        line: ResourceRequestLineItem,
        newApprovedQuantity: Int,
        actorId: Int,
        reason: String,
    ) {
        val lineId = requireNotNull(line.lineItemId)
        val existing = procurements.findAllByLineItemIdForUpdate(lineId)
        if (existing.isEmpty()) return
        if (existing.any { it.status == ProcurementStatus.ORDERED }) {
            throw ResponseStatusException(
                HttpStatus.CONFLICT,
                "External procurement is already ordered; resolve the vendor order before reducing the approved quantity",
            )
        }
        val received = existing
            .filter { it.status == ProcurementStatus.RECEIVED || it.status == ProcurementStatus.ACCEPTED }
            .sumOf { it.receivedQuantity ?: 0 }
        if (received > newApprovedQuantity) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Approved quantity cannot be lower than externally received quantity")
        }
        val targetAuthorized = newApprovedQuantity - received
        val open = existing.filter { it.status == ProcurementStatus.AUTHORISED }.sortedBy { it.id }
        val keeper = open.firstOrNull()
        if (keeper != null) {
            val previous = keeper.authorizedQuantity
            if (targetAuthorized == 0) {
                keeper.status = ProcurementStatus.CANCELLED
            } else {
                keeper.authorizedQuantity = targetAuthorized
                keeper.estimatedCost = line.unitPrice.multiply(java.math.BigDecimal.valueOf(targetAuthorized.toLong()))
            }
            procurements.save(keeper)
            history.save(
                RequestHistory(
                    requestId = requestId,
                    eventType = if (targetAuthorized == 0) "PROCUREMENT_CANCELLED" else "PROCUREMENT_ADJUSTED",
                    fromStatus = ProcurementStatus.AUTHORISED.name,
                    toStatus = if (targetAuthorized == 0) ProcurementStatus.CANCELLED.name else ProcurementStatus.AUTHORISED.name,
                    actorId = actorId,
                    lineItemId = lineId,
                    quantity = targetAuthorized,
                    comment = "Authorization changed from $previous to $targetAuthorized. $reason",
                ),
            )
        } else if (targetAuthorized > 0) {
            val vendorId = line.vendorId
                ?: throw ResponseStatusException(HttpStatus.CONFLICT, "Vendor is required for external procurement")
            val procurement = procurements.save(
                Procurement(
                    requestId = requestId,
                    lineItemId = lineId,
                    vendorId = vendorId,
                    authorizedQuantity = targetAuthorized,
                    estimatedCost = line.unitPrice.multiply(java.math.BigDecimal.valueOf(targetAuthorized.toLong())),
                ),
            )
            history.save(
                RequestHistory(
                    requestId = requestId,
                    eventType = "PROCUREMENT_AUTHORIZED",
                    fromStatus = null,
                    toStatus = ProcurementStatus.AUTHORISED.name,
                    actorId = actorId,
                    lineItemId = lineId,
                    quantity = targetAuthorized,
                    comment = "Replacement authorization ${procurement.id}. $reason",
                ),
            )
        }
        open.drop(1).forEach { duplicate ->
            duplicate.status = ProcurementStatus.CANCELLED
            procurements.save(duplicate)
            history.save(
                RequestHistory(
                    requestId = requestId,
                    eventType = "PROCUREMENT_CANCELLED",
                    fromStatus = ProcurementStatus.AUTHORISED.name,
                    toStatus = ProcurementStatus.CANCELLED.name,
                    actorId = actorId,
                    lineItemId = lineId,
                    quantity = duplicate.authorizedQuantity,
                    comment = "Superseded by reduced approved quantity. $reason",
                ),
            )
        }
    }

    fun list(
        actorId: Int,
        status: ProcurementStatus?,
        search: String?,
        page: Int,
        size: Int,
        sort: String,
        direction: String,
    ): Page<ProcurementResponse> {
        val actor = users.findById(actorId).orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "User not found") }
        if (actor.role != Role.DEPARTMENT_MANAGER) forbidden()
        val keyword = search?.trim()?.takeIf(String::isNotEmpty)
        if (keyword != null && keyword.length > 200) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Search must be 200 characters or less")
        }
        val pageable = ListQuery.pageable(page, size, sort, direction, SORT_FIELDS, "id")
        return procurements.searchForActor(status, actor.id, false, keyword, pageable)
            .map { it.toResponse() }
    }

    @Transactional
    fun update(id: Int, actorId: Int, dto: UpdateProcurementRequest): ProcurementResponse {
        val actor = users.findById(actorId).orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "User not found") }
        var procurement = procurements.findById(id).orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Procurement not found") }
        val request = requests.findForUpdateById(procurement.requestId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Request not found")
        if (actor.role != Role.DEPARTMENT_MANAGER || request.assignedManagerId != actor.id) forbidden()
        val lineProcurements = procurements.findAllByLineItemIdForUpdate(procurement.lineItemId)
        procurement = lineProcurements.firstOrNull { it.id == id }
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Procurement not found")
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
            if (quantity !in 1..procurement.authorizedQuantity) {
                throw ResponseStatusException(HttpStatus.BAD_REQUEST, "receivedQuantity exceeds this procurement's authorized quantity")
            }
            val receivedOnOtherOrders = lineProcurements
                .filter { it.id != procurement.id && it.status in setOf(ProcurementStatus.RECEIVED, ProcurementStatus.ACCEPTED) }
                .sumOf { it.receivedQuantity ?: 0 }
            val approved = ((line.approvedQuantity ?: line.quantity) - line.waivedQuantity).coerceAtLeast(0)
            if (receivedOnOtherOrders + quantity > approved) {
                throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Total received quantity would exceed the line's approved quantity")
            }
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

    private fun Procurement.toResponse() = ProcurementResponse(id, requestId, lineItemId, authorizedQuantity, vendorId, status, estimatedCost, expectedDelivery, receivedQuantity)
    private fun forbidden(): Nothing = throw ResponseStatusException(HttpStatus.FORBIDDEN, "Action is not permitted")

    companion object {
        private val SORT_FIELDS = setOf("id", "requestId", "lineItemId", "vendorId", "estimatedCost", "expectedDelivery", "status", "receivedQuantity")
    }
}
