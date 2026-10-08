package com.resourcetracker.vendor

import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException

@Service
class VendorService(private val vendorRepository: VendorRepository) {

    fun create(request: VendorRequest): VendorResponse {
        val name = request.name.trim()
        if (vendorRepository.existsByNameIgnoreCase(name)) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "A vendor with this name already exists")
        }
        val saved = vendorRepository.save(
            Vendor(name = name, contactInformation = request.contactInformation)
        )
        return saved.toResponse()
    }

    fun getAll(): List<VendorResponse> =
        vendorRepository.findAll().map { it.toResponse() }

    fun getById(id: Int): VendorResponse = findOrThrow(id).toResponse()

    @Transactional
    fun update(id: Int, request: VendorRequest): VendorResponse {
        val vendor = findOrThrow(id)
        val name = request.name.trim()
        if (!vendor.name.equals(name, ignoreCase = true) &&
            vendorRepository.existsByNameIgnoreCase(name)
        ) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "A vendor with this name already exists")
        }
        vendor.name = name
        vendor.contactInformation = request.contactInformation
        return vendorRepository.save(vendor).toResponse()
    }

    @Transactional
    fun deactivate(id: Int): VendorResponse {
        val vendor = findOrThrow(id)
        vendor.status = "INACTIVE"
        return vendorRepository.save(vendor).toResponse()
    }

    private fun findOrThrow(id: Int): Vendor =
        vendorRepository.findById(id).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Vendor not found")
        }

    private fun Vendor.toResponse() =
        VendorResponse(id, name, contactInformation, status)
}