package com.resourcetracker.resource

import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.data.jpa.repository.Modifying

interface ResourceAssetRepository : JpaRepository<ResourceAsset, Int> {
    fun findAllByResourceIdOrderByAssetCode(resourceId: Int): List<ResourceAsset>
    fun countByResourceIdAndStatus(resourceId: Int, status: AssetStatus): Long
    fun existsByAssetCode(assetCode: String): Boolean
    fun existsBySerialNumber(serialNumber: String): Boolean
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from ResourceAsset a where a.id = :id")
    fun findForUpdateById(@Param("id") id: Int): ResourceAsset?
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from ResourceAsset a where a.id in :ids order by a.id")
    fun findAllForUpdateByIdIn(@Param("ids") ids: Collection<Int>): List<ResourceAsset>
    @Query("select count(a) from ResourceAsset a where a.resourceId = :resourceId and a.status = com.resourcetracker.resource.AssetStatus.AVAILABLE")
    fun countAvailable(@Param("resourceId") resourceId: Int): Long
}

interface AssetMovementRepository : JpaRepository<AssetMovement, Int> {
    fun findAllByAssetIdOrderByCreatedAtAscIdAsc(assetId: Int): List<AssetMovement>
    @Query("select m from AssetMovement m, ResourceAsset a where m.assetId = a.id and a.resourceId = :resourceId order by m.createdAt asc, m.id asc")
    fun findAllByResourceIdOrderByCreatedAtAsc(@Param("resourceId") resourceId: Int): List<AssetMovement>
}

interface StockIssueAssetRepository : JpaRepository<StockIssueAsset, StockIssueAssetId> {
    fun findAllByIssueId(issueId: Int): List<StockIssueAsset>
}

interface StockReturnAssetRepository : JpaRepository<StockReturnAsset, StockReturnAssetId> {
    fun findAllByReturnId(returnId: Int): List<StockReturnAsset>
    @Modifying
    @Query("delete from StockReturnAsset a where a.returnId = :returnId and a.assetId in :assetIds")
    fun deleteAllByReturnIdAndAssetIdIn(@Param("returnId") returnId: Int, @Param("assetIds") assetIds: Collection<Int>): Int
}
