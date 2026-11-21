package com.resourcetracker.resource

import com.resourcetracker.common.ResourceNotFoundException
import com.resourcetracker.request.service.StockCorrectionRequest
import com.resourcetracker.request.service.StockOperationsService
import com.resourcetracker.user.Role
import com.resourcetracker.user.UserRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException

@Service
class InventoryCorrectionService(
    private val resources: ResourceRepository,
    private val users: UserRepository,
    private val stockOperations: StockOperationsService,
    private val assetService: AssetService,
) {
    @Transactional
    fun correct(resourceId: Int, actorId: Int, dto: InventoryCorrectionRequest): InventoryCorrectionResponse {
        val actor = users.findById(actorId).orElseThrow { ResourceNotFoundException("User not found") }
        if (actor.role != Role.MANAGING_DIRECTOR) throw ResponseStatusException(HttpStatus.FORBIDDEN, "Managing director role required")
        val resource = resources.findForUpdateById(resourceId) ?: throw ResourceNotFoundException("Resource not found")
        return when (resource.trackingMethod) {
            TrackingMethod.QUANTITY -> {
                if (dto.newQuantity == null || !dto.assets.isNullOrEmpty()) {
                    throw ResponseStatusException(HttpStatus.BAD_REQUEST, "QUANTITY corrections require newQuantity and must omit assets")
                }
                val movement = stockOperations.correctResource(resourceId, actorId, StockCorrectionRequest(dto.newQuantity, dto.reason))
                InventoryCorrectionResponse(resourceId, resource.trackingMethod, quantityChange = movement.quantity)
            }
            TrackingMethod.INDIVIDUAL -> {
                if (dto.newQuantity != null || dto.assets.isNullOrEmpty()) {
                    throw ResponseStatusException(HttpStatus.BAD_REQUEST, "INDIVIDUAL corrections require assets and must omit newQuantity")
                }
                if (dto.assets.map { it.assetId }.distinct().size != dto.assets.size) {
                    throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Asset IDs must be unique")
                }
                val changed = assetService.correctMany(resourceId, actorId, dto.assets, dto.reason)
                InventoryCorrectionResponse(resourceId, resource.trackingMethod, assets = changed)
            }
        }
    }
}
