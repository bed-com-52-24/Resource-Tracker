package com.resourcetracker.report.dto

import java.math.BigDecimal
import java.time.LocalDate

data class UpsertBudgetRequest(
    val departmentId: Int,
    val periodStart: LocalDate,
    val periodEnd: LocalDate,
    val allocatedAmount: BigDecimal,
    val currency: String,
    val overrideBudget: Boolean = false,
    val reason: String? = null
)