package com.resourcetracker.request.controller

import com.resourcetracker.common.ErrorResponse
import com.resourcetracker.common.ListQuery
import com.resourcetracker.common.PagedResponse
import com.resourcetracker.request.dto.*
import com.resourcetracker.request.entity.ProcurementStatus
import com.resourcetracker.request.entity.RequestStatus
import com.resourcetracker.request.service.*
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
    private val stockOperations: StockOperationsService,
    private val procurementService: ProcurementService,
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
                                  "justification": "Replacement laptops for the design team",
                                  "requiredDate": "2026-11-15",
                                  "lineItems": [
                                    {
                                      "resourceId": 7,
                                      "quantity": 4,
                                      "requestedDurationDays": 30
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
                                  "justification": "Purchase laptops not available in stock",
                                  "requiredDate": "2026-11-15",
                                  "lineItems": [
                                    {
                                      "resourceId": null,
                                      "description": "Business laptop",
                                      "vendorId": 5,
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
        ApiResponse(responseCode = "401", description = "Authentication required", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ApiResponse(responseCode = "403", description = "Team Lead role or team membership required", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ApiResponse(responseCode = "404", description = "Team, project, or referenced resource not found", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
    ])
    fun create(@Valid @RequestBody dto: CreateResourceRequestDto, @AuthenticationPrincipal caller: AuthenticatedUser) =
        service.createRequest(dto, caller.id)

    @PostMapping("/api/v1/requests/{id}/issues")
    @RequiresRoles(Role.DEPARTMENT_MANAGER)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Issue reserved stock for a request line")
    @ApiResponses(value = [
        ApiResponse(responseCode = "201", description = "Stock issued"),
        ApiResponse(responseCode = "400", description = "Invalid issue quantity or non-internal line"),
        ApiResponse(responseCode = "401", description = "Authentication required", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ApiResponse(responseCode = "403", description = "Assigned department manager role required"),
        ApiResponse(responseCode = "404", description = "Request or line item not found"),
        ApiResponse(responseCode = "409", description = "Request is not issuable, stock is not reserved/available, or quantity exceeds the approved balance"),
    ])
    fun issueStock(
        @PathVariable @Min(1) id: Int,
        @Valid @RequestBody dto: StockIssueRequest,
        @AuthenticationPrincipal caller: AuthenticatedUser,
    ) = stockOperations.issue(id, caller.id, dto)

    @GetMapping("/api/v1/requests/{id}/issues")
    @RequiresRoles(Role.TEAM_LEAD, Role.DEPARTMENT_MANAGER, Role.MANAGING_DIRECTOR)
    @Operation(summary = "List issued stock, due dates, and extension history for a request", description = "Allowed roles: TEAM_LEAD, DEPARTMENT_MANAGER, and MANAGING_DIRECTOR. Returns issued quantities, due dates, returns, and extension decisions for a request visible to the caller.")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Issue and return records returned"),
        ApiResponse(responseCode = "401", description = "Authentication required", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ApiResponse(responseCode = "403", description = "Request is not visible to the caller", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ApiResponse(responseCode = "404", description = "Request not found", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
    ])
    fun listIssuedStock(
        @PathVariable @Min(1) id: Int,
        @AuthenticationPrincipal caller: AuthenticatedUser,
    ) = stockOperations.listIssues(id, caller.id)

    @PutMapping("/api/v1/requests/{id}/issue")
    @RequiresRoles(Role.TEAM_LEAD, Role.DEPARTMENT_MANAGER)
    @Operation(
        summary = "Submit or decide an issued-item return or due-date extension",
        description = """
            Use one action per call and include only the fields listed for that action:

            - **SUBMIT_RETURN** — Team Lead belonging to the issue's team. Required: `issueId`, `quantity`. Optional: `note`. Creates a `PENDING` return; inventory is unchanged until verification.
            - **VERIFY_RETURN** — Department Manager assigned to the request. Required: `issueId`, `returnId`, `condition`. Optional: `quantity`, `note`. Moves the return from `PENDING` to `VERIFIED` and updates inventory according to the accepted quantity and condition.
            - **REQUEST_EXTENSION** — Team Lead belonging to the issue's team. Required: `issueId`, `newExpectedReturnDate`, `reason`. Creates a `PENDING` extension.
            - **DECIDE_EXTENSION** — Department Manager assigned to the request. Required: `issueId`, `extensionId`, `decision`. A `REJECT` decision also requires `comment`. Moves the extension from `PENDING` to `APPROVED` or `REJECTED`; approval updates the issue due date.

            Do not send a status value; the server controls these transitions. Issue fulfillment status is derived from reservation, issue, and verified return quantities. Possible values are `NOT_STARTED`, `RESERVED`, `PARTIALLY_ISSUED`, `ISSUED`, `PARTIALLY_RETURNED`, and `RETURNED`.

            First use `GET /api/v1/requests/{id}/issues` to review issue, return, and extension records and obtain their IDs.
        """.trimIndent(),
        requestBody = OpenApiRequestBody(
            required = true,
            content = [Content(mediaType = "application/json", schema = Schema(implementation = IssueWorkflowRequest::class), examples = [
                ExampleObject(name = "Submit return", summary = "Team lead submits a return", value = """
                    {"action":"SUBMIT_RETURN","issueId":21,"quantity":2,"note":"Returning two units"}
                """),
                ExampleObject(name = "Verify return", summary = "Assigned department manager verifies a return", value = """
                    {"action":"VERIFY_RETURN","issueId":21,"returnId":31,"condition":"USABLE","quantity":2,"note":"Inspected and usable"}
                """),
                ExampleObject(name = "Request extension", summary = "Team lead requests a later due date", value = """
                    {"action":"REQUEST_EXTENSION","issueId":21,"newExpectedReturnDate":"2026-12-01","reason":"Project requires the equipment for two more weeks"}
                """),
                ExampleObject(name = "Decide extension", summary = "Assigned department manager approves or rejects an extension", value = """
                    {"action":"DECIDE_EXTENSION","issueId":21,"extensionId":42,"decision":"APPROVE","comment":"Approved"}
                """),
            ])],
        ),
    )
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Workflow action completed"),
        ApiResponse(responseCode = "400", description = "Required action fields are missing or invalid"),
        ApiResponse(responseCode = "401", description = "Authentication required", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ApiResponse(responseCode = "403", description = "Role, team membership, or assigned-manager check failed"),
        ApiResponse(responseCode = "404", description = "Request, issue, return, or extension was not found"),
        ApiResponse(responseCode = "409", description = "Return quantity, issue state, or extension state does not allow this action"),
    ])
    fun updateIssueWorkflow(
        @PathVariable @Min(1) id: Int,
        @Valid @RequestBody request: IssueWorkflowRequest,
        @AuthenticationPrincipal caller: AuthenticatedUser,
    ) = stockOperations.processIssueWorkflow(id, caller.id, request)

    @PutMapping("/api/v1/requests/{id}")
    @RequiresRoles(Role.TEAM_LEAD, Role.DEPARTMENT_MANAGER)
    @Operation(
        summary = "Edit a request or make its manager decision",
        description = "TEAM_LEAD may clarify or cancel requests. DEPARTMENT_MANAGER may set priority (CRITICAL, HIGH, MEDIUM, LOW) only for requests in their department, and may make decisions or adjust quantities as allowed by the request status.",
        requestBody = OpenApiRequestBody(
            required = true,
            content = [Content(mediaType = "application/json", schema = Schema(implementation = UpdateRequestDto::class), examples = [
                ExampleObject(name = "Team lead clarification", summary = "Team lead responds to a query and edits lines", value = """
                    {"status":"PENDING","comment":"Updated requested quantities","lineItems":[{"resourceId":7,"quantity":4,"requestedDurationDays":30}]}
                """),
                ExampleObject(name = "Team lead cancellation", summary = "Team lead cancels a pending or queried request", value = """
                    {"status":"CANCELLED","comment":"The team no longer needs these items"}
                """),
                ExampleObject(name = "Manager decision", summary = "Department manager approves with per-line quantities", value = """
                    {"status":"APPROVED","commentApproval":"Approved for the project","approvedQuantities":[{"lineItemId":12,"quantity":4,"expectedFulfillmentDate":"2026-11-20","approvedDurationDays":21}]}
                """),
                ExampleObject(name = "Manager sets priority", summary = "Department manager sets request priority", value = """
                    {"priority":"HIGH"}
                """),
                ExampleObject(name = "Manager query", summary = "Department manager asks the team lead to clarify", value = """
                    {"status":"QUERIED","commentApproval":"Please explain why four units are needed"}
                """),
            ])],
        ),
    )
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Request updated or decision applied"),
        ApiResponse(responseCode = "400", description = "Invalid status, missing comment, or invalid line quantities"),
        ApiResponse(responseCode = "401", description = "Authentication required", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ApiResponse(responseCode = "403", description = "Caller role or department scope check failed"),
        ApiResponse(responseCode = "404", description = "Request or line item was not found"),
        ApiResponse(responseCode = "409", description = "Request cannot make the requested transition"),
    ])
    fun edit(
        @PathVariable @Min(1) id: Int,
        @Valid @RequestBody dto: UpdateRequestDto,
        @AuthenticationPrincipal caller: AuthenticatedUser,
    ) = service.updateRequest(id, serviceUser(caller), dto)

    @GetMapping("/api/v1/requests")
    @RequiresRoles(Role.TEAM_LEAD, Role.DEPARTMENT_MANAGER, Role.MANAGING_DIRECTOR)
    @Operation(summary = "List requests visible to the current role", description = "Allowed roles: TEAM_LEAD, DEPARTMENT_MANAGER, and MANAGING_DIRECTOR. Returns a paginated list of requests visible to the caller, with optional status, team, project, manager, date, and reassignment filters.")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Requests returned"),
        ApiResponse(responseCode = "400", description = "Invalid filters or pagination", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ApiResponse(responseCode = "401", description = "Authentication required", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ApiResponse(responseCode = "403", description = "Caller role is not permitted", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
    ])
    fun list(
        @Parameter(description = "Search request numbers", example = "REQ-2026")
        @RequestParam(required = false) search: String?,
        @Parameter(description = "Zero-based page number", example = "0")
        @RequestParam(defaultValue = "0") @Min(0) page: Int,
        @Parameter(description = "Page size, from 1 to 100", example = "20")
        @RequestParam(defaultValue = "20") @Min(1) size: Int,
        @Parameter(description = "Sort field: createdAt, requestNumber, status, requiredDate, or totalAmount", example = "createdAt")
        @RequestParam(defaultValue = "createdAt") sort: String,
        @Parameter(description = "Sort direction: asc or desc", example = "desc")
        @RequestParam(defaultValue = "desc") direction: String,
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
    @RequiresRoles(Role.TEAM_LEAD, Role.DEPARTMENT_MANAGER, Role.MANAGING_DIRECTOR)
    @Operation(summary = "Get a visible request with its line items", description = "Allowed roles: TEAM_LEAD, DEPARTMENT_MANAGER, and MANAGING_DIRECTOR. Returns a request and its line items when the caller is permitted to view it.")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Request returned"),
        ApiResponse(responseCode = "401", description = "Authentication required", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ApiResponse(responseCode = "403", description = "Request is not visible to the caller", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ApiResponse(responseCode = "404", description = "Request not found", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
    ])
    fun get(@PathVariable @Min(1) id: Int, @AuthenticationPrincipal caller: AuthenticatedUser) =
        service.get(id, serviceUser(caller))

    @GetMapping("/api/v1/requests/{id}/history")
    @RequiresRoles(Role.TEAM_LEAD, Role.DEPARTMENT_MANAGER, Role.MANAGING_DIRECTOR)
    @Operation(summary = "Get append-only request history", description = "Allowed roles: TEAM_LEAD, DEPARTMENT_MANAGER, and MANAGING_DIRECTOR. The caller must own the request, be its assigned manager, or be a Managing Director.")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Chronological request history"),
        ApiResponse(responseCode = "401", description = "Authentication required", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ApiResponse(responseCode = "403", description = "Owner, assigned manager, or Managing Director access required", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ApiResponse(responseCode = "404", description = "Request not found or not visible to the caller", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
    ])
    fun history(@PathVariable @Min(1) id: Int, @AuthenticationPrincipal caller: AuthenticatedUser) =
        service.history(id, serviceUser(caller))

    // Procurement management is exposed by ProcurementController (/api/v1/resources/procurements)

    private fun serviceUser(caller: AuthenticatedUser) = service.loadUser(caller.id)
}
