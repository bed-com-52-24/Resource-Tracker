package com.resourcetracker.report.service

import com.resourcetracker.report.dto.*
import com.resourcetracker.report.repository.DepartmentBudgetRepository
import com.resourcetracker.report.repository.ReportQueryRepository
import com.resourcetracker.user.UserRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import java.math.BigDecimal
import java.time.LocalDate

@Service
class ReportService(
    private val users: UserRepository,
    private val queries: ReportQueryRepository,
    private val budgets: DepartmentBudgetRepository
) {

    fun spending(
        actorId: Int,
        requestedDepartmentId: Int?,
        from: LocalDate?,
        to: LocalDate?
    ): SpendingReportResponse {

        validatePeriod(from, to)

        val actor = users.findById(actorId)
            .orElseThrow {
                ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "User not found"
                )
            }

        val departmentId = ReportScope.resolve(
            actor.role.name,
            actor.department?.id,
            requestedDepartmentId
        )

        val rows = queries.procurementRows(departmentId)

        val external = SpendAggregator.aggregate(
            rows,
            from,
            to
        )

        val names = queries.departmentNames()

        val departments = external.map { (id, figures) ->

            // Replace with the actual internal usage cost source
            // when that source is available in the project.
            val internalCost = BigDecimal.ZERO

            val total = figures.committed +
                        internalCost

            DepartmentSpending(
                departmentId = id,
                departmentName = names[id] ?: "Unknown",
                externalCommitted = figures.committed,
                externalSpent = figures.spent,
                internalUsageCost = internalCost,
                totalCost = total,
                requestCount = figures.requestCount
            )
        }.sortedBy { it.departmentName }

        val totals = SpendingTotals(
            externalCommitted = departments.sumOf {
                it.externalCommitted
            },
            externalSpent = departments.sumOf {
                it.externalSpent
            },
            internalUsageCost = departments.sumOf {
                it.internalUsageCost
            },
            totalCost = departments.sumOf {
                it.totalCost
            },
            requestCount = departments.sumOf {
                it.requestCount
            }
        )

        return SpendingReportResponse(
            departments = departments,
            totals = totals
        )
    }

    fun budget(
        actorId: Int,
        requestedDepartmentId: Int?,
        from: LocalDate,
        to: LocalDate
    ): BudgetReportResponse {

        validatePeriod(from, to)

        val actor = users.findById(actorId)
            .orElseThrow {
                ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "User not found"
                )
            }

        val departmentId = ReportScope.resolve(
            actor.role.name,
            actor.department?.id,
            requestedDepartmentId
        )

        val rows = queries.procurementRows(departmentId)

        val external = SpendAggregator.aggregate(
            rows,
            from,
            to
        )

        val allocations = budgets.findOverlapping(from, to)
            .filter {
                departmentId == null ||
                it.departmentId == departmentId
            }
            .groupBy { it.departmentId }

        val names = queries.departmentNames()

        val departmentIds = (
            external.keys + allocations.keys
        ).distinct().sorted()

        val departments = departmentIds.map { id ->

            val allocatedBudgets = allocations[id].orEmpty()

            val allocated = allocatedBudgets.fold(
                BigDecimal.ZERO
            ) { total, budget ->
                total + budget.allocatedAmount
            }

            val currency = allocatedBudgets
                .firstOrNull()?.currency ?: "MWK"

            val usage = external[id]

            val figures = BudgetCalculator.calculate(
                allocated = allocated,
                committed = usage?.committed ?: BigDecimal.ZERO,
                spent = usage?.spent ?: BigDecimal.ZERO
            )

            DepartmentBudgetReport(
                departmentId = id,
                departmentName = names[id] ?: "Unknown",
                allocated = figures.allocated,
                committed = figures.committed,
                spent = figures.spent,
                remaining = figures.remaining,
                utilisationPercent = figures.utilisationPercent,
                overrun = figures.overrun,
                currency = currency
            )
        }

        return BudgetReportResponse(
            from = from,
            to = to,
            departments = departments
        )
    }

    private fun validatePeriod(
        from: LocalDate?,
        to: LocalDate?
    ) {
        if (from != null && to != null && from.isAfter(to)) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "from must not be after to"
            )
        }
    }
}