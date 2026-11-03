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
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
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
    private val clock: Clock,
) {
    @Transactional
    fun enqueue(
        requestId: Int,
        lines: List<ResourceRequestLineItem>,
        actorId: Int,
        from: RequestStatus,
        comment: String?,
        expectedDates: Map<Int, LocalDate?> = emptyMap(),
        defaultExpectedDate: LocalDate? = null,
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
                expectedFulfillmentDate = expectedDates[lineId] ?: defaultExpectedDate,
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
     * Reserve already-available stock now, but backorder only the shortfall of approved internal lines.
     * The expected date is a planning target and does not reserve or issue units.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    fun backorder(
        requestId: Int,
        lines: List<ResourceRequestLineItem>,
        backorderedQuantities: Map<Int, Int>,
        actorId: Int,
        from: RequestStatus,
        comment: String?,
        expectedDates: Map<Int, LocalDate?> = emptyMap(),
        defaultExpectedDate: LocalDate? = null,
    ) {
        val entries = lines.mapNotNull { line ->
            val lineId = line.lineItemId ?: return@mapNotNull null
            val shortfall = backorderedQuantities[lineId] ?: return@mapNotNull null
            if (shortfall <= 0) return@mapNotNull null
            val resourceId = line.resourceId ?: return@mapNotNull null
            WaitlistEntry(
                requestId = requestId,
                lineItemId = lineId,
                resourceId = resourceId,
                quantity = shortfall,
                expectedFulfillmentDate = expectedDates[lineId] ?: defaultExpectedDate,
            ) to line
        }
        if (entries.isEmpty()) return
        waitlist.saveAll(entries.map { it.first })
        entries.forEach { (entry, line) ->
            history.save(
                RequestHistory(
                    requestId = requestId,
                    eventType = "BACKORDERED",
                    fromStatus = from.name,
                    toStatus = from.name,
                    actorId = actorId,
                    lineItemId = line.lineItemId,
                    quantity = entry.quantity,
                    comment = comment,
                ),
            )
        }

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

    /** Reduce a backordered (not yet reserved) quantity when part of it is waived/no longer required. */
    @Transactional(propagation = Propagation.MANDATORY)
    fun reduceBackorderedQuantity(lineItemId: Int, quantity: Int) {
        if (quantity <= 0) return
        val entry = waitlist.findByLineItemIdForUpdate(lineItemId) ?: return
        if (entry.status == WaitlistStatus.FULFILLED || entry.status == WaitlistStatus.CANCELLED) return
        val outstanding = (entry.quantity - entry.issuedQuantity).coerceAtLeast(0)
        val reduction = minOf(quantity, outstanding)
        if (reduction == 0) return
        val unreserved = (entry.quantity - entry.reservedQuantity).coerceAtLeast(0)
        val fromReserved = (reduction - unreserved).coerceAtLeast(0)
        val releasableReserved = (entry.reservedQuantity - entry.issuedQuantity).coerceAtLeast(0)
        entry.quantity -= reduction
        entry.reservedQuantity -= minOf(fromReserved, releasableReserved)
        if (entry.issuedQuantity >= entry.quantity) {
            entry.status = WaitlistStatus.FULFILLED
        } else if (entry.reservedQuantity <= entry.issuedQuantity) {
            entry.status = WaitlistStatus.WAITING
        } else {
            entry.status = WaitlistStatus.FULFILLABLE
        }
        waitlist.save(entry)
    }

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

    @Scheduled(cron = "0 0 8 * * *", zone = "Africa/Blantyre")
    @Transactional
    fun notifyOverdueBackorders() {
        val today = LocalDate.now(clock.withZone(ZoneId.of("Africa/Blantyre")))
        waitlist.findOverdueForNotificationForUpdate(openStatuses, today).forEach { entry ->
            val request = requests.findById(entry.requestId)
                .orElseThrow { notFound("Request not found") }
            notifications.notify(
                listOfNotNull(request.requesterId, request.assignedManagerId).distinct(),
                NotificationType.BACKORDER_OVERDUE,
                mapOf(
                    "requestId" to entry.requestId,
                    "requestNumber" to request.requestNumber,
                    "lineItemId" to entry.lineItemId,
                    "expectedFulfillmentDate" to entry.expectedFulfillmentDate.toString(),
                ),
            )
            entry.overdueNotifiedDate = today
            waitlist.save(entry)
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
