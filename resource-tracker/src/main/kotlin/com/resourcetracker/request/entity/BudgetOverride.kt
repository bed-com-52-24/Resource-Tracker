package com.resourcetracker.request.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.CreationTimestamp
import java.math.BigDecimal
import java.time.LocalDateTime

@Entity
@Table(name = "budget_overrides")
class BudgetOverride(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "override_id") var id: Int = 0,
    @Column(name = "request_id", nullable = false) var requestId: Int,
    @Column(name = "line_item_id", nullable = false) var lineItemId: Int,
    @Column(name = "department_id", nullable = false) var departmentId: Int,
    @Column(name = "requested_by", nullable = false) var requestedBy: Int,
    @Column(nullable = false, precision = 12, scale = 2) var amount: BigDecimal,
    @Column(name = "requested_quantity", nullable = false) var requestedQuantity: Int,
    @Column(nullable = false, length = 500) var reason: String,
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 12) var status: BudgetOverrideStatus = BudgetOverrideStatus.PENDING,
    @Column(name = "decided_by") var decidedBy: Int? = null,
    @Column(name = "decision_comment", length = 500) var decisionComment: String? = null,
    @Column(name = "decided_at") var decidedAt: LocalDateTime? = null,
    @CreationTimestamp @Column(name = "created_at", nullable = false, updatable = false) var createdAt: LocalDateTime? = null,
)
