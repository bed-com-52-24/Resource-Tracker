package com.resourcetracker.approval

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Component
import java.math.BigDecimal

/** A department's budget and how much of it is already committed. */
data class BudgetStatus(val departmentId: Int, val budget: BigDecimal, val committed: BigDecimal) {
    val remaining: BigDecimal get() = budget - committed
}

/** Everything the approval code needs from the budget. */
interface BudgetPort {
    fun lineCost(lineId: Int): BigDecimal
    fun departmentOfRequest(requestId: Int): Int?
    fun status(departmentId: Int): BudgetStatus?

    /** Commits the amount only if it fits in the remaining budget. Returns false if it does not. */
    fun tryCommit(departmentId: Int, amount: BigDecimal): Boolean

    /** Commits the amount even if it exceeds the budget (manager override). */
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
            RowMapper<Int> { rs, _ -> rs.getInt(1) }, requestId
        ).firstOrNull()

    override fun status(departmentId: Int): BudgetStatus? =
        jdbc.query(
            "SELECT department_id, budget_amount, committed_amount FROM department_budget WHERE department_id = ?",
            statusMapper, departmentId
        ).firstOrNull()

    /** One UPDATE that only succeeds while enough budget remains, so two approvals cannot both overspend. */
    override fun tryCommit(departmentId: Int, amount: BigDecimal): Boolean =
        jdbc.update(
            "UPDATE department_budget SET committed_amount = committed_amount + ? " +
                    "WHERE department_id = ? AND (budget_amount - committed_amount) >= ?",
            amount, departmentId, amount
        ) == 1

    override fun forceCommit(departmentId: Int, amount: BigDecimal) {
        jdbc.update(
            "UPDATE department_budget SET committed_amount = committed_amount + ? WHERE department_id = ?",
            amount, departmentId
        )
    }
}