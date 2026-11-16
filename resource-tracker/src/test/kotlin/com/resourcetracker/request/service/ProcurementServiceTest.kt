package com.resourcetracker.request.service

import com.resourcetracker.notification.NotificationService
import com.resourcetracker.request.entity.FulfillmentPreference
import com.resourcetracker.request.entity.Procurement
import com.resourcetracker.request.entity.ProcurementStatus
import com.resourcetracker.request.entity.ResourceRequest
import com.resourcetracker.request.entity.ResourceRequestLineItem
import com.resourcetracker.request.repository.ProcurementRepository
import com.resourcetracker.request.repository.RequestHistoryRepository
import com.resourcetracker.request.repository.RequestLineItemRepository
import com.resourcetracker.request.repository.ResourceRequestRepository
import com.resourcetracker.user.Role
import com.resourcetracker.user.User
import com.resourcetracker.user.UserRepository
import org.mockito.kotlin.any
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import java.time.Clock
import java.util.Optional

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
            vendorId = null,
        )

        val exception = assertThrows<ResponseStatusException> {
            service().authorize(request, listOf(externalLine), actorId = 2)
        }

        assertEquals(HttpStatus.CONFLICT, exception.statusCode)
        verify(procurements, never()).save(org.mockito.kotlin.any())
    }

    @Test
    fun `receiving a procurement cannot exceed the aggregate approved line quantity`() {
        val manager = User(
            id = 7,
            username = "manager",
            email = "manager@example.test",
            fullName = "Manager",
            passwordHash = "hash",
            role = Role.DEPARTMENT_MANAGER,
            department = null,
        )
        val request = ResourceRequest(requestId = 1, assignedManagerId = manager.id)
        val line = ResourceRequestLineItem(
            lineItemId = 3,
            requestId = 1,
            quantity = 6,
            approvedQuantity = 6,
            fulfillmentPreference = FulfillmentPreference.EXTERNAL.name,
        )
        val current = Procurement(
            id = 10,
            requestId = 1,
            lineItemId = 3,
            vendorId = 4,
            authorizedQuantity = 4,
            status = ProcurementStatus.ORDERED,
        )
        val previousReceipt = Procurement(
            id = 9,
            requestId = 1,
            lineItemId = 3,
            vendorId = 4,
            authorizedQuantity = 3,
            status = ProcurementStatus.ACCEPTED,
            receivedQuantity = 3,
        )
        whenever(users.findById(manager.id)).thenReturn(Optional.of(manager))
        whenever(procurements.findById(current.id)).thenReturn(Optional.of(current))
        whenever(requests.findForUpdateById(request.requestId!!)).thenReturn(request)
        whenever(lines.findById(line.lineItemId!!)).thenReturn(Optional.of(line))
        whenever(procurements.findAllByLineItemId(line.lineItemId!!)).thenReturn(listOf(previousReceipt, current))
        whenever(procurements.findAllByLineItemIdForUpdate(line.lineItemId!!)).thenReturn(listOf(previousReceipt, current))

        val exception = assertThrows<ResponseStatusException> {
            service().update(current.id, manager.id, UpdateProcurementRequest(ProcurementStatus.RECEIVED, receivedQuantity = 4))
        }

        assertEquals(HttpStatus.BAD_REQUEST, exception.statusCode)
        verify(procurements, never()).save(any())
    }

    @Test
    fun `cancelling a request cancels authorized and ordered procurement rows`() {
        val authorized = Procurement(id = 1, requestId = 1, lineItemId = 3, vendorId = 4, authorizedQuantity = 2)
        val ordered = Procurement(id = 2, requestId = 1, lineItemId = 3, vendorId = 4, authorizedQuantity = 1, status = ProcurementStatus.ORDERED)
        whenever(procurements.findAllByRequestIdForUpdate(1)).thenReturn(listOf(authorized, ordered))

        service().cancelForRequest(1, 7, "No longer needed")

        assertEquals(ProcurementStatus.CANCELLED, authorized.status)
        assertEquals(ProcurementStatus.CANCELLED, ordered.status)
        verify(history, times(2)).save(any())
    }

    @Test
    fun `cancellation is rejected after external goods have been received`() {
        val received = Procurement(
            id = 1,
            requestId = 1,
            lineItemId = 3,
            vendorId = 4,
            authorizedQuantity = 2,
            status = ProcurementStatus.RECEIVED,
            receivedQuantity = 1,
        )
        whenever(procurements.findAllByRequestIdForUpdate(1)).thenReturn(listOf(received))

        val exception = assertThrows<ResponseStatusException> {
            service().cancelForRequest(1, 7, "No longer needed")
        }

        assertEquals(HttpStatus.CONFLICT, exception.statusCode)
        verify(procurements, never()).save(any())
    }
}
