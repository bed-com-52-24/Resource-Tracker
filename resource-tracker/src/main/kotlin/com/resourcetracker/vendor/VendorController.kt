package com.resourcetracker.vendor

import com.resourcetracker.common.ErrorResponse
import com.resourcetracker.common.ListQuery
import com.resourcetracker.common.PagedResponse
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
import org.springframework.http.HttpStatus
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*

@RestController
@Validated
@RequestMapping("/api/v1/vendors")
@RequiresRoles(Role.TEAM_LEAD, Role.DEPARTMENT_MANAGER, Role.MANAGING_DIRECTOR)
@Tag(name = "Vendors", description = "Search and select vendors; Managing Directors manage the shared vendor list")
@SecurityRequirement(name = "bearerAuth")
class VendorController(private val vendorService: VendorService) {

    @PostMapping
    @RequiresRoles(Role.MANAGING_DIRECTOR)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a vendor", description = "Managing Directors create vendors in the shared organization-wide vendor list.")
    @ApiResponses(value = [
        ApiResponse(responseCode = "201", description = "Vendor created", content = [Content(schema = Schema(implementation = VendorResponse::class))]),
        ApiResponse(responseCode = "400", description = "Invalid request body", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ApiResponse(responseCode = "401", description = "Missing or invalid access token", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ApiResponse(responseCode = "403", description = "Managing Director role required", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ApiResponse(responseCode = "409", description = "A vendor with this name already exists", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
    ])
    fun create(@Valid @RequestBody request: VendorRequest) =
        vendorService.create(request)

    @GetMapping
    @RequiresRoles(Role.TEAM_LEAD, Role.DEPARTMENT_MANAGER, Role.MANAGING_DIRECTOR)
    @Operation(summary = "List vendors", description = "Returns a paginated shared vendor list. Team leads and department managers can view vendors to select for requests; Managing Directors can also manage the list. Search matches vendor names; status is an optional filter. Page size is capped at 100.")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Vendors returned"),
        ApiResponse(responseCode = "400", description = "Invalid search parameters", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ApiResponse(responseCode = "401", description = "Missing or invalid access token", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ApiResponse(responseCode = "403", description = "Caller role is not permitted for vendor access", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
    ])
    fun list(
        @Parameter(description = "Search vendor names", example = "office")
        @RequestParam(required = false) search: String?,
        @Parameter(description = "Zero-based page number", example = "0")
        @RequestParam(defaultValue = "0") @Min(0) page: Int,
        @Parameter(description = "Page size, from 1 to 100", example = "20")
        @RequestParam(defaultValue = "20") @Min(1) size: Int,
        @Parameter(description = "Sort field: id, name, contactInformation, or status", example = "name")
        @RequestParam(defaultValue = "name") sort: String,
        @Parameter(description = "Sort direction: asc or desc", example = "asc")
        @RequestParam(defaultValue = "asc") direction: String,
        @Parameter(description = "Filter by vendor status", example = "ACTIVE")
        @RequestParam(required = false) status: String?,
    ): PagedResponse<VendorResponse> = PagedResponse.from(vendorService.search(
        status,
        search,
        ListQuery.pageable(page, size, sort, direction, ALLOWED_SORT_FIELDS, "name"),
    ))

    @GetMapping("/{id}")
    @RequiresRoles(Role.TEAM_LEAD, Role.DEPARTMENT_MANAGER, Role.MANAGING_DIRECTOR)
    @Operation(summary = "Get a vendor", description = "Return a shared vendor by its numeric identifier. Team leads and department managers can read vendor details when selecting a vendor; Managing Directors can also manage vendor records.")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Vendor returned", content = [Content(schema = Schema(implementation = VendorResponse::class))]),
        ApiResponse(responseCode = "401", description = "Missing or invalid access token", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ApiResponse(responseCode = "403", description = "Caller role is not permitted for vendor access", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ApiResponse(responseCode = "404", description = "Vendor not found", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
    ])
    fun get(@PathVariable id: Int) = vendorService.getById(id)

    @PutMapping("/{id}")
    @RequiresRoles(Role.MANAGING_DIRECTOR)
    @Operation(summary = "Update a vendor", description = "Managing Directors maintain the shared organization-wide vendor list. All fields are optional. Supplied name and contact information are updated; omitted or null fields remain unchanged. Set active to true or false to change status.")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Vendor updated", content = [Content(schema = Schema(implementation = VendorResponse::class))]),
        ApiResponse(responseCode = "400", description = "Invalid request body", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ApiResponse(responseCode = "401", description = "Missing or invalid access token", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ApiResponse(responseCode = "403", description = "Managing Director role required", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ApiResponse(responseCode = "404", description = "Vendor not found", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ApiResponse(responseCode = "409", description = "A vendor with this name already exists", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
    ])
    fun update(@PathVariable id: Int, @Valid @RequestBody request: UpdateVendorRequest) =
        vendorService.update(id, request)

    companion object {
        private val ALLOWED_SORT_FIELDS = setOf("id", "name", "contactInformation", "status")
    }
}
