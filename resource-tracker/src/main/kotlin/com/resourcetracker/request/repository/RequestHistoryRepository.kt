package com.resourcetracker.request.repository

import com.resourcetracker.request.entity.RequestHistory
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.repository.Repository
import java.time.LocalDateTime

/** Deliberately exposes insert and ordered read only; history cannot be edited or deleted through this repository. */
interface RequestHistoryRepository : Repository<RequestHistory, Int> {
    fun save(history: RequestHistory): RequestHistory
    fun findAllByRequestIdOrderByCreatedAtAscIdAsc(requestId: Int): List<RequestHistory>
    fun findAllByLineItemIdOrderByCreatedAtAscIdAsc(lineItemId: Int): List<RequestHistory>

    fun findAllByActorIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThanEqualOrderByCreatedAtAscIdAsc(
        actorId: Int,
        from: LocalDateTime,
        to: LocalDateTime,
        pageable: Pageable,
    ): Page<RequestHistory>

    fun findAllByEventTypeInAndCreatedAtGreaterThanEqualAndCreatedAtLessThanEqualOrderByCreatedAtAscIdAsc(
        eventTypes: Collection<String>,
        from: LocalDateTime,
        to: LocalDateTime,
        pageable: Pageable,
    ): Page<RequestHistory>

}
