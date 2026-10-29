package com.resourcetracker.audit

import com.resourcetracker.common.ErrorResponse
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
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.Instant

@RestController
@RequestMapping("/api/v1/audit/entries")
@RequiresRoles(Role.ADMIN, Role.MANAGING_DIRECTOR)
@Tag(name = "Audit entries", description = "Read-only access to general immutable audit records")
@SecurityRequirement(name = "bearerAuth")
class AuditEntryController(private val query: AuditEntryQueryService) {
    @GetMapping
    @Operation(
        summary = "List general audit entries",
        description = "Admin and Managing Director only. Filter by actor, action, item, and an inclusive ISO-8601 timestamp range.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Matching audit entries"),
            ApiResponse(responseCode = "400", description = "Invalid filters or pagination", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
            ApiResponse(responseCode = "403", description = "Admin or Managing Director role required", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ],
    )
    fun list(
        @RequestParam(required = false) @Min(1) actorId: Int?,
        @RequestParam(required = false) action: AuditAction?,
        @RequestParam(required = false) itemType: String?,
        @RequestParam(required = false) itemId: String?,
        @Parameter(description = "Inclusive ISO-8601 timestamp, for example 2026-10-29T00:00:00Z")
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) from: Instant?,
        @Parameter(description = "Inclusive ISO-8601 timestamp, for example 2026-10-29T23:59:59Z")
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) to: Instant?,
        @RequestParam(defaultValue = "0") @Min(0) page: Int,
        @RequestParam(defaultValue = "20") @Min(1) size: Int,
    ) = query.list(actorId, action, itemType, itemId, from, to, page, size)
}
