package com.resourcetracker.vendor

import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableDefault
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/vendors")
class VendorController(private val vendorService: VendorService) {

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(@Valid @RequestBody request: VendorRequest) =
        vendorService.create(request)

    @GetMapping
    fun list(
        @RequestParam(required = false) status: String?,
        @RequestParam(required = false) keyword: String?,
        @PageableDefault(size = 20, sort = ["name"]) pageable: Pageable
    ) = vendorService.search(status, keyword, pageable)

    @GetMapping("/{id}")
    fun get(@PathVariable id: Int) = vendorService.getById(id)

    @PutMapping("/{id}")
    fun update(@PathVariable id: Int, @Valid @RequestBody request: VendorRequest) =
        vendorService.update(id, request)

    @PatchMapping("/{id}/deactivate")
    fun deactivate(@PathVariable id: Int) = vendorService.deactivate(id)
}