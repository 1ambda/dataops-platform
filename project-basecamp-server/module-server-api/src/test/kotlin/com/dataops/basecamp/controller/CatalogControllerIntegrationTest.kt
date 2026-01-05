package com.dataops.basecamp.controller

import com.dataops.basecamp.domain.entity.catalog.CatalogColumnEntity
import com.dataops.basecamp.domain.entity.catalog.CatalogTableEntity
import com.dataops.basecamp.domain.repository.catalog.CatalogColumnRepositoryJpa
import com.dataops.basecamp.domain.repository.catalog.CatalogTableRepositoryJpa
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/**
 * CatalogController Integration Tests
 *
 * PURPOSE:
 * Integration tests verify the FULL application stack end-to-end:
 * - HTTP Request -> Controller -> Service -> Repository -> Database -> Response
 *
 * KEY DIFFERENCES from CatalogControllerTest (Slice Test):
 *
 * | Aspect            | Slice Test (@WebMvcTest)    | Integration Test (@SpringBootTest)    |
 * |-------------------|-----------------------------|-----------------------------------------|
 * | Context           | Web layer only              | Full application context                |
 * | Dependencies      | Mocked (@MockkBean)         | Real beans wired together               |
 * | Database          | None (mocked)               | H2 in-memory (test profile)             |
 * | Speed             | Fast (~100ms)               | Slower (~2-5s startup)                  |
 * | Coverage          | Controller logic only       | Full integration of all layers          |
 * | Use case          | Unit testing web layer      | Verify end-to-end behavior              |
 *
 * WHEN TO USE:
 * - Slice tests: Test controller logic, validation, security (many tests, fast)
 * - Integration tests: Verify full flow works correctly (few tests, expensive)
 *
 * Note: This test uses @Transactional for automatic rollback after each test,
 * ensuring test isolation without manual cleanup.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Execution(ExecutionMode.SAME_THREAD)
@Transactional // Rollback after each test for isolation
@DisplayName("CatalogController Integration Tests (E2E)")
class CatalogControllerIntegrationTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var catalogTableRepositoryJpa: CatalogTableRepositoryJpa

    @Autowired
    private lateinit var catalogColumnRepositoryJpa: CatalogColumnRepositoryJpa

    private lateinit var testTable: CatalogTableEntity

    @BeforeEach
    fun setUp() {
        // Create test data in the actual database
        // Using companion factory method to ensure proper naming structure
        testTable =
            CatalogTableEntity(
                name = "test-project.analytics.users",
                project = "test-project",
                datasetName = "analytics",
                tableName = "users",
                engine = "bigquery",
                owner = "data-team@example.com",
                team = "@data-eng",
                description = "User dimension table for analytics",
                rowCount = 1000000L,
                lastUpdated = Instant.parse("2026-01-01T08:00:00Z"),
                basecampUrl = "https://basecamp.example.com/catalog/test-project.analytics.users",
                updateFrequency = "daily",
                staleThresholdHours = 24,
                avgUpdateLagHours = 2.0,
                qualityScore = 95,
                stewards = "alice@example.com,bob@example.com",
                consumers = "@analytics,@marketing",
                tags = setOf("tier::critical", "domain::analytics"),
            )

        // Save table first to get its ID (will be rolled back after test)
        testTable = catalogTableRepositoryJpa.save(testTable)

        // Create columns with FK reference to the saved table
        val columns =
            listOf(
                CatalogColumnEntity(
                    name = "user_id",
                    dataType = "STRING",
                    description = "Unique user identifier",
                    isPii = false,
                    fillRate = 1.0,
                    distinctCount = 1000000,
                    ordinalPosition = 0,
                    catalogTableId = testTable.id!!,
                ),
                CatalogColumnEntity(
                    name = "email",
                    dataType = "STRING",
                    description = "User email address",
                    isPii = true,
                    fillRate = 0.98,
                    distinctCount = 980000,
                    ordinalPosition = 1,
                    catalogTableId = testTable.id!!,
                ),
            )

        // Save columns to database
        columns.forEach { catalogColumnRepositoryJpa.save(it) }
    }

    @Nested
    @DisplayName("GET /api/v1/catalog/tables - E2E List Tables")
    inner class ListTablesE2E {
        @Test
        @DisplayName("should return tables from actual database")
        fun `should return tables from actual database`() {
            // When & Then: Full E2E test - no mocking
            mockMvc
                .get("/api/v1/catalog/tables") {
                    param("limit", "10")
                }.andExpect {
                    status { isOk() }
                    content { contentType(MediaType.APPLICATION_JSON) }
                    jsonPath("$") { isArray() }
                    jsonPath("$.length()") { value(1) }
                    jsonPath("$[0].name") { value("test-project.analytics.users") }
                    jsonPath("$[0].engine") { value("bigquery") }
                    jsonPath("$[0].owner") { value("data-team@example.com") }
                    jsonPath("$[0].team") { value("@data-eng") }
                    jsonPath("$[0].row_count") { value(1000000) }
                }
        }

        @Test
        @DisplayName("should filter tables by project parameter")
        fun `should filter tables by project parameter`() {
            mockMvc
                .get("/api/v1/catalog/tables") {
                    param("project", "test-project")
                }.andExpect {
                    status { isOk() }
                    jsonPath("$.length()") { value(1) }
                    jsonPath("$[0].name") { value("test-project.analytics.users") }
                }

            // Non-matching project should return empty
            mockMvc
                .get("/api/v1/catalog/tables") {
                    param("project", "other-project")
                }.andExpect {
                    status { isOk() }
                    jsonPath("$.length()") { value(0) }
                }
        }
    }

    @Nested
    @DisplayName("GET /api/v1/catalog/tables/{tableRef} - E2E Get Table Detail")
    inner class GetTableDetailE2E {
        @Test
        @DisplayName("should return full table detail with columns and metadata")
        fun `should return full table detail with columns and metadata`() {
            val tableRef = "test-project.analytics.users"

            mockMvc.get("/api/v1/catalog/tables/$tableRef").andExpect {
                status { isOk() }
                content { contentType(MediaType.APPLICATION_JSON) }
                // Basic info
                jsonPath("$.name") { value(tableRef) }
                jsonPath("$.engine") { value("bigquery") }
                jsonPath("$.owner") { value("data-team@example.com") }
                jsonPath("$.description") { value("User dimension table for analytics") }
                // Columns
                jsonPath("$.columns") { isArray() }
                jsonPath("$.columns.length()") { value(2) }
                jsonPath("$.columns[0].name") { value("user_id") }
                jsonPath("$.columns[0].is_pii") { value(false) }
                jsonPath("$.columns[1].name") { value("email") }
                jsonPath("$.columns[1].is_pii") { value(true) }
                // Ownership
                jsonPath("$.ownership.owner") { value("data-team@example.com") }
                jsonPath("$.ownership.team") { value("@data-eng") }
                jsonPath("$.ownership.stewards") { isArray() }
                // Freshness (is_stale depends on current time vs lastUpdated, so just check existence)
                jsonPath("$.freshness.update_frequency") { value("daily") }
                jsonPath("$.freshness.is_stale") { exists() }
                // Quality
                jsonPath("$.quality.score") { value(95) }
            }
        }

        // Note: Error cases (404, 400) are tested in CatalogControllerTest (slice test)
        // Integration tests focus on E2E happy path to verify full stack works together
    }

    @Nested
    @DisplayName("GET /api/v1/catalog/search - E2E Search Tables")
    inner class SearchTablesE2E {
        @Test
        @DisplayName("should search tables by keyword across name and columns")
        fun `should search tables by keyword across name and columns`() {
            mockMvc
                .get("/api/v1/catalog/search") {
                    param("keyword", "user")
                }.andExpect {
                    status { isOk() }
                    jsonPath("$.length()") { value(1) }
                    jsonPath("$[0].name") { value("test-project.analytics.users") }
                    jsonPath("$[0].match_context") { exists() }
                }
        }
    }
}
