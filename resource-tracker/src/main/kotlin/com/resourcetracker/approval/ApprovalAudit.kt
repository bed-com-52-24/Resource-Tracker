package com.resourcetracker.approval

import jakarta.persistence.*

/**
 * One row in audit_logs: who did what to which request, and the status
 * before and after (SRS 2.4.6.2). The timestamp column is left out on
 * purpose, because the database fills it in automatically.
 */
@Entity(name = "ApprovalAudit")
@Table(name = "audit_logs")
class ApprovalAudit(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "audit_id")
    val id: Int = 0,

    @Column(name = "request_id", nullable = false)
    val requestId: Int,

    @Column(name = "actor_id", nullable = false)
    val actorId: Int,

    @Column(name = "previous_state", length = 20)
    val previousState: String?,

    @Column(name = "new_state", nullable = false, length = 20)
    val newState: String,

    @Column(name = "action", nullable = false, length = 50)
    val action: String,

    @Column(name = "comment", length = 500)
    val comment: String?
)