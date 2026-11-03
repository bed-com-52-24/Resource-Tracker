package com.resourcetracker.budget

import com.resourcetracker.audit.AuditAction
import com.resourcetracker.audit.AuditService
import com.resourcetracker.department.Department
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.verify
import java.math.BigDecimal
import java.time.LocalDate
import java.util.Optional

class BudgetServiceTest {
    private val budgetRepository = mock(BudgetRepository::class.java)
    private val commitmentRepository = mock(BudgetCommitmentRepository::class.java)
    private val auditService = mock(AuditService::class.java)
    private val service = BudgetService(budgetRepository, commitmentRepository, auditService)

    private val department = Department(id = 7, name = "Engineering")

    @Test
    fun `remaining equals amount minus active commitments`() {
        val budget = Budget(
            id = 11L,
            department = department,
            periodType = BudgetPeriodType.MONTHLY,
            periodStart = LocalDate.of(2026, 11, 1),
            periodEnd = LocalDate.of(2026, 11, 30),
            amount = BigDecimal("1000.00"),
            active = true,
        )

        `when`(budgetRepository.findActiveBudgetForDate(7, LocalDate.of(2026, 11, 15))).thenReturn(Optional.of(budget))
        `when`(commitmentRepository.sumActiveAmountByBudgetId(11L)).thenReturn(BigDecimal("250.00"))

        assertEquals(BigDecimal("750.00"), service.remaining(department, LocalDate.of(2026, 11, 15)))
    }

    @Test
    fun `would exceed returns true when amount is above remaining`() {
        val budget = Budget(
            id = 12L,
            department = department,
            periodType = BudgetPeriodType.MONTHLY,
            periodStart = LocalDate.of(2026, 11, 1),
            periodEnd = LocalDate.of(2026, 11, 30),
            amount = BigDecimal("500.00"),
            active = true,
        )

        `when`(budgetRepository.findActiveBudgetForDate(7, LocalDate.of(2026, 11, 15))).thenReturn(Optional.of(budget))
        `when`(commitmentRepository.sumActiveAmountByBudgetId(12L)).thenReturn(BigDecimal("350.00"))

        assertTrue(service.wouldExceed(department, BigDecimal("200.00"), LocalDate.of(2026, 11, 15)))
        assertFalse(service.wouldExceed(department, BigDecimal("100.00"), LocalDate.of(2026, 11, 15)))
    }

    @Test
    fun `override without a comment is refused`() {
        val budget = Budget(
            id = 13L,
            department = department,
            periodType = BudgetPeriodType.MONTHLY,
            periodStart = LocalDate.of(2026, 11, 1),
            periodEnd = LocalDate.of(2026, 11, 30),
            amount = BigDecimal("500.00"),
            active = true,
        )

        `when`(budgetRepository.findActiveBudgetForDate(7, LocalDate.of(2026, 11, 15))).thenReturn(Optional.of(budget))
        `when`(commitmentRepository.sumActiveAmountByBudgetId(13L)).thenReturn(BigDecimal("400.00"))

        val error = assertThrows<IllegalArgumentException> {
            service.commit(request = BudgetRequestStub(requestId = 99, department = department), amount = BigDecimal("120.00"), overrideComment = null)
        }

        assertEquals("Override requires a reason with at least 10 characters", error.message)
    }

    @Test
    fun `override with comment is recorded and audited`() {
        val budget = Budget(
            id = 14L,
            department = department,
            periodType = BudgetPeriodType.MONTHLY,
            periodStart = LocalDate.of(2026, 11, 1),
            periodEnd = LocalDate.of(2026, 11, 30),
            amount = BigDecimal("500.00"),
            active = true,
        )

        `when`(budgetRepository.findActiveBudgetForDate(7, LocalDate.of(2026, 11, 15))).thenReturn(Optional.of(budget))
        `when`(commitmentRepository.sumActiveAmountByBudgetId(14L)).thenReturn(BigDecimal("400.00"))
        `when`(commitmentRepository.save(org.mockito.ArgumentMatchers.any(BudgetCommitment::class.java))).thenAnswer { it.arguments[0] as BudgetCommitment }

        val request = BudgetRequestStub(requestId = 100, department = department)
        val commitment = service.commit(request, BigDecimal("120.00"), "Budget override reason")

        assertEquals(BigDecimal("120.00"), commitment.amount)
        val auditCaptor = argumentCaptor<com.resourcetracker.audit.AuditRecord>()
        verify(auditService).record(auditCaptor.capture())
        assertEquals(AuditAction.BUDGET_OVERRIDE_USED, auditCaptor.firstValue.action)
    }
}

private data class BudgetRequestStub(
    val requestId: Int,
    val department: Department,
)
