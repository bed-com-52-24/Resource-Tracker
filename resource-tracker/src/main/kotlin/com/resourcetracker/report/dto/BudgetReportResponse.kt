package com.resourcetracker.report.dto

import java.math.BigDecimal
import java.time.LocalDate

data class BudgetReportResponse(
    val from: LocalDate,
    val to: LocalDate,
    val departments: List<DepartmentBudgetReport>
)

data class DepartmentBudgetReport(
    val departmentId: Int,
    val departmentName: String,
    val allocated: BigDecimal,
    val committed: BigDecimal,
    val spent: BigDecimal,
    val remaining: BigDecimal,
    val utilisationPercent: BigDecimal?,
    val overrun: BigDecimal,
    val currency: String
)