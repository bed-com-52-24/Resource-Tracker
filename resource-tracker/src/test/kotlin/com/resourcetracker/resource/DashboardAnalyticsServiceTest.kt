package com.resourcetracker.resource

import com.resourcetracker.request.entity.RequestStatus
import com.resourcetracker.request.repository.RequestStatusTotal
import com.resourcetracker.request.repository.ResourceRequestRepository
import com.resourcetracker.request.repository.StockIssueRepository
import com.resourcetracker.request.repository.StockReservationRepository
import com.resourcetracker.security.AuthenticatedUser
import com.resourcetracker.user.Role
import com.resourcetracker.user.UserRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.Instant
import java.util.Optional

class DashboardAnalyticsServiceTest {
    private val resources = mock<ResourceRepository>()
    private val requests = mock<ResourceRequestRepository>()
    private val issues = mock<StockIssueRepository>()
    private val reservations = mock<StockReservationRepository>()
    private val users = mock<UserRepository>()
    private val service = DashboardAnalyticsService(resources, requests, issues, reservations, users)

    @Test
    fun `analytics returns status counts and scoped inventory totals`() {
        val totals = mock<InternalStockTotals>()
        whenever(totals.getResourceCount()).thenReturn(2L)
        whenever(totals.getOnHandQuantity()).thenReturn(20L)
        whenever(totals.getUnavailableQuantity()).thenReturn(2L)
        whenever(resources.summarizeInternalStock(3)).thenReturn(totals)
        whenever(reservations.sumActiveByDepartmentId(3)).thenReturn(4L)
        whenever(issues.sumOutstandingByDepartmentId(3)).thenReturn(4L)
        val pending = mock<RequestStatusTotal>()
        whenever(pending.getStatus()).thenReturn(RequestStatus.PENDING)
        whenever(pending.getRequestCount()).thenReturn(3L)
        val approved = mock<RequestStatusTotal>()
        whenever(approved.getStatus()).thenReturn(RequestStatus.APPROVED)
        whenever(approved.getRequestCount()).thenReturn(1L)
        whenever(requests.summarizeStatusesByDepartment(3)).thenReturn(listOf(pending, approved))

        val result = service.analytics(caller(Role.ADMIN), 3)

        assertEquals(3, result.departmentId)
        assertEquals(3L, result.requestsByStatus[RequestStatus.PENDING])
        assertEquals(1L, result.requestsByStatus[RequestStatus.APPROVED])
        assertEquals(0L, result.requestsByStatus[RequestStatus.REJECTED])
        assertEquals(2L, result.inventory.internalResourceCount)
        assertEquals(20L, result.inventory.onHandQuantity)
        assertEquals(4L, result.inventory.reservedQuantity)
        assertEquals(2L, result.inventory.unavailableQuantity)
        assertEquals(14L, result.inventory.availableQuantity)
        assertEquals(4L, result.inventory.issuedQuantity)
        assertEquals("15.38", result.inventory.utilisationRate.toPlainString())
    }

    private fun caller(role: Role) = AuthenticatedUser(
        id = 9,
        username = role.name.lowercase(),
        role = role,
        jti = "test-token",
        tokenExpiresAt = Instant.parse("2026-10-27T00:00:00Z"),
    )
}
