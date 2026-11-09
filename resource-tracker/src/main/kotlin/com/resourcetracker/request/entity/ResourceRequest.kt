package com.resourcetracker.request.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Version
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp
import java.math.BigDecimal
import java.time.LocalDateTime
import java.time.LocalDate

@Entity
@Table(name = "resource_requests")
class ResourceRequest(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "request_id")
    var requestId: Int? = null,

    @Column(name = "request_number", nullable = false, unique = true, length = 20)
    var requestNumber: String = "",

    @Column(name = "item_description", nullable = false, length = 255)
    var itemDescription: String = "",

    @Column(name = "manufacturer", length = 150)
    var manufacturer: String? = null,

    @Column(name = "unit_price", nullable = false, precision = 12, scale = 2)
    var unitPrice: BigDecimal = BigDecimal.ZERO,

    @Column(name = "quantity", nullable = false)
    var quantity: Int = 1,

    @Column(name = "batch_number", length = 100)
    var batchNumber: String? = null,

    @Column(name = "vendor_id")
    var vendorId: Int? = null,

    @Column(name = "department_id", nullable = false)
    var departmentId: Int = 0,

    @Column(name = "project_id", nullable = false)
    var projectId: Int = 0,

    @Column(name = "team_id", nullable = false)
    var teamId: Int = 0,

    @Column(name = "requester_id", nullable = false)
    var requesterId: Int = 0,

    @Column(name = "assigned_manager_id")
    var assignedManagerId: Int? = null,

    @Column(name = "needs_reassignment", nullable = false)
    var needsReassignment: Boolean = false,

    @Column(name = "required_date")
    var requiredDate: LocalDate? = null,

    @Column(name = "decided_by")
    var decidedBy: Int? = null,

    @Column(name = "decided_at")
    var decidedAt: LocalDateTime? = null,

    @Column(name = "decision_reason", length = 500)
    var decisionReason: String? = null,

    @Column(name = "cancelled_by")
    var cancelledBy: Int? = null,

    @Column(name = "cancelled_at")
    var cancelledAt: LocalDateTime? = null,

    @Column(name = "cancel_reason", length = 500)
    var cancelReason: String? = null,

    @Column(name = "unique_key", length = 64, unique = true)
    var uniqueKey: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    var status: RequestStatus = RequestStatus.PENDING,

    @Enumerated(EnumType.STRING)
    @Column(name = "priority", nullable = false, length = 10)
    var priority: RequestPriority = RequestPriority.MEDIUM,

    @Column(name = "justification", length = 500)
    var justification: String? = null,

    @Column(name = "total_amount", nullable = false, precision = 19, scale = 2)
    var totalAmount: BigDecimal = BigDecimal.ZERO,

    @Column(name = "edited_after_query", nullable = false)
    var editedAfterQuery: Boolean = false,

    @Column(name = "queried_at")
    var queriedAt: LocalDateTime? = null,

    @CreationTimestamp
    @Column(name = "created_at", nullable = false)
    var createdAt: LocalDateTime? = null,

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime? = null,

    @Version
    @Column(name = "version", nullable = false)
    var version: Long = 0,
)
