package com.resourcetracker.approval

import com.resourcetracker.request.service.BudgetPort
import com.resourcetracker.request.service.BudgetReviewService
import com.resourcetracker.request.service.BudgetStatus
import com.resourcetracker.request.entity.ResourceRequest
import com.resourcetracker.request.entity.ResourceRequestLineItem
import com.resourcetracker.request.repository.RequestLineItemRepository
import com.resourcetracker.request.repository.ResourceRequestRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import java.math.BigDecimal
import java.util.Optional

class BudgetReviewServiceTest {
    private val requests = mock<ResourceRequestRepository>()
    private val lines = mock<RequestLineItemRepository>()
    private val budget = mock<BudgetPort>()
    private val service = BudgetReviewService(requests, lines, budget)

    @Test
    fun `review totals external approved quantities and reports remaining budget`() {
        whenever(requests.findById(101)).thenReturn(
            Optional.of(ResourceRequest(requestId = 101, assignedManagerId = 7)),
        )
        whenever(lines.findAllByRequestId(101)).thenReturn(
            listOf(
                ResourceRequestLineItem(
                    requestId = 101,
                    description = "External",
                    manufacturer = "Acme",
                    unitPrice = BigDecimal("12.50"),
                    quantity = 4,
                    fulfillmentPreference = "EXTERNAL",
                    approvedQuantity = 2,
                ),
                ResourceRequestLineItem(
                    requestId = 101,
                    description = "Internal",
                    manufacturer = "Acme",
                    unitPrice = BigDecimal("20.00"),
                    quantity = 8,
                    fulfillmentPreference = "INTERNAL",
                ),
            ),
        )
        whenever(budget.departmentOfRequest(101)).thenReturn(3)
        whenever(budget.status(3)).thenReturn(
            BudgetStatus(3, BigDecimal("100.00"), BigDecimal("80.00")),
        )

        val result = service.review(101, 7)

        assertEquals(BigDecimal("25.00"), result.requestCost)
        assertEquals(BigDecimal("20.00"), result.remaining)
        assertTrue(result.exceedsBudget)
    }

    @Test
    fun `review is hidden from a manager not assigned to the request`() {
        whenever(requests.findById(101)).thenReturn(
            Optional.of(ResourceRequest(requestId = 101, assignedManagerId = 8)),
        )

        val exception =
            assertThrows<ResponseStatusException> {
                service.review(101, 7)
            }

        assertEquals(HttpStatus.FORBIDDEN, exception.statusCode)
    }

    @Test
    fun `review reports when the request fits within its budget`() {
        whenever(requests.findById(101)).thenReturn(
            Optional.of(ResourceRequest(requestId = 101, assignedManagerId = 7)),
        )
        whenever(lines.findAllByRequestId(101)).thenReturn(emptyList())
        whenever(budget.departmentOfRequest(101)).thenReturn(3)
        whenever(budget.status(3)).thenReturn(
            BudgetStatus(3, BigDecimal("100.00"), BigDecimal("80.00")),
        )

        val result = service.review(101, 7)

        assertFalse(result.exceedsBudget)
        assertEquals(BigDecimal.ZERO, result.requestCost)
    }
}
