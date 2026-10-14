package com.resourcetracker.approval

import jakarta.persistence.*

/**
 * The part of resource_requests that the approval workflow needs.
 * It maps only some columns, which is allowed: Hibernate checks that the
 * columns it uses exist, and ignores the rest.
 */
@Entity(name = "ApprovalRequest")
@Table(name = "resource_requests")
class ApprovalRequest(

    @Id
    @Column(name = "request_id")
    val id: Int = 0,

    @Column(name = "assigned_manager_id", nullable = false)
    val assignedManagerId: Int,

    @Column(name = "requester_id", nullable = false)
    val requesterId: Int,

    @Column(name = "status", nullable = false, length = 20)
    var status: String
)