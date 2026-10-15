package com.resourcetracker.approval

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/** What happened to one line item. Passed to the handlers. */
data class LineDecision(
    val requestId: Int,
    val line: ApprovalLineItem,
    val approved: Boolean,
    val comment: String?,
    val actorId: Int
)

/** To be implemented by issue #39 (reservation, availability review). Runs per INTERNAL line, in the same transaction. */
interface InternalApprovalHandler {
    fun handle(decision: LineDecision)
}

/** To be implemented by issue #40 (budget check, commitment, procurement). Runs per EXTERNAL line, in the same transaction. */
interface ExternalApprovalHandler {
    fun handle(decision: LineDecision)
}

/** Tells the Team Lead what was decided (SRS 2.4.5.1). */
interface RequestNotifier {
    fun requestDecided(requestId: Int, requesterId: Int, outcome: RequestStatus, comment: String?)
}

/** Temporary notifier that writes to the log. We replace it once the notifications table is mapped. */
@Component
class LoggingRequestNotifier : RequestNotifier {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun requestDecided(requestId: Int, requesterId: Int, outcome: RequestStatus, comment: String?) {
        log.info("Notify user {}: request {} is now {}", requesterId, requestId, outcome)
    }
}