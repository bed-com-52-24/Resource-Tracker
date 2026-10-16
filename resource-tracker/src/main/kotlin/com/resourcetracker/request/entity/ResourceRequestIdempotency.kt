
package com.resourcetracker.request.entity

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(
    name = "resource_request_idempotency",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uq_request_idempotency_key",
            columnNames = ["idempotency_key"]
        )
    ]
)
class ResourceRequestIdempotency(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "idempotency_id")
    var idempotencyId: Long? = null,

    @Column(name = "idempotency_key", nullable = false, length = 64)
    var idempotencyKey: String = "",

    @Column(name = "requester_id", nullable = false)
    var requesterId: Int = 0,

    @Column(name = "request_id")
    var requestId: Int? = null,

    @Column(name = "processing", nullable = false)
    var processing: Boolean = true,

    @Column(name = "created_at", insertable = false, updatable = false)
    var createdAt: LocalDateTime? = null
)