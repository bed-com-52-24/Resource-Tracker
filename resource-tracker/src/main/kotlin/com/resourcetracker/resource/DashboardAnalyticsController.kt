package com.resourcetracker.resource

import com.resourcetracker.common.ErrorResponse
import com.resourcetracker.security.AuthenticatedUser
import com.resourcetracker.security.RequiresRoles
import com.resourcetracker.user.Role
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.constraints.Min
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@Validated
@RequestMapping("/api/v1/dashboard")
@RequiresRoles(Role.MANAGING_DIRECTOR, Role.DEPARTMENT_MANAGER)
@Tag(name = "Dashboard", description = "Dashboard analytics for requests and internal inventory")
@SecurityRequirement(name = "bearerAuth")
class DashboardAnalyticsController(private val analytics: DashboardAnalyticsService) {
    @GetMapping("/analytics")
    @Operation(
        summary = "Get dashboard analytics",
        description = "Allowed roles: DEPARTMENT_MANAGER and MANAGING_DIRECTOR. Returns request counts by status and current internal-inventory totals. Department Managers are limited to their own department; Managing Directors may optionally filter by department.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Dashboard analytics"),
            ApiResponse(responseCode = "400", description = "Invalid department filter", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
            ApiResponse(responseCode = "401", description = "Authentication required", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
            ApiResponse(responseCode = "403", description = "Role or department access is not permitted", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ],
    )
    fun analytics(
        @RequestParam(required = false) @Min(1) departmentId: Int?,
        @AuthenticationPrincipal caller: AuthenticatedUser,
    ): DashboardAnalytics = analytics.analytics(caller, departmentId)
}
