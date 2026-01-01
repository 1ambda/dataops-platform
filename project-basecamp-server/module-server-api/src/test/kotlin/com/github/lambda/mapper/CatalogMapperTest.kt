package com.github.lambda.mapper

import com.github.lambda.domain.model.catalog.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Instant

/**
 * CatalogMapper Unit Tests
 *
 * Tests Domain Model -> Response DTO mapping functionality for Catalog API responses.
 * Note: Catalog models are read-only (no request DTOs needed).
 */
@DisplayName("CatalogMapper Unit Tests")
class CatalogMapperTest {
    private lateinit var mapper: CatalogMapper
    private lateinit var testTableInfo: TableInfo
    private lateinit var testTableDetail: TableDetail
    private lateinit var testColumns: List<ColumnInfo>
    private lateinit var testSampleQuery: SampleQuery

    @BeforeEach
    fun setUp() {
        mapper = CatalogMapper()

        testTableInfo =
            TableInfo(
                name = "my-project.analytics.users",
                engine = "bigquery",
                owner = "data-team@example.com",
                team = "@data-eng",
                tags = setOf("tier::critical", "domain::analytics", "pii"),
                rowCount = 1500000L,
                lastUpdated = Instant.parse("2026-01-01T08:00:00Z"),
                matchContext = "Table name match: users",
            )

        testColumns =
            listOf(
                ColumnInfo(
                    name = "user_id",
                    dataType = "STRING",
                    description = "Unique user identifier",
                    isPii = false,
                    fillRate = 1.0,
                    distinctCount = 1500000,
                ),
                ColumnInfo(
                    name = "email",
                    dataType = "STRING",
                    description = "User email address",
                    isPii = true,
                    fillRate = 0.98,
                    distinctCount = 1470000,
                ),
                ColumnInfo(
                    name = "created_at",
                    dataType = "TIMESTAMP",
                    description = "Account creation timestamp",
                    isPii = false,
                    fillRate = 1.0,
                    distinctCount = 1000000,
                ),
            )

        testTableDetail =
            TableDetail(
                name = "my-project.analytics.users",
                engine = "bigquery",
                owner = "data-team@example.com",
                team = "@data-eng",
                description = "User dimension table with profile information",
                tags = setOf("tier::critical", "domain::analytics", "pii"),
                rowCount = 1500000L,
                lastUpdated = Instant.parse("2026-01-01T08:00:00Z"),
                basecampUrl = "https://basecamp.example.com/catalog/my-project.analytics.users",
                columns = testColumns,
                ownership =
                    TableOwnership(
                        owner = "data-team@example.com",
                        team = "@data-eng",
                        stewards = listOf("alice@example.com", "bob@example.com"),
                        consumers = listOf("@analytics", "@marketing", "@product"),
                    ),
                freshness =
                    TableFreshness(
                        lastUpdated = Instant.parse("2026-01-01T08:00:00Z"),
                        avgUpdateLagHours = 1.5,
                        updateFrequency = "hourly",
                        isStale = false,
                        staleThresholdHours = 6,
                    ),
                quality =
                    TableQuality(
                        score = 92,
                        totalTests = 15,
                        passedTests = 14,
                        failedTests = 1,
                        warnings = 0,
                        recentTests =
                            listOf(
                                QualityTestResult(
                                    testName = "user_id_not_null",
                                    testType = "not_null",
                                    status = TestStatus.PASS,
                                    failedRows = 0,
                                ),
                            ),
                    ),
                sampleData = emptyList(),
            )

        testSampleQuery =
            SampleQuery(
                title = "Active users last 7 days",
                sql =
                    "SELECT COUNT(*) FROM my-project.analytics.users " +
                        "WHERE created_at > DATE_SUB(CURRENT_DATE(), INTERVAL 7 DAY)",
                author = "alice@example.com",
                runCount = 150,
                lastRun = Instant.parse("2026-01-01T09:00:00Z"),
            )
    }

    @Nested
    @DisplayName("TableInfo -> Response Mapping")
    inner class TableInfoToResponseMapping {
        @Test
        @DisplayName("should map TableInfo to Response with all fields")
        fun `should map TableInfo to Response with all fields`() {
            // When
            val response = mapper.toTableInfoResponse(testTableInfo)

            // Then
            assertThat(response.name).isEqualTo("my-project.analytics.users")
            assertThat(response.engine).isEqualTo("bigquery")
            assertThat(response.owner).isEqualTo("data-team@example.com")
            assertThat(response.team).isEqualTo("@data-eng")
            assertThat(response.tags).containsExactly("domain::analytics", "pii", "tier::critical") // sorted
            assertThat(response.rowCount).isEqualTo(1500000L)
            assertThat(response.lastUpdated).isEqualTo(Instant.parse("2026-01-01T08:00:00Z"))
            assertThat(response.matchContext).isEqualTo("Table name match: users")
        }

        @Test
        @DisplayName("should map TableInfo with minimal fields")
        fun `should map TableInfo with minimal fields`() {
            // Given
            val minimalTableInfo =
                TableInfo(
                    name = "project.dataset.table",
                    engine = "bigquery",
                    owner = "owner@example.com",
                    team = null,
                    tags = emptySet(),
                    rowCount = null,
                    lastUpdated = null,
                    matchContext = null,
                )

            // When
            val response = mapper.toTableInfoResponse(minimalTableInfo)

            // Then
            assertThat(response.name).isEqualTo("project.dataset.table")
            assertThat(response.engine).isEqualTo("bigquery")
            assertThat(response.owner).isEqualTo("owner@example.com")
            assertThat(response.team).isNull()
            assertThat(response.tags).isEmpty()
            assertThat(response.rowCount).isNull()
            assertThat(response.lastUpdated).isNull()
            assertThat(response.matchContext).isNull()
        }

        @Test
        @DisplayName("should map list of TableInfo to Responses")
        fun `should map list of TableInfo to Responses`() {
            // Given
            val tableList =
                listOf(
                    testTableInfo,
                    testTableInfo.copy(name = "my-project.analytics.events"),
                )

            // When
            val responses = mapper.toTableInfoResponseList(tableList)

            // Then
            assertThat(responses).hasSize(2)
            assertThat(responses[0].name).isEqualTo("my-project.analytics.users")
            assertThat(responses[1].name).isEqualTo("my-project.analytics.events")
        }

        @Test
        @DisplayName("should handle empty list")
        fun `should handle empty list`() {
            // When
            val responses = mapper.toTableInfoResponseList(emptyList())

            // Then
            assertThat(responses).isEmpty()
        }
    }

    @Nested
    @DisplayName("TableDetail -> Response Mapping")
    inner class TableDetailToResponseMapping {
        @Test
        @DisplayName("should map TableDetail to Response with all fields")
        fun `should map TableDetail to Response with all fields`() {
            // When
            val response = mapper.toTableDetailResponse(testTableDetail)

            // Then
            assertThat(response.name).isEqualTo("my-project.analytics.users")
            assertThat(response.engine).isEqualTo("bigquery")
            assertThat(response.owner).isEqualTo("data-team@example.com")
            assertThat(response.team).isEqualTo("@data-eng")
            assertThat(response.description).isEqualTo("User dimension table with profile information")
            assertThat(response.tags).containsExactly("domain::analytics", "pii", "tier::critical") // sorted
            assertThat(response.rowCount).isEqualTo(1500000L)
            assertThat(response.lastUpdated).isEqualTo(Instant.parse("2026-01-01T08:00:00Z"))
            assertThat(
                response.basecampUrl,
            ).isEqualTo("https://basecamp.example.com/catalog/my-project.analytics.users")
        }

        @Test
        @DisplayName("should map TableDetail columns correctly")
        fun `should map TableDetail columns correctly`() {
            // When
            val response = mapper.toTableDetailResponse(testTableDetail)

            // Then
            assertThat(response.columns).hasSize(3)
            assertThat(response.columns[0].name).isEqualTo("user_id")
            assertThat(response.columns[0].dataType).isEqualTo("STRING")
            assertThat(response.columns[0].description).isEqualTo("Unique user identifier")
            assertThat(response.columns[0].isPii).isFalse()
            assertThat(response.columns[0].fillRate).isEqualTo(1.0)
            assertThat(response.columns[0].distinctCount).isEqualTo(1500000)

            assertThat(response.columns[1].name).isEqualTo("email")
            assertThat(response.columns[1].isPii).isTrue()
        }

        @Test
        @DisplayName("should map TableDetail ownership correctly")
        fun `should map TableDetail ownership correctly`() {
            // When
            val response = mapper.toTableDetailResponse(testTableDetail)

            // Then
            assertThat(response.ownership).isNotNull
            assertThat(response.ownership?.owner).isEqualTo("data-team@example.com")
            assertThat(response.ownership?.team).isEqualTo("@data-eng")
            assertThat(response.ownership?.stewards).containsExactly("alice@example.com", "bob@example.com")
            assertThat(response.ownership?.consumers).containsExactly("@analytics", "@marketing", "@product")
        }

        @Test
        @DisplayName("should map TableDetail freshness correctly")
        fun `should map TableDetail freshness correctly`() {
            // When
            val response = mapper.toTableDetailResponse(testTableDetail)

            // Then
            assertThat(response.freshness).isNotNull
            assertThat(response.freshness?.lastUpdated).isEqualTo(Instant.parse("2026-01-01T08:00:00Z"))
            assertThat(response.freshness?.avgUpdateLagHours).isEqualTo(1.5)
            assertThat(response.freshness?.updateFrequency).isEqualTo("hourly")
            assertThat(response.freshness?.isStale).isFalse()
            assertThat(response.freshness?.staleThresholdHours).isEqualTo(6)
        }

        @Test
        @DisplayName("should map TableDetail quality correctly")
        fun `should map TableDetail quality correctly`() {
            // When
            val response = mapper.toTableDetailResponse(testTableDetail)

            // Then
            assertThat(response.quality).isNotNull
            assertThat(response.quality?.score).isEqualTo(92)
            assertThat(response.quality?.totalTests).isEqualTo(15)
            assertThat(response.quality?.passedTests).isEqualTo(14)
            assertThat(response.quality?.failedTests).isEqualTo(1)
            assertThat(response.quality?.warnings).isEqualTo(0)
            assertThat(response.quality?.recentTests).hasSize(1)
            assertThat(
                response.quality
                    ?.recentTests
                    ?.get(0)
                    ?.testName,
            ).isEqualTo("user_id_not_null")
            assertThat(
                response.quality
                    ?.recentTests
                    ?.get(0)
                    ?.status,
            ).isEqualTo("pass")
        }

        @Test
        @DisplayName("should map TableDetail with minimal fields")
        fun `should map TableDetail with minimal fields`() {
            // Given
            val minimalTableDetail =
                TableDetail(
                    name = "project.dataset.table",
                    engine = "bigquery",
                    owner = "owner@example.com",
                    team = null,
                    description = null,
                    tags = emptySet(),
                    columns = emptyList(),
                    ownership = null,
                    freshness = null,
                    quality = null,
                    sampleData = emptyList(),
                )

            // When
            val response = mapper.toTableDetailResponse(minimalTableDetail)

            // Then
            assertThat(response.name).isEqualTo("project.dataset.table")
            assertThat(response.team).isNull()
            assertThat(response.description).isNull()
            assertThat(response.tags).isEmpty()
            assertThat(response.columns).isEmpty()
            assertThat(response.ownership).isNull()
            assertThat(response.freshness).isNull()
            assertThat(response.quality).isNull()
            assertThat(response.sampleData).isNull()
        }

        @Test
        @DisplayName("should map sample data when present")
        fun `should map sample data when present`() {
            // Given
            val tableDetailWithSample =
                testTableDetail.copy(
                    sampleData =
                        listOf(
                            mapOf("user_id" to "user_001", "email" to "***", "country" to "US"),
                            mapOf("user_id" to "user_002", "email" to "***", "country" to "UK"),
                        ),
                )

            // When
            val response = mapper.toTableDetailResponse(tableDetailWithSample)

            // Then
            assertThat(response.sampleData).hasSize(2)
            assertThat(response.sampleData?.get(0)?.get("user_id")).isEqualTo("user_001")
            assertThat(response.sampleData?.get(0)?.get("email")).isEqualTo("***")
            assertThat(response.sampleData?.get(0)?.get("country")).isEqualTo("US")
        }
    }

    @Nested
    @DisplayName("ColumnInfo -> Response Mapping")
    inner class ColumnInfoToResponseMapping {
        @Test
        @DisplayName("should map ColumnInfo to Response with all fields")
        fun `should map ColumnInfo to Response with all fields`() {
            // Given
            val columnInfo =
                ColumnInfo(
                    name = "email",
                    dataType = "STRING",
                    description = "User email address",
                    isPii = true,
                    fillRate = 0.98,
                    distinctCount = 1470000,
                )

            // When
            val response = mapper.toColumnInfoResponse(columnInfo)

            // Then
            assertThat(response.name).isEqualTo("email")
            assertThat(response.dataType).isEqualTo("STRING")
            assertThat(response.description).isEqualTo("User email address")
            assertThat(response.isPii).isTrue()
            assertThat(response.fillRate).isEqualTo(0.98)
            assertThat(response.distinctCount).isEqualTo(1470000)
        }

        @Test
        @DisplayName("should map ColumnInfo with minimal fields")
        fun `should map ColumnInfo with minimal fields`() {
            // Given
            val minimalColumnInfo =
                ColumnInfo(
                    name = "column_name",
                    dataType = "INT64",
                )

            // When
            val response = mapper.toColumnInfoResponse(minimalColumnInfo)

            // Then
            assertThat(response.name).isEqualTo("column_name")
            assertThat(response.dataType).isEqualTo("INT64")
            assertThat(response.description).isNull()
            assertThat(response.isPii).isFalse()
            assertThat(response.fillRate).isNull()
            assertThat(response.distinctCount).isNull()
        }
    }

    @Nested
    @DisplayName("SampleQuery -> Response Mapping")
    inner class SampleQueryToResponseMapping {
        @Test
        @DisplayName("should map SampleQuery to Response with all fields")
        fun `should map SampleQuery to Response with all fields`() {
            // When
            val response = mapper.toSampleQueryResponse(testSampleQuery)

            // Then
            assertThat(response.title).isEqualTo("Active users last 7 days")
            assertThat(response.sql).contains("SELECT COUNT(*)")
            assertThat(response.author).isEqualTo("alice@example.com")
            assertThat(response.runCount).isEqualTo(150)
            assertThat(response.lastRun).isEqualTo(Instant.parse("2026-01-01T09:00:00Z"))
        }

        @Test
        @DisplayName("should map SampleQuery with null lastRun")
        fun `should map SampleQuery with null lastRun`() {
            // Given
            val queryWithoutLastRun =
                SampleQuery(
                    title = "New Query",
                    sql = "SELECT 1",
                    author = "test@example.com",
                    runCount = 0,
                    lastRun = null,
                )

            // When
            val response = mapper.toSampleQueryResponse(queryWithoutLastRun)

            // Then
            assertThat(response.title).isEqualTo("New Query")
            assertThat(response.runCount).isEqualTo(0)
            assertThat(response.lastRun).isNull()
        }

        @Test
        @DisplayName("should map list of SampleQuery to Responses")
        fun `should map list of SampleQuery to Responses`() {
            // Given
            val queryList =
                listOf(
                    testSampleQuery,
                    testSampleQuery.copy(title = "Another query", runCount = 50),
                )

            // When
            val responses = mapper.toSampleQueryResponseList(queryList)

            // Then
            assertThat(responses).hasSize(2)
            assertThat(responses[0].title).isEqualTo("Active users last 7 days")
            assertThat(responses[0].runCount).isEqualTo(150)
            assertThat(responses[1].title).isEqualTo("Another query")
            assertThat(responses[1].runCount).isEqualTo(50)
        }
    }

    @Nested
    @DisplayName("TestStatus -> String Mapping")
    inner class TestStatusMapping {
        @Test
        @DisplayName("should map TestStatus.PASS to lowercase string")
        fun `should map TestStatus PASS to lowercase string`() {
            // Given
            val testResult =
                QualityTestResult(
                    testName = "test",
                    testType = "not_null",
                    status = TestStatus.PASS,
                    failedRows = 0,
                )

            // When
            val response = mapper.toQualityTestResultResponse(testResult)

            // Then
            assertThat(response.status).isEqualTo("pass")
        }

        @Test
        @DisplayName("should map TestStatus.FAIL to lowercase string")
        fun `should map TestStatus FAIL to lowercase string`() {
            // Given
            val testResult =
                QualityTestResult(
                    testName = "test",
                    testType = "not_null",
                    status = TestStatus.FAIL,
                    failedRows = 100,
                )

            // When
            val response = mapper.toQualityTestResultResponse(testResult)

            // Then
            assertThat(response.status).isEqualTo("fail")
            assertThat(response.failedRows).isEqualTo(100)
        }

        @Test
        @DisplayName("should map TestStatus.WARNING to lowercase string")
        fun `should map TestStatus WARNING to lowercase string`() {
            // Given
            val testResult =
                QualityTestResult(
                    testName = "test",
                    testType = "freshness",
                    status = TestStatus.WARNING,
                    failedRows = 0,
                )

            // When
            val response = mapper.toQualityTestResultResponse(testResult)

            // Then
            assertThat(response.status).isEqualTo("warning")
        }

        @Test
        @DisplayName("should map TestStatus.SKIPPED to lowercase string")
        fun `should map TestStatus SKIPPED to lowercase string`() {
            // Given
            val testResult =
                QualityTestResult(
                    testName = "test",
                    testType = "custom",
                    status = TestStatus.SKIPPED,
                    failedRows = 0,
                )

            // When
            val response = mapper.toQualityTestResultResponse(testResult)

            // Then
            assertThat(response.status).isEqualTo("skipped")
        }
    }

    @Nested
    @DisplayName("Ownership Mapping")
    inner class OwnershipMapping {
        @Test
        @DisplayName("should map TableOwnership correctly")
        fun `should map TableOwnership correctly`() {
            // Given
            val ownership =
                TableOwnership(
                    owner = "data-team@example.com",
                    team = "@data-eng",
                    stewards = listOf("alice@example.com", "bob@example.com"),
                    consumers = listOf("@analytics", "@marketing"),
                )

            // When
            val response = mapper.toTableOwnershipResponse(ownership)

            // Then
            assertThat(response.owner).isEqualTo("data-team@example.com")
            assertThat(response.team).isEqualTo("@data-eng")
            assertThat(response.stewards).containsExactly("alice@example.com", "bob@example.com")
            assertThat(response.consumers).containsExactly("@analytics", "@marketing")
        }
    }

    @Nested
    @DisplayName("Freshness Mapping")
    inner class FreshnessMapping {
        @Test
        @DisplayName("should map TableFreshness correctly")
        fun `should map TableFreshness correctly`() {
            // Given
            val freshness =
                TableFreshness(
                    lastUpdated = Instant.parse("2026-01-01T08:00:00Z"),
                    avgUpdateLagHours = 1.5,
                    updateFrequency = "hourly",
                    isStale = false,
                    staleThresholdHours = 6,
                )

            // When
            val response = mapper.toTableFreshnessResponse(freshness)

            // Then
            assertThat(response.lastUpdated).isEqualTo(Instant.parse("2026-01-01T08:00:00Z"))
            assertThat(response.avgUpdateLagHours).isEqualTo(1.5)
            assertThat(response.updateFrequency).isEqualTo("hourly")
            assertThat(response.isStale).isFalse()
            assertThat(response.staleThresholdHours).isEqualTo(6)
        }
    }

    @Nested
    @DisplayName("Quality Mapping")
    inner class QualityMapping {
        @Test
        @DisplayName("should map TableQuality correctly")
        fun `should map TableQuality correctly`() {
            // Given
            val quality =
                TableQuality(
                    score = 92,
                    totalTests = 15,
                    passedTests = 14,
                    failedTests = 1,
                    warnings = 0,
                    recentTests =
                        listOf(
                            QualityTestResult(
                                testName = "test1",
                                testType = "not_null",
                                status = TestStatus.PASS,
                                failedRows = 0,
                            ),
                        ),
                )

            // When
            val response = mapper.toTableQualityResponse(quality)

            // Then
            assertThat(response.score).isEqualTo(92)
            assertThat(response.totalTests).isEqualTo(15)
            assertThat(response.passedTests).isEqualTo(14)
            assertThat(response.failedTests).isEqualTo(1)
            assertThat(response.warnings).isEqualTo(0)
            assertThat(response.recentTests).hasSize(1)
        }
    }
}
