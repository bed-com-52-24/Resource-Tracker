package com.resourcetracker.request.repository

import com.resourcetracker.request.entity.WaitlistEntry
import com.resourcetracker.request.entity.WaitlistStatus
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDateTime

interface WaitlistEntryRepository : JpaRepository<WaitlistEntry, Int> {

    /**
     * Entries of one resource that still need stock, oldest first, row-locked.
     * The lock makes concurrent transactions queue instead of reserving the same units.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
        """
        select w from WaitlistEntry w
        where w.resourceId = :resourceId
          and w.status in :statuses
          and w.reservedQuantity < w.quantity
        order by w.createdAt asc, w.id asc
        """
    )
    fun findOpenForUpdateByResourceIdFifo(
        @Param("resourceId") resourceId: Int,
        @Param("statuses") statuses: Collection<WaitlistStatus>,
    ): List<WaitlistEntry>

    /** Plain read: used to find which resources to lock before locking entries. */
    fun findByRequestId(requestId: Int): List<WaitlistEntry>

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from WaitlistEntry w where w.requestId = :requestId order by w.id")
    fun findByRequestIdForUpdate(@Param("requestId") requestId: Int): List<WaitlistEntry>

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from WaitlistEntry w where w.lineItemId = :lineItemId")
    fun findByLineItemIdForUpdate(@Param("lineItemId") lineItemId: Int): WaitlistEntry?

    fun findByLineItemId(lineItemId: Int): WaitlistEntry?

    /** Entries ahead of the given one that still need stock (used for the queue position). */
    @Query(
        """
        select count(w) from WaitlistEntry w
        where w.resourceId = :resourceId
          and w.status in :statuses
          and w.reservedQuantity < w.quantity
          and (w.createdAt < :createdAt or (w.createdAt = :createdAt and w.id < :id))
        """
    )
    fun countAhead(
        @Param("resourceId") resourceId: Int,
        @Param("statuses") statuses: Collection<WaitlistStatus>,
        @Param("createdAt") createdAt: LocalDateTime,
        @Param("id") id: Int,
    ): Long
}