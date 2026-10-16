package com.resourcetracker.request.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.LocalDateTime

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

    @Column(name = "manufacturer", nullable = false, length = 150)
    var manufacturer: String = "",

    @Column(name = "unit_price", nullable = false, precision = 12, scale = 2)
    var unitPrice: BigDecimal = BigDecimal.ZERO,

    @Column(name = "quantity", nullable = false)
    var quantity: Int = 1,

    @Column(name = "batch_number", length = 100)
    var batchNumber: String? = null,

    @Column(name = "vendor_id", nullable = false)
    var vendorId: Int = 0,

    @Column(name = "department_id", nullable = false)
    var departmentId: Int = 0,

    @Column(name = "project_id", nullable = false)
    var projectId: Int = 0,

    @Column(name = "team_id", nullable = false)
    var teamId: Int = 0,

    @Column(name = "requester_id", nullable = false)
    var requesterId: Int = 0,

    @Column(name = "assigned_manager_id", nullable = false)
    var assignedManagerId: Int = 0,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    var status: RequestStatus = RequestStatus.PENDING,

    @Column(name = "justification", length = 500)
    var justification: String? = null,

    @Column(name = "total_amount", nullable = false, precision = 19, scale = 2)
    var totalAmount: BigDecimal = BigDecimal.ZERO,

    @Column(name = "edited_after_query", nullable = false)
    var editedAfterQuery: Boolean = false,

    @Column(name = "queried_at")
    var queriedAt: LocalDateTime? = null,

    @Column(name = "created_at", insertable = false, updatable = false)
    var createdAt: LocalDateTime? = null,

    @Column(name = "updated_at", insertable = false, updatable = false)
    var updatedAt: LocalDateTime? = null
)