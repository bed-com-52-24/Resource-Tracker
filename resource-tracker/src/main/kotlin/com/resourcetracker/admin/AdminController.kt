package com.resourcetracker.admin

import com.resourcetracker.security.RequiresRoles
import com.resourcetracker.user.Role
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/admin")
@Tag(name = "Administration", description = "Administrative health and management operations")
class AdminController {
    @GetMapping("/ping")
    @RequiresRoles(Role.ADMIN)
    @Operation(summary = "Check admin access", description = "Returns a simple status response. Requires an Admin JWT.", security = [SecurityRequirement(name = "bearerAuth")])
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Admin access confirmed"),
        ApiResponse(responseCode = "401", description = "Missing or invalid access token"),
        ApiResponse(responseCode = "403", description = "Authenticated user does not have the Admin role"),
    ])
    fun ping() = mapOf("status" to "ok")
}
