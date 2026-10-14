package com.resourcetracker.inventory

import com.resourcetracker.user.User
import jakarta.persistence.*
import java.time.Instant

@Entity
@Table(name = "inventory_audit_logs")
class InventoryAuditLog(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0,
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "inventory_id", nullable = false)
    var inventory: Inventory,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_id")
    var actor: User? = null,
    @Column(nullable = false, length = 40)
    var action: String,
    @Column(nullable = false, length = 500)
    var reason: String,
    @Column(name = "previous_total_quantity", nullable = false)
    var previousTotalQuantity: Int,
    @Column(name = "previous_reserved_quantity", nullable = false)
    var previousReservedQuantity: Int,
    @Column(name = "previous_issued_quantity", nullable = false)
    var previousIssuedQuantity: Int,
    @Column(name = "new_total_quantity", nullable = false)
    var newTotalQuantity: Int,
    @Column(name = "new_reserved_quantity", nullable = false)
    var newReservedQuantity: Int,
    @Column(name = "new_issued_quantity", nullable = false)
    var newIssuedQuantity: Int,
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant = Instant.now(),
)
