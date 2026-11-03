package com.resourcetracker.request.dto

import com.resourcetracker.request.entity.RequestStatus
import io.swagger.v3.oas.annotations.media.Schema
import java.math.BigDecimal
import java.time.LocalDateTime

data class RequestLineItemResponseDto(
    val lineItemId: Int,
    val quantity: Int,
    val availableQuantity: Int? = null,
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
