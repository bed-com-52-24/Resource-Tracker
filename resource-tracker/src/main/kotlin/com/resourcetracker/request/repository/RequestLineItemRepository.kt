package com.resourcetracker.request.repository

import com.resourcetracker.request.entity.ResourceRequestLineItem
import com.resourcetracker.request.entity.RequestStatus
import com.resourcetracker.request.entity.WaitlistStatus
import com.resourcetracker.resource.ResourceType
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface RequestLineItemRepository :
    JpaRepository<ResourceRequestLineItem, Int> {

    fun findAllByRequestId(
        requestId: Int
    ): List<ResourceRequestLineItem>

    fun deleteAllByRequestId(requestId: Int)

    @Query(
        value = """
        select line.lineItemId as lineItemId,
               request.requestId as requestId,
               request.requestNumber as requestNumber,
               resource.id as resourceId,
               resource.name as resourceName,
               request.status as requestStatus,
               (coalesce(line.approvedQuantity, line.quantity) -
                coalesce((select sum(issue.quantity) from StockIssue issue where issue.lineItemId = line.lineItemId), 0) -
                coalesce((select sum(reserved.quantity) from StockReservation reserved
                          where reserved.lineItemId = line.lineItemId
                            and reserved.status = com.resourcetracker.request.entity.ReservationStatus.ACTIVE), 0)) as remainingQuantity,
               request.requiredDate as requiredDate
        from ResourceRequestLineItem line, ResourceRequest request, Resource resource
        where request.requestId = line.requestId
          and resource.id = line.resourceId
          and resource.type = :resourceType
          and resource.active = true
          and line.fulfillmentPreference = 'INTERNAL'
          and request.status in :requestStatuses
          and ( :departmentId is null or resource.department.id = :departmentId )
          and (coalesce(line.approvedQuantity, line.quantity) -
               coalesce((select sum(issue.quantity) from StockIssue issue where issue.lineItemId = line.lineItemId), 0) -
               coalesce((select sum(reserved.quantity) from StockReservation reserved
                         where reserved.lineItemId = line.lineItemId
                           and reserved.status = com.resourcetracker.request.entity.ReservationStatus.ACTIVE), 0)) >
              (coalesce(resource.quantity, 0) - resource.unavailableQuantity -
               coalesce((select sum(reservation.quantity) from StockReservation reservation
                         where reservation.resourceId = resource.id
                           and reservation.status = com.resourcetracker.request.entity.ReservationStatus.ACTIVE), 0))
        order by request.requiredDate asc, request.requestId asc, line.lineItemId asc
        """,
        countQuery = """
        select count(line)
        from ResourceRequestLineItem line, ResourceRequest request, Resource resource
        where request.requestId = line.requestId
          and resource.id = line.resourceId
          and resource.type = :resourceType
          and resource.active = true
          and line.fulfillmentPreference = 'INTERNAL'
          and request.status = :requestStatus
          and ( :departmentId is null or resource.department.id = :departmentId )
          and (coalesce(line.approvedQuantity, line.quantity) -
               coalesce((select sum(issue.quantity) from StockIssue issue where issue.lineItemId = line.lineItemId), 0) -
               coalesce((select sum(reserved.quantity) from StockReservation reserved
                         where reserved.lineItemId = line.lineItemId
                           and reserved.status = com.resourcetracker.request.entity.ReservationStatus.ACTIVE), 0)) >
              (coalesce(resource.quantity, 0) - resource.unavailableQuantity -
               coalesce((select sum(reservation.quantity) from StockReservation reservation
                         where reservation.resourceId = resource.id
                           and reservation.status = com.resourcetracker.request.entity.ReservationStatus.ACTIVE), 0))
        """,
    )
    fun findApprovedWaitlist(
        @Param("resourceType") resourceType: ResourceType,
        @Param("requestStatuses") requestStatuses: Collection<RequestStatus>,
        @Param("departmentId") departmentId: Int?,
        pageable: Pageable,
    ): Page<WaitlistLineProjection>

    @Query(
        """
        select entry.resourceId as resourceId,
               sum(entry.quantity - entry.reservedQuantity) as quantity
        from WaitlistEntry entry
        where entry.resourceId in :resourceIds
          and entry.status in :statuses
          and entry.quantity > entry.reservedQuantity
        group by entry.resourceId
        """,
    )
    fun sumWaitingQuantityByResourceIds(
        @Param("resourceIds") resourceIds: Collection<Int>,
        @Param("statuses") statuses: Collection<WaitlistStatus>,
    ): List<RequestLineWaitlistDemand>
}

interface WaitlistLineProjection {
    val lineItemId: Int
    val requestId: Int
    val requestNumber: String
    val resourceId: Int
    val resourceName: String
    val requestStatus: RequestStatus
    val remainingQuantity: Long
    val requiredDate: java.time.LocalDate?
}

interface RequestLineWaitlistDemand {
    val resourceId: Int
    val quantity: Long
}
