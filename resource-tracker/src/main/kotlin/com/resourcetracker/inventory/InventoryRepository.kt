package com.resourcetracker.inventory

import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface InventoryRepository : JpaRepository<Inventory, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Inventory i join fetch i.resource r where i.resource.id = :resourceId and i.location = :location")
    fun findForUpdate(
        @Param("resourceId") resourceId: UUID,
        @Param("location") location: String,
    ): Inventory?

    fun findAllByResourceIdOrderByLocation(resourceId: UUID): List<Inventory>
}

interface InventoryAuditLogRepository : JpaRepository<InventoryAuditLog, Long>
