package com.resourcetracker.report.entity

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.LocalDateTime

@Entity
@Table(name = "department_budget_audits")
data class DepartmentBudgetAudit(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Int? = null,

    @Column(name = "budget_id", nullable = false)
    val budgetId: Int,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val action: BudgetAuditAction,

    @Column(name = "previous_amount", precision = 19, scale = 2)
    val previousAmount: BigDecimal?,

    @Column(name = "new_amount", nullable = false, precision = 19, scale = 2)
    val newAmount: BigDecimal,

    @Column
    val reason: String?,

    @Column(name = "changed_by", nullable = false)
    val changedBy: String,

    @Column(name = "changed_at", nullable = false)
    val changedAt: LocalDateTime
)