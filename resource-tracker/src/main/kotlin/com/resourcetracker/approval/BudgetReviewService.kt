package com.resourcetracker.approval

import com.resourcetracker.request.repository.RequestLineItemRepository
import com.resourcetracker.request.repository.ResourceRequestRepository
import com.resourcetracker.request.entity.FulfillmentPreference
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.math.BigDecimal

data class BudgetReview(
    val departmentId: Int,
    val budget: BigDecimal,
    val committed: BigDecimal,
    val remaining: BigDecimal,
    val requestCost: BigDecimal,
    val exceedsBudget: Boolean
)

@Service
class BudgetReviewService(
    private val requestRepository: ResourceRequestRepository,
    private val lineRepository: RequestLineItemRepository,
    private val budget: BudgetPort
) {

    /** What the assigned manager sees before approving external items. */
    @Transactional(readOnly = true)
    fun review(requestId: Int, actorId: Int): BudgetReview {
        val request = requestRepository.findById(requestId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Request not found")
        }
        if (request.assignedManagerId != actorId) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Only the assigned manager can review this request")
        }

        val cost = lineRepository.findAllByRequestId(requestId)
            .filter { it.fulfillmentPreference == FulfillmentPreference.EXTERNAL.name }
            .fold(BigDecimal.ZERO) { sum, line -> sum + budget.lineCost(requireNotNull(line.lineItemId)) }

        val departmentId = budget.departmentOfRequest(requestId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Request not found")
        val status = budget.status(departmentId)
            ?: throw ResponseStatusException(HttpStatus.CONFLICT, "Department $departmentId has no budget set up")

        return BudgetReview(
            departmentId, status.budget, status.committed, status.remaining, cost, cost > status.remaining
        )
    }
}