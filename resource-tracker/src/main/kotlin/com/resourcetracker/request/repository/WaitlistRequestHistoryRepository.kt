package com.resourcetracker.request.repository

import com.resourcetracker.request.entity.RequestHistory
import org.springframework.data.repository.Repository

/** Read-only query for waitlist audit assertions. */
interface WaitlistRequestHistoryRepository : Repository<RequestHistory, Int> {
    fun findByRequestIdAndEventType(requestId: Int, eventType: String): List<RequestHistory>
}
