package com.dataops.basecamp.domain.service

import com.dataops.basecamp.common.enums.TestStatus
import com.dataops.basecamp.common.exception.TableNotFoundException
import com.dataops.basecamp.domain.projection.catalog.*
import com.dataops.basecamp.domain.repository.catalog.CatalogRepositoryDsl
import com.dataops.basecamp.domain.repository.catalog.CatalogRepositoryJpa
import com.dataops.basecamp.domain.repository.catalog.SampleQueryRepositoryDsl
import io.mockk.*
import org.assertj.core.api.Assertions.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant

/**
 * CatalogService Unit Tests
 *
 * Uses MockK to isolate dependencies and test pure business logic.
 * Note: Cache Service is excluded from this test scope.
 */
@DisplayName("CatalogService Unit Tests")
class CatalogServiceTest {
    private lateinit var catalogRepositoryJpa: CatalogRepositoryJpa
    private lateinit var catalogRepositoryDsl: CatalogRepositoryDsl
    private lateinit var sampleQueryRepository: SampleQueryRepositoryDsl
    private lateinit var catalogService: CatalogService

    private lateinit var testTableInfo: TableInfo
    private lateinit var testTableDetail: TableDetail
    private lateinit var testColumns: List<ColumnInfo>
    private lateinit var testSampleQueries: List<SampleQuery>

    @BeforeEach
    fun setUp() {
        // Create fresh mocks for each test to avoid parallel test interference
        catalogRepositoryJpa = mockk()
        catalogRepositoryDsl = mockk()
        sampleQueryRepository = mockk()
        catalogService =
            CatalogService(
                catalogRepositoryJpa = catalogRepositoryJpa,
                catalogRepositoryDsl = catalogRepositoryDsl,
                sampleQueryRepository = sampleQueryRepository,
            )

        testTableInfo =
            TableInfo(
                name = "my-project.analytics.users",
                engine = "bigquery",
                owner = "data-team@example.com",
                team = "@data-eng",
                tags = setOf("tier::critical", "domain::analytics"),
                rowCount = 1500000L,
                lastUpdated = Instant.parse("2026-01-01T08:00:00Z"),
                matchContext = null,
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
                tags = setOf("tier::critical", "domain::analytics"),
                rowCount = 1500000L,
                lastUpdated = Instant.parse("2026-01-01T08:00:00Z"),
                basecampUrl = "https://basecamp.example.com/catalog/my-project.analytics.users",
                columns = testColumns,
                ownership =
                    TableOwnership(
                        owner = "data-team@example.com",
                        team = "@data-eng",
                        stewards = listOf("alice@example.com", "bob@example.com"),
                        consumers = listOf("@analytics", "@marketing"),
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
                                    status = TestStatus.PASSED,
                                    failedRows = 0,
                                ),
                            ),
                    ),
                sampleData = emptyList(),
            )

        testSampleQueries =
            listOf(
                SampleQuery(
                    title = "Active users last 7 days",
                    sql =
                        "SELECT COUNT(*) FROM my-project.analytics.users " +
                            "WHERE created_at > DATE_SUB(CURRENT_DATE(), INTERVAL 7 DAY)",
                    author = "alice@example.com",
                    runCount = 150,
                    lastRun = Instant.parse("2026-01-01T09:00:00Z"),
                ),
                SampleQuery(
                    title = "User count by country",
                    sql = "SELECT country, COUNT(*) as cnt FROM my-project.analytics.users GROUP BY 1 ORDER BY 2 DESC",
                    author = "bob@example.com",
                    runCount = 89,
                    lastRun = Instant.parse("2026-01-01T08:00:00Z"),
                ),
            )
    }

    @Nested
    @DisplayName("listTables")
    inner class ListTables {
        @Test
        @DisplayName("should return all tables without filters")
        fun `should return all tables without filters`() {
            // Given
            val expectedTables = listOf(testTableInfo)
            every { catalogRepositoryDsl.listTables(any()) } returns expectedTables

            // When
            val result = catalogService.listTables(limit = 50)

            // Then
            assertThat(result).hasSize(1)
            assertThat(result[0].name).isEqualTo("my-project.analytics.users")
            verify(exactly = 1) { catalogRepositoryDsl.listTables(any()) }
        }

        @Test
        @DisplayName("should return filtered tables by project")
        fun `should return filtered tables by project`() {
            // Given
            val expectedTables = listOf(testTableInfo)
            every { catalogRepositoryDsl.listTables(match { it.project == "my-project" }) } returns expectedTables

            // When
            val result = catalogService.listTables(project = "my-project", limit = 50)

            // Then
            assertThat(result).hasSize(1)
            assertThat(result[0].name).startsWith("my-project.")
            verify(exactly = 1) { catalogRepositoryDsl.listTables(match { it.project == "my-project" }) }
        }

        @Test
        @DisplayName("should return filtered tables by tag")
        fun `should return filtered tables by tag`() {
            // Given
            val expectedTables = listOf(testTableInfo)
            every {
                catalogRepositoryDsl.listTables(
                    match { it.tags.contains("tier::critical") },
                )
            } returns expectedTables

            // When
            val result = catalogService.listTables(tags = setOf("tier::critical"), limit = 50)

            // Then
            assertThat(result).hasSize(1)
            assertThat(result[0].tags).contains("tier::critical")
            verify(exactly = 1) {
                catalogRepositoryDsl.listTables(
                    match { it.tags.contains("tier::critical") },
                )
            }
        }

        @Test
        @DisplayName("should return empty list when no tables match filters")
        fun `should return empty list when no tables match filters`() {
            // Given
            every { catalogRepositoryDsl.listTables(match { it.project == "nonexistent-project" }) } returns emptyList()

            // When
            val result = catalogService.listTables(project = "nonexistent-project", limit = 50)

            // Then
            assertThat(result).isEmpty()
        }

        @Test
        @DisplayName("should filter by owner")
        fun `should filter by owner`() {
            // Given
            val expectedTables = listOf(testTableInfo)
            every {
                catalogRepositoryDsl.listTables(
                    match { it.owner == "data-team@example.com" },
                )
            } returns expectedTables

            // When
            val result = catalogService.listTables(owner = "data-team@example.com", limit = 50)

            // Then
            assertThat(result).hasSize(1)
            assertThat(result[0].owner).isEqualTo("data-team@example.com")
        }

        @Test
        @DisplayName("should filter by team")
        fun `should filter by team`() {
            // Given
            val expectedTables = listOf(testTableInfo)
            every { catalogRepositoryDsl.listTables(match { it.team == "@data-eng" }) } returns expectedTables

            // When
            val result = catalogService.listTables(team = "@data-eng", limit = 50)

            // Then
            assertThat(result).hasSize(1)
            assertThat(result[0].team).isEqualTo("@data-eng")
        }

        @Test
        @DisplayName("should apply pagination with limit and offset")
        fun `should apply pagination with limit and offset`() {
            // Given
            every { catalogRepositoryDsl.listTables(match { it.limit == 10 && it.offset == 5 }) } returns emptyList()

            // When
            catalogService.listTables(limit = 10, offset = 5)

            // Then
            verify(exactly = 1) { catalogRepositoryDsl.listTables(match { it.limit == 10 && it.offset == 5 }) }
        }
    }

    @Nested
    @DisplayName("searchTables")
    inner class SearchTables {
        @Test
        @DisplayName("should return matching results for keyword search")
        fun `should return matching results for keyword search`() {
            // Given
            val keyword = "user"
            val expectedTables =
                listOf(
                    testTableInfo.copy(matchContext = "Table name match: users"),
                )
            every { catalogRepositoryDsl.searchTables(keyword, null, 20) } returns expectedTables

            // When
            val result = catalogService.searchTables(keyword, null, 20)

            // Then
            assertThat(result).hasSize(1)
            assertThat(result[0].matchContext).isNotNull()
            verify(exactly = 1) { catalogRepositoryDsl.searchTables(keyword, null, 20) }
        }

        @Test
        @DisplayName("should filter search by project")
        fun `should filter search by project`() {
            // Given
            val keyword = "user"
            val project = "my-project"
            val expectedTables = listOf(testTableInfo.copy(matchContext = "Column: user_id"))
            every { catalogRepositoryDsl.searchTables(keyword, project, 20) } returns expectedTables

            // When
            val result = catalogService.searchTables(keyword, project, 20)

            // Then
            assertThat(result).hasSize(1)
            verify(exactly = 1) { catalogRepositoryDsl.searchTables(keyword, project, 20) }
        }

        @Test
        @DisplayName("should return empty list for no matches")
        fun `should return empty list for no matches`() {
            // Given
            val keyword = "nonexistent"
            every { catalogRepositoryDsl.searchTables(keyword, null, 20) } returns emptyList()

            // When
            val result = catalogService.searchTables(keyword, null, 20)

            // Then
            assertThat(result).isEmpty()
        }

        @Test
        @DisplayName("should respect limit parameter")
        fun `should respect limit parameter`() {
            // Given
            val keyword = "user"
            val limit = 5
            every { catalogRepositoryDsl.searchTables(keyword, null, limit) } returns emptyList()

            // When
            catalogService.searchTables(keyword, null, limit)

            // Then
            verify(exactly = 1) { catalogRepositoryDsl.searchTables(keyword, null, limit) }
        }
    }

    @Nested
    @DisplayName("getTableDetail")
    inner class GetTableDetail {
        @Test
        @DisplayName("should return table detail for existing table")
        fun `should return table detail for existing table`() {
            // Given
            val tableRef = "my-project.analytics.users"
            every { catalogRepositoryJpa.getTableDetail(tableRef) } returns testTableDetail

            // When
            val result = catalogService.getTableDetail(tableRef)

            // Then
            assertThat(result.name).isEqualTo(tableRef)
            assertThat(result.columns).hasSize(3)
            assertThat(result.owner).isEqualTo("data-team@example.com")
            verify(exactly = 1) { catalogRepositoryJpa.getTableDetail(tableRef) }
        }

        @Test
        @DisplayName("should throw TableNotFoundException when table not found")
        fun `should throw TableNotFoundException when table not found`() {
            // Given
            val tableRef = "project.dataset.nonexistent"
            every { catalogRepositoryJpa.getTableDetail(tableRef) } returns null

            // When & Then
            val exception =
                assertThrows<TableNotFoundException> {
                    catalogService.getTableDetail(tableRef)
                }

            assertThat(exception.message).contains(tableRef)
        }

        @Test
        @DisplayName("should return table detail with ownership information")
        fun `should return table detail with ownership information`() {
            // Given
            val tableRef = "my-project.analytics.users"
            every { catalogRepositoryJpa.getTableDetail(tableRef) } returns testTableDetail

            // When
            val result = catalogService.getTableDetail(tableRef)

            // Then
            assertThat(result.ownership).isNotNull
            assertThat(result.ownership?.owner).isEqualTo("data-team@example.com")
            assertThat(result.ownership?.team).isEqualTo("@data-eng")
            assertThat(result.ownership?.stewards).contains("alice@example.com", "bob@example.com")
        }

        @Test
        @DisplayName("should return table detail with freshness information")
        fun `should return table detail with freshness information`() {
            // Given
            val tableRef = "my-project.analytics.users"
            every { catalogRepositoryJpa.getTableDetail(tableRef) } returns testTableDetail

            // When
            val result = catalogService.getTableDetail(tableRef)

            // Then
            assertThat(result.freshness).isNotNull
            assertThat(result.freshness?.updateFrequency).isEqualTo("hourly")
            assertThat(result.freshness?.isStale).isFalse()
            assertThat(result.freshness?.staleThresholdHours).isEqualTo(6)
        }

        @Test
        @DisplayName("should return table detail with quality information")
        fun `should return table detail with quality information`() {
            // Given
            val tableRef = "my-project.analytics.users"
            every { catalogRepositoryJpa.getTableDetail(tableRef) } returns testTableDetail

            // When
            val result = catalogService.getTableDetail(tableRef)

            // Then
            assertThat(result.quality).isNotNull
            assertThat(result.quality?.score).isEqualTo(92)
            assertThat(result.quality?.totalTests).isEqualTo(15)
            assertThat(result.quality?.passedTests).isEqualTo(14)
            assertThat(result.quality?.failedTests).isEqualTo(1)
        }
    }

    @Nested
    @DisplayName("getSampleQueries")
    inner class GetSampleQueries {
        @Test
        @DisplayName("should return sample queries for existing table")
        fun `should return sample queries for existing table`() {
            // Given
            val tableRef = "my-project.analytics.users"
            every { catalogRepositoryJpa.getTableDetail(tableRef) } returns testTableDetail
            every { sampleQueryRepository.findByTableRef(tableRef, 10) } returns testSampleQueries

            // When
            val result = catalogService.getSampleQueries(tableRef, 10)

            // Then
            assertThat(result).hasSize(2)
            assertThat(result[0].title).isEqualTo("Active users last 7 days")
            assertThat(result[0].runCount).isEqualTo(150)
            verify(exactly = 1) { sampleQueryRepository.findByTableRef(tableRef, 10) }
        }

        @Test
        @DisplayName("should return empty list when no sample queries exist")
        fun `should return empty list when no sample queries exist`() {
            // Given
            val tableRef = "my-project.analytics.users"
            every { catalogRepositoryJpa.getTableDetail(tableRef) } returns testTableDetail
            every { sampleQueryRepository.findByTableRef(tableRef, 10) } returns emptyList()

            // When
            val result = catalogService.getSampleQueries(tableRef, 10)

            // Then
            assertThat(result).isEmpty()
        }

        @Test
        @DisplayName("should throw TableNotFoundException when table not found")
        fun `should throw TableNotFoundException when table not found`() {
            // Given
            val tableRef = "project.dataset.nonexistent"
            every { catalogRepositoryJpa.getTableDetail(tableRef) } returns null

            // When & Then
            val exception =
                assertThrows<TableNotFoundException> {
                    catalogService.getSampleQueries(tableRef, 10)
                }

            assertThat(exception.message).contains(tableRef)
        }

        @Test
        @DisplayName("should respect limit parameter")
        fun `should respect limit parameter`() {
            // Given
            val tableRef = "my-project.analytics.users"
            val limit = 5
            every { catalogRepositoryJpa.getTableDetail(tableRef) } returns testTableDetail
            every { sampleQueryRepository.findByTableRef(tableRef, limit) } returns testSampleQueries.take(1)

            // When
            val result = catalogService.getSampleQueries(tableRef, limit)

            // Then
            verify(exactly = 1) { sampleQueryRepository.findByTableRef(tableRef, limit) }
        }
    }

    @Nested
    @DisplayName("Error Handling")
    inner class ErrorHandling {
        @Test
        @DisplayName("should propagate repository exceptions")
        fun `should propagate repository exceptions`() {
            // Given
            val tableRef = "my-project.analytics.users"
            every { catalogRepositoryJpa.getTableDetail(tableRef) } throws RuntimeException("Connection timeout")

            // When & Then
            assertThrows<RuntimeException> {
                catalogService.getTableDetail(tableRef)
            }
        }
    }
}
