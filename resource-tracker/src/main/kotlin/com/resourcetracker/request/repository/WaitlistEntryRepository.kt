package com.resourcetracker.request.repository

import com.resourcetracker.request.entity.WaitlistEntry
import com.resourcetracker.request.entity.WaitlistStatus
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface WaitlistEntryRepository : JpaRepository<WaitlistEntry, Int> {
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

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
        """
        select entry from WaitlistEntry entry
        where entry.requestId = :requestId
          and entry.status in :statuses
        order by entry.id asc
        """,
    )
    fun findByRequestIdAndStatusInForUpdate(
        @Param("requestId") requestId: Int,
        @Param("statuses") statuses: Collection<WaitlistStatus>,
    ): List<WaitlistEntry>
}
