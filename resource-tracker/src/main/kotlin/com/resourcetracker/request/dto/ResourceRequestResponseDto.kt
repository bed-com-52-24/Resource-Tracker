package com.resourcetracker.request.dto

import com.resourcetracker.request.entity.RequestStatus
import com.resourcetracker.resource.ResourceType
import java.time.LocalDate

data class RequestLineItemResponseDto(
    val lineItemId: Int,
    val quantity: Int,
    val approvedQuantity: Int?,
    val type: ResourceType,
    val requestedDurationDays: Int?,
    val approvedDurationDays: Int?,
)

data class ResourceRequestResponseDto(
    val requestId: Int,
    val requestNumber: String,
    val status: RequestStatus,
    val lineItems: List<RequestLineItemResponseDto>,
)
