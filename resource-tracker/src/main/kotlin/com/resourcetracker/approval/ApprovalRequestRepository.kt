package com.resourcetracker.approval

import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface ApprovalRequestRepository : JpaRepository<ApprovalRequest, Int> {

    /**
     * Loads a request and locks its row until the transaction ends.
     * If two decisions arrive at the same moment, the second waits for the
     * first, so they can never both succeed (SRS 2.6.6).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM ApprovalRequest r WHERE r.id = :id")
    fun findForUpdate(@Param("id") id: Int): ApprovalRequest?
}