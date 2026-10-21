package com.resourcetracker.request.repository

import com.resourcetracker.request.entity.*
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.data.jpa.repository.Lock
import jakarta.persistence.LockModeType

interface StockReservationRepository : JpaRepository<StockReservation, Int> {
    @Query("select coalesce(sum(r.quantity), 0) from StockReservation r where r.resourceId = :resourceId and r.status = com.resourcetracker.request.entity.ReservationStatus.ACTIVE")
    fun sumActiveByResourceId(@Param("resourceId") resourceId: Int): Long
    fun findAllByRequestIdAndStatus(requestId: Int, status: ReservationStatus): List<StockReservation>
    fun findAllByRequestIdAndLineItemIdAndStatus(requestId: Int, lineItemId: Int, status: ReservationStatus): List<StockReservation>
    fun findAllByLineItemIdAndStatus(lineItemId: Int, status: ReservationStatus): List<StockReservation>
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from StockReservation r where r.resourceId = :resourceId and r.status = :status")
    fun findByResourceIdAndStatusForUpdate(@Param("resourceId") resourceId: Int, @Param("status") status: ReservationStatus): List<StockReservation>
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from StockReservation r where r.requestId = :requestId and r.lineItemId = :lineItemId and r.status = :status")
    fun findByRequestIdAndLineItemIdAndStatusForUpdate(@Param("requestId") requestId: Int, @Param("lineItemId") lineItemId: Int, @Param("status") status: ReservationStatus): List<StockReservation>
}

interface StockIssueRepository : JpaRepository<StockIssue, Int> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from StockIssue i where i.id = :id")
    fun findForUpdateById(@Param("id") id: Int): StockIssue?
    fun findAllByRequestIdAndLineItemId(requestId: Int, lineItemId: Int): List<StockIssue>
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from StockIssue i where i.requestId = :requestId and i.lineItemId = :lineItemId")
    fun findByRequestIdAndLineItemIdForUpdate(@Param("requestId") requestId: Int, @Param("lineItemId") lineItemId: Int): List<StockIssue>
    fun findAllByRequestId(requestId: Int): List<StockIssue>
    @Query("select coalesce(sum(i.quantity - i.returnedQuantity), 0) from StockIssue i where i.lineItemId = :lineItemId")
    fun sumOutstandingByLineItemId(@Param("lineItemId") lineItemId: Int): Long
}

interface StockReturnRepository : JpaRepository<StockReturn, Int> {
    @Query("select r.issueId from StockReturn r where r.id = :id")
    fun findIssueIdById(@Param("id") id: Int): Int?
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from StockReturn r where r.id = :id")
    fun findForUpdateById(@Param("id") id: Int): StockReturn?
    fun findAllByIssueId(issueId: Int): List<StockReturn>
    @Query("select coalesce(sum(r.quantity), 0) from StockReturn r where r.issueId = :issueId and r.status = com.resourcetracker.request.entity.ReturnStatus.PENDING")
    fun sumPendingByIssueId(@Param("issueId") issueId: Int): Long
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from StockReturn r where r.issueId = :issueId and r.status = :status")
    fun findByIssueIdAndStatusForUpdate(@Param("issueId") issueId: Int, @Param("status") status: ReturnStatus): List<StockReturn>
}

interface StockMovementRepository : JpaRepository<StockMovement, Int> {
    fun findAllByResourceIdOrderByCreatedAtAsc(resourceId: Int): List<StockMovement>
}

interface ProcurementRepository : JpaRepository<Procurement, Int> {
    fun findAllByRequestId(requestId: Int): List<Procurement>
}
