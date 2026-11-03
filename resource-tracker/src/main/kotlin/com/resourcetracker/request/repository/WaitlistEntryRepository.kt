package com.resourcetracker.request.repository

import com.resourcetracker.request.entity.WaitlistEntry
import com.resourcetracker.request.entity.WaitlistStatus
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDate
import java.time.LocalDateTime

interface WaitlistEntryRepository : JpaRepository<WaitlistEntry, Int> {
    fun findByRequestId(requestId: Int): List<WaitlistEntry>

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
        """
        select entry from WaitlistEntry entry
        where entry.requestId = :requestId
        order by entry.id asc
        """,
    )
    fun findByRequestIdForUpdate(@Param("requestId") requestId: Int): List<WaitlistEntry>

    fun findByLineItemId(lineItemId: Int): WaitlistEntry?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select entry from WaitlistEntry entry where entry.lineItemId = :lineItemId")
    fun findByLineItemIdForUpdate(@Param("lineItemId") lineItemId: Int): WaitlistEntry?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
        """
        select entry from WaitlistEntry entry
        where entry.status in :statuses
          and entry.expectedFulfillmentDate is not null
          and entry.expectedFulfillmentDate < :today
          and (entry.overdueNotifiedDate is null or entry.overdueNotifiedDate < :today)
        order by entry.expectedFulfillmentDate asc, entry.id asc
        """,
    )
    fun findOverdueForNotificationForUpdate(
        @Param("statuses") statuses: Collection<WaitlistStatus>,
        @Param("today") today: LocalDate,
    ): List<WaitlistEntry>

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
        """
        select entry from WaitlistEntry entry
        where entry.resourceId = :resourceId
          and entry.status in :statuses
        order by entry.createdAt asc, entry.id asc
        """,
    )
    fun findOpenForUpdateByResourceIdFifo(
        @Param("resourceId") resourceId: Int,
        @Param("statuses") statuses: Collection<WaitlistStatus>,
    ): List<WaitlistEntry>

    @Query(
        """
        select count(entry) from WaitlistEntry entry
        where entry.resourceId = :resourceId
          and entry.status in :statuses
          and entry.reservedQuantity < entry.quantity
          and (
              entry.createdAt < :createdAt
              or (entry.createdAt = :createdAt and entry.id < :entryId)
          )
        """,
    )
    fun countAhead(
        @Param("resourceId") resourceId: Int,
        @Param("statuses") statuses: Collection<WaitlistStatus>,
        @Param("createdAt") createdAt: LocalDateTime,
        @Param("entryId") entryId: Int,
    ): Long
}
