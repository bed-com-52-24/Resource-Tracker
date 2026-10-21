package com.resourcetracker.resource

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.Size

@Schema(description = "Information required to create a resource")
data class CreateResourceRequest(
    @field:NotBlank
    @field:Size(max = 120)
    val name: String,

    @field:NotNull
    val type: ResourceType,

    @field:NotNull
    val category: ResourceCategory,

    @field:NotBlank
    @field:Size(max = 50)
    val unitOfMeasure: String,

    @field:Size(max = 500)
    val description: String? = null,

    @field:Min(0)
    val quantity: Int? = null,
    @field:Size(max = 120)
    val location: String? = null,
    @field:Min(0)
    val reorderLevel: Int = 0,

    val departmentId: Int? = null,
    val preferredVendorId: Int? = null,
)

@Schema(description = "Editable fields for a resource")
data class UpdateResourceRequest(
    @field:NotBlank
    @field:Size(max = 120)
    val name: String,

    @field:NotNull
    val type: ResourceType,

    @field:NotNull
    val category: ResourceCategory,

    @field:NotBlank
    @field:Size(max = 50)
    val unitOfMeasure: String,

    @field:Size(max = 500)
    val description: String? = null,

    @field:Min(0)
    val quantity: Int? = null,
    @field:Size(max = 120)
    val location: String? = null,
    @field:Min(0)
    val reorderLevel: Int = 0,

    val departmentId: Int? = null,
    val preferredVendorId: Int? = null,
    val active: Boolean? = null,
)

data class ResourceResponse(
    val id: Int,
    val code: String,
    val name: String,
    val type: ResourceType,
    val category: ResourceCategory,
    val unitOfMeasure: String,
    val description: String?,
    val active: Boolean,
    val quantity: Int?,
    val location: String?,
    val reorderLevel: Int,
    val status: ResourceStatus?,
    val departmentId: Int?,
    val preferredVendorId: Int?,
)
