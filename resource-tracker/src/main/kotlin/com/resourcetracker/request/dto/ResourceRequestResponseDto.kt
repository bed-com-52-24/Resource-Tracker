package com.resourcetracker.request.dto

import com.resourcetracker.request.entity.RequestCategory
import com.resourcetracker.request.entity.RequestStatus
import java.math.BigDecimal
import java.time.LocalDateTime

data class RequestLineItemResponseDto(
    val lineItemId: Int,
    val description: String,
    val manufacturer: String,
    val unitPrice: BigDecimal,
    val quantity: Int,
    val category: RequestCategory,
    val batchNumber: String?
)

data class ResourceRequestResponseDto(
    val requestId: Int,
    val requestNumber: String,
    val departmentId: Int,
    val projectId: Int,
    val teamId: Int,
    val vendorId: Int,
    val requesterId: Int,
    val assignedManagerId: Int,
    val justification: String?,
    val totalAmount: BigDecimal,
    val status: RequestStatus,
    val editedAfterQuery: Boolean,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
    val lineItems: List<RequestLineItemResponseDto>
)