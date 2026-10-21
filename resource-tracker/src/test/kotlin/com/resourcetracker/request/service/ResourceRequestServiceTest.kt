package com.resourcetracker.request.service

import com.resourcetracker.request.dto.RequestLineItemDto
import com.resourcetracker.request.dto.RequesterUpdateStatus
import com.resourcetracker.request.dto.UpdateResourceRequestDto
import com.resourcetracker.request.entity.RequestCategory
import com.resourcetracker.request.entity.RequestStatus
import com.resourcetracker.request.entity.ResourceRequest
import com.resourcetracker.request.repository.RequestLineItemRepository
import com.resourcetracker.request.repository.ResourceRequestRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import tools.jackson.databind.ObjectMapper
import java.math.BigDecimal

class ResourceRequestServiceTest {
    private val requests = mock(ResourceRequestRepository::class.java)
    private val lineItems = mock(RequestLineItemRepository::class.java)
    private val numbers = mock(RequestNumberService::class.java)
    private val validation = RequestValidationService()
    private val mapper = mock(ObjectMapper::class.java)
    private val idempotency = mock(RequestIdempotencyService::class.java)
    private val service = ResourceRequestService(requests, lineItems, numbers, validation, mapper, idempotency)

    private fun dto(status: RequesterUpdateStatus) = UpdateResourceRequestDto(
        departmentId = 1,
        projectId = 2,
        teamId = 3,
        vendorId = 4,
        lineItems = listOf(
            RequestLineItemDto("Paper", "Maker", BigDecimal("1.00"), 2, RequestCategory.OTHER)
        ),
        status = status,
    )

    private fun owned(status: RequestStatus) = ResourceRequest(
        requestId = 9,
        requestNumber = "REQ-1",
        requesterId = 7,
        assignedManagerId = 8,
        status = status,
    )

    @Test
    fun `pending request can be cancelled`() {
        val request = owned(RequestStatus.PENDING)
        `when`(requests.findOwnedRequest(9, 7)).thenReturn(request)
        `when`(requests.save(request)).thenReturn(request)

        val result = service.updateRequest(9, 7, dto(RequesterUpdateStatus.CANCELLED))

        assertEquals(RequestStatus.CANCELLED, result.status)
    }

    @Test
    fun `queried request can be edited and resubmitted in one update`() {
        val request = owned(RequestStatus.QUERIED)
        `when`(requests.findOwnedRequest(9, 7)).thenReturn(request)
        `when`(requests.save(request)).thenReturn(request)

        val result = service.updateRequest(9, 7, dto(RequesterUpdateStatus.PENDING))

        assertEquals(RequestStatus.PENDING, result.status)
        assertEquals(false, result.editedAfterQuery)
        assertEquals(null, result.queriedAt)
    }

    @Test
    fun `queried request can be cancelled`() {
        val request = owned(RequestStatus.QUERIED)
        `when`(requests.findOwnedRequest(9, 7)).thenReturn(request)
        `when`(requests.save(request)).thenReturn(request)

        assertEquals(
            RequestStatus.CANCELLED,
            service.updateRequest(9, 7, dto(RequesterUpdateStatus.CANCELLED)).status,
        )
    }

    @Test
    fun `final request cannot be transitioned`() {
        val request = owned(RequestStatus.APPROVED)
        `when`(requests.findOwnedRequest(9, 7)).thenReturn(request)

        val error = assertThrows<ResponseStatusException> {
            service.updateRequest(9, 7, dto(RequesterUpdateStatus.CANCELLED))
        }

        assertEquals(HttpStatus.CONFLICT, error.statusCode)
    }
}
