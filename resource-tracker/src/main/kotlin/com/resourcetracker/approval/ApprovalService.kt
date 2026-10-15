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

    /**
     * Approve a PENDING request. With no line lists, every line is approved.
     * With lists, every line must be named exactly once, and the result is
     * APPROVED, REJECTED or PARTIALLY_APPROVED depending on the mix.
     */
    @Transactional
    fun approve(requestId: Int, actorId: Int, body: ApproveRequest?): DecisionResponse {
        val request = loadForManager(requestId, actorId)
        val current = currentStatus(request)
        RequestStateMachine.requirePending(current)

        val comment = optionalComment(body?.comment)
        val approvedIds = body?.approvedLineIds.orEmpty()
        val rejectedLines = body?.rejectedLines.orEmpty()
        val lines = lineRepository.findByRequestId(requestId)
        val hasLineDecisions = approvedIds.isNotEmpty() || rejectedLines.isNotEmpty()

        if (hasLineDecisions) {
            checkLineCoverage(lines, approvedIds, rejectedLines.map { it.lineId })
        }

        val target = when {
            rejectedLines.isEmpty() -> RequestStatus.APPROVED
            approvedIds.isEmpty() -> RequestStatus.REJECTED
            else -> RequestStatus.PARTIALLY_APPROVED
        }

        saveStatus(request, target)

        val rejectedComments = rejectedLines.associate { it.lineId to it.comment.trim() }
        val approvedResult = mutableListOf<Int>()
        val rejectedResult = mutableListOf<Int>()
        for (line in lines) {
            val rejectedComment = rejectedComments[line.id]
            if (rejectedComment == null) {
                decideLine(requestId, line, true, comment, actorId)
                approvedResult.add(line.id)
            } else {
                decideLine(requestId, line, false, rejectedComment, actorId)
                rejectedResult.add(line.id)
            }
        }
        return finish(request, actorId, current, target, "APPROVE", comment, approvedResult, rejectedResult)
    }

    /** Reject a PENDING request. A comment of 10 to 500 characters is required. */
    @Transactional
    fun reject(requestId: Int, actorId: Int, comment: String?): DecisionResponse {
        val request = loadForManager(requestId, actorId)
        val current = currentStatus(request)
        RequestStateMachine.requirePending(current)

        val clean = requiredComment(comment, 10)
        saveStatus(request, RequestStatus.REJECTED)

        val lines = lineRepository.findByRequestId(requestId)
        lines.forEach { decideLine(requestId, it, false, clean, actorId) }
        return finish(
            request, actorId, current, RequestStatus.REJECTED, "REJECT", clean,
            emptyList(), lines.map { it.id }
        )
    }

    /** Send a PENDING request back to the team lead. A comment is required. */
    @Transactional
    fun query(requestId: Int, actorId: Int, comment: String?): DecisionResponse {
        val request = loadForManager(requestId, actorId)
        val current = currentStatus(request)
        RequestStateMachine.requirePending(current)

        val clean = requiredComment(comment, 1)
        saveStatus(request, RequestStatus.QUERIED)
        return finish(
            request, actorId, current, RequestStatus.QUERIED, "QUERY", clean,
            emptyList(), emptyList()
        )
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
    private fun decideLine(requestId: Int, line: ApprovalLineItem, approved: Boolean, comment: String?, actorId: Int) {
        line.status = if (approved) "APPROVED" else "REJECTED"
        line.decisionComment = comment
        lineRepository.save(line)

        val decision = LineDecision(requestId, line, approved, comment, actorId)
        if (line.source == "INTERNAL") {
            internalHandlers.orderedStream().forEach { it.handle(decision) }
        } else {
            externalHandlers.orderedStream().forEach { it.handle(decision) }
        }
    }

    /** Every line item must be listed exactly once, and must belong to this request. */
    private fun checkLineCoverage(lines: List<ApprovalLineItem>, approvedIds: List<Int>, rejectedIds: List<Int>) {
        val mentioned = approvedIds + rejectedIds
        if (mentioned.size != mentioned.toSet().size) badRequest("A line item can only be listed once")

        val known = lines.map { it.id }.toSet()
        val unknown = mentioned.filter { it !in known }
        if (unknown.isNotEmpty()) badRequest("Line items not found on this request: $unknown")

        val missing = known - mentioned.toSet()
        if (missing.isNotEmpty()) badRequest("Every line item must be approved or rejected. Missing: $missing")
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
        if (trimmed.length > 500) badRequest("Comment must be at most 500 characters")
        return trimmed
    }

    private fun requiredComment(comment: String?, minLength: Int): String {
        val trimmed = comment?.trim().orEmpty()
        if (trimmed.length < minLength || trimmed.length > 500) {
            badRequest("A comment of $minLength to 500 characters is required")
        }
        return trimmed
    }

    private fun badRequest(message: String): Nothing =
        throw ResponseStatusException(HttpStatus.BAD_REQUEST, message)
}