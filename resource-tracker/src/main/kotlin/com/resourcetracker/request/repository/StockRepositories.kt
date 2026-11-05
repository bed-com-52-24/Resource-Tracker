package com.resourcetracker.request.repository

import com.resourcetracker.request.entity.*
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.data.jpa.repository.Lock
import jakarta.persistence.LockModeType
import java.time.LocalDate

interface StockReservationRepository : JpaRepository<StockReservation, Int> {
    @Query("select coalesce(sum(r.quantity), 0) from StockReservation r where r.resourceId = :resourceId and r.status = com.resourcetracker.request.entity.ReservationStatus.ACTIVE")
    fun sumActiveByResourceId(@Param("resourceId") resourceId: Int): Long
    @Query(
        """select r.resourceId as resourceId, sum(r.quantity) as totalQuantity
           from StockReservation r
           where r.status = com.resourcetracker.request.entity.ReservationStatus.ACTIVE
             and r.resourceId in :resourceIds
           group by r.resourceId""",
    )
    fun sumActiveByResourceIds(@Param("resourceIds") resourceIds: Collection<Int>): List<ActiveReservationTotal>
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

interface ActiveReservationTotal {
    fun getResourceId(): Int
    fun getTotalQuantity(): Long
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
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from StockIssue i where i.expectedReturnDate = :date and i.returnedQuantity < i.quantity and i.reminder7DaysSent = false")
    fun findForSevenDayReminder(@Param("date") date: LocalDate): List<StockIssue>
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from StockIssue i where i.expectedReturnDate = :date and i.returnedQuantity < i.quantity and i.reminder2DaysSent = false")
    fun findForTwoDayReminder(@Param("date") date: LocalDate): List<StockIssue>
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from StockIssue i where i.expectedReturnDate = :date and i.returnedQuantity < i.quantity and i.reminderDueDaySent = false")
    fun findForDueDayReminder(@Param("date") date: LocalDate): List<StockIssue>
    @Query("select coalesce(sum(i.quantity - i.returnedQuantity), 0) from StockIssue i where i.lineItemId = :lineItemId")
    fun sumOutstandingByLineItemId(@Param("lineItemId") lineItemId: Int): Long

    @Query(
        """
        select i.resourceId as resourceId,
               sum(i.quantity) as totalIssuedQuantity,
               sum(i.quantity - i.returnedQuantity) as outstandingQuantity,
               sum(case when i.expectedReturnDate < :asOfDate
                        then i.quantity - i.returnedQuantity else 0 end) as overdueQuantity
        from StockIssue i
        join ResourceRequest request on request.requestId = i.requestId
        where i.resourceId in :resourceIds
        group by i.resourceId
        """,
    )
    fun summarizeResources(
        @Param("resourceIds") resourceIds: Collection<Int>,
        @Param("asOfDate") asOfDate: LocalDate,
    ): List<ResourceIssueSummary>

    @Query(
        """
        select i.resourceId as resourceId,
               i.expectedReturnDate as expectedDate,
               sum(i.quantity - i.returnedQuantity) as quantity
        from StockIssue i
        where i.resourceId in :resourceIds
          and i.returnedQuantity < i.quantity
          and i.expectedReturnDate >= :asOfDate
        group by i.resourceId, i.expectedReturnDate
        """,
    )
    fun findExpectedReturns(
        @Param("resourceIds") resourceIds: Collection<Int>,
        @Param("asOfDate") asOfDate: LocalDate,
    ): List<ExpectedReturnProjection>
}

interface StockReturnExtensionRepository : JpaRepository<StockReturnExtension, Int> {
    fun existsByIssueIdAndStatus(issueId: Int, status: ReturnExtensionStatus): Boolean
    fun findAllByIssueIdOrderByCreatedAtDesc(issueId: Int): List<StockReturnExtension>

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from StockReturnExtension e where e.id = :id")
    fun findForUpdateById(@Param("id") id: Int): StockReturnExtension?
}

interface ResourceIssueSummary {
    val resourceId: Int
    val totalIssuedQuantity: Long
    val outstandingQuantity: Long
    val overdueQuantity: Long
}

interface ExpectedReturnProjection {
    val resourceId: Int
    val expectedDate: LocalDate
    val quantity: Long
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

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Procurement p where p.id = :id")
    fun findForUpdateById(@Param("id") id: Int): Procurement?

    @Query(
        """
        select p from Procurement p
        where (:status is null or p.status = :status)
          and (:isAdmin = true or exists (
            select r.requestId from ResourceRequest r
            where r.requestId = p.requestId and r.assignedManagerId = :actorId
          ))
          and (:search is null or exists (
            select r.requestId from ResourceRequest r
            where r.requestId = p.requestId
              and lower(r.requestNumber) like lower(concat('%', :search, '%'))
          ))
        """,
    )
    fun searchForActor(
        @Param("status") status: ProcurementStatus?,
        @Param("actorId") actorId: Int,
        @Param("isAdmin") isAdmin: Boolean,
        @Param("search") search: String?,
        pageable: Pageable,
    ): Page<Procurement>
}
