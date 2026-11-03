package com.resourcetracker.request.service

import com.resourcetracker.request.entity.RequestAction
import com.resourcetracker.request.entity.RequestActor
import com.resourcetracker.request.entity.RequestStatus
import com.resourcetracker.request.entity.RequestTransitions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class RequestTransitionsTest {
    @Test
    fun `every allowed transition has exactly the configured destination and actor`() {
        val expected = mapOf(
            Triple(RequestStatus.PENDING, RequestAction.QUERY, RequestActor.ASSIGNED_MANAGER) to RequestStatus.QUERIED,
            Triple(RequestStatus.PENDING, RequestAction.APPROVE, RequestActor.ASSIGNED_MANAGER) to RequestStatus.APPROVED,
            Triple(RequestStatus.PENDING, RequestAction.WAITLIST, RequestActor.ASSIGNED_MANAGER) to RequestStatus.WAITLISTED,
            Triple(RequestStatus.PENDING, RequestAction.REJECT, RequestActor.ASSIGNED_MANAGER) to RequestStatus.REJECTED,
            Triple(RequestStatus.PENDING, RequestAction.CLARIFY, RequestActor.TEAM_LEAD) to RequestStatus.PENDING,
            Triple(RequestStatus.QUERIED, RequestAction.CLARIFY, RequestActor.TEAM_LEAD) to RequestStatus.PENDING,
            Triple(RequestStatus.QUERIED, RequestAction.REJECT, RequestActor.ASSIGNED_MANAGER) to RequestStatus.REJECTED,
            Triple(RequestStatus.PENDING, RequestAction.CANCEL, RequestActor.TEAM_LEAD) to RequestStatus.CANCELLED,
            Triple(RequestStatus.QUERIED, RequestAction.CANCEL, RequestActor.TEAM_LEAD) to RequestStatus.CANCELLED,
            Triple(RequestStatus.APPROVED, RequestAction.CANCEL, RequestActor.TEAM_LEAD) to RequestStatus.CANCELLED,
            Triple(RequestStatus.WAITLISTED, RequestAction.CANCEL, RequestActor.TEAM_LEAD) to RequestStatus.CANCELLED,
        )
        assertEquals(expected.keys, RequestTransitions.allowedPairs())
        expected.forEach { (transition, target) ->
            assertEquals(target, RequestTransitions.target(transition.first, transition.second, transition.third))
        }
    }

    @Test
    fun `all other status action actor combinations are forbidden`() {
        val allowed = RequestTransitions.allowedPairs()
        RequestStatus.entries.forEach { status ->
            (RequestAction.entries - RequestAction.SUBMIT).forEach { action ->
                RequestActor.entries.forEach { actor ->
                    if (Triple(status, action, actor) !in allowed) {
                        assertNull(RequestTransitions.target(status, action, actor), "$status $action $actor")
                    }
                }
            }
        }
    }

    @Test
    fun `only a team lead can submit a new request`() {
        assertEquals(RequestStatus.PENDING, RequestTransitions.initialTarget(RequestAction.SUBMIT, RequestActor.TEAM_LEAD))
        assertNull(RequestTransitions.initialTarget(RequestAction.SUBMIT, RequestActor.ASSIGNED_MANAGER))
        RequestAction.entries.filter { it != RequestAction.SUBMIT }.forEach { action ->
            RequestActor.entries.forEach { actor -> assertNull(RequestTransitions.initialTarget(action, actor)) }
        }
    }
}
