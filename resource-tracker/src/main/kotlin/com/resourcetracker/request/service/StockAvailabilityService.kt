package com.resourcetracker.request.service

import com.resourcetracker.request.entity.ReservationStatus
import com.resourcetracker.request.repository.StockReservationRepository
import com.resourcetracker.resource.Resource
import com.resourcetracker.resource.ResourceType
import org.springframework.stereotype.Service

@Service
class StockAvailabilityService(private val reservations: StockReservationRepository) {
    fun reservedQuantities(resourceIds: Collection<Int>): Map<Int, Int> =
        if (resourceIds.isEmpty()) emptyMap()
        else reservations.sumActiveByResourceIds(resourceIds).associate { it.getResourceId() to it.getTotalQuantity().toInt() }

    fun reservedQuantity(resourceId: Int, forUpdate: Boolean = false): Int =
        if (forUpdate) reservations.findByResourceIdAndStatusForUpdate(resourceId, ReservationStatus.ACTIVE).sumOf { it.quantity }
        else reservations.sumActiveByResourceId(resourceId).toInt()

    /** The single availability formula used by request review, reservations, and resource reads. */
    fun availableQuantity(
        resource: Resource,
        lockReservations: Boolean = false,
        knownReservedQuantity: Int? = null,
    ): Int {
        if (resource.type != ResourceType.INTERNAL) return 0
        val physical = resource.quantity ?: 0
        val reserved = knownReservedQuantity ?: reservedQuantity(resource.id, lockReservations)
        return (physical - reserved - resource.unavailableQuantity).coerceAtLeast(0)
    }
}
