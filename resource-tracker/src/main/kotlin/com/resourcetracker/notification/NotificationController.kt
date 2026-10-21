package com.resourcetracker.notification

import com.resourcetracker.security.AuthenticatedUser
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springdoc.core.annotations.ParameterObject
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableDefault
import org.springframework.security.core.annotation.AuthenticationPrincipal
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/notifications")
@Tag(name = "Notifications", description = "Read-only user notifications for workflow actions")
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
        @RequestParam(required = false) unreadOnly: Boolean = false,
        @RequestParam(required = false) search: String? = null,
        @ParameterObject @PageableDefault(size = 20, sort = ["createdAt"]) pageable: Pageable,
    ) = service.listForUser(caller.id, unreadOnly, search, pageable)

    @PatchMapping
    @Operation(summary = "Update notification read state", description = "Set read on selected notification ids or all notifications owned by the current user.")
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
}
