package com.resourcetracker.request.dto

import com.resourcetracker.request.entity.RequestStatus
import java.time.LocalDate

data class RequestLineItemResponseDto(
    val lineItemId: Int,
    val quantity: Int,
)

data class ResourceRequestResponseDto(
    val requestId: Int,
    val requestNumber: String,
    val status: RequestStatus,
    val lineItems: List<RequestLineItemResponseDto>,
)