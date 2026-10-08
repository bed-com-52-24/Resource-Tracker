package com.resourcetracker.vendor

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
    fun list() = vendorService.getAll()

    @GetMapping("/{id}")
    fun get(@PathVariable id: Int) = vendorService.getById(id)

    @PutMapping("/{id}")
    fun update(@PathVariable id: Int, @Valid @RequestBody request: VendorRequest) =
        vendorService.update(id, request)

    @PatchMapping("/{id}/deactivate")
    fun deactivate(@PathVariable id: Int) = vendorService.deactivate(id)
}