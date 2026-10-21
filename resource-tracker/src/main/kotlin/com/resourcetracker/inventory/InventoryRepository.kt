package com.resourcetracker.inventory

import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface InventoryRepository : JpaRepository<Inventory, Long> {
    interface ResourceTotals {
        fun getResourceId(): Int
        fun getReserved(): Long
        fun getIssued(): Long
        fun getAvailable(): Long
    }

    @Query("""select i.resource.id as resourceId, sum(i.reservedQuantity) as reserved,
        sum(i.issuedQuantity) as issued,
        sum(i.totalQuantity - i.reservedQuantity - i.issuedQuantity) as available
        from Inventory i where i.resource.id = :resourceId group by i.resource.id""")
    fun totalsForResource(@Param("resourceId") resourceId: Int): ResourceTotals?

    @Query("""select i.resource.id as resourceId, sum(i.reservedQuantity) as reserved,
        sum(i.issuedQuantity) as issued,
        sum(i.totalQuantity - i.reservedQuantity - i.issuedQuantity) as available
        from Inventory i where i.resource.id in :resourceIds group by i.resource.id""")
    fun totalsForResources(@Param("resourceIds") resourceIds: Collection<Int>): List<ResourceTotals>

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Inventory i join fetch i.resource r where i.resource.id = :resourceId and i.location = :location")
    fun findForUpdate(
        @Param("resourceId") resourceId: Int,
        @Param("location") location: String,
    ): Inventory?

    fun findAllByResourceIdOrderByLocation(resourceId: Int): List<Inventory>
}

interface InventoryAuditLogRepository : JpaRepository<InventoryAuditLog, Long>
