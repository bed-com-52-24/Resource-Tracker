package com.resourcetracker.vendor

import com.resourcetracker.audit.AuditAction
import com.resourcetracker.audit.AuditRecord
import com.resourcetracker.audit.AuditService
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException

@Service
class VendorService(
    private val vendorRepository: VendorRepository,
    private val audit: AuditService,
) {

    @Transactional
    fun create(request: VendorRequest): VendorResponse {
        val name = request.name.trim()
        if (vendorRepository.existsByNameIgnoreCase(name)) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "A vendor with this name already exists")
        }
        val saved = vendorRepository.save(
            Vendor(name = name, contactInformation = request.contactInformation)
        )
        audit.record(
            AuditRecord.currentActor(
                AuditAction.VENDOR_CREATED,
                "VENDOR",
                saved.id.toString(),
                newState = snapshot(saved),
                changedFields = listOf("name", "contactInformation", "status"),
            ),
        )
        return saved.toResponse()
    }

    fun getAll(): List<VendorResponse> =
        vendorRepository.findAll().map { it.toResponse() }

    fun search(status: String?, keyword: String?, pageable: Pageable): Page<VendorResponse> =
        vendorRepository
            .search(status?.trim()?.ifBlank { null }, cleanKeyword(keyword), pageable)
            .map { it.toResponse() }

    private fun cleanKeyword(keyword: String?): String? {
        val trimmed = keyword?.trim() ?: return null
        if (trimmed.length > 200) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Keyword must be at most 200 characters")
        }
        return if (trimmed.length < 2) null else trimmed
    }

    fun getById(id: Int): VendorResponse = findOrThrow(id).toResponse()

    @Transactional
    fun update(id: Int, request: UpdateVendorRequest): VendorResponse {
        val vendor = findOrThrow(id)
        val before = snapshot(vendor)
        request.name?.trim()?.let { name ->
            if (!vendor.name.equals(name, ignoreCase = true) &&
                vendorRepository.existsByNameIgnoreCase(name)
            ) {
                throw ResponseStatusException(HttpStatus.CONFLICT, "A vendor with this name already exists")
            }
            vendor.name = name
        }
        request.contactInformation?.let { vendor.contactInformation = it }
        request.active?.let { vendor.status = if (it) "ACTIVE" else "INACTIVE" }
        val saved = vendorRepository.save(vendor)
        val after = snapshot(saved)
        val changed = before.keys.filter { before[it] != after[it] }
        if (changed.isNotEmpty()) {
            val action = when {
                "status" in changed && saved.status.equals("ACTIVE", ignoreCase = true) ->
                    AuditAction.VENDOR_RESTORED
                "status" in changed -> AuditAction.VENDOR_ARCHIVED
                else -> AuditAction.VENDOR_UPDATED
            }
            audit.record(
                AuditRecord.currentActor(
                    action,
                    "VENDOR",
                    saved.id.toString(),
                    previousState = before,
                    newState = after,
                    changedFields = changed,
                ),
            )
        }
        return saved.toResponse()
    }

    private fun findOrThrow(id: Int): Vendor =
        vendorRepository.findById(id).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Vendor not found")
        }

    private fun Vendor.toResponse() =
        VendorResponse(id, name, contactInformation, status)

    private fun snapshot(vendor: Vendor) = mapOf(
        "name" to vendor.name,
        "contactInformation" to vendor.contactInformation,
        "status" to vendor.status,
    )
}
