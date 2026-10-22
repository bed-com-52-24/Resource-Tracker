package com.resourcetracker.request.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime

@Entity
@Table(name = "request_history")
class RequestHistory(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "history_id")
    var id: Int = 0,
    @Column(name = "request_id", nullable = false)
    var requestId: Int,
    @Column(name = "event_type", nullable = false, length = 30)
    var eventType: String,
    @Column(name = "from_status", length = 20)
    var fromStatus: String? = null,
    @Column(name = "to_status", length = 20)
    var toStatus: String? = null,
    @Column(name = "actor_id", nullable = false)
    var actorId: Int,
    @Column(name = "line_item_id")
    var lineItemId: Int? = null,
    @Column(name = "quantity")
    var quantity: Int? = null,
    @Column(name = "comment", length = 1000)
    var comment: String? = null,
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    var createdAt: LocalDateTime? = null,
)
