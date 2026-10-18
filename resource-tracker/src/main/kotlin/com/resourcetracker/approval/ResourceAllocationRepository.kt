package com.resourcetracker.approval

import org.springframework.data.jpa.repository.JpaRepository

interface ResourceAllocationRepository : JpaRepository<ResourceAllocation, Int> {
    fun findByRequestId(requestId: Int): List<ResourceAllocation>
}