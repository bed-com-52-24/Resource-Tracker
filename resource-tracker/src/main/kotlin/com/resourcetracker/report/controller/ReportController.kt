package com.resourcetracker.report.controller

import com.resourcetracker.report.dto.BudgetReportResponse
import com.resourcetracker.report.dto.SpendingReportResponse
import com.resourcetracker.report.service.ReportService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.HttpStatus
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import java.time.LocalDate

private fun currentUserId(): Int =
    SecurityContextHolder.getContext()
        .authentication?.name?.toIntOrNull()
        ?: throw ResponseStatusException(
            HttpStatus.UNAUTHORIZED,
            "Not authenticated"
        )

@RestController
@RequestMapping("/api/v1/reports")
@Tag(name = "Reports")
class ReportController(
    private val reports: ReportService
) {

    @GetMapping("/spending-by-department")
    @Operation(
        summary = "Spending report by department"
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Report returned"),
        ApiResponse(responseCode = "400", description = "Invalid period"),
        ApiResponse(responseCode = "401", description = "Not authenticated"),
        ApiResponse(responseCode = "403", description = "Access denied")
    )
    fun spending(
        @RequestParam(required = false) departmentId: Int?,
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        from: LocalDate?,
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        to: LocalDate?
    ): SpendingReportResponse {

        if (from != null && to != null && from.isAfter(to)) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "from must not be after to"
            )
        }

        return reports.spending(
            currentUserId(),
            departmentId,
            from,
            to
        )
    }

    @GetMapping("/budget-by-department")
    @Operation(
        summary = "Department budget utilisation report"
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Report returned"),
        ApiResponse(responseCode = "400", description = "Invalid period"),
        ApiResponse(responseCode = "401", description = "Not authenticated"),
        ApiResponse(responseCode = "403", description = "Access denied")
    )
    fun budget(
        @RequestParam(required = false) departmentId: Int?,
        @RequestParam
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        from: LocalDate,
        @RequestParam
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        to: LocalDate
    ): BudgetReportResponse {

        if (from.isAfter(to)) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "from must not be after to"
            )
        }

        return reports.budget(
            currentUserId(),
            departmentId,
            from,
            to
        )
    }
}