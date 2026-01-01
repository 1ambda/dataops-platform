package com.github.lambda.mapper

import com.github.lambda.domain.model.catalog.CatalogColumnEntity
import com.github.lambda.domain.model.catalog.CatalogTableEntity
import com.github.lambda.domain.model.catalog.ColumnInfo
import com.github.lambda.domain.model.catalog.QualityTestResult
import com.github.lambda.domain.model.catalog.SampleQuery
import com.github.lambda.domain.model.catalog.SampleQueryEntity
import com.github.lambda.domain.model.catalog.TableDetail
import com.github.lambda.domain.model.catalog.TableFreshness
import com.github.lambda.domain.model.catalog.TableInfo
import com.github.lambda.domain.model.catalog.TableOwnership
import com.github.lambda.domain.model.catalog.TableQuality
import com.github.lambda.dto.catalog.ColumnInfoResponse
import com.github.lambda.dto.catalog.CreateCatalogTableRequest
import com.github.lambda.dto.catalog.CreateColumnRequest
import com.github.lambda.dto.catalog.CreateSampleQueryRequest
import com.github.lambda.dto.catalog.QualityTestResultResponse
import com.github.lambda.dto.catalog.SampleQueryResponse
import com.github.lambda.dto.catalog.TableDetailResponse
import com.github.lambda.dto.catalog.TableFreshnessResponse
import com.github.lambda.dto.catalog.TableInfoResponse
import com.github.lambda.dto.catalog.TableOwnershipResponse
import com.github.lambda.dto.catalog.TableQualityResponse
import org.springframework.stereotype.Component

/**
 * Catalog Mapper
 *
 * Handles conversions between Domain models and API DTOs.
 */
@Component
class CatalogMapper {
    /**
     * Convert TableInfo to TableInfoResponse
     *
     * Used for list/search results
     */
    fun toTableInfoResponse(domain: TableInfo): TableInfoResponse =
        TableInfoResponse(
            name = domain.name,
            engine = domain.engine,
            owner = domain.owner,
            team = domain.team,
            tags = domain.tags.sorted(),
            rowCount = domain.rowCount,
            lastUpdated = domain.lastUpdated,
            matchContext = domain.matchContext,
        )

    /**
     * Convert list of TableInfo to list of TableInfoResponse
     */
    fun toTableInfoResponseList(domains: List<TableInfo>): List<TableInfoResponse> =
        domains.map { toTableInfoResponse(it) }

    /**
     * Convert TableDetail to TableDetailResponse
     *
     * Used for detailed table view
     */
    fun toTableDetailResponse(domain: TableDetail): TableDetailResponse =
        TableDetailResponse(
            name = domain.name,
            engine = domain.engine,
            owner = domain.owner,
            team = domain.team,
            description = domain.description,
            tags = domain.tags.sorted(),
            rowCount = domain.rowCount,
            lastUpdated = domain.lastUpdated,
            basecampUrl = domain.basecampUrl,
            columns = domain.columns.map { toColumnInfoResponse(it) },
            ownership = domain.ownership?.let { toTableOwnershipResponse(it) },
            freshness = domain.freshness?.let { toTableFreshnessResponse(it) },
            quality = domain.quality?.let { toTableQualityResponse(it) },
            sampleData = domain.sampleData.takeIf { it.isNotEmpty() },
        )

    /**
     * Convert ColumnInfo to ColumnInfoResponse
     */
    fun toColumnInfoResponse(domain: ColumnInfo): ColumnInfoResponse =
        ColumnInfoResponse(
            name = domain.name,
            dataType = domain.dataType,
            description = domain.description,
            isPii = domain.isPii,
            fillRate = domain.fillRate,
            distinctCount = domain.distinctCount,
        )

    /**
     * Convert TableOwnership to TableOwnershipResponse
     */
    fun toTableOwnershipResponse(domain: TableOwnership): TableOwnershipResponse =
        TableOwnershipResponse(
            owner = domain.owner,
            team = domain.team,
            stewards = domain.stewards,
            consumers = domain.consumers,
        )

    /**
     * Convert TableFreshness to TableFreshnessResponse
     */
    fun toTableFreshnessResponse(domain: TableFreshness): TableFreshnessResponse =
        TableFreshnessResponse(
            lastUpdated = domain.lastUpdated,
            avgUpdateLagHours = domain.avgUpdateLagHours,
            updateFrequency = domain.updateFrequency,
            isStale = domain.isStale,
            staleThresholdHours = domain.staleThresholdHours,
        )

    /**
     * Convert TableQuality to TableQualityResponse
     */
    fun toTableQualityResponse(domain: TableQuality): TableQualityResponse =
        TableQualityResponse(
            score = domain.score,
            totalTests = domain.totalTests,
            passedTests = domain.passedTests,
            failedTests = domain.failedTests,
            warnings = domain.warnings,
            recentTests = domain.recentTests.map { toQualityTestResultResponse(it) },
        )

    /**
     * Convert QualityTestResult to QualityTestResultResponse
     */
    fun toQualityTestResultResponse(domain: QualityTestResult): QualityTestResultResponse =
        QualityTestResultResponse(
            testName = domain.testName,
            testType = domain.testType,
            status = domain.status.name.lowercase(),
            failedRows = domain.failedRows,
        )

    /**
     * Convert SampleQuery to SampleQueryResponse
     */
    fun toSampleQueryResponse(domain: SampleQuery): SampleQueryResponse =
        SampleQueryResponse(
            title = domain.title,
            sql = domain.sql,
            author = domain.author,
            runCount = domain.runCount,
            lastRun = domain.lastRun,
        )

    /**
     * Convert list of SampleQuery to list of SampleQueryResponse
     */
    fun toSampleQueryResponseList(domains: List<SampleQuery>): List<SampleQueryResponse> =
        domains.map { toSampleQueryResponse(it) }

    // === Request DTO to Entity Conversion ===

    /**
     * Convert CreateCatalogTableRequest to CatalogTableEntity
     */
    fun toEntity(request: CreateCatalogTableRequest): CatalogTableEntity {
        val parts = request.name.split(".")
        require(parts.size == 3) { "Table name must be in format: project.dataset.table" }

        val entity =
            CatalogTableEntity(
                name = request.name,
                project = parts[0],
                datasetName = parts[1],
                tableName = parts[2],
                engine = request.engine,
                owner = request.owner,
                team = request.team,
                description = request.description,
                tags = request.tags.toSet(),
                rowCount = request.rowCount,
                lastUpdated = request.lastUpdated,
                basecampUrl = request.basecampUrl,
                stewards = request.stewards?.joinToString(","),
                consumers = request.consumers?.joinToString(","),
                avgUpdateLagHours = request.avgUpdateLagHours,
                updateFrequency = request.updateFrequency,
                staleThresholdHours = request.staleThresholdHours ?: 24,
                qualityScore = request.qualityScore,
            )

        // Add columns if provided
        request.columns?.forEachIndexed { index, columnRequest ->
            val column = toColumnEntity(columnRequest, index)
            entity.addColumn(column)
        }

        return entity
    }

    /**
     * Convert CreateColumnRequest to CatalogColumnEntity
     */
    fun toColumnEntity(
        request: CreateColumnRequest,
        ordinalPosition: Int,
    ): CatalogColumnEntity =
        CatalogColumnEntity(
            name = request.name,
            dataType = request.dataType,
            description = request.description,
            isPii = request.isPii ?: false,
            fillRate = request.fillRate,
            distinctCount = request.distinctCount,
            ordinalPosition = ordinalPosition,
            isNullable = request.isNullable ?: true,
            isPrimaryKey = request.isPrimaryKey ?: false,
            isPartitionKey = request.isPartitionKey ?: false,
            isClusteringKey = request.isClusteringKey ?: false,
            defaultValue = request.defaultValue,
        )

    /**
     * Convert CreateSampleQueryRequest to SampleQueryEntity
     */
    fun toSampleQueryEntity(
        tableRef: String,
        request: CreateSampleQueryRequest,
    ): SampleQueryEntity =
        SampleQueryEntity(
            tableRef = tableRef,
            title = request.title,
            sql = request.sql,
            author = request.author,
            description = request.description,
        )

    // === Entity to Domain Model Conversion ===

    /**
     * Convert CatalogTableEntity to TableInfo
     */
    fun toTableInfo(
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

    /**
     * Convert CatalogTableEntity to TableDetail
     */
    fun toTableDetail(entity: CatalogTableEntity): TableDetail =
        TableDetail(
            name = entity.name,
            engine = entity.engine,
            owner = entity.owner,
            team = entity.team,
            description = entity.description,
            tags = entity.tags,
            rowCount = entity.rowCount,
            lastUpdated = entity.lastUpdated,
            basecampUrl = entity.basecampUrl,
            columns = entity.columns.map { toColumnInfo(it) },
            ownership = toTableOwnership(entity),
            freshness = entity.lastUpdated?.let { toTableFreshness(entity) },
            quality = entity.qualityScore?.let { toTableQualityFromEntity(entity) },
            sampleData = emptyList(),
        )

    /**
     * Convert CatalogColumnEntity to ColumnInfo
     */
    fun toColumnInfo(entity: CatalogColumnEntity): ColumnInfo =
        ColumnInfo(
            name = entity.name,
            dataType = entity.dataType,
            description = entity.description,
            isPii = entity.isPii,
            fillRate = entity.fillRate,
            distinctCount = entity.distinctCount,
        )

    /**
     * Convert CatalogTableEntity to TableOwnership
     */
    fun toTableOwnership(entity: CatalogTableEntity): TableOwnership =
        TableOwnership(
            owner = entity.owner,
            team = entity.team,
            stewards = entity.getStewardsList(),
            consumers = entity.getConsumersList(),
        )

    /**
     * Convert CatalogTableEntity to TableFreshness
     */
    fun toTableFreshness(entity: CatalogTableEntity): TableFreshness =
        TableFreshness(
            lastUpdated = entity.lastUpdated!!,
            avgUpdateLagHours = entity.avgUpdateLagHours,
            updateFrequency = entity.updateFrequency,
            isStale = entity.isStale(),
            staleThresholdHours = entity.staleThresholdHours,
        )

    /**
     * Convert CatalogTableEntity to TableQuality
     */
    fun toTableQualityFromEntity(entity: CatalogTableEntity): TableQuality =
        TableQuality(
            score = entity.qualityScore ?: 0,
            totalTests = 0,
            passedTests = 0,
            failedTests = 0,
            warnings = 0,
            recentTests = emptyList(),
        )

    /**
     * Convert SampleQueryEntity to SampleQuery domain model
     */
    fun toSampleQuery(entity: SampleQueryEntity): SampleQuery =
        SampleQuery(
            title = entity.title,
            sql = entity.sql,
            author = entity.author,
            runCount = entity.runCount,
            lastRun = entity.lastRun,
        )
}
