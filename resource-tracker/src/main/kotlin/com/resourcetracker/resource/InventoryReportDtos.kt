package com.resourcetracker.resource

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.PositiveOrZero
import jakarta.validation.constraints.Size
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

data class ResourceUtilisationReport(
    val resourceId: Int,
    val code: String,
    val name: String,
    val departmentId: Int?,
    val category: ResourceCategory,
    val onHandQuantity: Int,
    val unavailableQuantity: Int,
    val totalQuantity: Long,
    val totalIssuedQuantity: Long,
    val currentlyIssuedQuantity: Long,
    val utilisationRate: BigDecimal,
    val overdueReturnQuantity: Long,
)

data class ResourceAvailabilityReport(
    val resourceId: Int,
    val code: String,
    val name: String,
    val departmentId: Int?,
    val category: ResourceCategory,
    val status: ResourceStatus?,
    val onHandQuantity: Int,
    val reservedQuantity: Int,
    val unavailableQuantity: Int,
    val availableQuantity: Int,
)

data class ResourceWaitlistReport(
    val requestId: Int,
    val requestNumber: String,
    val requestStatus: String,
    val lineItemId: Int,
    val resourceId: Int,
    val resourceName: String,
    val remainingQuantity: Long,
    val availableQuantity: Int,
    val expectedAvailabilityDate: LocalDate?,
    val requiredDate: LocalDate?,
)

@Schema(description = "Physical stock count for an internal resource")
data class ReconciliationRequest(
    @field:PositiveOrZero
    val physicalCount: Int,
    @field:NotBlank
    @field:Size(max = 470)
    val reason: String,
    val adjustStock: Boolean = false,
)

data class ReconciliationReport(
    val id: Int,
    val resourceId: Int,
    val systemQuantity: Int,
    val physicalCount: Int,
    val variance: Int,
    val adjustmentApplied: Boolean,
    val reason: String,
    val actorId: Int,
    val createdAt: LocalDateTime?,
)
