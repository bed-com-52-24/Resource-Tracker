package com.resourcetracker.resource

import com.resourcetracker.common.DepartmentReference
import com.resourcetracker.common.VendorReference
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

    @field:Schema(description = "QUANTITY preserves the aggregate stock workflow. INDIVIDUAL requires registering each physical asset separately.", example = "QUANTITY")
    val trackingMethod: TrackingMethod = TrackingMethod.QUANTITY,

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
    val preferredVendorId: Int? = null,
)

@Schema(description = "Editable fields for a resource")
data class UpdateResourceRequest(
    @field:NotBlank
    @field:Size(max = 120)
    @field:Schema(description = "Optional resource name. Omit or send null to preserve the current name.")
    val name: String? = null,

    @field:Schema(description = "Optional confirmation of the existing type. Resource type cannot be changed.")
    val type: ResourceType? = null,

    @field:Schema(
        description = "Optional category. If omitted, the current category remains unchanged.",
        allowableValues = ["CONSUMABLE", "EQUIPMENT", "SOFTWARE", "OTHER"],
        example = "EQUIPMENT",
    )
    val category: ResourceCategory? = null,

    @field:Size(max = 50)
    @field:Schema(
        description = "Optional unit label. Omit to preserve the current value. Allowed values depend on category: CONSUMABLE (unit, dozen, pack, box, bottle, cartridge, roll, ream, gram, kilogram, milliliter, liter); EQUIPMENT (unit, dozen, pair, set); SOFTWARE (license, user, seat, month); OTHER (unit, dozen, pair, set, pack, box, gram, kilogram, milliliter, liter, meter, centimeter, day).",
        allowableValues = ["unit", "dozen", "pair", "set", "pack", "box", "bottle", "cartridge", "roll", "ream", "gram", "kilogram", "milliliter", "liter", "meter", "centimeter", "license", "user", "seat", "month", "day"],
        example = "unit",
    )
    val unitOfMeasure: String? = null,

    @field:Size(max = 500)
    val description: String? = null,

    @field:Min(0)
    val quantity: Int? = null,
    @field:Size(max = 120)
    val location: String? = null,
    @field:Min(0)
    val reorderLevel: Int? = null,

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
    val trackingMethod: TrackingMethod = TrackingMethod.QUANTITY,
    val unitOfMeasure: String,
    val description: String?,
    val active: Boolean,
    val quantity: Int?,
    val location: String?,
    val reorderLevel: Int,
    val status: ResourceStatus?,
    val preferredVendor: VendorReference?,
    val reservedQuantity: Int = 0,
    val unavailableQuantity: Int = 0,
    val availableQuantity: Int? = null,
    val assetCounts: AssetCounts? = null,
)

data class AssetCounts(
    val totalRegistered: Long,
    val available: Long,
    val reserved: Long,
    val issued: Long,
    val damaged: Long,
    val missing: Long,
    val disposed: Long,
    val returnPending: Long = 0,
)

data class AssetStatusCorrection(
    @field:Positive val assetId: Int,
    val status: AssetStatus,
)

@Schema(description = "Tracking-aware inventory correction. Send newQuantity for QUANTITY resources, or assets for INDIVIDUAL resources; do not send both.")
data class InventoryCorrectionRequest(
    @field:PositiveOrZero val newQuantity: Int? = null,
    @field:Size(min = 1) val assets: List<@jakarta.validation.Valid AssetStatusCorrection>? = null,
    @field:Size(max = 500) @field:NotBlank val reason: String,
)

data class InventoryCorrectionResponse(
    val resourceId: Int,
    val trackingMethod: TrackingMethod,
    val quantityChange: Int? = null,
    val assets: List<ResourceAssetResponse> = emptyList(),
)
