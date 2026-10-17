package com.resourcetracker.inventory

import com.resourcetracker.resource.Resource
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.PreUpdate
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import jakarta.persistence.Version
import java.time.Instant

@Entity
@Table(
    name = "resource_inventory",
    uniqueConstraints = [UniqueConstraint(name = "uq_resource_inventory_resource_location", columnNames = ["resource_id", "location"])],
)
class Inventory(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "resource_id", nullable = false)
    var resource: Resource,

    @Column(nullable = false, length = 120)
    var location: String,

    @Column(name = "total_quantity", nullable = false)
    var totalQuantity: Int,

    @Column(name = "reserved_quantity", nullable = false)
    var reservedQuantity: Int = 0,

    @Column(name = "issued_quantity", nullable = false)
    var issuedQuantity: Int = 0,

    @Column(name = "reorder_level", nullable = false)
    var reorderLevel: Int = 0,

    @Version
    @Column(nullable = false)
    var version: Long = 0,

    @Column(name = "last_updated", nullable = false)
    var lastUpdated: Instant = Instant.now(),
) {
    val availableQuantity: Int
        get() = totalQuantity - reservedQuantity - issuedQuantity

    @PreUpdate
    fun touch() {
        lastUpdated = Instant.now()
    }

    override fun equals(other: Any?) = other is Inventory && id != 0L && id == other.id
    override fun hashCode() = id.hashCode()
}
