package com.github.lambda.infra.repository

import com.github.lambda.domain.model.catalog.CatalogColumnEntity
import com.github.lambda.domain.model.catalog.CatalogFilters
import com.github.lambda.domain.model.catalog.CatalogTableEntity
import com.github.lambda.domain.model.catalog.ColumnInfo
import com.github.lambda.domain.model.catalog.TableDetail
import com.github.lambda.domain.model.catalog.TableFreshness
import com.github.lambda.domain.model.catalog.TableInfo
import com.github.lambda.domain.model.catalog.TableOwnership
import com.github.lambda.domain.model.catalog.TableQuality
import com.github.lambda.domain.repository.CatalogColumnRepositoryJpa
import com.github.lambda.domain.repository.CatalogRepository
import com.github.lambda.domain.repository.CatalogTableRepositoryDsl
import com.github.lambda.domain.repository.CatalogTableRepositoryJpa
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Repository

/**
 * Catalog Repository Implementation
 *
 * Adapter that implements the CatalogRepository interface using JPA entities.
 * This allows the existing CatalogService to work with our self-managed MySQL DB
 * instead of external catalog systems.
 */
@Repository
class CatalogRepositoryImpl(
    private val catalogTableRepositoryJpa: CatalogTableRepositoryJpa,
    private val catalogTableRepositoryDsl: CatalogTableRepositoryDsl,
    private val catalogColumnRepositoryJpa: CatalogColumnRepositoryJpa,
) : CatalogRepository {
    override fun listTables(filters: CatalogFilters): List<TableInfo> {
        val pageable = PageRequest.of(filters.offset / filters.limit, filters.limit)
        val page = catalogTableRepositoryDsl.findByFilters(filters, pageable)

        return page.content.map { entity -> toTableInfo(entity) }
    }

    override fun searchTables(
        keyword: String,
        project: String?,
        limit: Int,
    ): List<TableInfo> {
        val entities = catalogTableRepositoryDsl.searchByKeyword(keyword, project, limit)

        return entities.map { entity ->
            // Add match context based on where the keyword was found
            val matchContext = buildMatchContext(entity, keyword)
            toTableInfo(entity, matchContext)
        }
    }

    override fun getTableDetail(tableRef: String): TableDetail? {
        val entity = catalogTableRepositoryJpa.findByName(tableRef) ?: return null
        return toTableDetail(entity)
    }

    override fun getTableColumns(tableRef: String): List<ColumnInfo> {
        val entity = catalogTableRepositoryJpa.findByName(tableRef) ?: return emptyList()
        val columns = catalogColumnRepositoryJpa.findByCatalogTableIdOrderByOrdinalPositionAsc(entity.id!!)
        return columns.map { toColumnInfo(it) }
    }

    override fun getSampleData(
        tableRef: String,
        limit: Int,
    ): List<Map<String, Any>> {
        // Sample data would come from actual query execution against the data warehouse
        // For now, return empty list as we don't have direct BigQuery/Trino access
        // This could be enhanced to store sample data snapshots in the database
        return emptyList()
    }

    // === Entity to Domain Model Conversion ===

    private fun toTableInfo(
        entity: CatalogTableEntity,
        matchContext: String? = null,
    ): TableInfo =
        TableInfo(
            name = entity.name,
            engine = entity.engine,
            owner = entity.owner,
            team = entity.team,
            tags = entity.tags,
            rowCount = entity.rowCount,
            lastUpdated = entity.lastUpdated,
            matchContext = matchContext,
        )

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

    private fun buildMatchContext(
        entity: CatalogTableEntity,
        keyword: String,
    ): String {
        val lowerKeyword = keyword.lowercase()
        val columns = catalogColumnRepositoryJpa.findByCatalogTableId(entity.id!!)

        return when {
            entity.name.lowercase().contains(lowerKeyword) ->
                "Matched in table name: ${entity.name}"

            entity.description?.lowercase()?.contains(lowerKeyword) == true ->
                "Matched in description: ${extractMatchSnippet(entity.description, keyword)}"

            columns.any { it.name.lowercase().contains(lowerKeyword) } -> {
                val matchingColumn = columns.first { it.name.lowercase().contains(lowerKeyword) }
                "Matched in column: ${matchingColumn.name}"
            }

            else -> "Matched in metadata"
        }
    }

    private fun extractMatchSnippet(
        text: String?,
        keyword: String,
        contextLength: Int = 50,
    ): String {
        if (text == null) return ""

        val lowerText = text.lowercase()
        val lowerKeyword = keyword.lowercase()
        val index = lowerText.indexOf(lowerKeyword)

        if (index == -1) return text.take(contextLength)

        val start = maxOf(0, index - contextLength / 2)
        val end = minOf(text.length, index + keyword.length + contextLength / 2)

        val prefix = if (start > 0) "..." else ""
        val suffix = if (end < text.length) "..." else ""

        return "$prefix${text.substring(start, end)}$suffix"
    }
}
