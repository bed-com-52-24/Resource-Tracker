package com.resourcetracker.audit

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.Immutable
import java.time.Instant

@Entity
@Immutable
@Table(name = "audit_entry")
class AuditEntry(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "occurred_at", nullable = false, insertable = true, updatable = false)
    val occurredAt: Instant? = null,

    @Column(name = "actor_id")
    val actorId: Int? = null,

    @Column(name = "actor_role", length = 64)
    val actorRole: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 48)
    val action: AuditAction = AuditAction.REQUEST_RECEIVED,

    @Column(name = "item_type", nullable = false, length = 64)
    val itemType: String = "",

    @Column(name = "item_id", nullable = false, length = 128)
    val itemId: String = "",

    @Column(name = "previous_state", columnDefinition = "TEXT")
    val previousState: String? = null,

    @Column(name = "new_state", columnDefinition = "TEXT")
    val newState: String? = null,

    @Column(name = "comment", length = 500)
    val comment: String? = null,

    @Column(name = "ip_address", length = 45)
    val ipAddress: String? = null,

    @Column(name = "user_agent", length = 512)
    val userAgent: String? = null,

    @Column(name = "changed_fields", columnDefinition = "TEXT")
    val changedFields: String? = null,

    @Column(name = "correlation_id", nullable = false, length = 64)
    val correlationId: String = "",
)
