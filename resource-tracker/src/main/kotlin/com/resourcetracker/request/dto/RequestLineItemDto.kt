package com.resourcetracker.request.dto

import com.resourcetracker.request.entity.RequestCategory
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.Digits
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.math.BigDecimal

data class RequestLineItemDto(
    @field:NotBlank
    @field:Size(min = 3, max = 255)
    val description: String,

    @field:NotBlank
    @field:Size(min = 2, max = 120)
    val manufacturer: String,

    @field:NotNull
    @field:DecimalMin(value = "0", inclusive = false)
    @field:Digits(integer = 17, fraction = 2)
    val unitPrice: BigDecimal,

    @field:Min(1)
    @field:Max(100000)
    val quantity: Int,

    @field:NotNull
    val category: RequestCategory,

    @field:Size(max = 50)
    val batchNumber: String? = null
)