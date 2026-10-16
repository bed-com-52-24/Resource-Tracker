package com.resourcetracker.request.repository

import com.resourcetracker.request.entity.ResourceRequestLineItem
import org.springframework.data.jpa.repository.JpaRepository

interface RequestLineItemRepository :
    JpaRepository<ResourceRequestLineItem, Int> {

    fun findAllByRequestId(
        requestId: Int
    ): List<ResourceRequestLineItem>

    fun deleteAllByRequestId(requestId: Int)
}