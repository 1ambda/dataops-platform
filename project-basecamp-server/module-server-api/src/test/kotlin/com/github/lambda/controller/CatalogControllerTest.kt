package com.github.lambda.controller

import com.github.lambda.common.exception.TableNotFoundException
import com.github.lambda.config.SecurityConfig
import com.github.lambda.domain.model.catalog.ColumnInfo
import com.github.lambda.domain.model.catalog.QualityTestResult
import com.github.lambda.domain.model.catalog.SampleQuery
import com.github.lambda.domain.model.catalog.TableDetail
import com.github.lambda.domain.model.catalog.TableFreshness
import com.github.lambda.domain.model.catalog.TableInfo
import com.github.lambda.domain.model.catalog.TableOwnership
import com.github.lambda.domain.model.catalog.TableQuality
import com.github.lambda.domain.model.catalog.TestStatus
import com.github.lambda.domain.service.CatalogService
import com.github.lambda.dto.catalog.ColumnInfoResponse
import com.github.lambda.dto.catalog.QualityTestResultResponse
import com.github.lambda.dto.catalog.SampleQueryResponse
import com.github.lambda.dto.catalog.TableDetailResponse
import com.github.lambda.dto.catalog.TableFreshnessResponse
import com.github.lambda.dto.catalog.TableInfoResponse
import com.github.lambda.dto.catalog.TableOwnershipResponse
import com.github.lambda.dto.catalog.TableQualityResponse
import com.github.lambda.exception.GlobalExceptionHandler
import com.github.lambda.mapper.CatalogMapper
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.validation.beanvalidation.MethodValidationPostProcessor
import java.time.Instant

/**
 * CatalogController REST API Tests
 *
 * Spring Boot 4.x patterns:
 * - @WebMvcTest: Slice test for web layer only (faster than full integration test)
 * - @MockkBean: springmockk 5.0.1 (Spring Boot 4 compatible)
 * - @Import: Include SecurityConfig and GlobalExceptionHandler for proper security and exception handling
 */
@WebMvcTest(CatalogController::class)
@Import(
    SecurityConfig::class,
    GlobalExceptionHandler::class,
    CatalogControllerTest.ValidationConfig::class,
)
@ActiveProfiles("test")
@Execution(ExecutionMode.SAME_THREAD)
class CatalogControllerTest {
    /**
     * Test configuration to enable method-level validation for @Min, @Max, @Size annotations
     * on controller method parameters. Required for @WebMvcTest since it doesn't auto-configure this.
     */
    @TestConfiguration
    class ValidationConfig {
        @Bean
        fun methodValidationPostProcessor(): MethodValidationPostProcessor = MethodValidationPostProcessor()
    }

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockkBean(relaxed = true)
    private lateinit var catalogService: CatalogService

    @MockkBean(relaxed = true)
    private lateinit var catalogMapper: CatalogMapper

    // Query-related mock beans needed by CatalogController
    @MockkBean(relaxed = true)
    private lateinit var queryMetadataService: com.github.lambda.domain.service.QueryMetadataService

    @MockkBean(relaxed = true)
    private lateinit var queryMapper: com.github.lambda.mapper.QueryMapper

    // Lineage-related mock beans to fix test context loading
    @MockkBean(relaxed = true)
    private lateinit var lineageService: com.github.lambda.domain.service.LineageService

    @MockkBean(relaxed = true)
    private lateinit var lineageNodeRepositoryJpa: com.github.lambda.domain.repository.LineageNodeRepositoryJpa

    @MockkBean(relaxed = true)
    private lateinit var lineageEdgeRepositoryDsl: com.github.lambda.domain.repository.LineageEdgeRepositoryDsl

    @MockkBean(relaxed = true)
    private lateinit var lineageMapper: com.github.lambda.mapper.LineageMapper

    @MockkBean(relaxed = true)
    private lateinit var basecampParserClient: com.github.lambda.domain.external.BasecampParserClient

    private lateinit var testTableInfo: TableInfo
    private lateinit var testTableDetail: TableDetail
    private lateinit var testSampleQueries: List<SampleQuery>

    // Response DTOs
    private lateinit var testTableInfoResponse: TableInfoResponse
    private lateinit var testTableDetailResponse: TableDetailResponse
    private lateinit var testSampleQueryResponses: List<SampleQueryResponse>

    @BeforeEach
    fun setUp() {
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

        val testColumns =
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
                                    status = TestStatus.PASS,
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

        // Setup response DTOs
        testTableInfoResponse =
            TableInfoResponse(
                name = "my-project.analytics.users",
                engine = "bigquery",
                owner = "data-team@example.com",
                team = "@data-eng",
                tags = listOf("domain::analytics", "tier::critical"),
                rowCount = 1500000L,
                lastUpdated = Instant.parse("2026-01-01T08:00:00Z"),
                matchContext = null,
            )

        testTableDetailResponse =
            TableDetailResponse(
                name = "my-project.analytics.users",
                engine = "bigquery",
                owner = "data-team@example.com",
                team = "@data-eng",
                description = "User dimension table with profile information",
                tags = listOf("domain::analytics", "tier::critical"),
                rowCount = 1500000L,
                lastUpdated = Instant.parse("2026-01-01T08:00:00Z"),
                basecampUrl = "https://basecamp.example.com/catalog/my-project.analytics.users",
                columns =
                    listOf(
                        ColumnInfoResponse(
                            name = "user_id",
                            dataType = "STRING",
                            description = "Unique user identifier",
                            isPii = false,
                            fillRate = 1.0,
                            distinctCount = 1500000,
                        ),
                        ColumnInfoResponse(
                            name = "email",
                            dataType = "STRING",
                            description = "User email address",
                            isPii = true,
                            fillRate = 0.98,
                            distinctCount = 1470000,
                        ),
                        ColumnInfoResponse(
                            name = "created_at",
                            dataType = "TIMESTAMP",
                            description = "Account creation timestamp",
                            isPii = false,
                            fillRate = 1.0,
                            distinctCount = 1000000,
                        ),
                    ),
                ownership =
                    TableOwnershipResponse(
                        owner = "data-team@example.com",
                        team = "@data-eng",
                        stewards = listOf("alice@example.com", "bob@example.com"),
                        consumers = listOf("@analytics", "@marketing"),
                    ),
                freshness =
                    TableFreshnessResponse(
                        lastUpdated = Instant.parse("2026-01-01T08:00:00Z"),
                        avgUpdateLagHours = 1.5,
                        updateFrequency = "hourly",
                        isStale = false,
                        staleThresholdHours = 6,
                    ),
                quality =
                    TableQualityResponse(
                        score = 92,
                        totalTests = 15,
                        passedTests = 14,
                        failedTests = 1,
                        warnings = 0,
                        recentTests =
                            listOf(
                                QualityTestResultResponse(
                                    testName = "user_id_not_null",
                                    testType = "not_null",
                                    status = "pass",
                                    failedRows = 0,
                                ),
                            ),
                    ),
                sampleData = null,
            )

        testSampleQueryResponses =
            listOf(
                SampleQueryResponse(
                    title = "Active users last 7 days",
                    sql =
                        "SELECT COUNT(*) FROM my-project.analytics.users " +
                            "WHERE created_at > DATE_SUB(CURRENT_DATE(), INTERVAL 7 DAY)",
                    author = "alice@example.com",
                    runCount = 150,
                    lastRun = Instant.parse("2026-01-01T09:00:00Z"),
                ),
                SampleQueryResponse(
                    title = "User count by country",
                    sql = "SELECT country, COUNT(*) as cnt FROM my-project.analytics.users GROUP BY 1 ORDER BY 2 DESC",
                    author = "bob@example.com",
                    runCount = 89,
                    lastRun = Instant.parse("2026-01-01T08:00:00Z"),
                ),
            )
    }

    @Nested
    @DisplayName("GET /api/v1/catalog/tables")
    inner class ListTables {
        @Test
        @DisplayName("should return empty list when no tables exist")
        fun `should return empty list when no tables exist`() {
            // Given
            every { catalogService.listTables(any()) } returns emptyList()
            every { catalogMapper.toTableInfoResponseList(emptyList()) } returns emptyList()

            // When & Then
            mockMvc
                .perform(get("/api/v1/catalog/tables"))
                .andExpect(status().isOk)
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0))

            verify(exactly = 1) { catalogService.listTables(any()) }
        }

        @Test
        @DisplayName("should return tables list")
        fun `should return tables list`() {
            // Given
            val tables = listOf(testTableInfo)
            every { catalogService.listTables(any()) } returns tables
            every { catalogMapper.toTableInfoResponseList(tables) } returns listOf(testTableInfoResponse)

            // When & Then
            mockMvc
                .perform(get("/api/v1/catalog/tables"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("my-project.analytics.users"))
                .andExpect(jsonPath("$[0].engine").value("bigquery"))
                .andExpect(jsonPath("$[0].owner").value("data-team@example.com"))

            verify(exactly = 1) { catalogService.listTables(any()) }
        }

        @Test
        @DisplayName("should filter tables by project")
        fun `should filter tables by project`() {
            // Given
            val project = "my-project"
            val tables = listOf(testTableInfo)
            every {
                catalogService.listTables(
                    project = project,
                    dataset = any(),
                    owner = any(),
                    team = any(),
                    tags = any(),
                    limit = any(),
                    offset = any(),
                )
            } returns tables
            every { catalogMapper.toTableInfoResponseList(tables) } returns listOf(testTableInfoResponse)

            // When & Then
            mockMvc
                .perform(
                    get("/api/v1/catalog/tables")
                        .param("project", project),
                ).andExpect(status().isOk)
                .andExpect(jsonPath("$.length()").value(1))

            verify(exactly = 1) {
                catalogService.listTables(
                    project = project,
                    dataset = any(),
                    owner = any(),
                    team = any(),
                    tags = any(),
                    limit = any(),
                    offset = any(),
                )
            }
        }

        @Test
        @DisplayName("should filter tables by owner")
        fun `should filter tables by owner`() {
            // Given
            val owner = "data-team@example.com"
            val tables = listOf(testTableInfo)
            every {
                catalogService.listTables(
                    project = any(),
                    dataset = any(),
                    owner = owner,
                    team = any(),
                    tags = any(),
                    limit = any(),
                    offset = any(),
                )
            } returns tables
            every { catalogMapper.toTableInfoResponseList(tables) } returns listOf(testTableInfoResponse)

            // When & Then
            mockMvc
                .perform(
                    get("/api/v1/catalog/tables")
                        .param("owner", owner),
                ).andExpect(status().isOk)
                .andExpect(jsonPath("$.length()").value(1))

            verify(exactly = 1) {
                catalogService.listTables(
                    project = any(),
                    dataset = any(),
                    owner = owner,
                    team = any(),
                    tags = any(),
                    limit = any(),
                    offset = any(),
                )
            }
        }

        @Test
        @DisplayName("should filter tables by team")
        fun `should filter tables by team`() {
            // Given
            val team = "@data-eng"
            val tables = listOf(testTableInfo)
            every {
                catalogService.listTables(
                    project = any(),
                    dataset = any(),
                    owner = any(),
                    team = team,
                    tags = any(),
                    limit = any(),
                    offset = any(),
                )
            } returns tables
            every { catalogMapper.toTableInfoResponseList(tables) } returns listOf(testTableInfoResponse)

            // When & Then
            mockMvc
                .perform(
                    get("/api/v1/catalog/tables")
                        .param("team", team),
                ).andExpect(status().isOk)
                .andExpect(jsonPath("$.length()").value(1))

            verify(exactly = 1) {
                catalogService.listTables(
                    project = any(),
                    dataset = any(),
                    owner = any(),
                    team = team,
                    tags = any(),
                    limit = any(),
                    offset = any(),
                )
            }
        }

        @Test
        @DisplayName("should filter tables by tags")
        fun `should filter tables by tags`() {
            // Given
            val tables = listOf(testTableInfo)
            every {
                catalogService.listTables(
                    project = any(),
                    dataset = any(),
                    owner = any(),
                    team = any(),
                    tags = any(),
                    limit = any(),
                    offset = any(),
                )
            } returns tables
            every { catalogMapper.toTableInfoResponseList(tables) } returns listOf(testTableInfoResponse)

            // When & Then
            mockMvc
                .perform(
                    get("/api/v1/catalog/tables")
                        .param("tags", "tier::critical,pii"),
                ).andExpect(status().isOk)
                .andExpect(jsonPath("$.length()").value(1))

            verify(exactly = 1) {
                catalogService.listTables(
                    project = any(),
                    dataset = any(),
                    owner = any(),
                    team = any(),
                    tags = any(),
                    limit = any(),
                    offset = any(),
                )
            }
        }

        @Test
        @DisplayName("should apply limit and offset")
        fun `should apply limit and offset`() {
            // Given
            val limit = 10
            val offset = 5
            every {
                catalogService.listTables(
                    project = any(),
                    dataset = any(),
                    owner = any(),
                    team = any(),
                    tags = any(),
                    limit = limit,
                    offset = offset,
                )
            } returns emptyList()
            every { catalogMapper.toTableInfoResponseList(emptyList()) } returns emptyList()

            // When & Then
            mockMvc
                .perform(
                    get("/api/v1/catalog/tables")
                        .param("limit", limit.toString())
                        .param("offset", offset.toString()),
                ).andExpect(status().isOk)

            verify(exactly = 1) {
                catalogService.listTables(
                    project = any(),
                    dataset = any(),
                    owner = any(),
                    team = any(),
                    tags = any(),
                    limit = limit,
                    offset = offset,
                )
            }
        }

        @Test
        @DisplayName("should reject request when limit exceeds maximum")
        fun `should reject request when limit exceeds maximum`() {
            // When limit exceeds 500, validation should reject the request
            // Note: Returns 400 if ConstraintViolationException handler is configured,
            // otherwise may return 500 or call service with coerced value
            mockMvc
                .perform(
                    get("/api/v1/catalog/tables")
                        .param("limit", "501"),
                ).andExpect(status().is4xxClientError)
        }

        @Test
        @DisplayName("should reject request when offset is negative")
        fun `should reject request when offset is negative`() {
            // When offset is negative, validation should reject the request
            // Note: Returns 400 if ConstraintViolationException handler is configured
            mockMvc
                .perform(
                    get("/api/v1/catalog/tables")
                        .param("offset", "-1"),
                ).andExpect(status().is4xxClientError)
        }
    }

    @Nested
    @DisplayName("GET /api/v1/catalog/search")
    inner class SearchTables {
        @Test
        @DisplayName("should return matching results for keyword search")
        fun `should return matching results for keyword search`() {
            // Given
            val keyword = "user"
            val results = listOf(testTableInfo.copy(matchContext = "Table name match: users"))
            val responseWithMatch = testTableInfoResponse.copy(matchContext = "Table name match: users")
            every { catalogService.searchTables(keyword, null, 20) } returns results
            every { catalogMapper.toTableInfoResponseList(results) } returns listOf(responseWithMatch)

            // When & Then
            mockMvc
                .perform(
                    get("/api/v1/catalog/search")
                        .param("keyword", keyword),
                ).andExpect(status().isOk)
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].match_context").exists())

            verify(exactly = 1) { catalogService.searchTables(keyword, null, 20) }
        }

        @Test
        @DisplayName("should filter search by project")
        fun `should filter search by project`() {
            // Given
            val keyword = "user"
            val project = "my-project"
            val results = listOf(testTableInfo.copy(matchContext = "Column: user_id"))
            val responseWithMatch = testTableInfoResponse.copy(matchContext = "Column: user_id")
            every { catalogService.searchTables(keyword, project, 20) } returns results
            every { catalogMapper.toTableInfoResponseList(results) } returns listOf(responseWithMatch)

            // When & Then
            mockMvc
                .perform(
                    get("/api/v1/catalog/search")
                        .param("keyword", keyword)
                        .param("project", project),
                ).andExpect(status().isOk)
                .andExpect(jsonPath("$.length()").value(1))

            verify(exactly = 1) { catalogService.searchTables(keyword, project, 20) }
        }

        @Test
        @DisplayName("should respect limit parameter")
        fun `should respect limit parameter`() {
            // Given
            val keyword = "user"
            val limit = 5
            every { catalogService.searchTables(keyword, null, limit) } returns emptyList()
            every { catalogMapper.toTableInfoResponseList(emptyList()) } returns emptyList()

            // When & Then
            mockMvc
                .perform(
                    get("/api/v1/catalog/search")
                        .param("keyword", keyword)
                        .param("limit", limit.toString()),
                ).andExpect(status().isOk)

            verify(exactly = 1) { catalogService.searchTables(keyword, null, limit) }
        }

        @Test
        @DisplayName("should return 400 when keyword is missing")
        fun `should return 400 when keyword is missing`() {
            // When & Then
            mockMvc
                .perform(get("/api/v1/catalog/search"))
                .andExpect(status().isBadRequest)
        }

        @Test
        @DisplayName("should return 400 when keyword is too short")
        fun `should return 400 when keyword is too short`() {
            // When keyword is too short, validation should reject the request
            // Note: Returns 400 if ConstraintViolationException handler is configured
            mockMvc
                .perform(
                    get("/api/v1/catalog/search")
                        .param("keyword", "a"), // Min 2 characters
                ).andExpect(status().is4xxClientError)
        }

        @Test
        @DisplayName("should return empty list for no matches")
        fun `should return empty list for no matches`() {
            // Given
            val keyword = "nonexistent"
            every { catalogService.searchTables(keyword, null, 20) } returns emptyList()
            every { catalogMapper.toTableInfoResponseList(emptyList()) } returns emptyList()

            // When & Then
            mockMvc
                .perform(
                    get("/api/v1/catalog/search")
                        .param("keyword", keyword),
                ).andExpect(status().isOk)
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0))
        }
    }

    @Nested
    @DisplayName("GET /api/v1/catalog/tables/{tableRef}")
    inner class GetTableDetail {
        @Test
        @DisplayName("should return table detail for existing table")
        fun `should return table detail for existing table`() {
            // Given
            val tableRef = "my-project.analytics.users"
            every { catalogService.getTableDetail(tableRef) } returns testTableDetail
            every { catalogMapper.toTableDetailResponse(testTableDetail) } returns testTableDetailResponse

            // When & Then
            mockMvc
                .perform(get("/api/v1/catalog/tables/$tableRef"))
                .andExpect(status().isOk)
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.name").value(tableRef))
                .andExpect(jsonPath("$.engine").value("bigquery"))
                .andExpect(jsonPath("$.owner").value("data-team@example.com"))
                .andExpect(jsonPath("$.columns").isArray())
                .andExpect(jsonPath("$.columns.length()").value(3))
                .andExpect(jsonPath("$.ownership.owner").value("data-team@example.com"))
                .andExpect(jsonPath("$.freshness.update_frequency").value("hourly"))
                .andExpect(jsonPath("$.quality.score").value(92))

            verify(exactly = 1) { catalogService.getTableDetail(tableRef) }
        }

        @Test
        @DisplayName("should return 404 when table not found")
        fun `should return 404 when table not found`() {
            // Given
            val tableRef = "project.dataset.nonexistent"
            every { catalogService.getTableDetail(tableRef, any()) } throws TableNotFoundException(tableRef)

            // When & Then
            mockMvc
                .perform(get("/api/v1/catalog/tables/$tableRef"))
                .andExpect(status().isNotFound)

            verify(exactly = 1) { catalogService.getTableDetail(tableRef, any()) }
        }

        @Test
        @DisplayName("should return table detail with ownership information")
        fun `should return table detail with ownership information`() {
            // Given
            val tableRef = "my-project.analytics.users"
            every { catalogService.getTableDetail(tableRef) } returns testTableDetail
            every { catalogMapper.toTableDetailResponse(testTableDetail) } returns testTableDetailResponse

            // When & Then
            mockMvc
                .perform(get("/api/v1/catalog/tables/$tableRef"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.ownership").exists())
                .andExpect(jsonPath("$.ownership.owner").value("data-team@example.com"))
                .andExpect(jsonPath("$.ownership.team").value("@data-eng"))
                .andExpect(jsonPath("$.ownership.stewards").isArray())
                .andExpect(jsonPath("$.ownership.stewards.length()").value(2))
        }

        @Test
        @DisplayName("should return table detail with freshness information")
        fun `should return table detail with freshness information`() {
            // Given
            val tableRef = "my-project.analytics.users"
            every { catalogService.getTableDetail(tableRef) } returns testTableDetail
            every { catalogMapper.toTableDetailResponse(testTableDetail) } returns testTableDetailResponse

            // When & Then
            mockMvc
                .perform(get("/api/v1/catalog/tables/$tableRef"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.freshness").exists())
                .andExpect(jsonPath("$.freshness.update_frequency").value("hourly"))
                .andExpect(jsonPath("$.freshness.is_stale").value(false))
                .andExpect(jsonPath("$.freshness.stale_threshold_hours").value(6))
        }

        @Test
        @DisplayName("should return table detail with quality information")
        fun `should return table detail with quality information`() {
            // Given
            val tableRef = "my-project.analytics.users"
            every { catalogService.getTableDetail(tableRef) } returns testTableDetail
            every { catalogMapper.toTableDetailResponse(testTableDetail) } returns testTableDetailResponse

            // When & Then
            mockMvc
                .perform(get("/api/v1/catalog/tables/$tableRef"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.quality").exists())
                .andExpect(jsonPath("$.quality.score").value(92))
                .andExpect(jsonPath("$.quality.total_tests").value(15))
                .andExpect(jsonPath("$.quality.passed_tests").value(14))
                .andExpect(jsonPath("$.quality.failed_tests").value(1))
        }

        @Test
        @DisplayName("should return table detail with columns")
        fun `should return table detail with columns`() {
            // Given
            val tableRef = "my-project.analytics.users"
            every { catalogService.getTableDetail(tableRef) } returns testTableDetail
            every { catalogMapper.toTableDetailResponse(testTableDetail) } returns testTableDetailResponse

            // When & Then
            mockMvc
                .perform(get("/api/v1/catalog/tables/$tableRef"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.columns").isArray())
                .andExpect(jsonPath("$.columns.length()").value(3))
                .andExpect(jsonPath("$.columns[0].name").value("user_id"))
                .andExpect(jsonPath("$.columns[0].data_type").value("STRING"))
                .andExpect(jsonPath("$.columns[0].is_pii").value(false))
                .andExpect(jsonPath("$.columns[1].name").value("email"))
                .andExpect(jsonPath("$.columns[1].is_pii").value(true))
        }
    }

    @Nested
    @DisplayName("GET /api/v1/catalog/tables/{tableRef}/queries")
    inner class GetSampleQueries {
        @Test
        @DisplayName("should return sample queries for existing table")
        fun `should return sample queries for existing table`() {
            // Given
            val tableRef = "my-project.analytics.users"
            every { catalogService.getSampleQueries(tableRef, 5) } returns testSampleQueries
            every { catalogMapper.toSampleQueryResponseList(testSampleQueries) } returns testSampleQueryResponses

            // When & Then
            mockMvc
                .perform(get("/api/v1/catalog/tables/$tableRef/queries"))
                .andExpect(status().isOk)
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].title").value("Active users last 7 days"))
                .andExpect(jsonPath("$[0].run_count").value(150))
                .andExpect(jsonPath("$[0].author").value("alice@example.com"))

            verify(exactly = 1) { catalogService.getSampleQueries(tableRef, 5) }
        }

        @Test
        @DisplayName("should return empty list when no sample queries exist")
        fun `should return empty list when no sample queries exist`() {
            // Given
            val tableRef = "my-project.analytics.users"
            every { catalogService.getSampleQueries(tableRef, 5) } returns emptyList()
            every { catalogMapper.toSampleQueryResponseList(emptyList()) } returns emptyList()

            // When & Then
            mockMvc
                .perform(get("/api/v1/catalog/tables/$tableRef/queries"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0))
        }

        @Test
        @DisplayName("should return 404 when table not found")
        fun `should return 404 when table not found`() {
            // Given
            val tableRef = "project.dataset.nonexistent"
            every { catalogService.getSampleQueries(tableRef, 5) } throws TableNotFoundException(tableRef)

            // When & Then
            mockMvc
                .perform(get("/api/v1/catalog/tables/$tableRef/queries"))
                .andExpect(status().isNotFound)

            verify(exactly = 1) { catalogService.getSampleQueries(tableRef, 5) }
        }

        @Test
        @DisplayName("should respect limit parameter")
        fun `should respect limit parameter`() {
            // Given
            val tableRef = "my-project.analytics.users"
            val limit = 10
            every { catalogService.getSampleQueries(tableRef, limit) } returns testSampleQueries
            every { catalogMapper.toSampleQueryResponseList(testSampleQueries) } returns testSampleQueryResponses

            // When & Then
            mockMvc
                .perform(
                    get("/api/v1/catalog/tables/$tableRef/queries")
                        .param("limit", limit.toString()),
                ).andExpect(status().isOk)

            verify(exactly = 1) { catalogService.getSampleQueries(tableRef, limit) }
        }

        @Test
        @DisplayName("should return 400 when limit exceeds maximum")
        fun `should return 400 when limit exceeds maximum`() {
            // Given
            val tableRef = "my-project.analytics.users"

            // When limit exceeds maximum (20), validation should reject the request
            // Note: Returns 400 if ConstraintViolationException handler is configured
            mockMvc
                .perform(
                    get("/api/v1/catalog/tables/$tableRef/queries")
                        .param("limit", "21"), // Max is 20
                ).andExpect(status().is4xxClientError)
        }
    }
}
