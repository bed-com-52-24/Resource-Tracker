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

    /**
     * Cancel open entries before reservations are released. Resources are locked before entries,
     * matching the FIFO processor's lock order.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    fun cancelForRequest(requestId: Int): List<Int> {
        val resourceIds = waitlist.findByRequestId(requestId)
            .map { it.resourceId }
            .distinct()
            .sorted()
        lockResources(resourceIds)
        waitlist.findByRequestIdForUpdate(requestId)
            .filter { it.status in openStatuses }
            .forEach { entry ->
                entry.status = WaitlistStatus.CANCELLED
                waitlist.save(entry)
            }
        return resourceIds
    }

    /** Record issuance against the reserved quantity; the entry completes when all units are issued. */
    @Transactional(propagation = Propagation.MANDATORY)
    fun recordIssued(lineItemId: Int, quantity: Int, actorId: Int) {
        require(quantity > 0) { "Issued quantity must be positive" }
        val entry = waitlist.findByLineItemIdForUpdate(lineItemId) ?: return
        if (entry.status != WaitlistStatus.FULFILLABLE) return
        check(entry.issuedQuantity + quantity <= entry.reservedQuantity) {
            "Cannot issue more than reserved for waitlist entry ${entry.id}"
        }
        entry.issuedQuantity += quantity
        if (entry.issuedQuantity == entry.quantity) {
            entry.status = WaitlistStatus.FULFILLED
            history.save(
                RequestHistory(
                    requestId = entry.requestId,
                    eventType = "WAITLIST_FULFILLED",
                    fromStatus = WaitlistStatus.FULFILLABLE.name,
                    toStatus = WaitlistStatus.FULFILLED.name,
                    actorId = actorId,
                    actorType = ActorType.USER,
                    lineItemId = entry.lineItemId,
                    quantity = entry.quantity,
                ),
            )
        }
        waitlist.save(entry)
    }

    /** A line still waiting for its first reservation cannot bypass older queue entries. */
    @Transactional(readOnly = true)
    fun isWaiting(lineItemId: Int): Boolean =
        waitlist.findByLineItemId(lineItemId)?.status == WaitlistStatus.WAITING

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
        if (becameFulfillable) entry.status = WaitlistStatus.FULFILLABLE
        waitlist.save(entry)

        if (becameFulfillable) notifyFulfillable(entry)
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

    private fun lockResources(resourceIds: Collection<Int>) {
        resourceIds.distinct().sorted().forEach { resourceId ->
            resources.findForUpdateById(resourceId) ?: notFound("Resource not found")
        }
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
