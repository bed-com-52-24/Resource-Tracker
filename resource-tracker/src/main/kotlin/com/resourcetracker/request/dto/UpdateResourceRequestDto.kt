package com.resourcetracker.request.dto

import jakarta.validation.Valid
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.Size
import jakarta.validation.constraints.NotEmpty

data class UpdateResourceRequestDto(
    @field:Positive
    val departmentId: Int,

    @field:Positive
    val projectId: Int,

    @field:Positive
    val teamId: Int,

    @field:Positive
    val vendorId: Int,

    @field:Size(max = 500)
    val justification: String? = null,

    @field:NotEmpty
    @field:Valid
    val lineItems: List<RequestLineItemDto>
)