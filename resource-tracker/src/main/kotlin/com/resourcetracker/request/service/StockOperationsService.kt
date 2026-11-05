package com.resourcetracker.request.service

import com.resourcetracker.request.entity.*
import com.resourcetracker.request.repository.*
import com.resourcetracker.notification.NotificationService
import com.resourcetracker.notification.NotificationType
import com.resourcetracker.resource.ResourceRepository
import com.resourcetracker.resource.ResourceType
import com.resourcetracker.user.Role
import com.resourcetracker.user.UserRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size

data class StockIssueRequest(val lineItemId: Int, val quantity: Int)
data class StockIssueResponse(val id: Int, val requestId: Int, val lineItemId: Int, val resourceId: Int, val quantity: Int, val returnedQuantity: Int, val expectedReturnDate: LocalDate?)
data class CreateReturnRequest(val quantity: Int, val note: String? = null)
data class VerifyReturnRequest(val condition: ReturnCondition, val quantity: Int? = null, val note: String? = null)
data class StockCorrectionRequest(val newQuantity: Int, val reason: String)
data class RequestReturnExtension(
    @field:NotNull val newExpectedReturnDate: LocalDate,
    @field:NotBlank @field:Size(max = 1000) val reason: String,
)
enum class ReturnExtensionDecision { APPROVE, REJECT }
data class DecideReturnExtension(
    val decision: ReturnExtensionDecision,
    @field:Size(max = 1000) val comment: String? = null,
)
data class ReturnExtensionResponse(
    val id: Int,
    val issueId: Int,
    val requestedReturnDate: LocalDate,
    val reason: String,
    val status: ReturnExtensionStatus,
    val decisionComment: String?,
)
data class StockIssueProgressResponse(
    val issueId: Int,
    val lineItemId: Int,
    val resourceId: Int,
    val quantity: Int,
    val returnedQuantity: Int,
    val expectedReturnDate: LocalDate?,
    val extensions: List<ReturnExtensionResponse>,
)

@Service
class StockOperationsService(
    private val requests: ResourceRequestRepository,
    private val lines: RequestLineItemRepository,
    private val resources: ResourceRepository,
    private val reservations: StockReservationRepository,
    private val issues: StockIssueRepository,
    private val returns: StockReturnRepository,
    private val movements: StockMovementRepository,
    private val history: RequestHistoryRepository,
    private val availability: StockAvailabilityService,
    private val waitlistService: WaitlistService,
    private val users: UserRepository,
    private val clock: Clock,
    private val requestCompletion: RequestCompletionService,
    private val extensions: StockReturnExtensionRepository,
    private val notifications: NotificationService,
) {
    @Transactional
    fun issue(requestId: Int, actorId: Int, dto: StockIssueRequest): StockIssueResponse {
        val actor = users.findById(actorId).orElseThrow { notFound("User not found") }
        val request = requests.findForUpdateById(requestId) ?: notFound("Request not found")
        requireAssignedManager(request, actor.id, actor.role)
        if (request.status != RequestStatus.APPROVED && request.status != RequestStatus.WAITLISTED) {
            conflict("Request must be approved before issuing stock")
        }
        if (dto.quantity <= 0) badRequest("quantity must be positive")
        val line = lines.findById(dto.lineItemId).orElseThrow { notFound("Line item not found") }
        if (line.requestId != requestId) notFound("Line item not found")
        if (line.fulfillmentPreference != FulfillmentPreference.INTERNAL.name || line.resourceId == null) badRequest("Only internal resource lines can be issued")
        val resource = resources.findForUpdateById(line.resourceId!!) ?: notFound("Resource not found")
        if (resource.type != ResourceType.INTERNAL) conflict("Only internal resources can be issued")
        if (waitlistService.isWaiting(dto.lineItemId)) conflict("Line item is on the waitlist and has no reserved stock yet")
        val issuedSoFar = issues.findByRequestIdAndLineItemIdForUpdate(requestId, dto.lineItemId).sumOf { it.quantity }
        val approved = line.approvedQuantity ?: 0
        if (dto.quantity > approved - issuedSoFar) conflict("Issue exceeds approved quantity remaining")
        val activeReservations = reservations.findByRequestIdAndLineItemIdAndStatusForUpdate(requestId, dto.lineItemId, ReservationStatus.ACTIVE)
        val reserved = activeReservations.sumOf { it.quantity }
        if (reserved > 0) {
            if (dto.quantity > reserved) conflict("Issue exceeds reserved quantity")
            var toConsume = dto.quantity
            activeReservations.forEach { reservation ->
                if (toConsume > 0) {
                    val consumed = minOf(toConsume, reservation.quantity)
                    toConsume -= consumed
                    if (consumed == reservation.quantity) {
                        reservation.status = ReservationStatus.CONSUMED
                        reservation.closedAt = LocalDateTime.now(clock)
                        reservation.closeReason = "Stock issued"
                    } else reservation.quantity -= consumed
                    reservations.save(reservation)
                }
            }
        } else {
            val available = availability.availableQuantity(resource, lockReservations = true)
            if (dto.quantity > available) conflict("Insufficient available stock; available $available")
        }
        resource.quantity = (resource.quantity ?: 0) - dto.quantity
        resource.recomputeStatus()
        resources.save(resource)
        val durationDays = line.approvedDurationDays ?: conflict("The assigned manager must approve a return duration before stock can be issued")
        val expectedReturnDate = LocalDate.now(clock.withZone(ZoneId.of("Africa/Blantyre"))).plusDays(durationDays.toLong())
        val issue = issues.save(StockIssue(requestId = requestId, lineItemId = dto.lineItemId, resourceId = resource.id, teamId = request.teamId, reservationId = activeReservations.firstOrNull()?.id, quantity = dto.quantity, issuedBy = actor.id, expectedReturnDate = expectedReturnDate))
        waitlistService.recordIssued(dto.lineItemId, dto.quantity, actor.id)
        movements.save(StockMovement(resourceId = resource.id, type = MovementType.ISSUE, quantity = dto.quantity, requestId = requestId, issueId = issue.id, actorId = actor.id))
        history.save(RequestHistory(requestId = requestId, eventType = "ISSUED", fromStatus = request.status.name, toStatus = request.status.name, actorId = actor.id, lineItemId = dto.lineItemId, quantity = dto.quantity))
        requestCompletion.completeIfFulfilled(requestId, actor.id)
        return StockIssueResponse(issue.id, issue.requestId, issue.lineItemId, issue.resourceId, issue.quantity, issue.returnedQuantity, issue.expectedReturnDate)
    }

    @Transactional(readOnly = true)
    fun listIssues(requestId: Int, actorId: Int): List<StockIssueProgressResponse> {
        val actor = users.findById(actorId).orElseThrow { notFound("User not found") }
        val request = requests.findById(requestId).orElseThrow { notFound("Request not found") }
        val visible = when (actor.role) {
            Role.TEAM_LEAD -> actor.teams.any { it.id == request.teamId }
            Role.DEPARTMENT_MANAGER -> request.assignedManagerId == actor.id
            Role.ADMIN, Role.MANAGING_DIRECTOR -> true
            else -> false
        }
        if (!visible) forbidden()
        return issues.findAllByRequestId(requestId).map { issue ->
            StockIssueProgressResponse(
                issueId = issue.id,
                lineItemId = issue.lineItemId,
                resourceId = issue.resourceId,
                quantity = issue.quantity,
                returnedQuantity = issue.returnedQuantity,
                expectedReturnDate = issue.expectedReturnDate,
                extensions = extensions.findAllByIssueIdOrderByCreatedAtDesc(issue.id).map { it.toResponse() },
            )
        }
    }

    @Transactional
    fun requestReturnExtension(issueId: Int, actorId: Int, dto: RequestReturnExtension): ReturnExtensionResponse {
        val actor = users.findById(actorId).orElseThrow { notFound("User not found") }
        val issue = issues.findForUpdateById(issueId) ?: notFound("Issue not found")
        val request = requests.findById(issue.requestId).orElseThrow { notFound("Request not found") }
        if (actor.role != Role.TEAM_LEAD || actor.teams.none { it.id == issue.teamId }) forbidden()
        val currentDueDate = issue.expectedReturnDate ?: conflict("This issue has no expected return date")
        if (issue.returnedQuantity >= issue.quantity) conflict("The issue has already been fully returned")
        if (!dto.newExpectedReturnDate.isAfter(LocalDate.now(clock.withZone(ZoneId.of("Africa/Blantyre"))))) badRequest("The requested return date must be in the future")
        if (!dto.newExpectedReturnDate.isAfter(currentDueDate)) badRequest("The requested return date must be later than the current due date")
        if (extensions.existsByIssueIdAndStatus(issueId, ReturnExtensionStatus.PENDING)) conflict("A return-date extension is already pending")
        val saved = extensions.save(
            StockReturnExtension(
                issueId = issueId,
                requestedBy = actor.id,
                requestedReturnDate = dto.newExpectedReturnDate,
                reason = dto.reason.trim(),
            ),
        )
        history.save(RequestHistory(requestId = issue.requestId, eventType = "RETURN_EXTENSION_REQUESTED", actorId = actor.id, lineItemId = issue.lineItemId, quantity = issue.quantity - issue.returnedQuantity, comment = "Requested ${dto.newExpectedReturnDate}. ${dto.reason.trim()}"))
        notifications.notify(listOfNotNull(actor.id, request.requesterId, request.assignedManagerId).distinct(), NotificationType.RETURN_EXTENSION_REQUESTED, mapOf("requestId" to issue.requestId, "requestNumber" to request.requestNumber, "issueId" to issue.id, "requestedReturnDate" to saved.requestedReturnDate))
        return saved.toResponse()
    }

    @Transactional
    fun decideReturnExtension(extensionId: Int, actorId: Int, dto: DecideReturnExtension): ReturnExtensionResponse {
        val actor = users.findById(actorId).orElseThrow { notFound("User not found") }
        val extension = extensions.findForUpdateById(extensionId) ?: notFound("Return extension not found")
        val issue = issues.findForUpdateById(extension.issueId) ?: notFound("Issue not found")
        val request = requests.findForUpdateById(issue.requestId) ?: notFound("Request not found")
        requireAssignedManager(request, actor.id, actor.role)
        if (extension.status != ReturnExtensionStatus.PENDING) conflict("Return extension has already been decided")
        if (issue.returnedQuantity >= issue.quantity) conflict("The issue has already been fully returned")
        val comment = dto.comment?.trim()?.takeIf(String::isNotEmpty)
        if (dto.decision == ReturnExtensionDecision.REJECT && comment == null) badRequest("A reason is required when rejecting an extension")
        val previousDate = issue.expectedReturnDate
        if (dto.decision == ReturnExtensionDecision.APPROVE) {
            if (!extension.requestedReturnDate.isAfter(LocalDate.now(clock.withZone(ZoneId.of("Africa/Blantyre"))))) conflict("The requested return date has passed")
            issue.expectedReturnDate = extension.requestedReturnDate
            issue.reminder7DaysSent = false
            issue.reminder2DaysSent = false
            issue.reminderDueDaySent = false
            issues.save(issue)
            extension.status = ReturnExtensionStatus.APPROVED
        } else {
            extension.status = ReturnExtensionStatus.REJECTED
        }
        extension.decidedBy = actor.id
        extension.decidedAt = LocalDateTime.now(clock)
        extension.decisionComment = comment
        val saved = extensions.save(extension)
        val event = if (dto.decision == ReturnExtensionDecision.APPROVE) "RETURN_EXTENSION_APPROVED" else "RETURN_EXTENSION_REJECTED"
        history.save(RequestHistory(requestId = issue.requestId, eventType = event, actorId = actor.id, lineItemId = issue.lineItemId, quantity = issue.quantity - issue.returnedQuantity, comment = "Due date ${previousDate ?: "unset"}; requested ${extension.requestedReturnDate}. ${comment.orEmpty()}"))
        val notification = if (dto.decision == ReturnExtensionDecision.APPROVE) NotificationType.RETURN_EXTENSION_APPROVED else NotificationType.RETURN_EXTENSION_REJECTED
        notifications.notify(listOfNotNull(extension.requestedBy, request.requesterId, request.assignedManagerId).distinct(), notification, mapOf("requestId" to issue.requestId, "requestNumber" to request.requestNumber, "issueId" to issue.id, "requestedReturnDate" to extension.requestedReturnDate))
        return saved.toResponse()
    }

    @Transactional
    fun requestReturn(issueId: Int, actorId: Int, dto: CreateReturnRequest): StockReturn {
        val actor = users.findById(actorId).orElseThrow { notFound("User not found") }
        val issue = issues.findForUpdateById(issueId) ?: notFound("Issue not found")
        val request = requests.findById(issue.requestId).orElseThrow { notFound("Request not found") }
        val isOwner = actor.role == Role.TEAM_LEAD && actor.teams.any { it.id == issue.teamId }
        val isManager = actor.role == Role.DEPARTMENT_MANAGER && request.assignedManagerId == actor.id
        if (!isOwner && !isManager) forbidden()
        if (dto.quantity <= 0) badRequest("quantity must be positive")
        val pending = returns.findByIssueIdAndStatusForUpdate(issueId, ReturnStatus.PENDING).sumOf { it.quantity }
        if (dto.quantity > issue.quantity - issue.returnedQuantity - pending) conflict("Return exceeds unreturned issued quantity")
        val created = returns.save(StockReturn(issueId = issueId, quantity = dto.quantity, returnedBy = actor.id, note = dto.note))
        history.save(RequestHistory(requestId = issue.requestId, eventType = "RETURN_REQUESTED", actorId = actor.id, lineItemId = issue.lineItemId, quantity = dto.quantity, comment = dto.note))
        return created
    }

    @Transactional
    fun verifyReturn(returnId: Int, actorId: Int, dto: VerifyReturnRequest): StockReturn {
        val actor = users.findById(actorId).orElseThrow { notFound("User not found") }
        val issueId = returns.findIssueIdById(returnId) ?: notFound("Return not found")
        val issue = issues.findForUpdateById(issueId) ?: notFound("Issue not found")
        val stockReturn = returns.findForUpdateById(returnId) ?: notFound("Return not found")
        val request = requests.findById(issue.requestId).orElseThrow { notFound("Request not found") }
        requireAssignedManager(request, actor.id, actor.role)
        if (stockReturn.status != ReturnStatus.PENDING) conflict("Return has already been processed")
        val requestedQuantity = stockReturn.quantity
        val quantity = dto.quantity ?: requestedQuantity
        if (quantity !in 1..requestedQuantity) badRequest("Verified quantity must be between 1 and requested return quantity")
        val resource = resources.findForUpdateById(issue.resourceId) ?: notFound("Resource not found")
        stockReturn.quantity = quantity
        stockReturn.condition = dto.condition
        stockReturn.status = ReturnStatus.VERIFIED
        stockReturn.verifiedBy = actor.id
        stockReturn.verifiedAt = LocalDateTime.now(clock)
        stockReturn.note = dto.note ?: stockReturn.note
        val saved = returns.save(stockReturn)
        if (quantity < requestedQuantity) {
            returns.save(StockReturn(issueId = issue.id, quantity = requestedQuantity - quantity, returnedBy = stockReturn.returnedBy, note = stockReturn.note))
        }
        when (dto.condition) {
            ReturnCondition.USABLE -> {
                resource.quantity = (resource.quantity ?: 0) + quantity
                movements.save(StockMovement(resourceId = resource.id, type = MovementType.RETURN_USABLE, quantity = quantity, requestId = request.requestId, issueId = issue.id, returnId = saved.id, actorId = actor.id, reason = dto.note))
            }
            ReturnCondition.DAMAGED, ReturnCondition.QUARANTINED -> {
                resource.unavailableQuantity += quantity
                movements.save(StockMovement(resourceId = resource.id, type = MovementType.RETURN_UNAVAILABLE, quantity = quantity, requestId = request.requestId, issueId = issue.id, returnId = saved.id, actorId = actor.id, reason = dto.note ?: dto.condition.name))
            }
            ReturnCondition.LOST -> Unit
        }
        issue.returnedQuantity += quantity
        issues.save(issue)
        resource.recomputeStatus()
        resources.save(resource)
        history.save(RequestHistory(requestId = request.requestId!!, eventType = "RETURNED", actorId = actor.id, lineItemId = issue.lineItemId, quantity = quantity, comment = "${dto.condition}: ${dto.note.orEmpty()}"))
        if (dto.condition == ReturnCondition.USABLE) waitlistService.onStockReturned(resource.id)
        return saved
    }

    @Transactional
    fun correctResource(resourceId: Int, actorId: Int, dto: StockCorrectionRequest): StockMovement {
        val actor = users.findById(actorId).orElseThrow { notFound("User not found") }
        if (actor.role != Role.DEPARTMENT_MANAGER) forbidden()
        if (dto.newQuantity < 0 || dto.reason.isBlank()) badRequest("newQuantity must be non-negative and reason is required")
        val resource = resources.findForUpdateById(resourceId) ?: notFound("Resource not found")
        if (resource.department?.id != actor.department.id) notFound("Resource not found")
        if (resource.type != ResourceType.INTERNAL) badRequest("Only internal resources have physical stock")
        val current = resource.quantity ?: 0
        val difference = dto.newQuantity - current
        if (difference == 0) badRequest("newQuantity matches the current quantity")
        val minimum = availability.reservedQuantity(resourceId, forUpdate = true) + resource.unavailableQuantity
        if (dto.newQuantity < minimum) conflict("Correction cannot reduce stock below reserved and unavailable quantity ($minimum)")
        resource.quantity = dto.newQuantity
        resource.recomputeStatus()
        resources.save(resource)
        val movement = movements.save(StockMovement(resourceId = resource.id, type = MovementType.CORRECTION, quantity = difference, actorId = actor.id, reason = dto.reason.trim()))
        if (difference > 0) waitlistService.onStockReturned(resource.id)
        return movement
    }

    private fun requireAssignedManager(request: ResourceRequest, actorId: Int, role: Role) {
        if (role != Role.DEPARTMENT_MANAGER || request.assignedManagerId != actorId) forbidden()
    }
    private fun StockReturnExtension.toResponse() = ReturnExtensionResponse(id, issueId, requestedReturnDate, reason, status, decisionComment)
    private fun forbidden(): Nothing = throw ResponseStatusException(HttpStatus.FORBIDDEN, "Action is not permitted")
    private fun conflict(message: String): Nothing = throw ResponseStatusException(HttpStatus.CONFLICT, message)
    private fun badRequest(message: String): Nothing = throw ResponseStatusException(HttpStatus.BAD_REQUEST, message)
    private fun notFound(message: String): Nothing = throw ResponseStatusException(HttpStatus.NOT_FOUND, message)
}
