package com.resourcetracker.request.service

import com.resourcetracker.common.DepartmentReference
import com.resourcetracker.common.ProjectReference
import com.resourcetracker.common.ResourceReference
import com.resourcetracker.common.TeamReference
import com.resourcetracker.common.UserReference
import com.resourcetracker.common.VendorReference
import com.resourcetracker.common.ResourceNotFoundException
import com.resourcetracker.notification.NotificationService
import com.resourcetracker.notification.NotificationType
import com.resourcetracker.project.ProjectRepository
import com.resourcetracker.request.dto.*
import com.resourcetracker.request.entity.*
import com.resourcetracker.request.repository.*
import com.resourcetracker.resource.Resource
import com.resourcetracker.resource.ResourceRepository
import com.resourcetracker.resource.ResourceType
import com.resourcetracker.team.TeamRepository
import com.resourcetracker.user.Role
import com.resourcetracker.user.User
import com.resourcetracker.user.UserRepository
import com.resourcetracker.vendor.VendorRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.domain.Specification
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.math.BigDecimal
import java.security.SecureRandom
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime

@Service
class ResourceRequestService(
    private val requestRepository: ResourceRequestRepository,
    private val lineItemRepository: RequestLineItemRepository,
    private val historyRepository: RequestHistoryRepository,
    private val numberService: RequestNumberService,
    private val teams: TeamRepository,
    private val projects: ProjectRepository,
    private val vendors: VendorRepository,
    private val resources: ResourceRepository,
    private val users: UserRepository,
    private val notifications: NotificationService,
    private val reservations: ReservationService,
    private val availability: StockAvailabilityService,
    private val reservationRepository: StockReservationRepository,
    private val issueRepository: StockIssueRepository,
    private val waitlistEntries: WaitlistEntryRepository,
    private val procurementService: ProcurementService,
    private val waitlistService: WaitlistService,
    private val externalApprovalHandler: ExternalApprovalHandler,
    private val clock: Clock,
    private val budget: BudgetPort? = null,
    private val requestCompletionService: RequestCompletionService? = null,
) {
    @Transactional(readOnly = true)
    fun loadUser(id: Int): User = user(id)

    @Transactional
    fun createRequest(dto: CreateResourceRequestDto, requesterId: Int): ResourceRequestResponseDto {
        val caller = user(requesterId)
        if (caller.role != Role.TEAM_LEAD) forbidden()
        val initialStatus = RequestTransitions.initialTarget(RequestAction.SUBMIT, RequestActor.TEAM_LEAD)
            ?: conflict("Team Leads cannot submit requests")

        val team = teams.findById(dto.teamId).orElseThrow { badRequest("Unknown team") }
        if (caller.teams.none { it.id == team.id }) forbidden("You do not belong to this team")
        if (!team.isActive) badRequest("Team is inactive")
        if (team.department.id != caller.department.id) forbidden("Team belongs to another department")
        val project = projects.findById(dto.projectId).orElseThrow { badRequest("Unknown project") }
        if (!project.active || team.projects.none { it.id == project.id }) badRequest("Project is not active or assigned to the team")

        val normalized = normalizeLines(dto.lineItems, dto.vendorId)
        val externalLines = normalized.any { it.preference == FulfillmentPreference.EXTERNAL }
        val vendorId = if (externalLines) {
            val vendorId = dto.vendorId ?: badRequest("vendorId is required for external lines")
            val vendor = vendors.findById(vendorId).orElseThrow { badRequest("Unknown vendor") }
            if (!vendor.status.equals("ACTIVE", ignoreCase = true)) badRequest("Vendor is inactive")
            vendor.id
        } else null

        val managers = users.findActiveByRoleAndDepartmentId(Role.DEPARTMENT_MANAGER, team.department.id)
        val managerId = managers.singleOrNull()?.id
        val needsReassignment = managerId == null
        val first = normalized.first()
        val total = normalized.fold(BigDecimal.ZERO) { sum, line -> sum + line.unitPrice.multiply(BigDecimal.valueOf(line.dto.quantity.toLong())) }
        val saved = requestRepository.save(
            ResourceRequest(
                requestNumber = numberService.generateRequestNumber(),
                itemDescription = first.description,
                manufacturer = first.manufacturer,
                unitPrice = first.unitPrice,
                quantity = first.dto.quantity,
                batchNumber = first.dto.batchNumber,
                vendorId = vendorId,
                departmentId = team.department.id,
                projectId = project.id,
                teamId = team.id,
                requesterId = caller.id,
                assignedManagerId = managerId,
                needsReassignment = needsReassignment,
                requiredDate = dto.requiredDate,
                uniqueKey = uniqueKey(),
                status = initialStatus,
                justification = dto.justification.trim(),
                totalAmount = total,
            ),
        )
        val id = requireNotNull(saved.requestId)
        val lineEntities = normalized.map { line ->
            ResourceRequestLineItem(
                requestId = id,
                description = line.description,
                manufacturer = line.manufacturer,
                unitPrice = line.unitPrice,
                quantity = line.dto.quantity,
                category = line.dto.category,
                batchNumber = line.dto.batchNumber,
                resourceId = line.resource?.id,
                fulfillmentPreference = line.preference.name,
                unitOfMeasure = line.unitOfMeasure,
                requestedDurationDays = line.dto.requestedDurationDays,
            )
        }
        lineItemRepository.saveAll(lineEntities)
        historyRepository.save(RequestHistory(requestId = id, eventType = "SUBMITTED", toStatus = RequestStatus.PENDING.name, actorId = caller.id, comment = dto.justification.trim()))

        val lowStock = normalized.filter { it.resource?.type == ResourceType.INTERNAL && it.dto.quantity > availability.availableQuantity(it.resource) }
        if (managerId != null) {
            notifications.notify(listOf(managerId), NotificationType.SUBMITTED, mapOf("requestId" to id, "requestNumber" to saved.requestNumber))
        }
        if (needsReassignment) {
            val reason = if (managers.isEmpty()) "No active Department Manager exists for department ${team.department.id}." else "Multiple active Department Managers exist for department ${team.department.id}."
            historyRepository.save(RequestHistory(requestId = id, eventType = "ASSIGNED", toStatus = RequestStatus.PENDING.name, actorId = caller.id, comment = reason))
            notifications.notify(users.findActiveByRole(Role.ADMIN).map { it.id }, NotificationType.REASSIGNMENT_REQUIRED, mapOf("requestId" to id, "requestNumber" to saved.requestNumber, "message" to reason))
        }
        return saved.toResponse(lineEntities, lowStock.map { "${it.description}: requested ${it.dto.quantity}, physical stock ${it.resource?.quantity ?: 0}" })
    }

    @Transactional(readOnly = true)
    fun list(
        caller: User,
        status: RequestStatus?,
        teamId: Int?,
        projectId: Int?,
        assignedManagerId: Int?,
        needsReassignment: Boolean?,
        from: LocalDate?,
        to: LocalDate?,
        search: String?,
        pageable: Pageable,
    ): Page<ResourceRequestResponseDto> {
        val teamIds = when (caller.role) {
            Role.TEAM_LEAD -> caller.teams.map { it.id }.toSet()
            Role.DEPARTMENT_MANAGER -> null
            Role.ADMIN, Role.MANAGING_DIRECTOR -> null
            Role.MEMBER -> forbidden()
        }
        if (caller.role == Role.TEAM_LEAD && teamIds!!.isEmpty()) return Page.empty(pageable)
        val spec = Specification<ResourceRequest> { root, _, cb ->
            val predicates = mutableListOf<jakarta.persistence.criteria.Predicate>()
            when (caller.role) {
                Role.TEAM_LEAD -> predicates += root.get<Int>("teamId").`in`(teamIds)
                Role.DEPARTMENT_MANAGER -> predicates += cb.equal(root.get<Int?>("assignedManagerId"), caller.id)
                Role.ADMIN -> predicates += cb.isTrue(root.get<Boolean>("needsReassignment"))
                Role.MANAGING_DIRECTOR -> Unit
                Role.MEMBER -> forbidden()
            }
            status?.let { predicates += cb.equal(root.get<RequestStatus>("status"), it) }
            teamId?.let { predicates += cb.equal(root.get<Int>("teamId"), it) }
            projectId?.let { predicates += cb.equal(root.get<Int>("projectId"), it) }
            assignedManagerId?.let { predicates += cb.equal(root.get<Int?>("assignedManagerId"), it) }
            needsReassignment?.let { predicates += cb.equal(root.get<Boolean>("needsReassignment"), it) }
            from?.let { predicates += cb.greaterThanOrEqualTo(root.get("requiredDate"), it) }
            to?.let { predicates += cb.lessThanOrEqualTo(root.get("requiredDate"), it) }
            search?.trim()?.takeIf(String::isNotEmpty)?.let { predicates += cb.like(cb.lower(root.get("requestNumber")), "%${it.lowercase()}%") }
            cb.and(*predicates.toTypedArray())
        }
        return requestRepository.findAll(spec, pageable).map { request -> request.toResponse(lineItemRepository.findAllByRequestId(requireNotNull(request.requestId)), includeStock = caller.role != Role.TEAM_LEAD) }
    }

    @Transactional(readOnly = true)
    fun get(requestId: Int, caller: User): ResourceRequestResponseDto {
        val request = requestRepository.findById(requestId).orElseThrow { ResourceNotFoundException("Request not found") }
        if (!canView(request, caller)) throw ResourceNotFoundException("Request not found")
        return request.toResponse(lineItemRepository.findAllByRequestId(requestId), includeStock = caller.role != Role.TEAM_LEAD)
    }

    @Transactional(readOnly = true)
    fun history(requestId: Int, caller: User): List<RequestHistoryResponseDto> {
        val request = requestRepository.findById(requestId).orElseThrow { ResourceNotFoundException("Request not found") }
        if (!canViewHistory(request, caller)) throw ResourceNotFoundException("Request not found")
        return historyRepository.findAllByRequestIdOrderByCreatedAtAscIdAsc(requestId).map {
            RequestHistoryResponseDto(it.id, it.requestId, it.eventType, it.fromStatus, it.toStatus, it.actorId, it.lineItemId, it.quantity, it.comment, it.createdAt, it.actorType)
        }
    }

    @Transactional
    fun decide(requestId: Int, caller: User, dto: RequestDecisionDto): ResourceRequestResponseDto {
        if (caller.role != Role.DEPARTMENT_MANAGER) forbidden()
        val request = requestRepository.findForUpdateById(requestId)
            ?: throw ResourceNotFoundException("Request not found")
        if (request.assignedManagerId != caller.id) forbidden("Only the assigned manager may decide this request")
        val action = when (dto.decision) {
            Decision.QUERY -> RequestAction.QUERY
            Decision.APPROVE -> RequestAction.APPROVE
            Decision.REJECT -> RequestAction.REJECT
            Decision.WAITLIST -> RequestAction.WAITLIST
        }
        val from = request.status
        val target = RequestTransitions.target(from, action, RequestActor.ASSIGNED_MANAGER)
            ?: conflict("Cannot ${action.name.lowercase()} a request in ${request.status}")
        val comment = dto.comment?.trim()?.takeIf(String::isNotEmpty)
        if ((dto.decision == Decision.QUERY || dto.decision == Decision.REJECT) && comment == null) badRequest("A comment is required")
        val expectedDates = dto.approvedQuantities.associate { it.lineItemId to it.expectedFulfillmentDate }
        val lines = lineItemRepository.findAllByRequestId(requestId)
            .sortedBy { it.resourceId ?: Int.MAX_VALUE }
        if (dto.decision != Decision.QUERY) {
            val missing = buildList {
                if (request.requiredDate == null) add("requiredDate")
                if (lines.isEmpty()) add("lineItems")
                if (request.needsReassignment || request.assignedManagerId == null) add("assignedManager")
                if (lines.any { it.fulfillmentPreference == FulfillmentPreference.EXTERNAL.name } && request.vendorId == null) add("vendorId")
            }
            if (missing.isNotEmpty()) conflict("Cannot decide request; missing: ${missing.joinToString()}")
        }
        if (dto.decision == Decision.WAITLIST &&
            lines.any { it.fulfillmentPreference != FulfillmentPreference.INTERNAL.name || it.resourceId == null }
        ) {
            conflict("Only requests with internal resource lines can be waitlisted")
        }
        if (dto.decision == Decision.APPROVE || dto.decision == Decision.WAITLIST) {
            val ids = lines.mapNotNull { it.lineItemId }.toSet()
            val approvedByLine = dto.approvedQuantities.associate { it.lineItemId to it }
        val expectedDates = approvedByLine.mapValues { it.value.expectedFulfillmentDate }
            if (approvedByLine.keys.any { it !in ids }) badRequest("approvedQuantities contains an unknown line item")
            lines.forEach { line ->
                val lineId = requireNotNull(line.lineItemId)
                val approvedDecision = approvedByLine[lineId]
                val approved = approvedDecision?.quantity ?: line.quantity
                if (approved !in 1..line.quantity) badRequest("Approved quantity must be between 1 and requested quantity")
                if (line.fulfillmentPreference == FulfillmentPreference.INTERNAL.name) {
                    val duration = approvedDecision?.approvedDurationDays ?: line.requestedDurationDays
                    if (duration == null || duration <= 0) badRequest("An approved return duration is required for internal line item $lineId")
                    line.approvedDurationDays = duration
                }
                val resource = line.resourceId?.let { resources.findForUpdateById(it) ?: conflict("Resource for line item $lineId no longer exists") }
                if (resource != null && !resource.active) conflict("Resource for line item $lineId is inactive")
                line.approvedQuantity = approved
                if (approved < line.quantity) {
                    if (comment == null) badRequest("A comment is required when reducing an approved quantity")
                    historyRepository.save(RequestHistory(requestId = requestId, eventType = "QUANTITY_ADJUSTED", fromStatus = request.status.name, toStatus = request.status.name, actorId = caller.id, lineItemId = lineId, quantity = approved, comment = "Requested ${line.quantity}; approved $approved. $comment"))
                }
            }
            lineItemRepository.saveAll(lines)
            lines.filter { it.fulfillmentPreference == FulfillmentPreference.EXTERNAL.name }.forEach { line ->
                externalApprovalHandler.handle(
                    LineDecision(
                        requestId = requestId,
                        actorId = caller.id,
                        line = line,
                        approved = true,
                        comment = comment,
                    ),
                )
            }
        }
        request.status = target
        request.decidedBy = caller.id
        request.decidedAt = LocalDateTime.now(clock)
        request.decisionReason = comment
        if (dto.decision == Decision.QUERY) request.queriedAt = LocalDateTime.now(clock)
        val saved = requestRepository.saveAndFlush(request)
        historyRepository.save(RequestHistory(requestId = requestId, eventType = target.name, fromStatus = from.name, toStatus = target.name, actorId = caller.id, comment = comment))
        val notification = when (target) {
            RequestStatus.QUERIED -> NotificationType.QUERIED
            RequestStatus.APPROVED -> NotificationType.APPROVED
            RequestStatus.REJECTED -> NotificationType.REJECTED
            RequestStatus.WAITLISTED -> NotificationType.WAITLISTED
            else -> error("Unexpected decision target")
        }
        notifications.notify(listOf(request.requesterId), notification, mapOf("requestId" to requestId, "requestNumber" to request.requestNumber))
        if (target == RequestStatus.APPROVED) {
            procurementService.authorize(request, lines, caller.id)
            val backorderedByLine = reservations.reserveApprovedInternalLines(requestId, lines, caller.id).toMap()
            if (backorderedByLine.isNotEmpty()) {
                val backorderedLines = lines.filter { line ->
                    line.resourceId != null && line.lineItemId != null &&
                        backorderedByLine.containsKey(line.lineItemId)
                }
                waitlistService.backorder(
                    requestId,
                    lines,
                    backorderedByLine,
                    caller.id,
                    from,
                    comment,
                    expectedDates,
                    request.requiredDate,
                )
            }
        }
        if (target == RequestStatus.WAITLISTED) waitlistService.enqueue(requestId, lines, caller.id, from, comment, expectedDates, request.requiredDate)
        return saved.toResponse(lines, includeStock = true)
    }

    @Transactional
    fun updateRequest(requestId: Int, caller: User, dto: UpdateResourceRequestDto): ResourceRequestResponseDto {
        val request = requestRepository.findForUpdateById(requestId)?.takeIf { it.requesterId == caller.id }
            ?: throw ResourceNotFoundException("Request not found")
        if (caller.role != Role.TEAM_LEAD) forbidden()
        val action = if (dto.status == RequesterUpdateStatus.CANCELLED) RequestAction.CANCEL else RequestAction.CLARIFY
        val target = RequestTransitions.target(request.status, action, RequestActor.TEAM_LEAD)
            ?: conflict("Cannot ${action.name.lowercase()} a request in ${request.status}")
        val comment = dto.comment?.trim()?.takeIf(String::isNotEmpty)
        if (action == RequestAction.CLARIFY && comment == null) badRequest("A response comment is required")
        if (action == RequestAction.CLARIFY && dto.lineItems != null) {
            if (request.status != RequestStatus.QUERIED) conflict("Line items may only be edited while queried")
            val normalized = normalizeLines(dto.lineItems, request.vendorId)
            val total = normalized.fold(BigDecimal.ZERO) { sum, line -> sum + line.unitPrice.multiply(BigDecimal.valueOf(line.dto.quantity.toLong())) }
            val first = normalized.first()
            request.itemDescription = first.description
            request.manufacturer = first.manufacturer
            request.unitPrice = first.unitPrice
            request.quantity = first.dto.quantity
            request.batchNumber = first.dto.batchNumber
            request.totalAmount = total
            lineItemRepository.deleteAllByRequestId(requestId)
            lineItemRepository.saveAll(normalized.map { line -> ResourceRequestLineItem(requestId = requestId, description = line.description, manufacturer = line.manufacturer, unitPrice = line.unitPrice, quantity = line.dto.quantity, category = line.dto.category, batchNumber = line.dto.batchNumber, resourceId = line.resource?.id, fulfillmentPreference = line.preference.name, unitOfMeasure = line.unitOfMeasure, requestedDurationDays = line.dto.requestedDurationDays) })
            request.editedAfterQuery = true
        }
        if (action == RequestAction.CANCEL) {
            if ((request.status == RequestStatus.APPROVED || request.status == RequestStatus.WAITLISTED) &&
                issueRepository.findAllByRequestId(requestId).any { it.quantity > 0 }
            ) {
                conflict("An approved request with issued stock cannot be cancelled; use the return process")
            }
            // Lock order: request (locked above) -> resources -> waitlist entries -> reservations.
            val waitlistedResourceIds = waitlistService.cancelForRequest(requestId)
            val releasedResourceIds = reservations.releaseAllFor(requestId, caller.id, comment)
            (waitlistedResourceIds + releasedResourceIds).distinct().sorted().forEach(waitlistService::onStockReturned)
            if (request.status == RequestStatus.APPROVED || request.status == RequestStatus.WAITLISTED) {
                val externalCost = lineItemRepository.findAllByRequestId(requestId)
                    .filter { it.fulfillmentPreference == FulfillmentPreference.EXTERNAL.name }
                    .fold(BigDecimal.ZERO) { sum, line ->
                        sum + line.unitPrice.multiply(BigDecimal.valueOf((line.approvedQuantity ?: line.quantity).toLong()))
                    }
                if (externalCost > BigDecimal.ZERO) {
                    budget?.release(request.departmentId, request.requiredDate ?: LocalDate.now(clock), externalCost)
                }
            }
            request.cancelledBy = caller.id
            request.cancelledAt = LocalDateTime.now(clock)
            request.cancelReason = comment
        }
        val from = request.status
        request.status = target
        if (target == RequestStatus.PENDING) request.queriedAt = null
        val saved = requestRepository.saveAndFlush(request)
        historyRepository.save(RequestHistory(requestId = requestId, eventType = if (target == RequestStatus.PENDING) "CLARIFIED" else "CANCELLED", fromStatus = from.name, toStatus = target.name, actorId = caller.id, comment = comment))
        request.assignedManagerId?.let { managerId ->
            notifications.notify(listOf(managerId), if (target == RequestStatus.PENDING) NotificationType.RESUBMITTED else NotificationType.CANCELLED, mapOf("requestId" to requestId, "requestNumber" to request.requestNumber))
        }
        return saved.toResponse(lineItemRepository.findAllByRequestId(requestId), includeStock = false)
    }

    @Transactional
    fun reassign(requestId: Int, actor: User, managerId: Int): ResourceRequestResponseDto {
        if (actor.role != Role.ADMIN) forbidden()
        val request = requestRepository.findForUpdateById(requestId) ?: throw ResourceNotFoundException("Request not found")
        val oldManager = request.assignedManagerId
        val manager = users.findById(managerId).orElseThrow { badRequest("Manager not found") }
        if (!manager.active || manager.role != Role.DEPARTMENT_MANAGER || manager.department.id != request.departmentId) badRequest("Manager must be active and belong to the request department")
        request.assignedManagerId = manager.id
        request.needsReassignment = false
        val saved = requestRepository.saveAndFlush(request)
        historyRepository.save(RequestHistory(requestId = requestId, eventType = "REASSIGNED", fromStatus = request.status.name, toStatus = request.status.name, actorId = actor.id, comment = "Manager changed from ${oldManager ?: "unassigned"} to ${manager.id}"))
        notifications.notify((listOfNotNull(oldManager) + manager.id).distinct(), NotificationType.REASSIGNED, mapOf("requestId" to requestId, "requestNumber" to request.requestNumber))
        return saved.toResponse(lineItemRepository.findAllByRequestId(requestId), includeStock = true)
    }

    @Transactional
    fun adjustApprovedQuantity(requestId: Int, lineItemId: Int, caller: User, dto: ApprovedQuantityUpdateRequest): ResourceRequestResponseDto {
        val request = requestRepository.findForUpdateById(requestId) ?: throw ResourceNotFoundException("Request not found")
        if (caller.role != Role.DEPARTMENT_MANAGER || request.assignedManagerId != caller.id) forbidden()
        if (request.status != RequestStatus.APPROVED) conflict("Approved quantities can only change on approved requests")
        val line = lineItemRepository.findById(lineItemId).orElseThrow { ResourceNotFoundException("Line item not found") }
        if (line.requestId != requestId) throw ResourceNotFoundException("Line item not found")
        if (dto.quantity !in 1..line.quantity) badRequest("Approved quantity must be between 1 and requested quantity")
        val previous = line.approvedQuantity ?: line.quantity
        val issued = issueRepository.findAllByRequestIdAndLineItemId(requestId, lineItemId).sumOf { it.quantity }
        if (dto.quantity < issued) conflict("Approved quantity cannot be lower than issued quantity")
        if (dto.quantity > previous && line.resourceId != null) {
            val resource = resources.findForUpdateById(line.resourceId!!) ?: throw ResourceNotFoundException("Resource not found")
            val increase = dto.quantity - previous
            if (line.fulfillmentPreference == FulfillmentPreference.INTERNAL.name && increase > availability.availableQuantity(resource, lockReservations = true)) {
                conflict("Insufficient available stock; available ${availability.availableQuantity(resource, lockReservations = true)}")
            }
        }
        line.approvedQuantity = dto.quantity
        lineItemRepository.save(line)
        if (dto.quantity < previous) {
            reservations.releaseExcessForLine(requestId, lineItemId, dto.quantity, line.waivedQuantity, caller.id, dto.reason)
                .sorted()
                .forEach(waitlistService::onStockReturned)
        }
        historyRepository.save(RequestHistory(requestId = requestId, eventType = "QUANTITY_ADJUSTED", fromStatus = request.status.name, toStatus = request.status.name, actorId = caller.id, lineItemId = lineItemId, quantity = dto.quantity, comment = "Approved quantity changed from $previous to ${dto.quantity}. ${dto.reason}"))
        return request.toResponse(lineItemRepository.findAllByRequestId(requestId), includeStock = true)
    }

    @Transactional
    fun waiveLineBalance(requestId: Int, lineItemId: Int, caller: User, dto: WaiveLineBalanceRequest): ResourceRequestResponseDto {
        if (caller.role != Role.DEPARTMENT_MANAGER) forbidden()
        val request = requestRepository.findForUpdateById(requestId) ?: throw ResourceNotFoundException("Request not found")
        if (request.assignedManagerId != caller.id) forbidden("Only the assigned manager may waive a line balance")
        if (request.status != RequestStatus.APPROVED && request.status != RequestStatus.WAITLISTED) conflict("A balance can only be waived on an approved or waitlisted request")
        val line = lineItemRepository.findById(lineItemId).orElseThrow { ResourceNotFoundException("Line item not found") }
        if (line.requestId != requestId) throw ResourceNotFoundException("Line item not found")
        val approved = line.approvedQuantity ?: line.quantity
        val issued = issueRepository.findAllByRequestIdAndLineItemId(requestId, lineItemId).sumOf { it.quantity }
        val received = procurementService.acceptedQuantity(requestId, lineItemId)
        val fulfilled = if (line.fulfillmentPreference == FulfillmentPreference.EXTERNAL.name) received else issued
        val outstanding = (approved - fulfilled - line.waivedQuantity).coerceAtLeast(0)
        if (dto.quantity > outstanding) badRequest("Waived quantity exceeds the outstanding balance of $outstanding")
        line.waivedQuantity += dto.quantity
        lineItemRepository.save(line)
        val backorderResource = if (line.fulfillmentPreference == FulfillmentPreference.INTERNAL.name) {
            waitlistService.reduceBackorderedQuantity(lineItemId, dto.quantity, caller.id, dto.reason.trim())
        } else {
            null
        }
        val releasedResources = if (line.fulfillmentPreference == FulfillmentPreference.INTERNAL.name) {
            reservations.releaseExcessForLine(requestId, lineItemId, approved, line.waivedQuantity, caller.id, dto.reason.trim())
        } else {
            emptySet()
        }
        (releasedResources + listOfNotNull(backorderResource)).distinct().sorted().forEach(waitlistService::onStockReturned)
        historyRepository.save(RequestHistory(requestId = requestId, eventType = "BALANCE_WAIVED", fromStatus = request.status.name, toStatus = request.status.name, actorId = caller.id, lineItemId = lineItemId, quantity = dto.quantity, comment = dto.reason))
        requestCompletionService?.completeIfFulfilled(requestId, caller.id)
        return request.toResponse(lineItemRepository.findAllByRequestId(requestId), includeStock = true)
    }

    @Transactional
    fun updateExpectedFulfillmentDate(requestId: Int, lineItemId: Int, caller: User, dto: UpdateExpectedFulfillmentDateRequest): ResourceRequestResponseDto {
        if (caller.role != Role.DEPARTMENT_MANAGER) forbidden()
        val request = requestRepository.findForUpdateById(requestId) ?: throw ResourceNotFoundException("Request not found")
        if (request.assignedManagerId != caller.id) forbidden("Only the assigned manager may update expected fulfillment dates")
        val line = lineItemRepository.findById(lineItemId).orElseThrow { ResourceNotFoundException("Line item not found") }
        if (line.requestId != requestId) throw ResourceNotFoundException("Line item not found")
        waitlistService.updateExpectedFulfillmentDate(requestId, lineItemId, dto.expectedFulfillmentDate, caller.id)
        historyRepository.save(RequestHistory(requestId = requestId, eventType = "BACKORDER_DATE_UPDATED", fromStatus = request.status.name, toStatus = request.status.name, actorId = caller.id, lineItemId = lineItemId, comment = "Expected fulfillment date changed to ${dto.expectedFulfillmentDate}"))
        notifications.notify(listOfNotNull(request.requesterId, request.assignedManagerId), NotificationType.BACKORDER_DATE_UPDATED, mapOf("requestId" to requestId, "requestNumber" to request.requestNumber, "lineItemId" to lineItemId, "expectedFulfillmentDate" to dto.expectedFulfillmentDate.toString()))
        return request.toResponse(lineItemRepository.findAllByRequestId(requestId), includeStock = true)
    }

    private data class NormalizedLine(
        val dto: RequestLineItemDto,
        val resource: Resource?,
        val preference: FulfillmentPreference,
        val description: String,
        val manufacturer: String,
        val unitPrice: BigDecimal,
        val unitOfMeasure: String?,
    )

    private fun normalizeLines(items: List<RequestLineItemDto>, vendorId: Int?): List<NormalizedLine> {
        if (items.isEmpty()) badRequest("At least one line item is required")
        return items.mapIndexed { index, dto ->
            if (dto.quantity !in 1..100000) badRequest("Line ${index + 1}: quantity must be between 1 and 100000")
            val resource = dto.resourceId?.let { resources.findById(it).orElseThrow { badRequest("Line ${index + 1}: resource not found") } }
            if (resource != null && !resource.active) badRequest("Line ${index + 1}: resource is inactive")
            val preference = when (resource?.type) {
                ResourceType.INTERNAL -> FulfillmentPreference.INTERNAL
                ResourceType.EXTERNAL -> FulfillmentPreference.EXTERNAL
                null -> dto.fulfillmentPreference
            }
            if (preference == FulfillmentPreference.INTERNAL && (dto.requestedDurationDays == null || dto.requestedDurationDays <= 0)) {
                badRequest("Line ${index + 1}: a positive requestedDurationDays is required for internal resources")
            }
            val description = resource?.name ?: dto.description.trim()
            if (description.isBlank()) badRequest("Line ${index + 1}: resourceId or description is required")
            if (description.length > 255 || dto.manufacturer.length > 120 || (dto.unitOfMeasure?.length ?: 0) > 50) badRequest("Line ${index + 1}: text fields exceed their maximum length")
            if (preference == FulfillmentPreference.EXTERNAL && vendorId == null) badRequest("vendorId is required for external lines")
            if (dto.unitPrice < BigDecimal.ZERO || dto.unitPrice.scale() > 2) badRequest("Line ${index + 1}: invalid unit price")
            if (preference == FulfillmentPreference.EXTERNAL && dto.unitPrice <= BigDecimal.ZERO) badRequest("Line ${index + 1}: external unit price must be positive")
            if (dto.category == RequestCategory.CONSUMABLE && dto.batchNumber.isNullOrBlank()) badRequest("Line ${index + 1}: batch number is required for consumables")
            if (dto.category != RequestCategory.CONSUMABLE && dto.batchNumber != null) badRequest("Line ${index + 1}: batch number is only valid for consumables")
            NormalizedLine(dto, resource, preference, description, resource?.let { "N/A" } ?: dto.manufacturer.ifBlank { "N/A" }, dto.unitPrice, dto.unitOfMeasure ?: resource?.unitOfMeasure)
        }
    }

    private fun canView(request: ResourceRequest, caller: User): Boolean = when (caller.role) {
        Role.TEAM_LEAD -> caller.teams.any { it.id == request.teamId }
        Role.DEPARTMENT_MANAGER -> request.assignedManagerId == caller.id
        Role.ADMIN -> request.needsReassignment
        Role.MANAGING_DIRECTOR -> true
        Role.MEMBER -> false
    }

    private fun canViewHistory(request: ResourceRequest, caller: User): Boolean = when (caller.role) {
        Role.TEAM_LEAD -> request.requesterId == caller.id
        Role.DEPARTMENT_MANAGER -> request.assignedManagerId == caller.id
        Role.ADMIN, Role.MANAGING_DIRECTOR -> true
        Role.MEMBER -> false
    }

    private fun user(id: Int): User = users.findById(id).orElseThrow { ResourceNotFoundException("User not found") }

    private fun ResourceRequest.toResponse(
        lines: List<ResourceRequestLineItem>,
        warnings: List<String> = emptyList(),
        includeStock: Boolean = false,
    ): ResourceRequestResponseDto {
        val team = teams.findById(teamId).orElseThrow { ResourceNotFoundException("Team not found") }
        val project = projects.findById(projectId).orElseThrow { ResourceNotFoundException("Project not found") }
        val requester = user(requesterId)
        val assignedManager = assignedManagerId?.let(::user)

        return ResourceRequestResponseDto(
            requestId = requireNotNull(requestId),
            requestNumber = requestNumber,
            status = status,
            lineItems = lines.map { line ->
                RequestLineItemResponseDto(
                    lineItemId = requireNotNull(line.lineItemId),
                    quantity = line.quantity,
                    type = ResourceType.valueOf(line.fulfillmentPreference),
                    requestedDurationDays = line.requestedDurationDays,
                    approvedDurationDays = line.approvedDurationDays,
                )
            },
        )
    }

    private fun waitlistProgress(entry: WaitlistEntry): WaitlistProgressResponse {
        val needsStock = (entry.status == WaitlistStatus.WAITING || entry.status == WaitlistStatus.FULFILLABLE) &&
            entry.reservedQuantity < entry.quantity
        val position = if (needsStock && entry.createdAt != null) {
            waitlistEntries.countAhead(
                entry.resourceId,
                listOf(WaitlistStatus.WAITING, WaitlistStatus.FULFILLABLE),
                entry.createdAt!!,
                entry.id,
            ).toInt() + 1
        } else null
        return WaitlistProgressResponse(
            entry.id,
            entry.status,
            entry.quantity,
            entry.reservedQuantity,
            entry.issuedQuantity,
            position,
        )
    }

    private fun fulfillmentStatus(requestId: Int, lines: List<ResourceRequestLineItem>, status: RequestStatus): String {
        val activeReservations = reservationRepository.findAllByRequestIdAndStatus(requestId, ReservationStatus.ACTIVE)
        val issues = issueRepository.findAllByRequestId(requestId)
        val issued = issues.sumOf { it.quantity }
        val returned = issues.sumOf { it.returnedQuantity }
        if (issued > 0 && returned >= issued) return "RETURNED"
        if (returned > 0) return "PARTIALLY_RETURNED"
        if (issued > 0) {
            val approvedTotal = lines.sumOf { it.approvedQuantity ?: it.quantity }
            return if (issued >= approvedTotal) "ISSUED" else "PARTIALLY_ISSUED"
        }
        if (activeReservations.isNotEmpty()) return "RESERVED"
        if (status == RequestStatus.WAITLISTED) return "WAITLISTED"
        return "NOT_STARTED"
    }

    private fun uniqueKey(): String = ByteArray(32).also(random::nextBytes).joinToString("") { "%02x".format(it.toInt() and 0xff) }

    private fun forbidden(message: String = "This action is not permitted"): Nothing = throw ResponseStatusException(HttpStatus.FORBIDDEN, message)
    private fun badRequest(message: String): Nothing = throw ResponseStatusException(HttpStatus.BAD_REQUEST, message)
    private fun conflict(message: String): Nothing = throw ResponseStatusException(HttpStatus.CONFLICT, message)

    companion object { private val random = SecureRandom() }
}
