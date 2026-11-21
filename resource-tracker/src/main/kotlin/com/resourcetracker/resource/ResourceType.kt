package com.resourcetracker.resource

import com.resourcetracker.common.BadRequestException
import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Resource ownership type. INTERNAL resources are tracked in inventory; EXTERNAL resources are not.")
enum class ResourceType {
    INTERNAL,
    EXTERNAL
}

@Schema(description = "Computed stock status for INTERNAL resources. EXTERNAL resources have no status.")
enum class ResourceStatus {
    AVAILABLE,
    OUT_OF_STOCK
}

@Schema(
    description = "Classification assigned to a resource",
    allowableValues = ["CONSUMABLE", "EQUIPMENT", "SOFTWARE", "OTHER"],
)
enum class ResourceCategory {
    CONSUMABLE,
    EQUIPMENT,
    SOFTWARE,
    OTHER
}

@Schema(description = "How inventory for a catalogue resource is tracked")
enum class TrackingMethod { QUANTITY, INDIVIDUAL }

/** Canonical unit labels accepted by the resource API, grouped by category. */
object ResourceUnits {
    val byCategory: Map<ResourceCategory, Set<String>> = mapOf(
        ResourceCategory.CONSUMABLE to setOf(
            "unit", "dozen", "pack", "box", "bottle", "cartridge", "roll", "ream",
            "gram", "kilogram", "milliliter", "liter",
        ),
        ResourceCategory.EQUIPMENT to setOf("unit", "dozen", "pair", "set"),
        ResourceCategory.SOFTWARE to setOf("license", "user", "seat", "month"),
        ResourceCategory.OTHER to setOf(
            "unit", "dozen", "pair", "set", "pack", "box", "gram", "kilogram",
            "milliliter", "liter", "meter", "centimeter", "day",
        ),
    )

    val all: List<String> = byCategory.values.flatten().distinct().sorted()

    fun normalize(category: ResourceCategory, unit: String): String {
        val canonical = unit.trim().lowercase()
        if (canonical !in (byCategory[category] ?: emptySet())) {
            throw BadRequestException(
                "Unit of measure '$unit' is not allowed for category $category. Allowed units: ${byCategory[category]?.sorted()?.joinToString()}."
            )
        }
        return canonical
    }
}
