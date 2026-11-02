package com.resourcetracker.report.service

import com.resourcetracker.request.entity.ProcurementStatus
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

data class ProcurementRow(
    val requestId: Int,
    val departmentId: Int,
    val status: ProcurementStatus,
    val cost: BigDecimal,
    val authorizedAt: LocalDateTime?,
    val receivedAt: LocalDateTime?
)

data class DepartmentExternal(
    val departmentId: Int,
    val committed: BigDecimal,
    val spent: BigDecimal,
    val requestCount: Int
)

object SpendAggregator {

    private val committedStatuses = setOf(
        ProcurementStatus.AUTHORISED,
        ProcurementStatus.ORDERED
    )

    private val spentStatuses = setOf(
        ProcurementStatus.RECEIVED,
        ProcurementStatus.ACCEPTED
    )

    fun aggregate(
        rows: List<ProcurementRow>,
        from: LocalDate?,
        to: LocalDate?
    ): Map<Int, DepartmentExternal> {

        return rows
            .filter { inPeriod(it, from, to) }
            .groupBy { it.departmentId }
            .mapValues { (departmentId, records) ->

                DepartmentExternal(
                    departmentId = departmentId,

                    committed = records
                        .filter { it.status in committedStatuses }
                        .sumOf { it.cost },

                    spent = records
                        .filter { it.status in spentStatuses }
                        .sumOf { it.cost },

                    requestCount = records
                        .map { it.requestId }
                        .distinct()
                        .size
                )
            }
    }

    private fun inPeriod(
        row: ProcurementRow,
        from: LocalDate?,
        to: LocalDate?
    ): Boolean {

        if (from == null && to == null) return true

        val date = when {
            row.status in committedStatuses -> row.authorizedAt
            row.status in spentStatuses -> row.receivedAt
            else -> null
        }?.toLocalDate() ?: return false

        return (from == null || !date.isBefore(from)) &&
               (to == null || !date.isAfter(to))
    }
}