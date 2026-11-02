package com.resourcetracker.report.service

import java.math.BigDecimal
import java.math.RoundingMode

data class BudgetFigures(
    val allocated: BigDecimal,
    val committed: BigDecimal,
    val spent: BigDecimal,
    val remaining: BigDecimal,
    val utilisationPercent: BigDecimal?,
    val overrun: BigDecimal
)

object BudgetCalculator {

    fun calculate(
        allocated: BigDecimal,
        committed: BigDecimal,
        spent: BigDecimal
    ): BudgetFigures {

        val used = committed
        val remaining = allocated - used
        val overrun = (used - allocated).max(BigDecimal.ZERO)

        val utilisation = when {
            allocated.signum() == 0 && used.signum() == 0 ->
                BigDecimal.ZERO.setScale(2)

            allocated.signum() == 0 ->
                null

            else ->
                used.multiply(BigDecimal("100"))
                    .divide(allocated, 2, RoundingMode.HALF_UP)
        }

        return BudgetFigures(
            allocated = allocated,
            committed = committed,
            spent = spent,
            remaining = remaining,
            utilisationPercent = utilisation,
            overrun = overrun
        )
    }
}