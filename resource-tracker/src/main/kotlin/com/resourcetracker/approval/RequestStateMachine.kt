package com.resourcetracker.approval

import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException

/** The statuses a resource request can have (stored as text in resource_requests.status). */
enum class RequestStatus {
    PENDING, QUERIED, APPROVED, PARTIALLY_APPROVED, REJECTED, CANCELLED;

    /** Final statuses can never change again. */
    val isFinal: Boolean
        get() = this == APPROVED || this == PARTIALLY_APPROVED ||
                this == REJECTED || this == CANCELLED
}

/** The single source of truth for allowed status changes. */
object RequestStateMachine {

    private val allowedMoves: Map<RequestStatus, Set<RequestStatus>> = mapOf(
        RequestStatus.PENDING to setOf(
            RequestStatus.APPROVED,
            RequestStatus.PARTIALLY_APPROVED,
            RequestStatus.REJECTED,
            RequestStatus.QUERIED,
            RequestStatus.CANCELLED
        ),
        RequestStatus.QUERIED to setOf(RequestStatus.PENDING)
    )

    fun canMove(from: RequestStatus, to: RequestStatus): Boolean =
        allowedMoves[from]?.contains(to) == true

    fun requireMove(from: RequestStatus, to: RequestStatus) {
        if (!canMove(from, to)) {
            val options = allowedMoves[from]?.joinToString() ?: "none (final status)"
            throw ResponseStatusException(
                HttpStatus.CONFLICT,
                "Cannot change a request from $from to $to. Allowed moves: $options"
            )
        }
    }

    /** A manager can only decide on a request that is PENDING. */
    fun requirePending(current: RequestStatus) {
        if (current != RequestStatus.PENDING) {
            throw ResponseStatusException(
                HttpStatus.CONFLICT,
                "Only PENDING requests can be decided on. Current status: $current"
            )
        }
    }
}