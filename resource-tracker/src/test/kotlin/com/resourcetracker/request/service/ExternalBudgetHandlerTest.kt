package com.resourcetracker.request.service

import com.resourcetracker.audit.AuditService
import com.resourcetracker.request.entity.FulfillmentPreference
import com.resourcetracker.request.entity.ResourceRequestLineItem
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.time.LocalDate

class ExternalBudgetHandlerTest {
    private val budget = mock<BudgetPort>()
    private val audit = mock<AuditService>()

    @Test
    fun `incremental external approval commits only the newly approved amount`() {
        val date = LocalDate.parse("2026-11-14")
        whenever(budget.departmentOfRequest(10)).thenReturn(2)
        whenever(budget.requestDateOfRequest(10)).thenReturn(date)
        whenever(budget.status(2, date)).thenReturn(BudgetStatus(2, BigDecimal("1000"), BigDecimal("100")))
        whenever(budget.tryCommit(2, date, BigDecimal("30.00"))).thenReturn(true)
        val line = ResourceRequestLineItem(
            lineItemId = 4,
            requestId = 10,
            unitPrice = BigDecimal("10.00"),
            quantity = 10,
            approvedQuantity = 7,
            fulfillmentPreference = FulfillmentPreference.EXTERNAL.name,
        )

        ExternalBudgetHandler(budget, audit).handle(
            LineDecision(
                requestId = 10,
                actorId = 6,
                line = line,
                approved = true,
                comment = "Approved for the project",
                budgetCost = BigDecimal("30.00"),
            ),
        )

        verify(budget).tryCommit(2, date, BigDecimal("30.00"))
        verify(budget, never()).lineCost(4)
    }
}
