package com.resourcetracker.resource

import com.resourcetracker.common.ErrorResponse
import com.resourcetracker.common.PagedResponse
import com.resourcetracker.common.ListQuery
import com.resourcetracker.security.RequiresRoles
import com.resourcetracker.security.AuthenticatedUser
import com.resourcetracker.request.service.AvailabilityService
import com.resourcetracker.request.service.CreateReservationRequest
import com.resourcetracker.request.service.ReservationService
import com.resourcetracker.request.service.StockCorrectionRequest
import com.resourcetracker.request.service.StockIssueRequest
import com.resourcetracker.request.service.StockOperationsService
import com.resourcetracker.common.ResourceNotFoundException
import com.resourcetracker.user.Role
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import jakarta.validation.constraints.Min
import org.springframework.data.domain.Page
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.validation.annotation.Validated
import org.springframework.web.servlet.support.ServletUriComponentsBuilder
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@Validated
@RequestMapping("/api/v1/resources")
@RequiresRoles(Role.TEAM_LEAD, Role.DEPARTMENT_MANAGER, Role.MANAGING_DIRECTOR)
@Tag(name = "Resources", description = "Browse resources and manage inventory and stock corrections")
@SecurityRequirement(name = "bearerAuth")
class ResourceController(
    private val resources: ResourceService,
    private val stockOperations: StockOperationsService,
    private val assetService: AssetService,
    private val inventoryCorrections: InventoryCorrectionService,
) {

    @GetMapping("/{id}/assets")
    @Operation(summary = "List individually tracked assets", description = "Lists registered assets and their authoritative statuses for an individual-tracked resource.")
    fun listAssets(@PathVariable @Min(1) id: Int): List<ResourceAssetResponse> = assetService.list(id)

    @PostMapping("/{id}/assets")
    @RequiresRoles(Role.MANAGING_DIRECTOR)
    @Operation(summary = "Register assets", description = "Registers one or more real physical assets against an active INDIVIDUAL resource. Asset codes and non-empty serial numbers must be unique.")
    fun registerAssets(
        @PathVariable @Min(1) id: Int,
        @Valid @RequestBody request: List<@Valid AssetRegistrationRequest>,
        @AuthenticationPrincipal caller: AuthenticatedUser,
    ): List<ResourceAssetResponse> = assetService.register(id, caller.id, request)

    @GetMapping("/assets/{assetId}/history")
    @Operation(summary = "Get asset movement history", description = "Returns append-only registration and correction movements for one asset.")
    fun assetHistory(@PathVariable @Min(1) assetId: Int): List<AssetMovementResponse> = assetService.history(assetId)

    @GetMapping
    @Operation(
        summary = "List resources",
        description = "Returns the shared resource catalog and inventory to authorized callers. Search matches resource names and codes; active state, type, category, status, and availability are optional filters. Page size is capped at 100."
    )
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Resources returned"),
        ApiResponse(responseCode = "400", description = "Invalid filter or pagination parameters", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ApiResponse(responseCode = "401", description = "Authentication required", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ApiResponse(responseCode = "403", description = "The caller's role does not permit resource access", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
    ])
    fun list(
        @Parameter(description = "Search resource names or codes", example = "laptop")
        @RequestParam(required = false) search: String?,
        @Parameter(description = "Zero-based page number", example = "0")
        @RequestParam(defaultValue = "0") @Min(0) page: Int,
        @Parameter(description = "Page size, from 1 to 100", example = "20")
        @RequestParam(defaultValue = "20") @Min(1) size: Int,
        @Parameter(description = "Sort field: name, code, type, category, active, createdAt, quantity, or status", example = "name")
        @RequestParam(defaultValue = "name") sort: String,
        @Parameter(description = "Sort direction: asc or desc", example = "asc")
        @RequestParam(defaultValue = "asc") direction: String,
        @Parameter(description = "Filter by active state") @RequestParam(required = false) active: Boolean?,
        @Parameter(description = "Filter by resource type: INTERNAL or EXTERNAL") @RequestParam(required = false) type: ResourceType?,
        @Parameter(
            description = "Filter by category",
            schema = Schema(allowableValues = ["CONSUMABLE", "EQUIPMENT", "SOFTWARE", "OTHER"]),
        ) @RequestParam(required = false) category: ResourceCategory?,
        @Parameter(description = "Filter by inventory status (internal resources)") @RequestParam(required = false) status: ResourceStatus?,
        @Parameter(description = "Only return resources with available quantity greater than zero", example = "false") @RequestParam(defaultValue = "false") availableOnly: Boolean,
    ): PagedResponse<ResourceResponse> = PagedResponse.from(resources.list(
        active = active,
        type = type,
        category = category,
        status = status,
        availableOnly = availableOnly,
        keyword = search,
        pageable = ListQuery.pageable(page, size, sort, direction, ALLOWED_SORT_FIELDS, "name"),
    ))

    @PostMapping
    @RequiresRoles(Role.MANAGING_DIRECTOR)
    @Operation(summary = "Create a resource", description = "Managing directors create resources in the shared catalogue. trackingMethod defaults to QUANTITY for backward compatibility. INTERNAL QUANTITY resources require an opening quantity; INTERNAL INDIVIDUAL resources must omit quantity and register real assets separately. EXTERNAL resources remain quantity-free.")
    @ApiResponses(value = [
        ApiResponse(responseCode = "201", description = "Resource created", content = [Content(schema = Schema(implementation = ResourceResponse::class))]),
        ApiResponse(responseCode = "400", description = "Invalid request body, unit, or resource type fields", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ApiResponse(responseCode = "401", description = "Authentication required", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ApiResponse(responseCode = "403", description = "Managing director role required", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ApiResponse(responseCode = "404", description = "Preferred vendor not found", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ApiResponse(responseCode = "409", description = "Generated resource code conflicts with an existing resource", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
    ])
    fun create(
        @Valid @RequestBody request: CreateResourceRequest,
        @AuthenticationPrincipal caller: AuthenticatedUser,
    ): ResponseEntity<ResourceResponse> {
        val response = resources.create(request, caller.id)
        val location = ServletUriComponentsBuilder.fromCurrentRequest()
            .path("/{id}")
            .buildAndExpand(response.id)
            .toUri()
        return ResponseEntity.created(location).body(response)
    }

    @PutMapping("/{id}")
    @RequiresRoles(Role.MANAGING_DIRECTOR)
    @Operation(summary = "Update a resource", description = "Managing directors can update any supplied resource fields; omitted or null fields remain unchanged. Resource type is immutable; physical quantity can only be changed through stock operations. Set active to true or false to change its state, or omit it to leave the state unchanged.")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Resource updated", content = [Content(schema = Schema(implementation = ResourceResponse::class))]),
        ApiResponse(responseCode = "400", description = "Invalid request body, unit, or resource type fields", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ApiResponse(responseCode = "401", description = "Authentication required", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ApiResponse(responseCode = "403", description = "Managing director role required", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ApiResponse(responseCode = "404", description = "Resource or vendor not found", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ApiResponse(responseCode = "409", description = "Resource type cannot change or physical stock was included in the update", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
    ])
    fun update(
        @Parameter(description = "Resource ID", example = "12") @PathVariable @Min(1) id: Int,
        @Valid @RequestBody request: UpdateResourceRequest,
    ): ResourceResponse = resources.update(id, request)

    @GetMapping("/{id}")
    @Operation(summary = "Get a resource", description = "Returns one shared resource. QUANTITY resources report stock balances; INDIVIDUAL resources derive quantity and availability from registered asset records and include counts by asset status.")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Resource returned", content = [Content(schema = Schema(implementation = ResourceResponse::class))]),
        ApiResponse(responseCode = "401", description = "Authentication required", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ApiResponse(responseCode = "403", description = "The caller's role does not permit resource access", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ApiResponse(responseCode = "404", description = "Resource not found", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
    ])
    fun get(
        @Parameter(description = "Resource ID", example = "12") @PathVariable @Min(1) id: Int,
    ): ResourceResponse = resources.get(id)

    @GetMapping("/{id}/movement-history")
    @Operation(
        summary = "Get unified resource and asset movement history",
        description = "Returns one chronological timeline combining quantity movements, request issues/returns, and individual asset events. Asset entries include asset code, status/location/assignment changes, actor, reason, and linked request, issue, or return. Team leads see events tied to their teams; department managers see events in their department; managing directors see the full audit history.",
    )
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Movement history returned"),
        ApiResponse(responseCode = "401", description = "Authentication required", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ApiResponse(responseCode = "403", description = "The caller cannot view movement history for this resource"),
        ApiResponse(responseCode = "404", description = "Resource not found"),
    ])
    fun movementHistory(
        @Parameter(description = "Resource ID", example = "12") @PathVariable @Min(1) id: Int,
        @AuthenticationPrincipal caller: AuthenticatedUser,
    ) = stockOperations.resourceMovementHistory(id, caller.id)

    @PutMapping("/{id}/corrections")
    @RequiresRoles(Role.MANAGING_DIRECTOR)
    @Operation(
        summary = "Correct inventory for a resource",
        description = "One tracking-aware correction operation. For QUANTITY resources send newQuantity; for INDIVIDUAL resources send assets containing exact assetId and corrected status values. Send a mandatory reason and only the fields for that tracking method.",
        requestBody = io.swagger.v3.oas.annotations.parameters.RequestBody(
            content = [Content(mediaType = "application/json", schema = Schema(implementation = InventoryCorrectionRequest::class), examples = [
                io.swagger.v3.oas.annotations.media.ExampleObject(name = "Quantity correction", value = "{\"newQuantity\":42,\"reason\":\"Cycle count\"}"),
                io.swagger.v3.oas.annotations.media.ExampleObject(name = "Asset correction", value = "{\"assets\":[{\"assetId\":103,\"status\":\"MISSING\"}],\"reason\":\"LAP-003 was not found during stocktake\"}"),
            ])],
        ),
    )
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Inventory corrected", content = [Content(schema = Schema(implementation = InventoryCorrectionResponse::class))]),
        ApiResponse(responseCode = "400", description = "Invalid tracking-method correction body or reason", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ApiResponse(responseCode = "401", description = "Authentication required", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ApiResponse(responseCode = "403", description = "Managing director role required", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ApiResponse(responseCode = "404", description = "Resource not found", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ApiResponse(responseCode = "409", description = "Stock correction is not allowed", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
    ])
    fun correctInventory(
        @Parameter(description = "Resource ID", example = "12") @PathVariable @Min(1) id: Int,
        @Valid @RequestBody request: InventoryCorrectionRequest,
        @AuthenticationPrincipal caller: AuthenticatedUser,
    ): InventoryCorrectionResponse = inventoryCorrections.correct(id, caller.id, request)

    companion object {
        private val ALLOWED_SORT_FIELDS = setOf("name", "code", "type", "category", "active", "createdAt", "quantity", "status")
    }
}
