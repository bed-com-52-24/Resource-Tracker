package com.resourcetracker.request.service

import com.resourcetracker.request.dto.CreateResourceRequestDto
import com.resourcetracker.request.dto.UpdateResourceRequestDto
import com.resourcetracker.request.dto.RequesterUpdateStatus
import com.resourcetracker.request.entity.*
import com.resourcetracker.request.repository.*
import com.resourcetracker.team.TeamRepository
import com.resourcetracker.common.BusinessRuleException
import com.resourcetracker.common.ResourceNotFoundException
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import tools.jackson.databind.ObjectMapper
import java.math.BigDecimal

@Service
class ResourceRequestService(
    private val requestRepository: ResourceRequestRepository,
    private val lineItemRepository: RequestLineItemRepository,
    private val numberService: RequestNumberService,
    private val validationService: RequestValidationService,
    private val objectMapper: ObjectMapper,
    private val idempotencyService: RequestIdempotencyService,
    private val teams: TeamRepository
) {

    @Transactional
    fun createRequest(
        dto: CreateResourceRequestDto,
        requesterId: Int
    ): ResourceRequest {
        val team = teams.findById(dto.teamId).orElseThrow { ResourceNotFoundException("Team not found") }
        if (!team.isActive) throw BusinessRuleException("Team is inactive")
        val assignedManagerId = team.manager?.id ?: throw BusinessRuleException("Team has no manager")
        validationService.validateLineItems(dto.lineItems)

        val total = validationService.calculateTotal(dto.lineItems)
        val firstItem = dto.lineItems.first()

        val request = requestRepository.save(
            ResourceRequest(
                requestNumber = numberService.generateRequestNumber(),
                itemDescription = firstItem.description,
                manufacturer = firstItem.manufacturer,
                unitPrice = firstItem.unitPrice,
                quantity = firstItem.quantity,
                batchNumber = firstItem.batchNumber,
                vendorId = dto.vendorId,
                departmentId = dto.departmentId,
                projectId = dto.projectId,
                teamId = dto.teamId,
                requesterId = requesterId,
                assignedManagerId = assignedManagerId,
                status = RequestStatus.PENDING,
                justification = dto.justification,
                totalAmount = total
            )
        )

        val requestId = requireNotNull(request.requestId)

        lineItemRepository.saveAll(
            dto.lineItems.map { item ->
                ResourceRequestLineItem(
                    requestId = requestId,
                    description = item.description,
                    manufacturer = item.manufacturer,
                    unitPrice = item.unitPrice,
                    quantity = item.quantity,
                    category = item.category,
                    batchNumber = item.batchNumber
                )
            }
        )

        return request
    }

    @Transactional(readOnly = true)
    fun listMyRequests(
        requesterId: Int,
        pageable: Pageable
    ): Page<ResourceRequest> =
        requestRepository.findAllByRequesterId(
            requesterId,
            pageable
        )

    @Transactional
    fun updateRequest(
        requestId: Int,
        requesterId: Int,
        dto: UpdateResourceRequestDto
    ): ResourceRequest {
        val request = requestRepository.findOwnedRequest(
            requestId,
            requesterId
        ) ?: throw NoSuchElementException("Request not found")

        if (dto.status == RequesterUpdateStatus.CANCELLED) {
            requireTransition(request.status, RequestStatus.CANCELLED)
            request.status = RequestStatus.CANCELLED
            return requestRepository.save(request)
        }

        throw ResponseStatusException(HttpStatus.CONFLICT, "Requests cannot be edited after submission")
    }

    private fun requireTransition(from: RequestStatus, to: RequestStatus) {
        val allowed = when (from) {
            RequestStatus.PENDING -> setOf(RequestStatus.CANCELLED)
            else -> emptySet()
        }
        if (to !in allowed) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Cannot change a request from $from to $to")
        }
    }
    @Transactional
    fun createRequestIdempotently(
        dto: CreateResourceRequestDto,
        requesterId: Int,
        idempotencyId: Long
    ): RequestCreationResult {
        val team = teams.findById(dto.teamId).orElseThrow { ResourceNotFoundException("Team not found") }
        if (!team.isActive) throw BusinessRuleException("Team is inactive")
        val assignedManagerId = team.manager?.id ?: throw BusinessRuleException("Team has no manager")
        validationService.validateLineItems(dto.lineItems)

        val total = validationService.calculateTotal(dto.lineItems)
        val firstItem = dto.lineItems.first()

        val request = requestRepository.save(
            ResourceRequest(
                requestNumber = numberService.generateRequestNumber(),
                itemDescription = firstItem.description,
                manufacturer = firstItem.manufacturer,
                unitPrice = firstItem.unitPrice,
                quantity = firstItem.quantity,
                batchNumber = firstItem.batchNumber,
                vendorId = dto.vendorId,
                departmentId = dto.departmentId,
                projectId = dto.projectId,
                teamId = dto.teamId,
                requesterId = requesterId,
                assignedManagerId = assignedManagerId,
                status = RequestStatus.PENDING,
                justification = dto.justification,
                totalAmount = total
            )
        )

        val requestId = requireNotNull(request.requestId)

        lineItemRepository.saveAll(
            dto.lineItems.map { item ->
                ResourceRequestLineItem(
                    requestId = requestId,
                    description = item.description,
                    manufacturer = item.manufacturer,
                    unitPrice = item.unitPrice,
                    quantity = item.quantity,
                    category = item.category,
                    batchNumber = item.batchNumber
                )
            }
        )

        val body = objectMapper.writeValueAsString(request)

        idempotencyService.complete(
            idempotencyId = idempotencyId,
            requestId = requestId,
            responseStatus = 201,
            responseBody = body
        )

        return RequestCreationResult(
            httpStatus = 201,
            responseBody = body
        )
    }
}
