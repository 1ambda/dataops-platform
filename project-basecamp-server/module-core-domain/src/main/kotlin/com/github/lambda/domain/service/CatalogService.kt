package com.github.lambda.domain.service

import com.github.lambda.common.exception.InvalidTableReferenceException
import com.github.lambda.common.exception.TableNotFoundException
import com.github.lambda.domain.model.catalog.CatalogFilters
import com.github.lambda.domain.model.catalog.SampleQuery
import com.github.lambda.domain.model.catalog.TableDetail
import com.github.lambda.domain.model.catalog.TableInfo
import com.github.lambda.domain.repository.catalog.CatalogRepositoryDsl
import com.github.lambda.domain.repository.catalog.CatalogRepositoryJpa
import com.github.lambda.domain.repository.catalog.SampleQueryRepositoryDsl
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Catalog Service
 *
 * Provides data discovery capabilities for browsing and searching table metadata.
 * Services are concrete classes (no interfaces) following Pure Hexagonal Architecture.
 *
 * This is a simple MVP implementation that delegates to repositories.
 * No cache service is implemented as per exclusion requirements.
 */
@Service
@Transactional(readOnly = true)
class CatalogService(
    private val catalogRepositoryJpa: CatalogRepositoryJpa,
    private val catalogRepositoryDsl: CatalogRepositoryDsl,
    private val sampleQueryRepository: SampleQueryRepositoryDsl,
) {
    companion object {
        private const val DEFAULT_SAMPLE_LIMIT = 10
    }

    /**
     * List tables with optional filters
     *
     * @param project Filter by project/catalog name
     * @param dataset Filter by dataset/schema name
     * @param owner Filter by owner email
     * @param team Filter by team (e.g., @data-eng)
     * @param tags Filter by tags (comma-separated, AND condition)
     * @param limit Maximum results (1-500)
     * @param offset Pagination offset
     * @return List of table summaries
     */
    fun listTables(
        project: String? = null,
        dataset: String? = null,
        owner: String? = null,
        team: String? = null,
        tags: Set<String> = emptySet(),
        limit: Int = 50,
        offset: Int = 0,
    ): List<TableInfo> {
        val filters =
            CatalogFilters(
                project = project,
                dataset = dataset,
                owner = owner,
                team = team,
                tags = tags,
                limit = limit.coerceIn(1, 500),
                offset = offset.coerceAtLeast(0),
            )

        return catalogRepositoryDsl.listTables(filters)
    }

    /**
     * Search tables by keyword
     *
     * Searches across table names, column names, and descriptions.
     *
     * @param keyword Search keyword (minimum 2 characters)
     * @param project Optional project filter
     * @param limit Maximum results (1-100)
     * @return List of matching tables with match context
     */
    fun searchTables(
        keyword: String,
        project: String? = null,
        limit: Int = 20,
    ): List<TableInfo> {
        require(keyword.length >= 2) { "Keyword must be at least 2 characters" }

        return catalogRepositoryDsl.searchTables(
            keyword = keyword,
            project = project,
            limit = limit.coerceIn(1, 100),
        )
    }

    /**
     * Get detailed table information
     *
     * @param tableRef Fully qualified table reference (project.dataset.table)
     * @param includeSample Whether to include sample data
     * @return Table detail
     * @throws InvalidTableReferenceException if table reference format is invalid
     * @throws TableNotFoundException if table not found
     */
    fun getTableDetail(
        tableRef: String,
        includeSample: Boolean = false,
    ): TableDetail {
        validateTableReference(tableRef)

        val detail =
            catalogRepositoryJpa.getTableDetail(tableRef)
                ?: throw TableNotFoundException(tableRef)

        return if (includeSample) {
            val sampleData = catalogRepositoryDsl.getSampleData(tableRef, DEFAULT_SAMPLE_LIMIT)
            detail.copy(sampleData = sampleData)
        } else {
            detail
        }
    }

    /**
     * Get sample queries for a table
     *
     * @param tableRef Fully qualified table reference
     * @param limit Maximum queries to return (1-20)
     * @return List of sample queries sorted by popularity
     * @throws InvalidTableReferenceException if table reference format is invalid
     * @throws TableNotFoundException if table not found
     */
    fun getSampleQueries(
        tableRef: String,
        limit: Int = 5,
    ): List<SampleQuery> {
        validateTableReference(tableRef)

        // Verify table exists
        catalogRepositoryJpa.getTableDetail(tableRef)
            ?: throw TableNotFoundException(tableRef)

        return sampleQueryRepository.findByTableRef(
            tableRef = tableRef,
            limit = limit.coerceIn(1, 20),
        )
    }

    /**
     * Validate table reference format
     *
     * Expected format: project.dataset.table
     */
    private fun validateTableReference(tableRef: String) {
        val parts = tableRef.split(".")
        if (parts.size != 3 || parts.any { it.isBlank() }) {
            throw InvalidTableReferenceException(tableRef)
        }
    }
}
