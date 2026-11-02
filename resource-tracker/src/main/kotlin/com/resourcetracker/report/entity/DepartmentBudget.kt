package com.resourcetracker.report.entity

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.LocalDate

@Entity
@Table(name = "department_budgets")
data class DepartmentBudget(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Int? = null,

    @Column(name = "department_id", nullable = false)
    val departmentId: Int,

    @Column(name = "period_start", nullable = false)
    val periodStart: LocalDate,

    @Column(name = "period_end", nullable = false)
    val periodEnd: LocalDate,

    @Column(name = "allocated_amount", nullable = false, precision = 19, scale = 2)
    var allocatedAmount: BigDecimal,

    @Column(nullable = false, length = 3)
    val currency: String,

    @Column(name = "created_by", nullable = false)
    val createdBy: String,

    @Column(name = "updated_by", nullable = false)
    var updatedBy: String
)