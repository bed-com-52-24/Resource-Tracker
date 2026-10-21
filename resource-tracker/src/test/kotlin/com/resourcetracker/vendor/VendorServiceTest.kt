package com.resourcetracker.vendor

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.util.Optional

class VendorServiceTest {
    private val vendors = mock(VendorRepository::class.java)
    private val service = VendorService(vendors)

    @Test
    fun `update without active preserves current state`() {
        val vendor = Vendor(id = 1, name = "Acme", status = "INACTIVE")
        `when`(vendors.findById(1)).thenReturn(Optional.of(vendor))
        `when`(vendors.save(vendor)).thenReturn(vendor)

        val response = service.update(1, UpdateVendorRequest("Acme", active = null))

        assertEquals("INACTIVE", response.status)
    }

    @Test
    fun `update active toggles vendor state`() {
        val vendor = Vendor(id = 1, name = "Acme")
        `when`(vendors.findById(1)).thenReturn(Optional.of(vendor))
        `when`(vendors.save(vendor)).thenReturn(vendor)

        val response = service.update(1, UpdateVendorRequest("Acme", active = false))

        assertEquals("INACTIVE", response.status)
    }
}
