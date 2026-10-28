package com.resourcetracker.request.service

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Component
import java.math.BigDecimal

data class BudgetStatus(val departmentId: Int, val budget: BigDecimal, val committed: BigDecimal) {
    val remaining: BigDecimal get() = budget - committed
}

interface BudgetPort {
    fun lineCost(lineId: Int): BigDecimal
    fun departmentOfRequest(requestId: Int): Int?
    fun status(departmentId: Int): BudgetStatus?
    fun tryCommit(departmentId: Int, amount: BigDecimal): Boolean
    fun forceCommit(departmentId: Int, amount: BigDecimal)
}

@Component
class JdbcBudgetPort(private val jdbc: JdbcTemplate) : BudgetPort {
    private val statusMapper = RowMapper<BudgetStatus> { rs, _ ->
        BudgetStatus(rs.getInt("department_id"), rs.getBigDecimal("budget_amount"), rs.getBigDecimal("committed_amount"))
    }

    override fun lineCost(lineId: Int): BigDecimal =
        jdbc.queryForObject(
            "SELECT unit_price * quantity FROM request_line_items WHERE line_id = ?",
            BigDecimal::class.java, lineId
        ) ?: BigDecimal.ZERO

    override fun departmentOfRequest(requestId: Int): Int? =
        jdbc.query(
            "SELECT department_id FROM resource_requests WHERE request_id = ?",
            RowMapper<Int> { rs, _ -> rs.getInt(1) },
            requestId,
        ).firstOrNull()

    override fun status(departmentId: Int): BudgetStatus? =
        jdbc.query(
            "SELECT department_id, budget_amount, committed_amount FROM department_budget WHERE department_id = ?",
            statusMapper,
            departmentId,
        ).firstOrNull()

    override fun tryCommit(departmentId: Int, amount: BigDecimal): Boolean =
        jdbc.update(
            "UPDATE department_budget SET committed_amount = committed_amount + ? " +
                "WHERE department_id = ? AND (budget_amount - committed_amount) >= ?",
            amount,
            departmentId,
            amount,
        ) == 1

    override fun forceCommit(departmentId: Int, amount: BigDecimal) {
        jdbc.update(
            "UPDATE department_budget SET committed_amount = committed_amount + ? WHERE department_id = ?",
            amount,
            departmentId,
        )
    }
}
