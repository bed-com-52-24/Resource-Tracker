package com.resourcetracker.approval

import org.springframework.beans.factory.ObjectProvider
import org.springframework.dao.OptimisticLockingFailureException
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException

@Service
class ApprovalService(
    private val requestRepository: ApprovalRequestRepository,
    private val lineRepository: ApprovalLineItemRepository,
    private val auditRepository: ApprovalAuditRepository,
    private val internalHandlers: ObjectProvider<InternalApprovalHandler>,
    private val externalHandlers: ObjectProvider<ExternalApprovalHandler>,
    private val notifier: RequestNotifier
) {

    @Transactional
    fun decide(requestId: Int, actorId: Int, body: DecisionRequest): DecisionResponse {
        val request = loadForManager(requestId, actorId)
        val current = currentStatus(request)
        RequestStateMachine.requirePending(current)

        val lines = lineRepository.findByRequestId(requestId)
        when (body.decision) {
            ApprovalDecision.APPROVE -> {
                val comment = optionalComment(body.comment)
                saveStatus(request, RequestStatus.APPROVED)
                lines.forEach { decideLine(requestId, it, true, comment, actorId) }
                return finish(
                    request, actorId, current, RequestStatus.APPROVED, "APPROVE", comment,
                    lines.map { it.id }, emptyList()
                )
            }
            ApprovalDecision.REJECT -> {
                val comment = requiredComment(body.comment, 10)
                saveStatus(request, RequestStatus.REJECTED)
                lines.forEach { decideLine(requestId, it, false, comment, actorId) }
                return finish(
                    request, actorId, current, RequestStatus.REJECTED, "REJECT", comment,
                    emptyList(), lines.map { it.id }
                )
            }
            ApprovalDecision.QUERY -> {
                val comment = requiredComment(body.comment, 1)
                saveStatus(request, RequestStatus.QUERIED)
                return finish(
                    request, actorId, current, RequestStatus.QUERIED, "QUERY", comment,
                    emptyList(), emptyList()
                )
            }
        }
    }

    // ---------- helpers ----------

    /** Loads the request (404) and checks the caller is the assigned manager (403). */
    private fun loadForManager(requestId: Int, actorId: Int): ApprovalRequest {
        val request = requestRepository.findById(requestId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Request not found")
        }
        if (request.assignedManagerId != actorId) {
            throw ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "Only the manager assigned to this request can decide on it"
            )
        }
        return request
    }

    private fun currentStatus(request: ApprovalRequest): RequestStatus =
        RequestStatus.entries.firstOrNull { it.name == request.status }
            ?: throw ResponseStatusException(
                HttpStatus.CONFLICT,
                "Request has an unknown status: ${request.status}"
            )

    /**
     * Changes the status and writes it to the database straight away.
     * If someone else changed the request first, the version check fails
     * and we answer 409 (one winner, one conflict).
     */
    private fun saveStatus(request: ApprovalRequest, target: RequestStatus) {
        RequestStateMachine.requireMove(currentStatus(request), target)
        request.status = target.name
        try {
            requestRepository.saveAndFlush(request)
        } catch (e: OptimisticLockingFailureException) {
            throw ResponseStatusException(
                HttpStatus.CONFLICT,
                "This request was changed by someone else. Reload it and try again."
            )
        }
    }

    /** Marks one line, then calls the internal or external handler for it. */
    private fun decideLine(
        requestId: Int,
        line: ApprovalLineItem,
        approved: Boolean,
        comment: String?,
        actorId: Int,
        waitlist: Boolean = false
    ) {
        line.status = if (approved) "APPROVED" else "REJECTED"
        line.decisionComment = comment
        lineRepository.save(line)

        val decision = LineDecision(requestId, line, approved, comment, actorId, waitlist)
        if (line.source == "INTERNAL") {
            internalHandlers.orderedStream().forEach { it.handle(decision) }
        } else {
            externalHandlers.orderedStream().forEach { it.handle(decision) }
        }
    }

    /** Writes the audit row and notifies the team lead, in the same transaction as the status change. */
    private fun finish(
        request: ApprovalRequest, actorId: Int, previous: RequestStatus, target: RequestStatus,
        action: String, comment: String?, approvedIds: List<Int>, rejectedIds: List<Int>
    ): DecisionResponse {
        auditRepository.save(
            ApprovalAudit(
                requestId = request.id,
                actorId = actorId,
                previousState = previous.name,
                newState = target.name,
                action = action,
                comment = comment
            )
        )
        notifier.requestDecided(request.id, request.requesterId, target, comment)
        return DecisionResponse(request.id, previous.name, target.name, actorId, comment, approvedIds, rejectedIds)
    }

    private fun optionalComment(comment: String?): String? {
        val trimmed = comment?.trim()?.ifBlank { null } ?: return null
        if (trimmed.length > 1000) badRequest("Comment must be at most 1000 characters")
        return trimmed
    }

    private fun requiredComment(comment: String?, minLength: Int): String {
        val trimmed = comment?.trim().orEmpty()
        if (trimmed.length < minLength || trimmed.length > 1000) {
            badRequest("A comment of $minLength to 1000 characters is required")
        }
        return trimmed
    }

    private fun badRequest(message: String): Nothing =
        throw ResponseStatusException(HttpStatus.BAD_REQUEST, message)
}
