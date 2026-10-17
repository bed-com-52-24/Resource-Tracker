package com.resourcetracker.routing_rules

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "routing_rule_audit")
class AuditEntry(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "audit_id")
    var id: Long? = null,

    @Column(name = "routing_rule_id", nullable = false)
    var entityId: Int,

    @Column(name = "actor", nullable = false, length = 50)
    var actor: String,

    @Column(nullable = false)
    var action: String,

    @Column(name = "old_values", columnDefinition = "json")
    var oldValues: String? = null,

    @Column(name = "new_values", columnDefinition = "json")
    var newValues: String? = null,

    @Column(name = "changed_at", nullable = false)
    var occurredAt: LocalDateTime = LocalDateTime.now()
)