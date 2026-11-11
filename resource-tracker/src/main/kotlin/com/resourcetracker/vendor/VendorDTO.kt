package com.resourcetracker.vendor

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import io.swagger.v3.oas.annotations.media.Schema

data class VendorRequest(
    @field:NotBlank(message = "Vendor name is required")
    @field:Size(max = 150, message = "Vendor name must be at most 150 characters")
    val name: String,

    @field:Size(max = 255, message = "Contact information must be at most 255 characters")
    val contactInformation: String? = null
)

data class UpdateVendorRequest(
    @field:NotBlank(message = "Vendor name cannot be blank when supplied")
    @field:Size(max = 150, message = "Vendor name must be at most 150 characters")
    @field:Schema(description = "Optional vendor name. Null or omitted preserves the current name.", nullable = true)
    val name: String? = null,

    @field:Size(max = 255, message = "Contact information must be at most 255 characters")
    @field:Schema(description = "Optional contact information. Null or omitted preserves the current value.", nullable = true)
    val contactInformation: String? = null,

    @field:Schema(description = "Optional active state. Null or omitted preserves the current status.", nullable = true)
    val active: Boolean? = null,
)

data class VendorReference(
    val id: Int,
    val name: String,
)

data class VendorResponse(
    val id: Int,
    val name: String,
    val contactInformation: String?,
    val status: String
)
