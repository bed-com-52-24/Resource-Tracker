package com.resourcetracker.approval

import jakarta.persistence.*

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
    var status: String,

    /** Optimistic lock: if two people change the same row, the second save fails. */
    @Version
    @Column(name = "version", nullable = false)
    var version: Long = 0
)