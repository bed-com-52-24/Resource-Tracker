package com.resourcetracker.resource

import com.resourcetracker.department.Department
import com.resourcetracker.vendor.Vendor
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.PreUpdate
import jakarta.persistence.Table
import jakarta.persistence.Version
import java.time.Instant
import java.util.UUID

@Entity
@Table(
    name = "resources",
    indexes = [
        Index(name = "idx_resources_type_active", columnList = "type, is_active"),
        Index(name = "idx_resources_department", columnList = "department_id")
    ]
)
class Resource(
    @Id
    @Column(columnDefinition = "BINARY(16)")
    var id: UUID = UUID.randomUUID(),

    @Column(nullable = false, unique = true, updatable = false, length = 20)
    var code: String,

    @Column(nullable = false, length = 120)
    var name: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var type: ResourceType,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var category: ResourceCategory,

    @Column(length = 500)
    var description: String? = null,

    @Column(name = "unit_of_measure", nullable = false, length = 50)
    var unitOfMeasure: String,

    @Column(name = "is_active", nullable = false)
    var active: Boolean = true,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id")
    var department: Department? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vendor_id")
    var preferredVendor: Vendor? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),

    @Version
    @Column(nullable = false)
    var version: Long = 0
) {
    @PreUpdate
    fun touch() {
        updatedAt = Instant.now()
    }

    override fun equals(other: Any?) = other is Resource && id == other.id
    override fun hashCode() = id.hashCode()
}
