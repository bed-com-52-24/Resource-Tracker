package com.resourcetracker.routing_rules

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import com.resourcetracker.security.AuthenticatedUser
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/routing-rules")
@Tag(name = "Routing Rules")
@SecurityRequirement(name = "bearerAuth")
class RoutingRuleController(
    private val service: RoutingRuleService
) {

    @GetMapping
    @Operation(summary = "List routing rules using RT-03 scope")
    @ApiResponse(responseCode = "200", description = "Rules returned")
    @ApiResponse(responseCode = "401", description = "Authentication required")
    fun list() = service.list().map(RoutingRuleResponse::from)

    @GetMapping("/{id}")
    @Operation(summary = "Get routing rule")
    @ApiResponse(responseCode = "404", description = "Rule not found")
    fun get(@PathVariable id: Int) =
        RoutingRuleResponse.from(service.get(id))

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Create routing rule")
    @ApiResponse(responseCode = "201", description = "Created")
    @ApiResponse(responseCode = "400", description = "Invalid request")
    @ApiResponse(responseCode = "403", description = "Admin required")
    fun create(
        @Valid @RequestBody request: RoutingRuleRequest,
        @AuthenticationPrincipal caller: AuthenticatedUser
    ): ResponseEntity<RoutingRuleResponse> =
        ResponseEntity.status(HttpStatus.CREATED).body(
            RoutingRuleResponse.from(service.create(request, caller.username))
        )

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Update routing rule")
    @ApiResponse(responseCode = "409", description = "Duplicate active combination")
    fun update(
        @PathVariable id: Int,
        @Valid @RequestBody request: UpdateRoutingRuleRequest,
        @AuthenticationPrincipal caller: AuthenticatedUser
    ) = RoutingRuleResponse.from(service.update(id, request, caller.username))
}
