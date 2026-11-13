package com.resourcetracker.resource

import com.resourcetracker.common.ResourceNotFoundException
import com.resourcetracker.request.entity.RequestStatus
import com.resourcetracker.request.entity.WaitlistStatus
import com.resourcetracker.request.repository.ExpectedReturnProjection
import com.resourcetracker.request.repository.RequestLineItemRepository
import com.resourcetracker.request.repository.StockIssueRepository
import com.resourcetracker.request.service.StockAvailabilityService
import com.resourcetracker.request.service.StockCorrectionRequest
import com.resourcetracker.request.service.StockOperationsService
import com.resourcetracker.security.AuthenticatedUser
import com.resourcetracker.user.Role
import com.resourcetracker.user.UserRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Clock
import java.time.LocalDate

@Service
class InventoryReportingService(
    private val resources: ResourceRepository,
    private val issues: StockIssueRepository,
    private val lines: RequestLineItemRepository,
    private val availability: StockAvailabilityService,
    private val users: UserRepository,
    private val reconciliations: ResourceReconciliationRepository,
    private val stockOperations: StockOperationsService,
    private val clock: Clock,
) {
    @Transactional(readOnly = true)
    fun utilisation(
        caller: AuthenticatedUser,
        departmentId: Int?,
        category: ResourceCategory?,
        status: ResourceStatus?,
        pageable: Pageable,
    ): Page<ResourceUtilisationReport> {
        val departmentScope = departmentScope(caller, departmentId)
        val page = resources.findAll(
            ResourceSpecifications.filter(
                active = null,
                type = ResourceType.INTERNAL,
                category = category,
                status = status,
                keyword = null,
            ),
            pageable,
        )
        val today = LocalDate.now(clock)
        val totals = if (page.isEmpty) emptyMap() else {
            issues.summarizeResources(page.content.map { it.id }, today).associateBy { it.resourceId }
        }
        return page.map { resource ->
            val issueTotals = totals[resource.id]
            val onHand = resource.quantity ?: 0
            val currentlyIssued = issueTotals?.outstandingQuantity ?: 0L
            val total = onHand.toLong() + resource.unavailableQuantity + currentlyIssued
            // Utilisation is the share of managed units currently out on issue.
            val rate = if (total == 0L) BigDecimal.ZERO.setScale(2) else {
                BigDecimal.valueOf(currentlyIssued)
                    .multiply(BigDecimal.valueOf(100))
                    .divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP)
            }
            ResourceUtilisationReport(
                resourceId = resource.id,
                code = resource.code,
                name = resource.name,
                departmentId = departmentScope,
                category = resource.category,
                onHandQuantity = onHand,
                unavailableQuantity = resource.unavailableQuantity,
                totalQuantity = total,
                totalIssuedQuantity = issueTotals?.totalIssuedQuantity ?: 0L,
                currentlyIssuedQuantity = currentlyIssued,
                utilisationRate = rate,
                overdueReturnQuantity = issueTotals?.overdueQuantity ?: 0L,
            )
        }
    }

    @Transactional(readOnly = true)
    fun availabilityReport(
        caller: AuthenticatedUser,
        departmentId: Int?,
        category: ResourceCategory?,
        status: ResourceStatus?,
        pageable: Pageable,
    ): Page<ResourceAvailabilityReport> {
        val departmentScope = departmentScope(caller, departmentId)
        return resources.findAll(
            ResourceSpecifications.filter(
                active = null,
                type = ResourceType.INTERNAL,
                category = category,
                status = status,
                keyword = null,
            ),
            pageable,
        ).map { resource ->
            ResourceAvailabilityReport(
                resourceId = resource.id,
                code = resource.code,
                name = resource.name,
                departmentId = departmentScope,
                category = resource.category,
                status = resource.status,
                onHandQuantity = resource.quantity ?: 0,
                reservedQuantity = availability.reservedQuantity(resource.id),
                unavailableQuantity = resource.unavailableQuantity,
                availableQuantity = availability.availableQuantity(resource),
            )
        }
    }

    @Transactional(readOnly = true)
    fun stockBalanceReport(
        caller: AuthenticatedUser,
        departmentId: Int?,
        pageable: Pageable,
    ): Page<ResourceStockBalanceReport> {
        val departmentScope = departmentScope(caller, departmentId)
        val page = resources.findAll(
            ResourceSpecifications.filter(
                active = null,
                type = ResourceType.INTERNAL,
                category = null,
                status = null,
                keyword = null,
            ),
            pageable,
        )
        val resourceIds = page.content.map { it.id }
        val reserved = availability.reservedQuantities(resourceIds)
        val issued = if (resourceIds.isEmpty()) emptyMap() else {
            issues.summarizeResources(resourceIds, LocalDate.now(clock)).associateBy { it.resourceId }
        }
        val waitlisted = if (resourceIds.isEmpty()) emptyMap() else {
            lines.sumWaitingQuantityByResourceIds(
                resourceIds,
                setOf(WaitlistStatus.WAITING, WaitlistStatus.FULFILLABLE),
            ).associate { it.resourceId to it.quantity }
        }

        return page.map { resource ->
            val onHand = resource.quantity ?: 0
            val reservedQuantity = reserved[resource.id] ?: 0
            ResourceStockBalanceReport(
                resourceId = resource.id,
                code = resource.code,
                name = resource.name,
                departmentId = departmentScope,
                onHandQuantity = onHand,
                reservedQuantity = reservedQuantity,
                issuedQuantity = issued[resource.id]?.outstandingQuantity ?: 0L,
                unavailableQuantity = resource.unavailableQuantity,
                waitlistedQuantity = waitlisted[resource.id] ?: 0L,
                availableQuantity = availability.availableQuantity(resource, knownReservedQuantity = reservedQuantity),
            )
        }
    }

    @Transactional(readOnly = true)
    fun waitlist(caller: AuthenticatedUser, departmentId: Int?, pageable: Pageable): Page<ResourceWaitlistReport> {
        val departmentScope = departmentScope(caller, departmentId)
        val waiting = lines.findApprovedWaitlist(
            ResourceType.INTERNAL,
            listOf(RequestStatus.APPROVED, RequestStatus.PARTIALLY_APPROVED),
            departmentScope,
            pageable,
        )
        val resourceIds = waiting.content.map { it.resourceId }.distinct()
        val resourceById = if (resourceIds.isEmpty()) emptyMap() else resources.findAllById(resourceIds).associateBy { it.id }
        val currentAvailability = resourceById.mapValues { (_, resource) -> availability.availableQuantity(resource) }
        val expectedReturns = if (resourceIds.isEmpty()) emptyMap() else {
            issues.findExpectedReturns(resourceIds, LocalDate.now(clock)).groupBy { it.resourceId }
        }
        return waiting.map { entry ->
            val available = currentAvailability[entry.resourceId] ?: 0
            val shortfall = (entry.remainingQuantity - available.toLong()).coerceAtLeast(0)
            ResourceWaitlistReport(
                requestId = entry.requestId,
                requestNumber = entry.requestNumber,
                requestStatus = entry.requestStatus.name,
                lineItemId = entry.lineItemId,
                resourceId = entry.resourceId,
                resourceName = entry.resourceName,
                remainingQuantity = entry.remainingQuantity,
                availableQuantity = available,
                expectedAvailabilityDate = expectedAvailabilityDate(
                    shortfall,
                    expectedReturns[entry.resourceId].orEmpty(),
                ),
                requiredDate = entry.requiredDate,
            )
        }
    }

    @Transactional
    fun reconcile(resourceId: Int, actorId: Int, role: Role, request: ReconciliationRequest): ReconciliationReport {
        requireManagingDirector(role)
        if (request.physicalCount < 0) badRequest("physicalCount must be non-negative")
        val reason = request.reason.trim()
        if (reason.isEmpty() || reason.length > 470) badRequest("reason must contain 1 to 470 characters")
        val resource = resources.findForUpdateById(resourceId) ?: throw ResourceNotFoundException("Resource not found")
        if (resource.type != ResourceType.INTERNAL) badRequest("Only internal resources can be reconciled")
        val systemQuantity = resource.quantity ?: 0
        val variance = request.physicalCount - systemQuantity
        val adjustmentApplied = request.adjustStock && variance != 0
        if (adjustmentApplied) {
            stockOperations.correctResource(
                resourceId,
                actorId,
                StockCorrectionRequest(request.physicalCount, "Inventory reconciliation: $reason"),
            )
        }
        return reconciliations.save(
            ResourceReconciliation(
                resourceId = resourceId,
                systemQuantity = systemQuantity,
                physicalCount = request.physicalCount,
                variance = variance,
                adjustmentApplied = adjustmentApplied,
                reason = reason,
                actorId = actorId,
            ),
        ).toReport()
    }

    @Transactional(readOnly = true)
    fun reconciliationHistory(resourceId: Int, role: Role): List<ReconciliationReport> {
        requireManagingDirector(role)
        if (!resources.existsById(resourceId)) throw ResourceNotFoundException("Resource not found")
        return reconciliations.findAllByResourceIdOrderByCreatedAtAscIdAsc(resourceId).map { it.toReport() }
    }

    private fun departmentScope(caller: AuthenticatedUser, requestedDepartmentId: Int?): Int? {
        if (requestedDepartmentId != null && requestedDepartmentId < 1) badRequest("departmentId must be positive")
        return when (caller.role) {
            Role.ADMIN, Role.MANAGING_DIRECTOR -> requestedDepartmentId
            Role.DEPARTMENT_MANAGER -> {
                val departmentId = users.findById(caller.id)
                    .orElseThrow { ResourceNotFoundException("User not found") }
                    .department?.id ?: forbidden()
                if (requestedDepartmentId != null && requestedDepartmentId != departmentId) forbidden()
                departmentId
            }
            else -> forbidden()
        }
    }

    private fun expectedAvailabilityDate(
        shortfall: Long,
        expectedReturns: List<ExpectedReturnProjection>,
    ): LocalDate? {
        if (shortfall == 0L) return LocalDate.now(clock)
        var expectedQuantity = 0L
        expectedReturns.groupBy { it.expectedDate }
            .toSortedMap()
            .forEach { (date, returns) ->
                expectedQuantity += returns.sumOf { it.quantity }
                if (expectedQuantity >= shortfall) return date
            }
        return null
    }

    private fun ResourceReconciliation.toReport() = ReconciliationReport(
        id = id,
        resourceId = resourceId,
        systemQuantity = systemQuantity,
        physicalCount = physicalCount,
        variance = variance,
        adjustmentApplied = adjustmentApplied,
        reason = reason,
        actorId = actorId,
        createdAt = createdAt,
    )

    private fun requireManagingDirector(role: Role) {
        if (role != Role.MANAGING_DIRECTOR) forbidden()
    }

    private fun forbidden(): Nothing = throw ResponseStatusException(HttpStatus.FORBIDDEN, "Action is not permitted")
    private fun badRequest(message: String): Nothing = throw ResponseStatusException(HttpStatus.BAD_REQUEST, message)
}
