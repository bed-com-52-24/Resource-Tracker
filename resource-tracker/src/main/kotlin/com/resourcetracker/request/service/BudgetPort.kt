package com.resourcetracker.request.service

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.time.LocalDate

data class BudgetStatus(val departmentId: Int, val budget: BigDecimal, val committed: BigDecimal) {
    val remaining: BigDecimal get() = budget - committed
}

interface BudgetPort {
    fun lineCost(lineId: Int): BigDecimal
    fun departmentOfRequest(requestId: Int): Int?
    fun requestDateOfRequest(requestId: Int): LocalDate?
    fun status(departmentId: Int, date: LocalDate): BudgetStatus?
    fun tryCommit(departmentId: Int, date: LocalDate, amount: BigDecimal): Boolean
    fun forceCommit(departmentId: Int, date: LocalDate, amount: BigDecimal)
    fun release(departmentId: Int, date: LocalDate, amount: BigDecimal)
}

@Component
class JdbcBudgetPort(private val jdbc: JdbcTemplate) : BudgetPort {
    private val statusMapper = RowMapper<BudgetStatus> { rs, _ ->
        BudgetStatus(rs.getInt("department_id"), rs.getBigDecimal("budget_amount"), rs.getBigDecimal("committed_amount"))
    }

    override fun lineCost(lineId: Int): BigDecimal =
        jdbc.queryForObject(
            "SELECT unit_price * COALESCE(approved_quantity, quantity) " +
                "FROM resource_request_line_items WHERE line_item_id = ?",
            BigDecimal::class.java,
            lineId,
        ) ?: BigDecimal.ZERO

    override fun departmentOfRequest(requestId: Int): Int? =
        jdbc.query(
            "SELECT department_id FROM resource_requests WHERE request_id = ?",
            RowMapper<Int> { rs, _ -> rs.getInt(1) },
            requestId,
        ).firstOrNull()

    override fun requestDateOfRequest(requestId: Int): LocalDate? =
        jdbc.query(
            "SELECT required_date FROM resource_requests WHERE request_id = ?",
            RowMapper<LocalDate?> { rs, _ -> rs.getDate(1)?.toLocalDate() },
            requestId,
        ).firstOrNull()

    override fun status(departmentId: Int, date: LocalDate): BudgetStatus? =
        jdbc.query(
            "SELECT department_id, amount AS budget_amount, committed_amount FROM budget " +
                "WHERE department_id = ? AND active = TRUE AND period_start <= ? AND period_end >= ?",
            statusMapper,
            departmentId,
            java.sql.Date.valueOf(date),
            java.sql.Date.valueOf(date),
        ).firstOrNull()

    override fun tryCommit(departmentId: Int, date: LocalDate, amount: BigDecimal): Boolean =
        jdbc.update(
            "UPDATE budget SET committed_amount = committed_amount + ? " +
                "WHERE department_id = ? AND active = TRUE AND period_start <= ? AND period_end >= ? " +
                "AND (amount - committed_amount) >= ?",
            amount,
            departmentId,
            java.sql.Date.valueOf(date),
            java.sql.Date.valueOf(date),
            amount,
        ) == 1

    override fun forceCommit(departmentId: Int, date: LocalDate, amount: BigDecimal) {
        jdbc.update(
            "UPDATE budget SET committed_amount = committed_amount + ? " +
                "WHERE department_id = ? AND active = TRUE AND period_start <= ? AND period_end >= ?",
            amount,
            departmentId,
            java.sql.Date.valueOf(date),
            java.sql.Date.valueOf(date),
        )
    }

    override fun release(departmentId: Int, date: LocalDate, amount: BigDecimal) {
        jdbc.update(
            "UPDATE budget SET committed_amount = GREATEST(committed_amount - ?, 0) " +
                "WHERE department_id = ? AND active = TRUE AND period_start <= ? AND period_end >= ?",
            amount,
            departmentId,
            java.sql.Date.valueOf(date),
            java.sql.Date.valueOf(date),
        )
    }
}
