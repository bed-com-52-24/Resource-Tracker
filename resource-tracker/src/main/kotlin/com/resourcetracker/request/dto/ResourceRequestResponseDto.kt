package com.resourcetracker.request.dto

import com.resourcetracker.request.entity.RequestStatus
import io.swagger.v3.oas.annotations.media.Schema
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

data class RequestLineItemResponseDto(
    val lineItemId: Int,
    val quantity: Int,
    val approvedQuantity: Int? = null,
    val issuedQuantity: Int = 0,
    val acceptedQuantity: Int = 0,
    val waivedQuantity: Int = 0,
    val outstandingQuantity: Int = 0,
    val availableQuantity: Int? = null,
    val expectedFulfillmentDate: LocalDate? = null,
)

data class ResourceRequestResponseDto(
    val requestId: Int,
    val requestNumber: String,
    val requesterId: Int,
    val assignedManagerId: Int?,
    val totalAmount: BigDecimal,
    val status: RequestStatus,
    val createdAt: LocalDateTime,
    val lineItems: List<RequestLineItemResponseDto>,
)
