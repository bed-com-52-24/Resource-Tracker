package com.resourcetracker.request.dto

import com.resourcetracker.request.entity.FulfillmentPreference
import com.resourcetracker.request.entity.RequestCategory
import jakarta.validation.Valid
import jakarta.validation.constraints.FutureOrPresent
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.Size
import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDate

data class CreateResourceRequestDto(
    @field:Positive val teamId: Int,
    @field:Positive val projectId: Int,
    @field:NotBlank @field:Size(max = 500) val justification: String,
    @field:NotNull @field:FutureOrPresent val requiredDate: LocalDate,
    @field:NotEmpty @field:Valid val lineItems: List<RequestLineItemDto>,
)

data class RequestLineItemDto(
    val resourceId: Int? = null,
    @field:Size(max = 255) val description: String = "",
    @field:Size(max = 120) val manufacturer: String? = null,
    val unitPrice: java.math.BigDecimal = java.math.BigDecimal.ZERO,
    @field:Positive val quantity: Int,
    val category: RequestCategory = RequestCategory.OTHER,
    @field:Size(max = 50) val batchNumber: String? = null,
    @field:Schema(description = "Optional unit of measure for this item.", nullable = true)
    val unitOfMeasure: String? = null,
    @field:Positive
    @field:Schema(description = "Vendor for external procurement lines. Required when fulfillmentPreference is EXTERNAL.", nullable = true)
    val vendorId: Int? = null,
    val fulfillmentPreference: FulfillmentPreference = FulfillmentPreference.INTERNAL,
    @field:Positive
    @field:Schema(description = "Requested use duration in days for internal resources. The manager may approve a different duration.", example = "30")
    val requestedDurationDays: Int? = null,
)
