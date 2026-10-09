package com.resourcetracker.vendor

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class VendorRequest(
    @field:NotBlank(message = "Vendor name is required")
    @field:Size(max = 150, message = "Vendor name must be at most 150 characters")
    val name: String,

    @field:Size(max = 255, message = "Contact information must be at most 255 characters")
    val contactInformation: String? = null
)

data class VendorResponse(
    val id: Int,
    val name: String,
    val contactInformation: String?,
    val status: String
)