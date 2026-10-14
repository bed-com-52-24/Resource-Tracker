package com.resourcetracker.approval

import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException

@Service
class ApprovalService(
    private val requestRepository: ApprovalRequestRepository,
    private val auditRepository: ApprovalAuditRepository
) {

    /** Approve a PENDING request. The comment is optional (SRS 2.4.4.5). */
    @Transactional
    fun approve(requestId: Int, actorId: Int, comment: String?): DecisionResponse =
        decide(requestId, actorId, RequestStatus.APPROVED, "APPROVE", optionalComment(comment))

    /** Reject a PENDING request. A 10 to 500 character comment is required (SRS 2.4.4.6). */
    @Transactional
    fun reject(requestId: Int, actorId: Int, comment: String?): DecisionResponse =
        decide(requestId, actorId, RequestStatus.REJECTED, "REJECT", requiredComment(comment))

    /** Query a PENDING request: send it back to the team lead for clarification (SRS 2.4.4). */
    @Transactional
    fun query(requestId: Int, actorId: Int, comment: String?): DecisionResponse =
        decide(requestId, actorId, RequestStatus.QUERIED, "QUERY", requiredComment(comment))

    private fun decide(
        requestId: Int,
        actorId: Int,
        target: RequestStatus,
        action: String,
        comment: String?
    ): DecisionResponse {
        // 1. Load the request and lock it, or 404 if it doesn't exist.
        val request = requestRepository.findForUpdate(requestId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Request not found")

        // 2. Only the manager assigned to this request may decide (403 otherwise).
        if (request.assignedManagerId != actorId) {
            throw ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "Only the manager assigned to this request can decide on it"
            )
        }

        // 3. Check the status move is legal (409 for a request that is not PENDING).
        val current = RequestStatus.entries.firstOrNull { it.name == request.status }
            ?: throw ResponseStatusException(
                HttpStatus.CONFLICT,
                "Request has an unknown status: ${request.status}"
            )
        RequestStateMachine.requireMove(current, target)

        // 4. Change the status and record who did it, in the same transaction.
        request.status = target.name
        requestRepository.save(request)
        auditRepository.save(
            ApprovalAudit(
                requestId = requestId,
                actorId = actorId,
                previousState = current.name,
                newState = target.name,
                action = action,
                comment = comment
            )
        )

        return DecisionResponse(requestId, current.name, target.name, actorId, comment)
    }

    private fun optionalComment(comment: String?): String? {
        val trimmed = comment?.trim()?.ifBlank { null } ?: return null
        if (trimmed.length > 500) badRequest("Comment must be at most 500 characters")
        return trimmed
    }

    private fun requiredComment(comment: String?): String {
        val trimmed = comment?.trim().orEmpty()
        if (trimmed.length < 10 || trimmed.length > 500) {
            badRequest("A comment of 10 to 500 characters is required")
        }
        return trimmed
    }

    private fun badRequest(message: String): Nothing =
        throw ResponseStatusException(HttpStatus.BAD_REQUEST, message)
}