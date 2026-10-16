package com.resourcetracker.request.repository

import com.resourcetracker.request.entity.ResourceRequestIdempotency
import org.springframework.data.jpa.repository.JpaRepository

interface RequestIdempotencyRepository :
    JpaRepository<ResourceRequestIdempotency, Long> {

    fun findByIdempotencyKey(
        idempotencyKey: String
    ): ResourceRequestIdempotency?
}