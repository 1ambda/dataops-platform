package com.github.lambda.infra.repository

import com.github.lambda.domain.model.catalog.CatalogColumnEntity
import com.github.lambda.domain.model.catalog.CatalogTableEntity
import com.github.lambda.domain.model.catalog.ColumnInfo
import com.github.lambda.domain.model.catalog.TableDetail
import com.github.lambda.domain.model.catalog.TableFreshness
import com.github.lambda.domain.model.catalog.TableOwnership
import com.github.lambda.domain.model.catalog.TableQuality
import com.github.lambda.domain.repository.CatalogColumnRepositoryJpa
import com.github.lambda.domain.repository.CatalogRepositoryJpa
import com.github.lambda.domain.repository.CatalogTableRepositoryJpa
import org.springframework.stereotype.Repository

/**
 * Catalog Repository JPA Implementation
 *
 * Adapter that implements the CatalogRepositoryJpa interface using JPA entities.
 * Handles simple CRUD operations following Pure Hexagonal Architecture.
 *
 * Bean naming follows convention: @Repository("catalogRepositoryJpa")
 */
@Repository("catalogRepositoryJpa")
class CatalogRepositoryJpaImpl(
    private val catalogTableRepositoryJpa: CatalogTableRepositoryJpa,
    private val catalogColumnRepositoryJpa: CatalogColumnRepositoryJpa,
) : CatalogRepositoryJpa {
    override fun getTableDetail(tableRef: String): TableDetail? {
        val entity = catalogTableRepositoryJpa.findByName(tableRef) ?: return null
        return toTableDetail(entity)
    }

    override fun getTableColumns(tableRef: String): List<ColumnInfo> {
        val entity = catalogTableRepositoryJpa.findByName(tableRef) ?: return emptyList()
        val columns = catalogColumnRepositoryJpa.findByCatalogTableIdOrderByOrdinalPositionAsc(entity.id!!)
        return columns.map { toColumnInfo(it) }
    }

    // === Entity to Domain Model Conversion ===

    private fun toTableDetail(entity: CatalogTableEntity): TableDetail {
        val columns = catalogColumnRepositoryJpa.findByCatalogTableIdOrderByOrdinalPositionAsc(entity.id!!)
        return TableDetail(
            name = entity.name,
            engine = entity.engine,
            owner = entity.owner,
            team = entity.team,
            description = entity.description,
            tags = entity.tags,
            rowCount = entity.rowCount,
            lastUpdated = entity.lastUpdated,
            basecampUrl = entity.basecampUrl,
            columns = columns.map { toColumnInfo(it) },
            ownership = toTableOwnership(entity),
            freshness = entity.lastUpdated?.let { toTableFreshness(entity) },
            quality = entity.qualityScore?.let { toTableQuality(entity) },
            sampleData = emptyList(), // Sample data not stored in entity
        )
    }

    private fun toColumnInfo(entity: CatalogColumnEntity): ColumnInfo =
        ColumnInfo(
            name = entity.name,
            dataType = entity.dataType,
            description = entity.description,
            isPii = entity.isPii,
            fillRate = entity.fillRate,
            distinctCount = entity.distinctCount,
        )

    private fun toTableOwnership(entity: CatalogTableEntity): TableOwnership =
        TableOwnership(
            owner = entity.owner,
            team = entity.team,
            stewards = entity.getStewardsList(),
            consumers = entity.getConsumersList(),
        )

    private fun toTableFreshness(entity: CatalogTableEntity): TableFreshness =
        TableFreshness(
            lastUpdated = entity.lastUpdated!!,
            avgUpdateLagHours = entity.avgUpdateLagHours,
            updateFrequency = entity.updateFrequency,
            isStale = entity.isStale(),
            staleThresholdHours = entity.staleThresholdHours,
        )

    private fun toTableQuality(entity: CatalogTableEntity): TableQuality =
        TableQuality(
            score = entity.qualityScore ?: 0,
            totalTests = 0, // Would need quality test tracking
            passedTests = 0,
            failedTests = 0,
            warnings = 0,
            recentTests = emptyList(),
        )
}
