package com.resourcetracker.request.repository

import com.resourcetracker.request.entity.RequestHistory
import org.springframework.data.repository.Repository

/** Deliberately exposes insert and ordered read only; history cannot be edited or deleted through this repository. */
interface RequestHistoryRepository : Repository<RequestHistory, Int> {
    fun save(history: RequestHistory): RequestHistory
    fun findAllByRequestIdOrderByCreatedAtAscIdAsc(requestId: Int): List<RequestHistory>
}
