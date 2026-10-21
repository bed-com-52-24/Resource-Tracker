package com.resourcetracker.request.service

import com.resourcetracker.request.entity.*
import com.resourcetracker.request.repository.*
import com.resourcetracker.resource.ResourceRepository
import com.resourcetracker.resource.ResourceType
import com.resourcetracker.user.Role
import com.resourcetracker.user.UserRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.time.Clock
import java.time.LocalDateTime

data class ReservationResponse(val id: Int, val resourceId: Int, val requestId: Int, val lineItemId: Int, val quantity: Int, val status: ReservationStatus)
data class CreateReservationRequest(val lineItemId: Int, val quantity: Int)

@Service
class ReservationService(
    private val reservations: StockReservationRepository,
    private val requests: ResourceRequestRepository,
    private val lines: RequestLineItemRepository,
    private val resources: ResourceRepository,
    private val availability: StockAvailabilityService,
    private val issues: StockIssueRepository,
    private val history: RequestHistoryRepository,
    private val users: UserRepository,
    private val clock: Clock,
) {
    @Transactional
    fun reserve(requestId: Int, actorId: Int, command: CreateReservationRequest): ReservationResponse {
        val actor = users.findById(actorId).orElseThrow { notFound("User not found") }
        val request = requests.findForUpdateById(requestId) ?: notFound("Request not found")
        if (actor.role != Role.DEPARTMENT_MANAGER || request.assignedManagerId != actor.id) forbidden()
        if (request.status != RequestStatus.APPROVED) conflict("Request must be approved before stock can be reserved")
        if (command.quantity <= 0) badRequest("quantity must be positive")
        val line = lines.findById(command.lineItemId).orElseThrow { notFound("Line item not found") }
        if (line.requestId != requestId) notFound("Line item not found")
        if (line.fulfillmentPreference != FulfillmentPreference.INTERNAL.name || line.resourceId == null) badRequest("Only internal resource lines can be reserved")
        val resource = resources.findForUpdateById(line.resourceId!!) ?: notFound("Resource not found")
        if (resource.type != ResourceType.INTERNAL || !resource.active) conflict("Resource is unavailable for reservation")
        val lineReserved = reservations.findByRequestIdAndLineItemIdAndStatusForUpdate(requestId, line.lineItemId!!, ReservationStatus.ACTIVE).sumOf { it.quantity }
        val alreadyIssued = issues.findByRequestIdAndLineItemIdForUpdate(requestId, command.lineItemId).sumOf { it.quantity }
        val approved = line.approvedQuantity ?: 0
        if (command.quantity > approved - lineReserved - alreadyIssued) conflict("Reservation exceeds approved quantity remaining")
        val available = availability.availableQuantity(resource, lockReservations = true)
        if (command.quantity > available) conflict("Insufficient available stock; available $available")
        val saved = reservations.save(StockReservation(resourceId = resource.id, requestId = requestId, lineItemId = command.lineItemId, teamId = request.teamId, quantity = command.quantity, createdBy = actor.id))
        history.save(RequestHistory(requestId = requestId, eventType = "RESERVED", fromStatus = request.status.name, toStatus = request.status.name, actorId = actor.id, lineItemId = command.lineItemId, quantity = command.quantity))
        return ReservationResponse(saved.id, saved.resourceId, saved.requestId, saved.lineItemId, saved.quantity, saved.status)
    }

    @Transactional
    fun releaseAllFor(requestId: Int, actorId: Int, reason: String?) {
        val active = reservations.findAllByRequestIdAndStatus(requestId, ReservationStatus.ACTIVE)
        active.forEach { reservation ->
            reservation.status = ReservationStatus.RELEASED
            reservation.closedAt = LocalDateTime.now(clock)
            reservation.closeReason = reason ?: "Request cancelled"
            reservations.save(reservation)
            history.save(RequestHistory(requestId = requestId, eventType = "RELEASED", actorId = actorId, lineItemId = reservation.lineItemId, quantity = reservation.quantity, comment = reservation.closeReason))
        }
    }

    @Transactional
    fun releaseExcessForLine(requestId: Int, lineItemId: Int, approvedQuantity: Int, actorId: Int, reason: String) {
        val active = reservations.findByRequestIdAndLineItemIdAndStatusForUpdate(requestId, lineItemId, ReservationStatus.ACTIVE)
        val issued = issues.findByRequestIdAndLineItemIdForUpdate(requestId, lineItemId).sumOf { it.quantity }
        var excess = (active.sumOf { it.quantity } - (approvedQuantity - issued)).coerceAtLeast(0)
        active.asReversed().forEach { reservation ->
            if (excess > 0) {
                val released = minOf(excess, reservation.quantity)
                excess -= released
                if (released == reservation.quantity) {
                    reservation.status = ReservationStatus.RELEASED
                    reservation.closedAt = LocalDateTime.now(clock)
                    reservation.closeReason = reason
                } else reservation.quantity -= released
                reservations.save(reservation)
                history.save(RequestHistory(requestId = requestId, eventType = "RELEASED", actorId = actorId, lineItemId = lineItemId, quantity = released, comment = reason))
            }
        }
    }

    private fun forbidden(): Nothing = throw ResponseStatusException(HttpStatus.FORBIDDEN, "Only the assigned manager may reserve stock")
    private fun conflict(message: String): Nothing = throw ResponseStatusException(HttpStatus.CONFLICT, message)
    private fun badRequest(message: String): Nothing = throw ResponseStatusException(HttpStatus.BAD_REQUEST, message)
    private fun notFound(message: String): Nothing = throw ResponseStatusException(HttpStatus.NOT_FOUND, message)
}
