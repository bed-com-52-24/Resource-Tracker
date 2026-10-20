package com.resourcetracker.request.service

import com.resourcetracker.request.dto.CreateResourceRequestDto
import com.resourcetracker.request.dto.RequestLineItemDto
import com.resourcetracker.request.dto.UpdateResourceRequestDto
import com.resourcetracker.request.dto.RequesterUpdateStatus
import com.resourcetracker.request.entity.*
import com.resourcetracker.request.repository.*
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
    private val idempotencyService: RequestIdempotencyService
) {

    @Transactional
    fun createRequest(
        dto: CreateResourceRequestDto,
        requesterId: Int,
        assignedManagerId: Int
    ): ResourceRequest {
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

        if (request.status != RequestStatus.QUERIED) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Only queried requests can be edited")
        }

        validationService.validateLineItems(dto.lineItems)

        val total = validationService.calculateTotal(dto.lineItems)
        val firstItem = dto.lineItems.first()

        request.departmentId = dto.departmentId
        request.projectId = dto.projectId
        request.teamId = dto.teamId
        request.vendorId = dto.vendorId
        request.justification = dto.justification
        request.totalAmount = total

        // Keep the legacy single-item columns consistent.
        request.itemDescription = firstItem.description
        request.manufacturer = firstItem.manufacturer
        request.unitPrice = firstItem.unitPrice
        request.quantity = firstItem.quantity
        request.batchNumber = firstItem.batchNumber
        request.editedAfterQuery = true

        lineItemRepository.deleteAllByRequestId(requestId)

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

        if (dto.status == RequesterUpdateStatus.PENDING) {
            requireTransition(request.status, RequestStatus.PENDING)
            request.status = RequestStatus.PENDING
            request.editedAfterQuery = false
            request.queriedAt = null
        }
        return requestRepository.save(request)
    }

    private fun requireTransition(from: RequestStatus, to: RequestStatus) {
        val allowed = when (from) {
            RequestStatus.PENDING -> setOf(RequestStatus.CANCELLED)
            RequestStatus.QUERIED -> setOf(RequestStatus.PENDING, RequestStatus.CANCELLED)
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
        assignedManagerId: Int,
        idempotencyId: Long
    ): RequestCreationResult {
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
