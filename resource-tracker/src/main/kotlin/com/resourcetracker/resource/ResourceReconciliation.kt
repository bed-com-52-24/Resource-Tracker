package com.resourcetracker.resource

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.springframework.data.jpa.repository.JpaRepository
import java.time.LocalDateTime

@Entity
@Table(name = "resource_reconciliations")
class ResourceReconciliation(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "reconciliation_id")
    var id: Int = 0,
    @Column(name = "resource_id", nullable = false)
    var resourceId: Int,
    @Column(name = "system_quantity", nullable = false)
    var systemQuantity: Int,
    @Column(name = "physical_count", nullable = false)
    var physicalCount: Int,
    @Column(nullable = false)
    var variance: Int,
    @Column(name = "adjustment_applied", nullable = false)
    var adjustmentApplied: Boolean,
    @Column(nullable = false, length = 500)
    var reason: String,
    @Column(name = "actor_id", nullable = false)
    var actorId: Int,
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    var createdAt: LocalDateTime? = null,
)

interface ResourceReconciliationRepository : JpaRepository<ResourceReconciliation, Int> {
    fun findAllByResourceIdOrderByCreatedAtAscIdAsc(resourceId: Int): List<ResourceReconciliation>
}
