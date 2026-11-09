package com.resourcetracker.audit.controller

import com.resourcetracker.audit.AuditEntryQueryService
import com.resourcetracker.common.ErrorResponse
import com.resourcetracker.common.ListQuery
import com.resourcetracker.common.PagedResponse
import com.resourcetracker.request.service.AuditRetrievalService
import com.resourcetracker.security.AuthenticatedUser
import com.resourcetracker.security.RequiresRoles
import com.resourcetracker.user.Role
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
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
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate

@RestController
@Validated
@RequestMapping("/api/v1/audit")
@Tag(name = "Audit", description = "Read-only retrieval of audit history")
@SecurityRequirement(name = "bearerAuth")
class AuditController(
    private val audit: AuditRetrievalService,
    private val auditEntries: AuditEntryQueryService,
) {
    @GetMapping("/actors/{actorId}")
    @RequiresRoles(Role.ADMIN)
    @Operation(
        summary = "Retrieve audit history by actor",
        description = "Admin only. Defaults to the last 30 days; date ranges may not exceed one year.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Matching audit entries"),
            ApiResponse(responseCode = "400", description = "Invalid date range or actor ID", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
            ApiResponse(responseCode = "403", description = "Admin role required", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ],
    )
    fun byActor(
        @PathVariable @Min(1) actorId: Int,
        @RequestParam(required = false) from: LocalDate?,
        @RequestParam(required = false) to: LocalDate?,
        @RequestParam(defaultValue = "0") @Min(0) page: Int,
        @RequestParam(defaultValue = "20") @Min(1) size: Int,
        @AuthenticationPrincipal caller: AuthenticatedUser,
    ) = PagedResponse.from(audit.byActor(
        actorId,
        caller.role,
        from,
        to,
        pageable(page, size),
    ))

    @GetMapping("/items/{itemType}/{itemId}")
    @RequiresRoles(Role.ADMIN)
    @Operation(
        summary = "Retrieve audit history by item",
        description = "Admin only. Supported item types are REQUEST and LINE_ITEM. Entries are returned chronologically.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Matching audit entries"),
            ApiResponse(responseCode = "400", description = "Unsupported item type or invalid item ID", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
            ApiResponse(responseCode = "403", description = "Admin role required", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ],
    )
    fun byItem(
        @PathVariable itemType: String,
        @PathVariable @Min(1) itemId: Int,
        @AuthenticationPrincipal caller: AuthenticatedUser,
    ) = audit.byItem(itemType, itemId, caller.role)

    @GetMapping("/actions")
    @RequiresRoles(Role.ADMIN)
    @Operation(
        summary = "Retrieve audit history by action and date range",
        description = "Admin only. Filter by one or more event types and inclusive from/to dates. The range may not exceed one year.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Matching audit entries"),
            ApiResponse(responseCode = "400", description = "Missing actions or dates, or invalid date range", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
            ApiResponse(responseCode = "403", description = "Admin role required", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ],
    )
    fun byActions(
        @Parameter(description = "Event types, supplied once per action; for example APPROVED and ISSUED")
        @RequestParam actions: List<String>,
        @RequestParam from: LocalDate,
        @RequestParam to: LocalDate,
        @RequestParam(defaultValue = "0") @Min(0) page: Int,
        @RequestParam(defaultValue = "20") @Min(1) size: Int,
        @AuthenticationPrincipal caller: AuthenticatedUser,
    ) = PagedResponse.from(audit.byActions(actions, caller.role, from, to, pageable(page, size)))

    @GetMapping("/entries")
    @RequiresRoles(Role.ADMIN)
    @Operation(
        summary = "List general audit entries",
        description = "Admin only. Filter by actor, action, item, and an inclusive ISO-8601 timestamp range.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Matching audit entries"),
            ApiResponse(responseCode = "400", description = "Invalid filters or pagination", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
            ApiResponse(responseCode = "403", description = "Admin role required", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ],
    )
    fun entries(
        @RequestParam(required = false) @Min(1) actorId: Int?,
        @RequestParam(required = false) action: com.resourcetracker.audit.AuditAction?,
        @RequestParam(required = false) itemType: String?,
        @RequestParam(required = false) itemId: String?,
        @Parameter(description = "Inclusive ISO-8601 timestamp, for example 2026-10-29T00:00:00Z")
        @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE_TIME) from: java.time.Instant?,
        @Parameter(description = "Inclusive ISO-8601 timestamp, for example 2026-10-29T23:59:59Z")
        @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE_TIME) to: java.time.Instant?,
        @RequestParam(defaultValue = "0") @Min(0) page: Int,
        @RequestParam(defaultValue = "20") @Min(1) size: Int,
    ) = PagedResponse.from(auditEntries.list(actorId, action, itemType, itemId, from, to, page, size))

    private fun pageable(page: Int, size: Int) = ListQuery.pageable(
        page,
        size,
        "createdAt",
        "asc",
        setOf("createdAt"),
        "createdAt",
    )
}
