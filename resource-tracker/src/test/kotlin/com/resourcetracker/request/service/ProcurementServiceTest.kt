package com.resourcetracker.request.service

import com.resourcetracker.notification.NotificationService
import com.resourcetracker.request.entity.FulfillmentPreference
import com.resourcetracker.request.entity.ResourceRequest
import com.resourcetracker.request.entity.ResourceRequestLineItem
import com.resourcetracker.request.repository.ProcurementRepository
import com.resourcetracker.request.repository.RequestHistoryRepository
import com.resourcetracker.request.repository.RequestLineItemRepository
import com.resourcetracker.request.repository.ResourceRequestRepository
import com.resourcetracker.user.UserRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import java.time.Clock

class ProcurementServiceTest {
    private val procurements = mock<ProcurementRepository>()
    private val requests = mock<ResourceRequestRepository>()
    private val lines = mock<RequestLineItemRepository>()
    private val history = mock<RequestHistoryRepository>()
    private val users = mock<UserRepository>()
    private val notifications = mock<NotificationService>()

    private fun service() = ProcurementService(
        procurements, requests, lines, history, users, notifications, Clock.systemUTC(),
    )

    @Test
    fun `authorizing an internal-only request does not require a vendor`() {
        val request = ResourceRequest(requestId = 1, vendorId = null)
        val internalLine = ResourceRequestLineItem(
            lineItemId = 1,
            requestId = 1,
            fulfillmentPreference = FulfillmentPreference.INTERNAL.name,
        )

        service().authorize(request, listOf(internalLine), actorId = 2)

        verify(procurements, never()).save(org.mockito.kotlin.any())
    }

    @Test
    fun `authorizing external lines still requires a vendor`() {
        val request = ResourceRequest(requestId = 1, vendorId = null)
        val externalLine = ResourceRequestLineItem(
            lineItemId = 1,
            requestId = 1,
            fulfillmentPreference = FulfillmentPreference.EXTERNAL.name,
        )

        val exception = assertThrows<ResponseStatusException> {
            service().authorize(request, listOf(externalLine), actorId = 2)
        }

        assertEquals(HttpStatus.CONFLICT, exception.statusCode)
        verify(procurements, never()).save(org.mockito.kotlin.any())
    }
}
