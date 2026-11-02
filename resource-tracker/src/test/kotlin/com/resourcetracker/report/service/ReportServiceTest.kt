package com.resourcetracker.report.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class ReportServiceTest {

    @Test
    fun `report totals are the sum of committed values`() {
        val figures = BudgetCalculator.calculate(
            allocated = BigDecimal("100.00"),
            committed = BigDecimal("40.00"),
            spent = BigDecimal("10.00")
        )

        assertEquals("100.00", figures.allocated.toPlainString())
        assertEquals("40.00", figures.committed.toPlainString())
        assertEquals("10.00", figures.spent.toPlainString())
        assertEquals("60.00", figures.remaining.toPlainString())
        assertEquals("40.00", figures.utilisationPercent!!.toPlainString())
    }

    @Test
    fun `utilisation percentage matches zero partial and overrun cases`() {
        val zero = BudgetCalculator.calculate(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO)
        assertEquals("0.00", zero.utilisationPercent!!.toPlainString())

        val partial = BudgetCalculator.calculate(
            allocated = BigDecimal("200.00"),
            committed = BigDecimal("40.00"),
            spent = BigDecimal("20.00")
        )
        assertEquals("20.00", partial.utilisationPercent!!.toPlainString())

        val overrun = BudgetCalculator.calculate(
            allocated = BigDecimal("200.00"),
            committed = BigDecimal("220.00"),
            spent = BigDecimal("40.00")
        )
        assertEquals("110.00", overrun.utilisationPercent!!.toPlainString())
    }
}
