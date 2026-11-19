package com.resourcetracker.request.entity

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Request lifecycle status. The assigned Department Manager can query, approve, reject, or waitlist PENDING requests. A Team Lead clarifies QUERIED requests, returning them to PENDING. Partial quantities produce PARTIALLY_APPROVED. Requests become COMPLETED when all approved quantities are issued, handed over, or waived. Team Leads may cancel PENDING, QUERIED, APPROVED, PARTIALLY_APPROVED, or WAITLISTED requests before stock is issued. REJECTED and CANCELLED are terminal.")
enum class RequestStatus {
    PENDING,
    QUERIED,
    APPROVED,
    COMPLETED,
    PARTIALLY_APPROVED,
    REJECTED,
    WAITLISTED,
    CANCELLED
}

enum class FulfillmentPreference { INTERNAL, EXTERNAL }

enum class RequestAction { SUBMIT, QUERY, CLARIFY, APPROVE, REJECT, WAITLIST, CANCEL }

enum class RequestActor { TEAM_LEAD, ASSIGNED_MANAGER }

/** Single source of truth for lifecycle transitions and who may perform them. */
object RequestTransitions {
    private data class Rule(val to: RequestStatus, val actor: RequestActor)

    private val rules = mapOf(
        (RequestStatus.PENDING to RequestAction.QUERY) to Rule(RequestStatus.QUERIED, RequestActor.ASSIGNED_MANAGER),
        (RequestStatus.PENDING to RequestAction.APPROVE) to Rule(RequestStatus.APPROVED, RequestActor.ASSIGNED_MANAGER),
        (RequestStatus.PENDING to RequestAction.WAITLIST) to Rule(RequestStatus.WAITLISTED, RequestActor.ASSIGNED_MANAGER),
        (RequestStatus.PENDING to RequestAction.REJECT) to Rule(RequestStatus.REJECTED, RequestActor.ASSIGNED_MANAGER),
        (RequestStatus.QUERIED to RequestAction.CLARIFY) to Rule(RequestStatus.PENDING, RequestActor.TEAM_LEAD),
        (RequestStatus.PENDING to RequestAction.CLARIFY) to Rule(RequestStatus.PENDING, RequestActor.TEAM_LEAD),
        (RequestStatus.QUERIED to RequestAction.REJECT) to Rule(RequestStatus.REJECTED, RequestActor.ASSIGNED_MANAGER),
        (RequestStatus.PENDING to RequestAction.CANCEL) to Rule(RequestStatus.CANCELLED, RequestActor.TEAM_LEAD),
        (RequestStatus.QUERIED to RequestAction.CANCEL) to Rule(RequestStatus.CANCELLED, RequestActor.TEAM_LEAD),
        (RequestStatus.APPROVED to RequestAction.CANCEL) to Rule(RequestStatus.CANCELLED, RequestActor.TEAM_LEAD),
        (RequestStatus.PARTIALLY_APPROVED to RequestAction.CANCEL) to Rule(RequestStatus.CANCELLED, RequestActor.TEAM_LEAD),
        (RequestStatus.WAITLISTED to RequestAction.CANCEL) to Rule(RequestStatus.CANCELLED, RequestActor.TEAM_LEAD),
    )
    private val initialRules = mapOf((RequestAction.SUBMIT to RequestActor.TEAM_LEAD) to RequestStatus.PENDING)

    fun initialTarget(action: RequestAction, actor: RequestActor): RequestStatus? = initialRules[action to actor]

    fun target(from: RequestStatus, action: RequestAction, actor: RequestActor): RequestStatus? =
        rules[from to action]?.takeIf { it.actor == actor }?.to

    fun completionTarget(from: RequestStatus): RequestStatus? = when (from) {
        RequestStatus.APPROVED,
        RequestStatus.WAITLISTED -> RequestStatus.COMPLETED
        else -> null
    }

    fun allowedPairs(): Set<Triple<RequestStatus, RequestAction, RequestActor>> =
        rules.mapTo(linkedSetOf()) { (key, rule) -> Triple(key.first, key.second, rule.actor) }
}
