package com.resourcetracker.approval

import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException

/**
 * The statuses a resource request can have.
 * The database column resource_requests.status stores these as text.
 */
enum class RequestStatus {
    PENDING, QUERIED, APPROVED, REJECTED, CANCELLED;

    /** Final statuses can never change again (SRS 2.4.4.5, 2.4.3.13). */
    val isFinal: Boolean
        get() = this == APPROVED || this == REJECTED || this == CANCELLED
}

/**
 * Holds the rules for which status changes are allowed.
 * Keeping them in one place means every service uses the same rules
 * and invalid transitions are refused consistently (SRS 2.4.4.5, 2.4.4.6).
 */
object RequestStateMachine {

    // For each status, the statuses it is allowed to move to.
    // A status with no entry (APPROVED, REJECTED, CANCELLED) cannot move at all.
    private val allowedMoves: Map<RequestStatus, Set<RequestStatus>> = mapOf(
        RequestStatus.PENDING to setOf(
            RequestStatus.APPROVED,   // manager approves
            RequestStatus.REJECTED,   // manager rejects (comment required)
            RequestStatus.QUERIED,    // manager asks for clarification (comment required)
            RequestStatus.CANCELLED   // team lead cancels
        ),
        RequestStatus.QUERIED to setOf(
            RequestStatus.PENDING     // team lead resubmits after correcting
        )
    )

    /** True if a request may move from one status to another. */
    fun canMove(from: RequestStatus, to: RequestStatus): Boolean =
        allowedMoves[from]?.contains(to) == true

    /** Throws 409 Conflict if the move is not allowed; does nothing if it is. */
    fun requireMove(from: RequestStatus, to: RequestStatus) {
        if (!canMove(from, to)) {
            val options = allowedMoves[from]?.joinToString() ?: "none (final status)"
            throw ResponseStatusException(
                HttpStatus.CONFLICT,
                "Cannot change a request from $from to $to. Allowed moves: $options"
            )
        }
    }
}