package com.resourcetracker.resource

import com.resourcetracker.audit.AuditService
import com.resourcetracker.audit.AuditAction
import com.resourcetracker.audit.AuditRecord
import com.resourcetracker.vendor.VendorRepository
import com.resourcetracker.common.BusinessRuleException
import com.resourcetracker.common.BadRequestException
import com.resourcetracker.request.service.StockAvailabilityService
import com.resourcetracker.request.repository.StockMovementRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.verify
import java.util.Optional

class ResourceServiceTest {
    private val resources = mock(ResourceRepository::class.java)
    private val codes = mock(ResourceCodeGenerator::class.java)
    private val vendors = mock(VendorRepository::class.java)
    private val audit = mock(AuditService::class.java)
    private val availability = mock(StockAvailabilityService::class.java)
    private val movements = mock(StockMovementRepository::class.java)
    private val service = ResourceService(resources, codes, vendors, audit, availability, movements)

    private fun resource(active: Boolean) = Resource(
        id = 1,
        code = "R-1",
        name = "Projector",
        type = ResourceType.EXTERNAL,
        category = ResourceCategory.EQUIPMENT,
        unitOfMeasure = "unit",
        active = active,
    )

    private fun request(active: Boolean? = null) = UpdateResourceRequest(
        name = "Projector",
        type = ResourceType.EXTERNAL,
        category = ResourceCategory.EQUIPMENT,
        unitOfMeasure = "unit",
        active = active,
    )

    private fun internalRequest(quantity: Int?) = CreateResourceRequest(
        name = "Projector",
        type = ResourceType.INTERNAL,
        category = ResourceCategory.EQUIPMENT,
        unitOfMeasure = "unit",
        quantity = quantity,
    )

    @Test
    fun `update without active preserves current state`() {
        val resource = resource(active = false)
        `when`(resources.findById(resource.id)).thenReturn(Optional.of(resource))
        `when`(resources.save(resource)).thenReturn(resource)

        val response = service.update(resource.id, request())

        assertEquals(false, response.active)
    }

    @Test
    fun `update can reactivate resource`() {
        val resource = resource(active = false)
        `when`(resources.findById(resource.id)).thenReturn(Optional.of(resource))
        `when`(resources.save(resource)).thenReturn(resource)

        val response = service.update(resource.id, request(active = true))

        assertEquals(true, response.active)
    }

    @Test
    fun `resource metadata changes are audited`() {
        val resource = resource(active = true)
        `when`(resources.findById(resource.id)).thenReturn(Optional.of(resource))
        `when`(resources.save(resource)).thenReturn(resource)

        service.update(
            resource.id,
            UpdateResourceRequest(
                name = "Updated projector",
                type = ResourceType.EXTERNAL,
                category = ResourceCategory.EQUIPMENT,
                unitOfMeasure = "unit",
            ),
        )

        val auditRecord = argumentCaptor<AuditRecord>()
        verify(audit).record(auditRecord.capture())
        assertEquals(AuditAction.RESOURCE_UPDATED, auditRecord.firstValue.action)
        assertEquals("Projector", (auditRecord.firstValue.previousState as Map<*, *>)["name"])
        assertEquals("Updated projector", (auditRecord.firstValue.newState as Map<*, *>)["name"])
    }

    @Test
    fun `internal create requires quantity`() {
        assertThrows(BadRequestException::class.java) {
            service.create(internalRequest(null), 1)
        }
    }

    @Test
    fun `external create rejects quantity`() {
        val request = CreateResourceRequest(
            name = "Projector",
            type = ResourceType.EXTERNAL,
            category = ResourceCategory.EQUIPMENT,
            unitOfMeasure = "unit",
            quantity = 1,
        )

        assertThrows(BadRequestException::class.java) {
            service.create(request, 1)
        }
    }

    @Test
    fun `zero quantity is out of stock`() {
        `when`(codes.next()).thenReturn("R-2")
        `when`(resources.save(org.mockito.ArgumentMatchers.any())).thenAnswer { it.arguments[0] }

        val response = service.create(internalRequest(0), 1)

        assertEquals(ResourceStatus.OUT_OF_STOCK, response.status)
    }

    @Test
    fun `resource update cannot change physical quantity`() {
        val resource = Resource(
            code = "R-1",
            name = "Projector",
            type = ResourceType.INTERNAL,
            category = ResourceCategory.EQUIPMENT,
            unitOfMeasure = "unit",
            quantity = 0,
            status = ResourceStatus.OUT_OF_STOCK,
        )
        `when`(resources.findById(resource.id)).thenReturn(Optional.of(resource))
        `when`(resources.save(resource)).thenReturn(resource)

        assertThrows(BusinessRuleException::class.java) {
            service.update(
                resource.id,
                UpdateResourceRequest(
                    name = "Projector",
                    type = ResourceType.INTERNAL,
                    category = ResourceCategory.EQUIPMENT,
                    unitOfMeasure = "unit",
                    quantity = 5,
                ),
            )
        }
    }
}
