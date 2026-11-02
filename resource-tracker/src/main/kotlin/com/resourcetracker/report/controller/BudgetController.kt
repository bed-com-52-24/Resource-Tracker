package com.resourcetracker.report.controller

import com.resourcetracker.report.dto.BudgetResponse
import com.resourcetracker.report.dto.UpsertBudgetRequest
import com.resourcetracker.report.service.BudgetService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException

private fun budgetActorId(): Int =
    SecurityContextHolder.getContext()
        .authentication?.name?.toIntOrNull()
        ?: throw ResponseStatusException(
            HttpStatus.UNAUTHORIZED,
            "Not authenticated"
        )

@RestController
@RequestMapping("/api/v1/budgets")
@Tag(name = "Budgets")
class BudgetController(
    private val budgets: BudgetService
) {

    @PutMapping
    @Operation(
        summary = "Create or update a department budget"
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Budget saved"),
        ApiResponse(responseCode = "400", description = "Invalid input"),
        ApiResponse(responseCode = "401", description = "Not authenticated"),
        ApiResponse(responseCode = "403", description = "Admin access required"),
        ApiResponse(responseCode = "404", description = "Department not found"),
        ApiResponse(responseCode = "409", description = "Currency conflict")
    )
    fun upsert(
        @RequestBody dto: UpsertBudgetRequest
    ): BudgetResponse {

        return budgets.upsert(
            budgetActorId(),
            dto
        )
    }
}