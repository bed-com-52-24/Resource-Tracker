package com.resourcetracker.request.repository

import com.resourcetracker.request.entity.ResourceRequest
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface ResourceRequestRepository :
    JpaRepository<ResourceRequest, Int> {

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
}