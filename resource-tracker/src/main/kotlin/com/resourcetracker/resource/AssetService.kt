package com.resourcetracker.resource

import com.resourcetracker.common.BadRequestException
import com.resourcetracker.common.ResourceNotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class AssetService(
    private val resources: ResourceRepository,
    private val assets: ResourceAssetRepository,
    private val movements: AssetMovementRepository,
) {
    @Transactional
    fun register(resourceId: Int, actorId: Int, requests: List<AssetRegistrationRequest>): List<ResourceAssetResponse> {
        val resource = resources.findForUpdateById(resourceId) ?: throw ResourceNotFoundException("Resource not found")
        if (!resource.active) throw BadRequestException("Cannot register assets for an inactive resource")
        if (resource.trackingMethod != TrackingMethod.INDIVIDUAL) throw BadRequestException("Resource must use INDIVIDUAL tracking")
        if (requests.isEmpty()) throw BadRequestException("At least one asset is required")
        val codes = requests.map { it.assetCode.trim() }
        val serials = requests.mapNotNull { it.serialNumber?.trim()?.takeIf(String::isNotEmpty) }
        if (codes.distinct().size != codes.size || codes.any(assets::existsByAssetCode)) throw BadRequestException("Asset codes must be unique")
        if (serials.distinct().size != serials.size || serials.any(assets::existsBySerialNumber)) throw BadRequestException("Serial numbers must be unique")
        return requests.mapIndexed { index, dto ->
            val asset = assets.save(ResourceAsset(resourceId = resourceId, assetCode = codes[index], serialNumber = dto.serialNumber?.trim()?.takeIf(String::isNotEmpty), location = dto.location?.trim()))
            movements.save(AssetMovement(assetId = asset.id, type = AssetMovementType.REGISTERED, newStatus = asset.status, newLocation = asset.location, reason = "Asset registered", actorId = actorId))
            asset.toResponse()
        }
    }

    @Transactional(readOnly = true)
    fun list(resourceId: Int): List<ResourceAssetResponse> {
        if (!resources.existsById(resourceId)) throw ResourceNotFoundException("Resource not found")
        return assets.findAllByResourceIdOrderByAssetCode(resourceId).map { it.toResponse() }
    }

    @Transactional(readOnly = true)
    fun history(assetId: Int): List<AssetMovementResponse> {
        if (!assets.existsById(assetId)) throw ResourceNotFoundException("Asset not found")
        return movements.findAllByAssetIdOrderByCreatedAtAscIdAsc(assetId).map {
            AssetMovementResponse(it.id, it.type, it.previousStatus, it.newStatus, it.previousLocation, it.newLocation, it.reason, it.actorId, it.createdAt, it.relatedIssueId, it.relatedReturnId, it.previousAssignedUserId, it.newAssignedUserId)
        }
    }

    @Transactional
    fun correct(assetId: Int, actorId: Int, dto: AssetCorrectionRequest, expectedResourceId: Int? = null): ResourceAssetResponse {
        val asset = assets.findForUpdateById(assetId) ?: throw ResourceNotFoundException("Asset not found")
        if (expectedResourceId != null && asset.resourceId != expectedResourceId) throw ResourceNotFoundException("Asset not found for this resource")
        val allowed = when (asset.status) {
            AssetStatus.AVAILABLE -> setOf(AssetStatus.DAMAGED, AssetStatus.MISSING, AssetStatus.DISPOSED)
            AssetStatus.RESERVED -> setOf(AssetStatus.AVAILABLE, AssetStatus.DAMAGED, AssetStatus.MISSING)
            AssetStatus.ISSUED -> setOf(AssetStatus.DAMAGED, AssetStatus.MISSING)
            AssetStatus.RETURN_PENDING -> setOf(AssetStatus.DAMAGED, AssetStatus.MISSING)
            AssetStatus.DAMAGED -> setOf(AssetStatus.AVAILABLE, AssetStatus.MISSING, AssetStatus.DISPOSED)
            AssetStatus.MISSING -> setOf(AssetStatus.AVAILABLE, AssetStatus.DISPOSED)
            AssetStatus.DISPOSED -> emptySet()
        }
        if (dto.status !in allowed) throw BadRequestException("Asset status transition ${asset.status} -> ${dto.status} is not allowed")
        val previous = asset.status
        val previousAssignee = asset.assignedUserId
        asset.status = dto.status
        if (dto.status != AssetStatus.ISSUED) asset.assignedUserId = null
        assets.save(asset)
        movements.save(AssetMovement(assetId = asset.id, type = if (dto.status == AssetStatus.DISPOSED) AssetMovementType.DISPOSED else AssetMovementType.CORRECTED, previousStatus = previous, newStatus = dto.status, previousLocation = asset.location, newLocation = asset.location, previousAssignedUserId = previousAssignee, newAssignedUserId = asset.assignedUserId, reason = dto.reason.trim(), actorId = actorId))
        return asset.toResponse()
    }

    @Transactional
    fun correctMany(resourceId: Int, actorId: Int, corrections: List<AssetStatusCorrection>, reason: String): List<ResourceAssetResponse> {
        val corrected = corrections.sortedBy { it.assetId }.associate { correction ->
            correction.assetId to correct(
                correction.assetId,
                actorId,
                AssetCorrectionRequest(correction.status, reason),
                expectedResourceId = resourceId,
            )
        }
        return corrections.map { corrected.getValue(it.assetId) }
    }

    @Transactional(readOnly = true)
    fun counts(resourceId: Int): AssetCounts = AssetCounts(
        totalRegistered = AssetStatus.entries.sumOf { assets.countByResourceIdAndStatus(resourceId, it) },
        available = assets.countByResourceIdAndStatus(resourceId, AssetStatus.AVAILABLE),
        reserved = assets.countByResourceIdAndStatus(resourceId, AssetStatus.RESERVED),
        issued = assets.countByResourceIdAndStatus(resourceId, AssetStatus.ISSUED),
        damaged = assets.countByResourceIdAndStatus(resourceId, AssetStatus.DAMAGED),
        missing = assets.countByResourceIdAndStatus(resourceId, AssetStatus.MISSING),
        disposed = assets.countByResourceIdAndStatus(resourceId, AssetStatus.DISPOSED),
        returnPending = assets.countByResourceIdAndStatus(resourceId, AssetStatus.RETURN_PENDING),
    )

    private fun ResourceAsset.toResponse() = ResourceAssetResponse(id, resourceId, assetCode, serialNumber, status, location, assignedUserId, createdAt)
}
