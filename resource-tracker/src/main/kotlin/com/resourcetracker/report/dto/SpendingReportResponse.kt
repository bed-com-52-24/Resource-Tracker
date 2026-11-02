package com.resourcetracker.report.dto

import java.math.BigDecimal

data class SpendingReportResponse(
    val departments: List<DepartmentSpending>,
    val totals: SpendingTotals
)

data class DepartmentSpending(
    val departmentId: Int,
    val departmentName: String,
    val externalCommitted: BigDecimal,
    val externalSpent: BigDecimal,
    val internalUsageCost: BigDecimal,
    val totalCost: BigDecimal,
    val requestCount: Int
)

data class SpendingTotals(
    val externalCommitted: BigDecimal,
    val externalSpent: BigDecimal,
    val internalUsageCost: BigDecimal,
    val totalCost: BigDecimal,
    val requestCount: Int
)