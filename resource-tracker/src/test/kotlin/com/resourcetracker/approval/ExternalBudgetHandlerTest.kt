package com.resourcetracker.approval

import com.resourcetracker.request.service.BudgetStatus
import com.resourcetracker.audit.AuditAction
import com.resourcetracker.audit.AuditRecord
import com.resourcetracker.audit.AuditService
import com.resourcetracker.request.entity.ResourceRequestLineItem
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import java.math.BigDecimal

class ExternalBudgetHandlerTest {
    private val budget = mock<com.resourcetracker.request.service.BudgetPort>()
    private val audit = mock<AuditService>()
    private val procurement = mock<ProcurementNotifier>()
    private val handler = ExternalBudgetHandler(budget, audit, procurement)

    @Test
    fun `approved external line commits budget and creates procurement notice`() {
        whenever(budget.departmentOfRequest(101)).thenReturn(3)
        whenever(budget.status(3)).thenReturn(
            com.resourcetracker.request.service.BudgetStatus(
                 3,
                 BigDecimal("100.00"),
                 BigDecimal("20.00")
                 ),
        )
        whenever(budget.tryCommit(3, BigDecimal("25.00"))).thenReturn(true)

        handler.commitApprovedLines(101, listOf(externalLine()), actorId = 7, comment = null)

        verify(budget).tryCommit(3, BigDecimal("25.00"))
        verify(procurement).itemApproved(101, 5, BigDecimal("25.00"))
        val auditRecord = argumentCaptor<AuditRecord>()
        verify(audit).record(auditRecord.capture())
        assertEquals(AuditAction.BUDGET_ALLOCATION_CHANGED, auditRecord.firstValue.action)
    }

    @Test
    fun `over-budget approval without a comment is rejected without side effects`() {
        whenever(budget.departmentOfRequest(101)).thenReturn(3)
        whenever(budget.status(3)).thenReturn(
            com.resourcetracker.request.service.BudgetStatus(
                  3,
                  BigDecimal("100.00"),
                  BigDecimal("20.00")
                  ),
        )
        whenever(budget.tryCommit(3, BigDecimal("25.00"))).thenReturn(false)

        val exception =
            assertThrows<ResponseStatusException> {
                handler.commitApprovedLines(101, listOf(externalLine()), actorId = 7, comment = null)
            }

        assertEquals(HttpStatus.CONFLICT, exception.statusCode)
        verify(budget, never()).forceCommit(any(), any())
        verify(audit, never()).record(any())
        verify(procurement, never()).itemApproved(any(), any(), any())
    }

    @Test
    fun `over-budget approval with a reason commits and records an override`() {
        whenever(budget.departmentOfRequest(101)).thenReturn(3)
        whenever(budget.status(3)).thenReturn(
            com.resourcetracker.request.service.BudgetStatus(
                 3,
                 BigDecimal("100.00"),
                 BigDecimal("20.00")
                 ),
        )
        whenever(budget.tryCommit(3, BigDecimal("25.00"))).thenReturn(false)

        handler.commitApprovedLines(101, listOf(externalLine()), actorId = 7, comment = "Urgent")

        verify(budget).forceCommit(3, BigDecimal("25.00"))
        verify(procurement).itemApproved(101, 5, BigDecimal("25.00"))
        val auditRecord = argumentCaptor<AuditRecord>()
        verify(audit).record(auditRecord.capture())
        assertEquals(AuditAction.POLICY_OVERRIDE_APPROVED, auditRecord.firstValue.action)
        assertEquals("Urgent", auditRecord.firstValue.comment)
    }

    @Test
    fun `internal lines do not change the external budget`() {
        val line = externalLine().apply { fulfillmentPreference = "INTERNAL" }

        handler.commitApprovedLines(101, listOf(line), actorId = 7, comment = null)

        verify(budget, never()).departmentOfRequest(any())
        verify(audit, never()).record(any())
        verify(procurement, never()).itemApproved(any(), any(), any())
    }

    private fun externalLine() =
        ResourceRequestLineItem(
            lineItemId = 5,
            requestId = 101,
            description = "Laptop",
            manufacturer = "Acme",
            unitPrice = BigDecimal("12.50"),
            quantity = 4,
            fulfillmentPreference = "EXTERNAL",
            approvedQuantity = 2,
        )
}
