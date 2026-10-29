package com.resourcetracker.request.repository

import com.resourcetracker.request.entity.ResourceRequest
import com.resourcetracker.request.entity.RequestStatus
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.jpa.repository.Lock
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface ResourceRequestRepository :
    JpaRepository<ResourceRequest, Int>, JpaSpecificationExecutor<ResourceRequest> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from ResourceRequest r where r.requestId = :requestId")
    fun findForUpdateById(@Param("requestId") requestId: Int): ResourceRequest?

    fun findAllByRequesterId(
        requesterId: Int,
        pageable: Pageable
    ): Page<ResourceRequest>

    @Query(
        """
        SELECT r FROM ResourceRequest r
        WHERE r.requestId = :requestId
          AND r.requesterId = :requesterId
        """
    )
    fun findOwnedRequest(
        @Param("requestId") requestId: Int,
        @Param("requesterId") requesterId: Int
    ): ResourceRequest?
    fun existsByProjectIdAndStatusNotIn(
        projectId: Int,
        statuses: Collection<RequestStatus>
    ): Boolean
}
