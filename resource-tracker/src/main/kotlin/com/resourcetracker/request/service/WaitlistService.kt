package com.resourcetracker.request.service

import com.resourcetracker.notification.NotificationService
import com.resourcetracker.notification.NotificationType
import com.resourcetracker.request.entity.ActorType
import com.resourcetracker.request.entity.RequestHistory
import com.resourcetracker.request.entity.RequestStatus
import com.resourcetracker.request.entity.ResourceRequestLineItem
import com.resourcetracker.request.entity.WaitlistEntry
import com.resourcetracker.request.entity.WaitlistStatus
import com.resourcetracker.request.repository.RequestHistoryRepository
import com.resourcetracker.request.repository.ResourceRequestRepository
import com.resourcetracker.request.repository.WaitlistEntryRepository
import com.resourcetracker.resource.ResourceRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Service
class WaitlistService(
    private val waitlist: WaitlistEntryRepository,
    private val history: RequestHistoryRepository,
    private val resources: ResourceRepository,
    private val reservationService: ReservationService,
    private val requests: ResourceRequestRepository,
    private val notifications: NotificationService,
) {
    @Transactional
    fun enqueue(
        requestId: Int,
        lines: List<ResourceRequestLineItem>,
        actorId: Int,
        from: RequestStatus,
        comment: String?,
    ) {
        val entries = lines.map { line ->
            val lineId = requireNotNull(line.lineItemId)
            val resourceId = requireNotNull(line.resourceId)
            val approvedQuantity = requireNotNull(line.approvedQuantity)
            WaitlistEntry(
                requestId = requestId,
                lineItemId = lineId,
                resourceId = resourceId,
                quantity = approvedQuantity,
            ) to line
        }
        waitlist.saveAll(entries.map { it.first })
        entries.forEach { (entry, line) ->
            history.save(
                RequestHistory(
                    requestId = requestId,
                    eventType = "WAITLIST_CREATED",
                    fromStatus = from.name,
                    toStatus = RequestStatus.WAITLISTED.name,
                    actorId = actorId,
                    lineItemId = line.lineItemId,
                    quantity = entry.quantity,
                    comment = comment,
                ),
            )
        }

        // Stock may already be free (for example partly available): serve the queue now.
        entries.map { it.first.resourceId }.distinct().sorted().forEach(::onStockReturned)
    }

    @Transactional(propagation = Propagation.MANDATORY)
    fun cancelForRequest(requestId: Int, actorId: Int) {
        waitlist.findByRequestIdAndStatusInForUpdate(requestId, WaitlistStatus.entries).forEach { entry ->
            val from = entry.status
            entry.status = WaitlistStatus.CANCELLED
            waitlist.save(entry)
            history.save(
                RequestHistory(
                    requestId = requestId,
                    eventType = "WAITLIST_CANCELLED",
                    fromStatus = from.name,
                    toStatus = WaitlistStatus.CANCELLED.name,
                    actorId = actorId,
                    lineItemId = entry.lineItemId,
                    quantity = entry.quantity - entry.reservedQuantity,
                    comment = "Request cancelled",
                ),
            )
        }
    }

    /**
     * Called whenever usable stock becomes free. The caller must already be in a transaction.
     * Lock order is resource, waitlist entries in FIFO order, then reservations.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    fun onStockReturned(resourceId: Int) {
        resources.findForUpdateById(resourceId) ?: notFound("Resource not found")
        val queue = waitlist.findOpenForUpdateByResourceIdFifo(resourceId, openStatuses)
        for (entry in queue) {
            val wanted = entry.quantity - entry.reservedQuantity
            val granted = reservationService.reserveForWaitlist(entry, wanted)
            if (granted > 0) applyReservation(entry, granted)
            if (granted < wanted) break
        }
    }

    private fun applyReservation(entry: WaitlistEntry, granted: Int) {
        val becameFulfillable = entry.status == WaitlistStatus.WAITING
        entry.reservedQuantity += granted
        entry.status = if (entry.reservedQuantity == entry.quantity) {
            WaitlistStatus.FULFILLED
        } else if (becameFulfillable) {
            WaitlistStatus.FULFILLABLE
        } else {
            entry.status
        }
        waitlist.save(entry)

        if (becameFulfillable) notifyFulfillable(entry)
        if (entry.status == WaitlistStatus.FULFILLED) {
            history.save(
                RequestHistory(
                    requestId = entry.requestId,
                    eventType = "WAITLIST_FULFILLED",
                    actorId = null,
                    actorType = ActorType.SYSTEM,
                    lineItemId = entry.lineItemId,
                    quantity = entry.quantity,
                ),
            )
        }
        log.info(
            "Waitlist entry {} reserved {} ({}/{})",
            entry.id,
            granted,
            entry.reservedQuantity,
            entry.quantity,
        )
    }

    private fun notifyFulfillable(entry: WaitlistEntry) {
        val request = requests.findById(entry.requestId)
            .orElseThrow { notFound("Request not found") }
        notifications.notify(
            listOfNotNull(request.requesterId, request.assignedManagerId).distinct(),
            NotificationType.WAITLIST_FULFILLABLE,
            mapOf(
                "requestId" to entry.requestId,
                "requestNumber" to request.requestNumber,
                "lineItemId" to entry.lineItemId,
            ),
        )
    }

    private fun notFound(message: String): Nothing =
        throw org.springframework.web.server.ResponseStatusException(
            org.springframework.http.HttpStatus.NOT_FOUND,
            message,
        )

    private companion object {
        val openStatuses = setOf(WaitlistStatus.WAITING, WaitlistStatus.FULFILLABLE)
        val log = LoggerFactory.getLogger(WaitlistService::class.java)
    }
}
