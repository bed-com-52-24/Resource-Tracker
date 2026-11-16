package com.resourcetracker.request.service

import com.resourcetracker.notification.NotificationService
import com.resourcetracker.request.entity.*
import com.resourcetracker.request.repository.RequestHistoryRepository
import com.resourcetracker.request.repository.ResourceRequestRepository
import com.resourcetracker.request.repository.WaitlistEntryRepository
import com.resourcetracker.resource.Resource
import com.resourcetracker.resource.ResourceCategory
import com.resourcetracker.resource.ResourceRepository
import com.resourcetracker.resource.ResourceType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Clock
import java.time.LocalDateTime
import java.util.Optional

class WaitlistServiceTest {
    private val waitlist = mock<WaitlistEntryRepository>()
    private val history = mock<RequestHistoryRepository>()
    private val resources = mock<ResourceRepository>()
    private val reservations = mock<ReservationService>()
    private val requests = mock<ResourceRequestRepository>()
    private val notifications = mock<NotificationService>()

    private fun service() = WaitlistService(
        waitlist, history, resources, reservations, requests, notifications, Clock.systemUTC(),
    )

    @Test
    fun `stock allocation stops at the first FIFO entry that cannot be fully reserved`() {
        val earlier = WaitlistEntry(
            id = 11,
            requestId = 101,
            lineItemId = 201,
            resourceId = 7,
            quantity = 5,
            createdAt = LocalDateTime.parse("2026-11-01T09:00:00"),
        )
        val later = WaitlistEntry(
            id = 12,
            requestId = 102,
            lineItemId = 202,
            resourceId = 7,
            quantity = 3,
            createdAt = LocalDateTime.parse("2026-11-02T09:00:00"),
        )
        val resource = Resource(
            id = 7,
            code = "R-7",
            name = "Projector",
            type = ResourceType.INTERNAL,
            category = ResourceCategory.EQUIPMENT,
            unitOfMeasure = "unit",
            quantity = 2,
        )
        val request = ResourceRequest(requestId = 101, requesterId = 31, assignedManagerId = 41)
        whenever(resources.findForUpdateById(7)).thenReturn(resource)
        whenever(waitlist.findOpenForUpdateByResourceIdFifo(7, setOf(WaitlistStatus.WAITING, WaitlistStatus.FULFILLABLE)))
            .thenReturn(listOf(earlier, later))
        whenever(reservations.reserveForWaitlist(earlier, 5)).thenReturn(2)
        whenever(requests.findById(101)).thenReturn(Optional.of(request))

        service().onStockReturned(7)

        assertEquals(2, earlier.reservedQuantity)
        assertEquals(WaitlistStatus.FULFILLABLE, earlier.status)
        assertEquals(0, later.reservedQuantity)
        assertEquals(WaitlistStatus.WAITING, later.status)
        verify(reservations).reserveForWaitlist(earlier, 5)
        verify(reservations, never()).reserveForWaitlist(eq(later), any())
    }
}
