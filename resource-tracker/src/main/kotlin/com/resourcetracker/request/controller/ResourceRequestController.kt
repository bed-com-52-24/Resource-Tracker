package com.resourcetracker.request.controller

import com.resourcetracker.common.ErrorResponse
import com.resourcetracker.request.dto.*
import com.resourcetracker.request.entity.RequestStatus
import com.resourcetracker.request.service.ResourceRequestService
import com.resourcetracker.request.service.ReservationService
import com.resourcetracker.request.service.CreateReservationRequest
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
import jakarta.validation.Valid
import jakarta.validation.constraints.Min
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableDefault
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*
import java.time.LocalDate

@RestController
@Validated
@RequestMapping("/api/v1/requests")
@Tag(name = "Resource Requests", description = "Submit and manage resource requests")
@SecurityRequirement(name = "bearerAuth")
class ResourceRequestController(private val service: ResourceRequestService, private val reservationService: ReservationService) {

    @PostMapping
    @RequiresRoles(Role.TEAM_LEAD)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Submit a resource request", description = "Department, requester and manager are derived from the authenticated user and selected team.")
    @ApiResponses(value = [
        ApiResponse(responseCode = "201", description = "Request submitted"),
        ApiResponse(responseCode = "400", description = "Invalid references or request data", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ApiResponse(responseCode = "403", description = "Team Lead role or team membership required", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
    ])
    fun create(@Valid @RequestBody dto: CreateResourceRequestDto, @AuthenticationPrincipal caller: AuthenticatedUser) =
        service.createRequest(dto, caller.id)

    @PostMapping("/{id}/decision")
    @RequiresRoles(Role.DEPARTMENT_MANAGER)
    @Operation(summary = "Query, approve, or reject a request")
    fun decide(
        @PathVariable @Min(1) id: Int,
        @Valid @RequestBody dto: RequestDecisionDto,
        @AuthenticationPrincipal caller: AuthenticatedUser,
    ) = service.decide(id, serviceUser(caller), dto)

    @PostMapping("/{id}/reservations")
    @RequiresRoles(Role.DEPARTMENT_MANAGER)
    @Operation(summary = "Reserve approved internal stock")
    fun reserve(
        @PathVariable @Min(1) id: Int,
        @Valid @RequestBody dto: CreateReservationRequest,
        @AuthenticationPrincipal caller: AuthenticatedUser,
    ) = reservationService.reserve(id, caller.id, dto)

    @PutMapping("/{id}/lines/{lineItemId}/approved-quantity")
    @RequiresRoles(Role.DEPARTMENT_MANAGER)
    @Operation(summary = "Adjust approved line quantity")
    fun adjustApprovedQuantity(
        @PathVariable @Min(1) id: Int,
        @PathVariable @Min(1) lineItemId: Int,
        @Valid @RequestBody dto: ApprovedQuantityUpdateRequest,
        @AuthenticationPrincipal caller: AuthenticatedUser,
    ) = service.adjustApprovedQuantity(id, lineItemId, serviceUser(caller), dto)

    @PutMapping("/{id}")
    @RequiresRoles(Role.TEAM_LEAD)
    @Operation(summary = "Clarify or cancel an owned request")
    fun edit(
        @PathVariable @Min(1) id: Int,
        @Valid @RequestBody dto: UpdateResourceRequestDto,
        @AuthenticationPrincipal caller: AuthenticatedUser,
    ) = service.updateRequest(id, serviceUser(caller), dto)

    @GetMapping
    @RequiresRoles(Role.TEAM_LEAD, Role.DEPARTMENT_MANAGER, Role.ADMIN, Role.MANAGING_DIRECTOR)
    @Operation(summary = "List requests visible to the current role")
    fun list(
        @RequestParam(required = false) status: RequestStatus?,
        @RequestParam(required = false) @Min(1) teamId: Int?,
        @RequestParam(required = false) @Min(1) projectId: Int?,
        @RequestParam(required = false) @Min(1) assignedManagerId: Int?,
        @RequestParam(required = false) needsReassignment: Boolean?,
        @RequestParam(required = false) from: LocalDate?,
        @RequestParam(required = false) to: LocalDate?,
        @RequestParam(required = false) search: String?,
        @PageableDefault(size = 20, sort = ["createdAt"]) pageable: Pageable,
        @AuthenticationPrincipal caller: AuthenticatedUser,
    ) = service.list(serviceUser(caller), status, teamId, projectId, assignedManagerId, needsReassignment, from, to, search, pageable)

    @GetMapping("/{id}")
    @RequiresRoles(Role.TEAM_LEAD, Role.DEPARTMENT_MANAGER, Role.ADMIN, Role.MANAGING_DIRECTOR)
    @Operation(summary = "Get a visible request with its line items")
    fun get(@PathVariable @Min(1) id: Int, @AuthenticationPrincipal caller: AuthenticatedUser) =
        service.get(id, serviceUser(caller))

    @GetMapping("/{id}/history")
    @RequiresRoles(Role.TEAM_LEAD, Role.DEPARTMENT_MANAGER, Role.ADMIN, Role.MANAGING_DIRECTOR)
    @Operation(summary = "Get append-only request history")
    fun history(@PathVariable @Min(1) id: Int, @AuthenticationPrincipal caller: AuthenticatedUser) =
        service.history(id, serviceUser(caller))

    @PutMapping("/{id}/manager")
    @RequiresRoles(Role.ADMIN)
    @Operation(summary = "Assign or reassign a department manager")
    fun reassign(
        @PathVariable @Min(1) id: Int,
        @Valid @RequestBody dto: ManagerReassignmentDto,
        @AuthenticationPrincipal caller: AuthenticatedUser,
    ) = service.reassign(id, serviceUser(caller), dto.managerId)

    private fun serviceUser(caller: AuthenticatedUser) = service.loadUser(caller.id)
}
