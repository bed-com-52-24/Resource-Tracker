package com.resourcetracker.request.dto

import com.resourcetracker.request.entity.RequestCategory
import com.resourcetracker.request.entity.RequestStatus
import io.swagger.v3.oas.annotations.media.Schema
import java.math.BigDecimal
import java.time.LocalDateTime
import java.time.LocalDate

data class RequestLineItemResponseDto(
    val lineItemId: Int,
    val description: String,
    val manufacturer: String,
    val unitPrice: BigDecimal,
    val quantity: Int,
    val category: RequestCategory,
    val batchNumber: String?,
    val resourceId: Int? = null,
    val fulfillmentPreference: String = "INTERNAL",
    val unitOfMeasure: String? = null,
    val approvedQuantity: Int? = null,
    val physicalQuantity: Int? = null,
    val reservedQuantity: Int? = null,
    val unavailableQuantity: Int? = null,
    val availableQuantity: Int? = null,
    @Schema(description = "Present only when the line item is waitlisted")
    val waitlist: WaitlistProgressResponse? = null,
)

data class ResourceRequestResponseDto(
    val requestId: Int,
    val requestNumber: String,
    val departmentId: Int,
    val projectId: Int,
    val teamId: Int,
    val vendorId: Int?,
    val requesterId: Int,
    val assignedManagerId: Int?,
    val needsReassignment: Boolean = false,
    val requiredDate: LocalDate? = null,
    val justification: String?,
    val totalAmount: BigDecimal,
    val status: RequestStatus,
    val editedAfterQuery: Boolean,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
    val lineItems: List<RequestLineItemResponseDto>,
    val warnings: List<String> = emptyList(),
    val fulfillmentStatus: String = "NOT_STARTED",
)
