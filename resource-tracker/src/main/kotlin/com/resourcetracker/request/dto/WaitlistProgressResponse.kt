package com.resourcetracker.request.dto

import com.resourcetracker.request.entity.WaitlistStatus
import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Fulfilment progress of a waitlisted request line item")
data class WaitlistProgressResponse(
    val entryId: Int,
    val status: WaitlistStatus,
    val quantity: Int,
    val reservedQuantity: Int,
    val issuedQuantity: Int,
    @Schema(description = "1 = next in line for this resource; null when the entry no longer needs stock")
    val queuePosition: Int?,
)