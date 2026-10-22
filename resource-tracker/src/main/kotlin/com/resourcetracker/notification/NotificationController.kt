package com.resourcetracker.notification

import com.resourcetracker.common.ListQuery
import com.resourcetracker.security.AuthenticatedUser
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.security.core.annotation.AuthenticationPrincipal
import jakarta.validation.Valid
import jakarta.validation.constraints.Min
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@Validated
@RequestMapping("/api/v1/notifications")
@Tag(name = "Notifications", description = "User notifications for workflow actions")
@SecurityRequirement(name = "bearerAuth")
class NotificationController(
    private val service: NotificationService,
) {

    @GetMapping
    @Operation(summary = "List notifications for the current user", description = "Returns notifications owned by the authenticated user.")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Notifications returned", content = [Content(schema = Schema(implementation = NotificationResponse::class))]),
            ApiResponse(responseCode = "401", description = "Missing or invalid access token", content = [Content(schema = Schema(implementation = com.resourcetracker.common.ErrorResponse::class))]),
        ],
    )
    fun list(
        @AuthenticationPrincipal caller: AuthenticatedUser,
        @Parameter(description = "Search notification text or type", example = "request submitted")
        @RequestParam(required = false) search: String? = null,
        @Parameter(description = "Zero-based page number", example = "0")
        @RequestParam(defaultValue = "0") @Min(0) page: Int,
        @Parameter(description = "Page size, from 1 to 100", example = "20")
        @RequestParam(defaultValue = "20") @Min(1) size: Int,
        @Parameter(description = "Sort field: createdAt, type, status, or id", example = "createdAt")
        @RequestParam(defaultValue = "createdAt") sort: String,
        @Parameter(description = "Sort direction: asc or desc", example = "desc")
        @RequestParam(defaultValue = "desc") direction: String,
        @RequestParam(required = false) status: NotificationStatus? = null,
    ) = service.listForUser(
        caller.id,
        status,
        search,
        ListQuery.pageable(page, size, sort, direction, SORT_FIELDS, "createdAt"),
    )

    @PatchMapping
    @Operation(summary = "Update notification status", description = "Set status on selected notification ids or all notifications owned by the current user.")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Notification read state updated", content = [Content(schema = Schema(implementation = NotificationUpdateResponse::class))]),
            ApiResponse(responseCode = "400", description = "Provide either non-empty ids or all=true", content = [Content(schema = Schema(implementation = com.resourcetracker.common.ErrorResponse::class))]),
            ApiResponse(responseCode = "401", description = "Missing or invalid access token", content = [Content(schema = Schema(implementation = com.resourcetracker.common.ErrorResponse::class))]),
        ],
    )
    fun updateReadState(
        @AuthenticationPrincipal caller: AuthenticatedUser,
        @Valid @RequestBody request: NotificationUpdateRequest,
    ) = service.updateReadState(caller.id, request)

    @GetMapping("/unread-count")
    @Operation(summary = "Get unread notification count", description = "Returns the number of unread notifications for the current user.")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Unread count returned", content = [Content(schema = Schema(implementation = NotificationCountResponse::class))]),
            ApiResponse(responseCode = "401", description = "Missing or invalid access token", content = [Content(schema = Schema(implementation = com.resourcetracker.common.ErrorResponse::class))]),
        ],
    )
    fun unreadCount(@AuthenticationPrincipal caller: AuthenticatedUser) = NotificationCountResponse(service.unreadCount(caller.id))

    companion object {
        private val SORT_FIELDS = setOf("createdAt", "type", "status", "id")
    }
}
