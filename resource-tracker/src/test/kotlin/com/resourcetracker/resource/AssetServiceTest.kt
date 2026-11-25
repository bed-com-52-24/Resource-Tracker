package com.resourcetracker.resource

import com.resourcetracker.common.BadRequestException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.mockito.kotlin.any
import org.mockito.kotlin.verify

class AssetServiceTest {
    private val resources = mock(ResourceRepository::class.java)
    private val assets = mock(ResourceAssetRepository::class.java)
    private val movements = mock(AssetMovementRepository::class.java)
    private val service = AssetService(resources, assets, movements)

    @Test
    fun `registers actual assets and records registration movement`() {
        val resource = Resource(1, "R-1", "Laptop", ResourceType.INTERNAL, ResourceCategory.EQUIPMENT, trackingMethod = TrackingMethod.INDIVIDUAL, unitOfMeasure = "unit")
        `when`(resources.findForUpdateById(1)).thenReturn(resource)
        `when`(assets.existsByAssetCode("LAP-001")).thenReturn(false)
        `when`(assets.save(any<ResourceAsset>())).thenAnswer { it.getArgument<ResourceAsset>(0).apply { id = 12 } }

        val result = service.register(1, 7, listOf(AssetRegistrationRequest("LAP-001", "SN-1", "Room 2")))

        assertEquals(1, result.size)
        assertEquals("LAP-001", result.single().assetCode)
        verify(movements).save(any<AssetMovement>())
    }

    @Test
    fun `rejects asset registration against quantity resource`() {
        val resource = Resource(1, "R-1", "Paper", ResourceType.INTERNAL, ResourceCategory.CONSUMABLE, unitOfMeasure = "ream", quantity = 20)
        `when`(resources.findForUpdateById(1)).thenReturn(resource)

        assertThrows(BadRequestException::class.java) {
            service.register(1, 7, listOf(AssetRegistrationRequest("PAPER-1")))
        }
    }

    @Test
    fun `prevents disposed asset from returning to availability`() {
        val asset = ResourceAsset(id = 12, resourceId = 1, assetCode = "LAP-001", status = AssetStatus.DISPOSED)
        `when`(assets.findForUpdateById(12)).thenReturn(asset)

        assertThrows(BadRequestException::class.java) {
            service.correct(12, 7, AssetCorrectionRequest(AssetStatus.AVAILABLE, "Inventory review"))
        }
    }
}
