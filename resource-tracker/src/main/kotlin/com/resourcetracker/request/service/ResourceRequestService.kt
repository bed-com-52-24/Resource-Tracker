package com.resourcetracker.request.service

import com.resourcetracker.request.dto.CreateResourceRequestDto
import com.resourcetracker.request.dto.RequestLineItemDto
import com.resourcetracker.request.dto.UpdateResourceRequestDto
import com.resourcetracker.request.entity.*
import com.resourcetracker.request.repository.*
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

@Service
class ResourceRequestService(
    private val requestRepository: ResourceRequestRepository,
    private val lineItemRepository: RequestLineItemRepository,
    private val numberService: RequestNumberService,
    private val validationService: RequestValidationService
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
    fun editRequest(
        requestId: Int,
        requesterId: Int,
        dto: UpdateResourceRequestDto
    ): ResourceRequest {
        val request = requestRepository.findOwnedRequest(
            requestId,
            requesterId
        ) ?: throw NoSuchElementException("Request not found")

        check(request.status == RequestStatus.QUERIED) {
            "Only queried requests can be edited"
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

        return requestRepository.save(request)
    }

    @Transactional
    fun resubmitRequest(
        requestId: Int,
        requesterId: Int
    ): ResourceRequest {
        val request = requestRepository.findOwnedRequest(
            requestId,
            requesterId
        ) ?: throw NoSuchElementException("Request not found")

        check(
            request.status == RequestStatus.QUERIED &&
            request.editedAfterQuery
        ) {
            "Only edited, queried requests can be resubmitted"
        }

        request.status = RequestStatus.PENDING
        request.editedAfterQuery = false
        request.queriedAt = null

        return requestRepository.save(request)
    }

    @Transactional
    fun cancelRequest(
        requestId: Int,
        requesterId: Int
    ): ResourceRequest {
        val request = requestRepository.findOwnedRequest(
            requestId,
            requesterId
        ) ?: throw NoSuchElementException("Request not found")

        check(request.status == RequestStatus.PENDING) {
            "Only pending requests can be cancelled"
        }

        request.status = RequestStatus.CANCELLED

        return requestRepository.save(request)
    }
}