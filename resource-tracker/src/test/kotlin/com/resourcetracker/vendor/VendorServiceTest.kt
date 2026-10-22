package com.resourcetracker.vendor

import com.resourcetracker.audit.AuditService
import com.resourcetracker.audit.AuditAction
import com.resourcetracker.audit.AuditRecord
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.verify
import java.util.Optional

class VendorServiceTest {
    private val vendors = mock(VendorRepository::class.java)
    private val audit = mock(AuditService::class.java)
    private val service = VendorService(vendors, audit)

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
        val auditRecord = argumentCaptor<AuditRecord>()
        verify(audit).record(auditRecord.capture())
        assertEquals(AuditAction.VENDOR_ARCHIVED, auditRecord.firstValue.action)
        assertEquals(listOf("status"), auditRecord.firstValue.changedFields)
    }
}
