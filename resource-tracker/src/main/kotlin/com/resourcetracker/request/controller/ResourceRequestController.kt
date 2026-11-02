package com.resourcetracker.request.controller

import com.resourcetracker.common.ErrorResponse
import com.resourcetracker.common.ListQuery
import com.resourcetracker.common.PagedResponse
import com.resourcetracker.request.dto.*
import com.resourcetracker.request.entity.RequestStatus
import com.resourcetracker.request.service.*
import com.resourcetracker.request.entity.ProcurementStatus
import com.resourcetracker.security.AuthenticatedUser
import com.resourcetracker.security.RequiresRoles
import com.resourcetracker.user.Role
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.parameters.RequestBody as OpenApiRequestBody
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import jakarta.validation.constraints.Min
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*
import java.time.LocalDate

@RestController
@Validated
@Tag(name = "Resource Requests", description = "Submit and manage resource requests")
@SecurityRequirement(name = "bearerAuth")
class ResourceRequestController(
    private val service: ResourceRequestService,
    private val reservationService: ReservationService,
    private val stockOperations: StockOperationsService,
    private val availabilityService: AvailabilityService,
) {

    @PostMapping("/api/v1/requests")
    @RequiresRoles(Role.TEAM_LEAD)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
        summary = "Submit a resource request",
        description = "Submit a request for internal stock or external procurement. Department, requester and manager are derived from the authenticated user and selected team.",
        requestBody = OpenApiRequestBody(
            required = true,
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = CreateResourceRequestDto::class),
                    examples = [
                        ExampleObject(
                            name = "INTERNAL",
                            summary = "Request resources from existing stock",
                            value = """
                                {
                                  "teamId": 3,
                                  "projectId": 12,
                                  "vendorId": null,
                                  "justification": "Replacement laptops for the design team",
                                  "requiredDate": "2026-11-15",
                                  "lineItems": [
                                    {
                                      "resourceId": 7,
                                      "quantity": 4
                                    }
                                  ]
                                }
                            """,
                        ),
                        ExampleObject(
                            name = "EXTERNAL",
                            summary = "Request resources through procurement",
                            value = """
                                {
                                  "teamId": 3,
                                  "projectId": 12,
                                  "vendorId": 5,
                                  "justification": "Purchase laptops not available in stock",
                                  "requiredDate": "2026-11-15",
                                  "lineItems": [
                                    {
                                      "resourceId": null,
                                      "description": "Business laptop",
                                      "manufacturer": "Example Manufacturer",
                                      "unitPrice": 850000,
                                      "quantity": 4,
                                      "category": "OTHER",
                                      "batchNumber": null,
                                      "unitOfMeasure": "unit",
                                      "fulfillmentPreference": "EXTERNAL"
                                    }
                                  ]
                                }
                            """,
                        ),
                    ],
                ),
            ],
        ),
    )
    @ApiResponses(value = [
        ApiResponse(responseCode = "201", description = "Request submitted"),
        ApiResponse(responseCode = "400", description = "Invalid references or request data", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ApiResponse(responseCode = "403", description = "Team Lead role or team membership required", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
    ])
    fun create(@Valid @RequestBody dto: CreateResourceRequestDto, @AuthenticationPrincipal caller: AuthenticatedUser) =
        service.createRequest(dto, caller.id)

    @PostMapping("/api/v1/requests/{id}/decision")
    @RequiresRoles(Role.DEPARTMENT_MANAGER)
    @Operation(
        summary = "Query, approve, reject, or waitlist a request",
        description = "WAITLIST approves the request into the FIFO waitlist; all request lines must be internal resources.",
    )
    @ApiResponses(value = [
        ApiResponse(responseCode = "409", description = "Request cannot transition to the selected decision or cannot be waitlisted"),
    ])
    fun decide(
        @PathVariable @Min(1) id: Int,
        @Valid @RequestBody dto: RequestDecisionDto,
        @AuthenticationPrincipal caller: AuthenticatedUser,
    ) = service.decide(id, serviceUser(caller), dto)


    @PutMapping("/api/v1/requests/{id}/lines/{lineItemId}/approved-quantity")
    @RequiresRoles(Role.DEPARTMENT_MANAGER)
    @Operation(summary = "Adjust approved line quantity")
    fun adjustApprovedQuantity(
        @PathVariable @Min(1) id: Int,
        @PathVariable @Min(1) lineItemId: Int,
        @Valid @RequestBody dto: ApprovedQuantityUpdateRequest,
        @AuthenticationPrincipal caller: AuthenticatedUser,
    ) = service.adjustApprovedQuantity(id, lineItemId, serviceUser(caller), dto)

    @PutMapping("/api/v1/requests/{id}")
    @RequiresRoles(Role.TEAM_LEAD)
    @ResponseStatus(HttpStatus.OK)
    @Operation(
        summary = "Clarify or cancel an owned request",
        description = "Use status CANCELLED (with an optional comment) to cancel an owned request. Use PENDING with an optional comment and optional replacement line items to edit a pending request or clarify a queried request.",
        requestBody = OpenApiRequestBody(
            required = true,
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = UpdateResourceRequestDto::class),
                    examples = [
                        ExampleObject(
                            name = "EDIT_PENDING",
                            summary = "Edit a pending request (line items optional)",
                            value = """
                                {
                                  "status": "PENDING",
                                  "comment": "Updating quantities before manager review",
                                  "lineItems": [
                                    {
                                      "resourceId": 7,
                                      "description": "Replacement laptop",
                                      "manufacturer": "Example Manufacturer",
                                      "unitPrice": 850000,
                                      "quantity": 2,
                                      "category": "OTHER",
                                      "batchNumber": null,
                                      "unitOfMeasure": "unit",
                                      "fulfillmentPreference": "INTERNAL"
                                    }
                                  ]
                                }
                            """,
                        ),
                        ExampleObject(
                            name = "EDIT_PENDING_COMMENT_ONLY",
                            summary = "Add a note to a pending request without changing line items",
                            value = """
                                {
                                  "status": "PENDING",
                                  "comment": "Please note updated urgency"
                                }
                            """,
                        ),
                        ExampleObject(
                            name = "CLARIFY",
                            summary = "Clarify a queried request with revised line items",
                            value = """
                                {
                                  "status": "PENDING",
                                  "comment": "Clarified as requested: updated quantities and descriptions",
                                  "lineItems": [
                                    {
                                      "resourceId": 7,
                                      "description": "Replacement laptop",
                                      "manufacturer": "Example Manufacturer",
                                      "unitPrice": 850000,
                                      "quantity": 3,
                                      "category": "OTHER",
                                      "batchNumber": null,
                                      "unitOfMeasure": "unit",
                                      "fulfillmentPreference": "INTERNAL"
                                    }
                                  ]
                                }
                            """,
                        ),
                        ExampleObject(
                            name = "CANCEL",
                            summary = "Cancel an owned request",
                            value = """
                                {
                                  "status": "CANCELLED",
                                  "comment": "No longer needed by the team"
                                }
                            """,
                        ),
                    ],
                ),
            ],
        ),
    )
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Request clarified or cancelled"),
        ApiResponse(responseCode = "400", description = "Missing required comment or invalid data", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ApiResponse(responseCode = "403", description = "Team Lead role or request ownership required", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ApiResponse(responseCode = "404", description = "Request not found", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ApiResponse(responseCode = "409", description = "Request cannot be clarified or cancelled in its current status", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
    ])
    fun edit(
        @PathVariable @Min(1) id: Int,
        @Valid @RequestBody dto: UpdateResourceRequestDto,
        @AuthenticationPrincipal caller: AuthenticatedUser,
    ) = service.updateRequest(id, serviceUser(caller), dto)

    @GetMapping("/api/v1/requests")
    @RequiresRoles(Role.TEAM_LEAD, Role.DEPARTMENT_MANAGER)
    @Operation(summary = "List requests visible to the current role")
    fun list(
        @Parameter(description = "Search request numbers", example = "REQ-2026")
        @RequestParam(required = false) search: String?,
        @Parameter(description = "Zero-based page number", example = "0")
        @RequestParam(defaultValue = "0") @Min(0) page: Int,
        @Parameter(description = "Page size, from 1 to 100", example = "20")
        @RequestParam(defaultValue = "20") @Min(1) size: Int,
        @Parameter(description = "Sort field: createdAt, requestNumber, status, requiredDate, or totalAmount", example = "createdAt")
        @RequestParam(defaultValue = "createdAt") sort: String,
        @Parameter(description = "Sort direction: asc or desc", example = "asc")
        @RequestParam(defaultValue = "asc") direction: String,
        @RequestParam(required = false) status: RequestStatus?,
        @RequestParam(required = false) @Min(1) teamId: Int?,
        @RequestParam(required = false) @Min(1) projectId: Int?,
        @RequestParam(required = false) @Min(1) assignedManagerId: Int?,
        @RequestParam(required = false) needsReassignment: Boolean?,
        @RequestParam(required = false) from: LocalDate?,
        @RequestParam(required = false) to: LocalDate?,
        @AuthenticationPrincipal caller: AuthenticatedUser,
    ) = PagedResponse.from(service.list(
        serviceUser(caller), status, teamId, projectId, assignedManagerId,
        needsReassignment, from, to, search,
        ListQuery.pageable(
            page, size, sort, direction,
            setOf("createdAt", "requestNumber", "status", "requiredDate", "totalAmount"),
            "createdAt",
        ),
    ))

    @GetMapping("/api/v1/requests/{id}")
    @RequiresRoles(Role.TEAM_LEAD, Role.DEPARTMENT_MANAGER)
    @Operation(summary = "Get a visible request with its line items")
    fun get(@PathVariable @Min(1) id: Int, @AuthenticationPrincipal caller: AuthenticatedUser) =
        service.get(id, serviceUser(caller))

    @GetMapping("/api/v1/requests/{id}/history")
    @RequiresRoles(Role.TEAM_LEAD, Role.DEPARTMENT_MANAGER, Role.MANAGING_DIRECTOR)
    @Operation(summary = "Get append-only request history")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Chronological request history"),
        ApiResponse(responseCode = "403", description = "Owner, assigned manager, Managing Director, or Admin access required", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ApiResponse(responseCode = "404", description = "Request not found or not visible to the caller", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
    ])
    fun history(@PathVariable @Min(1) id: Int, @AuthenticationPrincipal caller: AuthenticatedUser) =
        service.history(id, serviceUser(caller))

    @PutMapping("/api/v1/requests/{id}/manager")
    @RequiresRoles(Role.ADMIN)
    @Operation(summary = "Assign or reassign a department manager")
    fun reassign(
        @PathVariable @Min(1) id: Int,
        @Valid @RequestBody dto: ManagerReassignmentDto,
        @AuthenticationPrincipal caller: AuthenticatedUser,
    ) = service.reassign(id, serviceUser(caller), dto.managerId)




    @PostMapping("/api/v1/resources/requests/{requestId}/reservations")
    @RequiresRoles(Role.DEPARTMENT_MANAGER)
    @Operation(summary = "Reserve approved internal stock")
    fun reserveResourceRequest(
        @PathVariable @Min(1) requestId: Int,
        @Valid @RequestBody dto: CreateReservationRequest,
        @AuthenticationPrincipal caller: AuthenticatedUser,
    ) = reservationService.reserve(requestId, caller.id, dto)

    @PostMapping("/api/v1/resources/requests/{requestId}/issues")
    @RequiresRoles(Role.DEPARTMENT_MANAGER)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Issue approved stock for a request")
    fun issueResourceRequest(
        @PathVariable @Min(1) requestId: Int,
        @Valid @RequestBody request: StockIssueRequest,
        @AuthenticationPrincipal caller: AuthenticatedUser,
    ) = stockOperations.issue(requestId, caller.id, request)

    @GetMapping("/api/v1/resources/requests/{requestId}/availability")
    @Operation(summary = "Stock review for a request's internal items")
    fun availabilityResourceRequest(
        @PathVariable @Min(1) requestId: Int,
        @AuthenticationPrincipal caller: AuthenticatedUser,
    ) = availabilityService.review(requestId, caller.id)

    private fun serviceUser(caller: AuthenticatedUser) = service.loadUser(caller.id)
}
