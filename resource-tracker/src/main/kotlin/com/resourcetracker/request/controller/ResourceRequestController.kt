package com.resourcetracker.request.controller

import com.resourcetracker.request.dto.CreateResourceRequestDto
import com.resourcetracker.request.dto.UpdateResourceRequestDto
import com.resourcetracker.request.service.RequestCreationFacade
import com.resourcetracker.request.service.ResourceRequestService
import com.resourcetracker.common.UnauthenticatedException
import com.resourcetracker.security.AuthenticatedUser
import com.resourcetracker.common.ErrorResponse
import com.resourcetracker.request.dto.ResourceRequestResponseDto
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableDefault
import org.springframework.http.HttpStatus
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/requests")
@Tag(name = "Resource Requests", description = "Create and manage the authenticated user's resource requests")
@SecurityRequirement(name = "bearerAuth")
class ResourceRequestController(
    private val requestService: ResourceRequestService,
    private val requestCreationFacade: RequestCreationFacade
) {

    private fun currentUserId(
        authentication: Authentication
    ): Int =
        (authentication.principal as? AuthenticatedUser)?.id
            ?: throw UnauthenticatedException()

    @PostMapping
    @Operation(
        summary = "Create a resource request",
        description = "Creates a pending resource request for the authenticated user. " +
                "Requires a unique Idempotency-Key header; repeated submissions with the same key are safely replayed."
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "201", description = "Resource request created"),
            ApiResponse(responseCode = "200", description = "Existing idempotent request replayed"),
            ApiResponse(responseCode = "400", description = "Invalid request, line items, or idempotency key", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
            ApiResponse(responseCode = "401", description = "Authentication required", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
            ApiResponse(responseCode = "409", description = "Idempotency key conflicts with another request", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ]
    )
    fun create(
    @Valid @RequestBody dto: CreateResourceRequestDto,
    @Parameter(description = "Unique key used to make request creation idempotent", required = true, example = "req-20261021-0001")
    @RequestHeader("Idempotency-Key") idempotencyKey: String,
    authentication: Authentication
): org.springframework.http.ResponseEntity<String> {
    val requesterId = currentUserId(authentication)

    // Replace this TODO with your routing-rule service.
    val assignedManagerId = TODO(
        "Resolve the manager using your routing rules"
    )

    val result = requestCreationFacade.create(
        dto = dto,
        requesterId = requesterId,
        assignedManagerId = assignedManagerId,
        idempotencyKey = idempotencyKey
    )

    return org.springframework.http.ResponseEntity
        .status(result.httpStatus)
        .header(
            org.springframework.http.HttpHeaders.CONTENT_TYPE,
            org.springframework.http.MediaType.APPLICATION_JSON_VALUE
        )
        .body(result.responseBody)
}

    @GetMapping
    @Operation(
        summary = "List my resource requests",
        description = "Returns only resource requests belonging to the authenticated user. Supports standard pageable parameters."
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Resource requests returned"),
            ApiResponse(responseCode = "401", description = "Authentication required", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ]
    )
    fun list(
        @PageableDefault(size = 20, sort = ["createdAt"]) pageable: Pageable,
        authentication: Authentication
    ) = requestService.listMyRequests(
        requesterId = currentUserId(authentication),
        pageable = pageable
    )

    @PutMapping("/{id}")
    @Operation(
        summary = "Edit a resource request",
        description = "Edits an owned request while QUERIED. Set status to PENDING to resubmit or CANCELLED to cancel; omit status to only edit."
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Resource request updated"),
            ApiResponse(responseCode = "400", description = "Invalid request data", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
            ApiResponse(responseCode = "401", description = "Authentication required", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
            ApiResponse(responseCode = "404", description = "Request not found or not owned by the authenticated user", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
            ApiResponse(responseCode = "409", description = "Requested status transition is not allowed", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ]
    )
    fun edit(
        @Parameter(description = "Resource request ID", example = "42")
        @PathVariable id: Int,
        @Valid @RequestBody dto: UpdateResourceRequestDto,
        authentication: Authentication
    ) = requestService.updateRequest(
        id,
        currentUserId(authentication),
        dto
    )
}
