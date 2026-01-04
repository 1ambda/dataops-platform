package com.github.lambda.infra.repository.catalog

import com.github.lambda.domain.entity.catalog.CatalogTableEntity
import com.github.lambda.domain.model.catalog.CatalogFilters
import com.github.lambda.domain.model.catalog.TableInfo
import com.github.lambda.domain.repository.catalog.CatalogColumnRepositoryJpa
import com.github.lambda.domain.repository.catalog.CatalogRepositoryDsl
import com.github.lambda.domain.repository.catalog.CatalogTableRepositoryDsl
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Repository

/**
 * Catalog Repository DSL Implementation
 *
 * Adapter that implements the CatalogRepositoryDsl interface using QueryDSL.
 * Handles complex queries following Pure Hexagonal Architecture.
 *
 * Bean naming follows convention: @Repository("catalogRepositoryDsl")
 */
@Repository("catalogRepositoryDsl")
class CatalogRepositoryDslImpl(
    private val catalogTableRepositoryDsl: CatalogTableRepositoryDsl,
    private val catalogColumnRepositoryJpa: CatalogColumnRepositoryJpa,
) : CatalogRepositoryDsl {
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
