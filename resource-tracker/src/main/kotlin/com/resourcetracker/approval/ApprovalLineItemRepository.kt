package com.resourcetracker.approval

import org.springframework.data.jpa.repository.JpaRepository

interface ApprovalLineItemRepository : JpaRepository<ApprovalLineItem, Int> {
    fun findByRequestId(requestId: Int): List<ApprovalLineItem>
}