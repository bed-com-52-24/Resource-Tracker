package com.resourcetracker.budget

import com.resourcetracker.department.Department
import jakarta.persistence.*
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

enum class BudgetPeriodType { MONTHLY, QUARTERLY, ANNUAL }
enum class BudgetCommitmentStatus { ACTIVE, RELEASED }

@Entity
@Table(
    name = "budget",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_budget_department_period", columnNames = ["department_id", "period_type", "period_start", "period_end"]),
    ],
    indexes = [
        Index(name = "idx_budget_department", columnList = "department_id"),
        Index(name = "idx_budget_period", columnList = "period_start, period_end"),
    ],
)
class Budget(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long = 0,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "department_id", nullable = false)
    var department: Department,

    @Enumerated(EnumType.STRING)
    @Column(name = "period_type", nullable = false, length = 10)
    var periodType: BudgetPeriodType = BudgetPeriodType.MONTHLY,

    @Column(name = "period_start", nullable = false)
    var periodStart: LocalDate,

    @Column(name = "period_end", nullable = false)
    var periodEnd: LocalDate,

    @Column(name = "amount", nullable = false, precision = 19, scale = 2)
    var amount: BigDecimal = BigDecimal.ZERO,

    @Column(name = "committed_amount", nullable = false, precision = 19, scale = 2)
    var committedAmount: BigDecimal = BigDecimal.ZERO,

    @Column(name = "active", nullable = false)
    var active: Boolean = true,

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    var createdAt: LocalDateTime? = null,

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    var updatedAt: LocalDateTime? = null,
) {
    fun remaining(): BigDecimal = amount - committedAmount

    fun contains(date: LocalDate): Boolean = !date.isBefore(periodStart) && !date.isAfter(periodEnd)
}

@Entity
@Table(
    name = "budget_commitment",
    indexes = [Index(name = "idx_budget_commitment_budget", columnList = "budget_id")],
)
class BudgetCommitment(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long = 0,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "budget_id", nullable = false)
    var budget: Budget,

    @Column(name = "request_id", nullable = false)
    var requestId: Int = 0,

    @Column(name = "amount", nullable = false, precision = 19, scale = 2)
    var amount: BigDecimal = BigDecimal.ZERO,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    var status: BudgetCommitmentStatus = BudgetCommitmentStatus.ACTIVE,

    @Column(name = "comment", length = 500)
    var comment: String? = null,

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    var createdAt: LocalDateTime? = null,
)
