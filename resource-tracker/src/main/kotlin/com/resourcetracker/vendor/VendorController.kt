package com.resourcetracker.vendor

import com.resourcetracker.common.ErrorResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableDefault
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/vendors")
@Tag(name = "Vendors", description = "Create, search, update, and deactivate vendors")
@SecurityRequirement(name = "bearerAuth")
class VendorController(private val vendorService: VendorService) {

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a vendor", description = "Create a new active vendor.")
    @ApiResponses(value = [
        ApiResponse(responseCode = "201", description = "Vendor created", content = [Content(schema = Schema(implementation = VendorResponse::class))]),
        ApiResponse(responseCode = "400", description = "Invalid request body", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ApiResponse(responseCode = "401", description = "Missing or invalid access token", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ApiResponse(responseCode = "403", description = "Admin role required", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ApiResponse(responseCode = "409", description = "A vendor with this name already exists", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
    ])
    fun create(@Valid @RequestBody request: VendorRequest) =
        vendorService.create(request)

    @GetMapping
    @Operation(summary = "List vendors", description = "Search vendors by status and keyword with pagination and sorting.")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Vendors returned"),
        ApiResponse(responseCode = "400", description = "Invalid search parameters", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ApiResponse(responseCode = "401", description = "Missing or invalid access token", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ApiResponse(responseCode = "403", description = "Admin role required", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
    ])
    fun list(
        @RequestParam(required = false) status: String?,
        @RequestParam(required = false) keyword: String?,
        @PageableDefault(size = 20, sort = ["name"]) pageable: Pageable
    ) = vendorService.search(status, keyword, pageable)

    @GetMapping("/{id}")
    @Operation(summary = "Get a vendor", description = "Return a vendor by its numeric identifier.")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Vendor returned", content = [Content(schema = Schema(implementation = VendorResponse::class))]),
        ApiResponse(responseCode = "401", description = "Missing or invalid access token", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ApiResponse(responseCode = "403", description = "Admin role required", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ApiResponse(responseCode = "404", description = "Vendor not found", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
    ])
    fun get(@PathVariable id: Int) = vendorService.getById(id)

    @PutMapping("/{id}")
    @Operation(summary = "Update a vendor", description = "Update a vendor's name and contact information.")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Vendor updated", content = [Content(schema = Schema(implementation = VendorResponse::class))]),
        ApiResponse(responseCode = "400", description = "Invalid request body", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ApiResponse(responseCode = "401", description = "Missing or invalid access token", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ApiResponse(responseCode = "403", description = "Admin role required", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ApiResponse(responseCode = "404", description = "Vendor not found", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ApiResponse(responseCode = "409", description = "A vendor with this name already exists", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
    ])
    fun update(@PathVariable id: Int, @Valid @RequestBody request: VendorRequest) =
        vendorService.update(id, request)

    @PatchMapping("/{id}/deactivate")
    @Operation(summary = "Deactivate a vendor", description = "Mark a vendor as inactive.")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Vendor deactivated", content = [Content(schema = Schema(implementation = VendorResponse::class))]),
        ApiResponse(responseCode = "401", description = "Missing or invalid access token", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ApiResponse(responseCode = "403", description = "Admin role required", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ApiResponse(responseCode = "404", description = "Vendor not found", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
    ])
    fun deactivate(@PathVariable id: Int) = vendorService.deactivate(id)
}