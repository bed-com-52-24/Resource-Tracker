package com.resourcetracker.resource

import com.resourcetracker.department.DepartmentRepository
import com.resourcetracker.vendor.VendorRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.util.Optional
import java.util.UUID

class ResourceServiceTest {
    private val resources = mock(ResourceRepository::class.java)
    private val codes = mock(ResourceCodeGenerator::class.java)
    private val departments = mock(DepartmentRepository::class.java)
    private val vendors = mock(VendorRepository::class.java)
    private val service = ResourceService(resources, codes, departments, vendors)

    private fun resource(active: Boolean) = Resource(
        id = UUID.randomUUID(),
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
}
