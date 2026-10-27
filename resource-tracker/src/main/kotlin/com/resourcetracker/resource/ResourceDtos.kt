package com.resourcetracker.resource

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.Size
import jakarta.validation.constraints.PositiveOrZero
import jakarta.validation.constraints.Positive

@Schema(description = "Information required to create a resource")
data class CreateResourceRequest(
    @field:NotBlank
    @field:Size(max = 120)
    val name: String,

    @field:NotNull
    val type: ResourceType,

    @field:NotNull
    @field:Schema(
        description = "Resource category",
        allowableValues = ["CONSUMABLE", "EQUIPMENT", "SOFTWARE", "OTHER"],
        example = "EQUIPMENT",
    )
    val category: ResourceCategory,

    @field:NotBlank
    @field:Size(max = 50)
    @field:Schema(
        description = "Unit label. Allowed values depend on category: CONSUMABLE (unit, dozen, pack, box, bottle, cartridge, roll, ream, gram, kilogram, milliliter, liter); EQUIPMENT (unit, dozen, pair, set); SOFTWARE (license, user, seat, month); OTHER (unit, dozen, pair, set, pack, box, gram, kilogram, milliliter, liter, meter, centimeter, day).",
        allowableValues = ["unit", "dozen", "pair", "set", "pack", "box", "bottle", "cartridge", "roll", "ream", "gram", "kilogram", "milliliter", "liter", "meter", "centimeter", "license", "user", "seat", "month", "day"],
        example = "unit",
    )
    val unitOfMeasure: String,

    @field:Size(max = 500)
    val description: String? = null,

    @field:Min(0)
    val quantity: Int? = null,
    @field:Size(max = 120)
    val location: String? = null,
    @field:Min(0)
    val reorderLevel: Int = 0,

    @field:Positive
    val departmentId: Int? = null,
    @field:Positive
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
    @field:Schema(
        description = "Resource category",
        allowableValues = ["CONSUMABLE", "EQUIPMENT", "SOFTWARE", "OTHER"],
        example = "EQUIPMENT",
    )
    val category: ResourceCategory,

    @field:NotBlank
    @field:Size(max = 50)
    @field:Schema(
        description = "Unit label. Allowed values depend on category: CONSUMABLE (unit, dozen, pack, box, bottle, cartridge, roll, ream, gram, kilogram, milliliter, liter); EQUIPMENT (unit, dozen, pair, set); SOFTWARE (license, user, seat, month); OTHER (unit, dozen, pair, set, pack, box, gram, kilogram, milliliter, liter, meter, centimeter, day).",
        allowableValues = ["unit", "dozen", "pair", "set", "pack", "box", "bottle", "cartridge", "roll", "ream", "gram", "kilogram", "milliliter", "liter", "meter", "centimeter", "license", "user", "seat", "month", "day"],
        example = "unit",
    )
    val unitOfMeasure: String,

    @field:Size(max = 500)
    val description: String? = null,

    @field:Min(0)
    val quantity: Int? = null,
    @field:Size(max = 120)
    val location: String? = null,
    @field:Min(0)
    val reorderLevel: Int = 0,

    @field:Positive
    val departmentId: Int? = null,
    @field:Positive
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
    val reservedQuantity: Int = 0,
    val unavailableQuantity: Int = 0,
    val availableQuantity: Int? = null,
)

data class CorrectionRequestDto(
    @field:PositiveOrZero val newQuantity: Int,
    @field:Size(max = 500)
    @field:NotBlank val reason: String,
)

@Schema(description = "Audit record for a stock correction")
data class StockCorrectionResponse(
    val movementId: Int,
    val resourceId: Int,
    val quantityChange: Int,
    val reason: String?,
)
