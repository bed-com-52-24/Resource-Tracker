package com.resourcetracker.request.service

import com.resourcetracker.audit.AuditService
import com.resourcetracker.notification.NotificationService
import com.resourcetracker.request.entity.RequestStatus
import com.resourcetracker.request.entity.ResourceRequest
import com.resourcetracker.request.repository.ProcurementRepository
import com.resourcetracker.request.repository.RequestHistoryRepository
import com.resourcetracker.request.repository.RequestLineItemRepository
import com.resourcetracker.request.repository.ResourceRequestRepository
import com.resourcetracker.request.repository.StockIssueRepository
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class RequestCompletionServiceTest {
    private val requests = mock<ResourceRequestRepository>()
    private val lines = mock<RequestLineItemRepository>()
    private val issues = mock<StockIssueRepository>()
    private val procurements = mock<ProcurementRepository>()
    private val history = mock<RequestHistoryRepository>()
    private val audit = mock<AuditService>()
    private val notifications = mock<NotificationService>()

    @Test
    fun `partially approved request cannot complete before all lines are approved`() {
        val request = ResourceRequest(
            requestId = 10,
            status = RequestStatus.PARTIALLY_APPROVED,
        )
        whenever(requests.findForUpdateById(10)).thenReturn(request)

        val completed = RequestCompletionService(
            requests, lines, issues, procurements, history, audit, notifications,
        ).completeIfFulfilled(10, actorId = 20)

        assertFalse(completed)
        verify(lines, never()).findAllByRequestId(10)
    }
}
