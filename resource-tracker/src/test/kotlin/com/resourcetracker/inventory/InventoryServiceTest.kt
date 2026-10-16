package com.resourcetracker.inventory

import com.resourcetracker.resource.Resource
import com.resourcetracker.resource.ResourceCategory
import com.resourcetracker.resource.ResourceRepository
import com.resourcetracker.resource.ResourceType
import com.resourcetracker.security.AuthenticatedUser
import com.resourcetracker.user.Role
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.springframework.context.ApplicationEventPublisher
import java.util.Optional
import java.util.UUID
import kotlin.test.assertTrue

class InventoryServiceTest {
    @Test
    fun `availability classification follows documented stock rules`() {
        assertEquals(AvailabilityStatus.AVAILABLE, classifyAvailability(5, 0, 0))
        assertEquals(AvailabilityStatus.PARTIALLY_AVAILABLE, classifyAvailability(2, 2, 1))
        assertEquals(AvailabilityStatus.FULLY_RESERVED, classifyAvailability(0, 3, 0))
        assertEquals(AvailabilityStatus.OUT_OF_STOCK, classifyAvailability(0, 0, 4))
        assertEquals(AvailabilityStatus.OUT_OF_STOCK, classifyAvailability(0, 0, 0))
    }

    private val inventories = mock(InventoryRepository::class.java)
    private val audits = mock(InventoryAuditLogRepository::class.java)
    private val resources = mock(ResourceRepository::class.java)
    private val users = mock(com.resourcetracker.user.UserRepository::class.java)
    private val events = mock(ApplicationEventPublisher::class.java)
    private val service = InventoryService(inventories, audits, resources, users, events)
    private val caller = AuthenticatedUser(
        id = 1,
        username = "admin",
        role = Role.ADMIN,
        jti = "test",
        tokenExpiresAt = java.time.Instant.MAX,
    )
    private val resource = Resource(
        id = UUID.randomUUID(), code = "R-1", name = "Printer paper",
        type = ResourceType.INTERNAL, category = ResourceCategory.CONSUMABLE, unitOfMeasure = "ream",
    )
    private val inventory = Inventory(resource = resource, location = "HQ", totalQuantity = 1)

    @Test
    fun `only one reserve can consume the last unit`() {
        `when`(inventories.findAllByResourceIdOrderByLocation(resource.id)).thenReturn(listOf(inventory))
        `when`(inventories.findForUpdate(resource.id, "HQ")).thenReturn(inventory)

        service.transact(
            resource.id,
            InventoryTransactionRequest(
                type = InventoryTransactionType.RESERVE,
                quantity = 1,
                location = "HQ",
            ),
            caller,
        )

        assertEquals(0, inventory.availableQuantity)
        assertThrows<com.resourcetracker.common.BusinessRuleException> {
            service.transact(
                resource.id,
                InventoryTransactionRequest(
                    type = InventoryTransactionType.RESERVE,
                    quantity = 1,
                    location = "HQ",
                ),
                caller,
            )
        }
        assertEquals(1, inventory.reservedQuantity)
        assertTrue(inventory.availableQuantity >= 0)
    }
}
