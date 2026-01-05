package com.dataops.basecamp.domain.service

import com.dataops.basecamp.common.enums.ExecutionStatus
import com.dataops.basecamp.common.exception.AdHocExecutionException
import com.dataops.basecamp.common.exception.InvalidSqlException
import com.dataops.basecamp.common.exception.QueryExecutionTimeoutException
import com.dataops.basecamp.common.exception.RateLimitExceededException
import com.dataops.basecamp.common.exception.ResultNotFoundException
import com.dataops.basecamp.common.exception.ResultSizeLimitExceededException
import com.dataops.basecamp.common.util.QueryUtility
import com.dataops.basecamp.domain.entity.adhoc.AdHocExecutionEntity
import com.dataops.basecamp.domain.external.adhoc.RunExecutionConfig
import com.dataops.basecamp.domain.external.queryengine.QueryEngineClient
import com.dataops.basecamp.domain.external.queryengine.QueryExecutionResponse
import com.dataops.basecamp.domain.external.queryengine.QueryValidationResponse
import com.dataops.basecamp.domain.repository.adhoc.AdHocExecutionRepositoryJpa
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * AdHocExecutionService Unit Tests
 *
 * Tests for ad-hoc SQL execution, parameter substitution, and result handling.
 * Uses MockK to isolate dependencies and test pure business logic.
 */
@DisplayName("AdHocExecutionService Unit Tests")
class AdHocExecutionServiceTest {
    private val config: RunExecutionConfig = mockk()
    private val executionPolicyService: ExecutionPolicyService = mockk()
    private val queryEngineClient: QueryEngineClient = mockk()
    private val executionRepositoryJpa: AdHocExecutionRepositoryJpa = mockk()
    private val clock: Clock = Clock.fixed(Instant.parse("2026-01-01T10:00:00Z"), ZoneId.of("UTC"))
    private val queryUtility: QueryUtility = mockk()

    private lateinit var adHocExecutionService: AdHocExecutionService

    private val testUserId = "test-user@example.com"
    private val testSql = "SELECT * FROM users WHERE date = {date}"
    private val testEngine = "bigquery"
    private val testQueryId = "adhoc_20260101_100000_abc12345"

    @BeforeEach
    fun setUp() {
        // Setup default config values (per RUN_FEATURE.md spec)
        every { config.maxQueryDurationSeconds } returns 1800 // 30 min per spec
        every { config.maxResultRows } returns 10000
        every { config.maxResultSizeMb } returns 100
        every { config.resultExpirationHours } returns 8 // Per spec: 8 hours

        // Setup query utility to return consistent ID for tests
        every { queryUtility.generate() } returns testQueryId

        adHocExecutionService =
            AdHocExecutionService(
                config,
                executionPolicyService,
                queryEngineClient,
                executionRepositoryJpa,
                clock,
                queryUtility,
            )
    }

    @Nested
    @DisplayName("executeSQL - dry run")
    inner class ExecuteSQLDryRun {
        @Test
        @DisplayName("should return VALIDATED status for successful dry run")
        fun `should return VALIDATED status for successful dry run`() {
            // Given
            val validationResult =
                QueryValidationResponse(
                    valid = true,
                    errorMessage = null,
                    validationTimeSeconds = 0.5,
                )
            every { executionPolicyService.validateExecution(testUserId, testSql, testEngine) } just runs
            every { queryEngineClient.validateSQL(testSql, testEngine) } returns validationResult

            // When
            val result =
                adHocExecutionService.executeSQL(
                    userId = testUserId,
                    sql = testSql,
                    engine = testEngine,
                    parameters = emptyMap(),
                    downloadFormat = null,
                    dryRun = true,
                )

            // Then
            assertThat(result.status).isEqualTo(ExecutionStatus.VALIDATED)
            assertThat(result.queryId).isNull()
            assertThat(result.rowsReturned).isEqualTo(0)
            assertThat(result.rows).isEmpty()
            assertThat(result.renderedSql).isEqualTo(testSql)

            verify(exactly = 1) { executionPolicyService.validateExecution(testUserId, testSql, testEngine) }
            verify(exactly = 1) { queryEngineClient.validateSQL(testSql, testEngine) }
            verify(exactly = 0) { queryEngineClient.execute(any(), any(), any(), any()) }
            verify(exactly = 0) { executionRepositoryJpa.save(any()) }
        }

        @Test
        @DisplayName("should throw InvalidSqlException for invalid SQL in dry run")
        fun `should throw InvalidSqlException for invalid SQL in dry run`() {
            // Given
            val invalidSql = "SELECT * FORM users" // typo: FORM instead of FROM
            val validationResult =
                QueryValidationResponse(
                    valid = false,
                    errorMessage = "Syntax error at line 1:10",
                    validationTimeSeconds = 0.1,
                )
            every { executionPolicyService.validateExecution(testUserId, invalidSql, testEngine) } just runs
            every { queryEngineClient.validateSQL(invalidSql, testEngine) } returns validationResult

            // When & Then
            val exception =
                assertThrows<InvalidSqlException> {
                    adHocExecutionService.executeSQL(
                        userId = testUserId,
                        sql = invalidSql,
                        engine = testEngine,
                        parameters = emptyMap(),
                        downloadFormat = null,
                        dryRun = true,
                    )
                }

            assertThat(exception.sql).isEqualTo(invalidSql)
            assertThat(exception.sqlError).contains("Syntax error")
        }

        @Test
        @DisplayName("should substitute parameters in dry run")
        fun `should substitute parameters in dry run`() {
            // Given
            val parameters = mapOf("date" to "2026-01-01")
            val expectedRenderedSql = "SELECT * FROM users WHERE date = '2026-01-01'"
            val validationResult =
                QueryValidationResponse(
                    valid = true,
                    validationTimeSeconds = 0.2,
                )
            every { executionPolicyService.validateExecution(testUserId, testSql, testEngine) } just runs
            every { queryEngineClient.validateSQL(expectedRenderedSql, testEngine) } returns validationResult

            // When
            val result =
                adHocExecutionService.executeSQL(
                    userId = testUserId,
                    sql = testSql,
                    engine = testEngine,
                    parameters = parameters,
                    downloadFormat = null,
                    dryRun = true,
                )

            // Then
            assertThat(result.renderedSql).isEqualTo(expectedRenderedSql)
        }
    }

    @Nested
    @DisplayName("executeSQL - actual execution")
    inner class ExecuteSQLActual {
        @Test
        @DisplayName("should return COMPLETED status for successful execution")
        fun `should return COMPLETED status for successful execution`() {
            // Given
            val queryResult =
                QueryExecutionResponse(
                    rows =
                        listOf(
                            mapOf("id" to 1, "name" to "Alice"),
                            mapOf("id" to 2, "name" to "Bob"),
                        ),
                    bytesScanned = 1024L,
                    costUsd = BigDecimal("0.01"),
                    executionTimeSeconds = 1.5,
                )
            every { executionPolicyService.validateExecution(testUserId, testSql, testEngine) } just runs
            every { executionPolicyService.incrementUsage(testUserId) } just runs
            every {
                queryEngineClient.execute(
                    sql = testSql,
                    engine = testEngine,
                    timeoutSeconds = 1800, // Per spec: 30 min
                    maxRows = 10000,
                )
            } returns queryResult
            val savedEntitySlot = slot<AdHocExecutionEntity>()
            every { executionRepositoryJpa.save(capture(savedEntitySlot)) } answers { savedEntitySlot.captured }

            // When
            val result =
                adHocExecutionService.executeSQL(
                    userId = testUserId,
                    sql = testSql,
                    engine = testEngine,
                    parameters = emptyMap(),
                    downloadFormat = null,
                    dryRun = false,
                )

            // Then
            assertThat(result.status).isEqualTo(ExecutionStatus.COMPLETED)
            assertThat(result.queryId).isNotNull()
            assertThat(result.queryId).startsWith("adhoc_")
            assertThat(result.rowsReturned).isEqualTo(2)
            assertThat(result.rows).hasSize(2)
            assertThat(result.bytesScanned).isEqualTo(1024L)
            assertThat(result.costUsd).isEqualTo(BigDecimal("0.01"))
            assertThat(result.expiresAt).isNotNull()

            verify(exactly = 1) { executionPolicyService.validateExecution(testUserId, testSql, testEngine) }
            verify(exactly = 1) { executionPolicyService.incrementUsage(testUserId) }
            verify(exactly = 2) { executionRepositoryJpa.save(any()) } // Once for create, once for complete
        }

        @Test
        @DisplayName("should substitute parameters correctly")
        fun `should substitute parameters correctly`() {
            // Given
            val parameters =
                mapOf(
                    "date" to "2026-01-01",
                    "limit" to 100,
                    "active" to true,
                )
            val sqlWithParams = "SELECT * FROM users WHERE date = {date} AND active = {active} LIMIT {limit}"
            val expectedRenderedSql = "SELECT * FROM users WHERE date = '2026-01-01' AND active = true LIMIT 100"

            val queryResult =
                QueryExecutionResponse(
                    rows = listOf(mapOf("id" to 1)),
                    bytesScanned = 512L,
                    costUsd = null,
                    executionTimeSeconds = 0.8,
                )
            every { executionPolicyService.validateExecution(testUserId, sqlWithParams, testEngine) } just runs
            every { executionPolicyService.incrementUsage(testUserId) } just runs
            every {
                queryEngineClient.execute(
                    sql = expectedRenderedSql,
                    engine = testEngine,
                    timeoutSeconds = any(),
                    maxRows = any(),
                )
            } returns queryResult
            val savedEntitySlot = slot<AdHocExecutionEntity>()
            every { executionRepositoryJpa.save(capture(savedEntitySlot)) } answers { savedEntitySlot.captured }

            // When
            val result =
                adHocExecutionService.executeSQL(
                    userId = testUserId,
                    sql = sqlWithParams,
                    engine = testEngine,
                    parameters = parameters,
                    downloadFormat = null,
                    dryRun = false,
                )

            // Then
            assertThat(result.renderedSql).isEqualTo(expectedRenderedSql)
        }

        @Test
        @DisplayName("should escape string parameters to prevent SQL injection")
        fun `should escape string parameters to prevent SQL injection`() {
            // Given
            val parameters = mapOf("name" to "O'Brien")
            val sqlWithParams = "SELECT * FROM users WHERE name = {name}"
            val expectedRenderedSql = "SELECT * FROM users WHERE name = 'O''Brien'"

            val queryResult =
                QueryExecutionResponse(
                    rows = emptyList(),
                    bytesScanned = 256L,
                    costUsd = null,
                    executionTimeSeconds = 0.5,
                )
            every { executionPolicyService.validateExecution(testUserId, sqlWithParams, testEngine) } just runs
            every { executionPolicyService.incrementUsage(testUserId) } just runs
            every {
                queryEngineClient.execute(
                    sql = expectedRenderedSql,
                    engine = testEngine,
                    timeoutSeconds = any(),
                    maxRows = any(),
                )
            } returns queryResult
            every { executionRepositoryJpa.save(any()) } answers { firstArg() }

            // When
            val result =
                adHocExecutionService.executeSQL(
                    userId = testUserId,
                    sql = sqlWithParams,
                    engine = testEngine,
                    parameters = parameters,
                    downloadFormat = null,
                    dryRun = false,
                )

            // Then
            assertThat(result.renderedSql).isEqualTo(expectedRenderedSql)
        }

        @Test
        @DisplayName("should throw RateLimitExceededException when policy validation fails")
        fun `should throw RateLimitExceededException when policy validation fails`() {
            // Given (per RUN_FEATURE.md spec: 50 queries/hour)
            every {
                executionPolicyService.validateExecution(testUserId, testSql, testEngine)
            } throws
                RateLimitExceededException(
                    limitType = "queries_per_hour",
                    limit = 50, // Per spec: 50/hour
                    currentUsage = 50,
                    resetAt = LocalDateTime.now().plusMinutes(30),
                )

            // When & Then
            val exception =
                assertThrows<RateLimitExceededException> {
                    adHocExecutionService.executeSQL(
                        userId = testUserId,
                        sql = testSql,
                        engine = testEngine,
                        parameters = emptyMap(),
                        downloadFormat = null,
                        dryRun = false,
                    )
                }

            assertThat(exception.limitType).isEqualTo("queries_per_hour")
            verify(exactly = 0) { queryEngineClient.execute(any(), any(), any(), any()) }
            verify(exactly = 0) { executionRepositoryJpa.save(any()) }
        }

        @Test
        @DisplayName("should throw QueryExecutionTimeoutException and record failure")
        fun `should throw QueryExecutionTimeoutException and record failure`() {
            // Given
            every { executionPolicyService.validateExecution(testUserId, testSql, testEngine) } just runs
            every {
                queryEngineClient.execute(any(), any(), any(), any())
            } throws QueryExecutionTimeoutException("test-query-id", 300)
            val savedEntitySlot = slot<AdHocExecutionEntity>()
            every { executionRepositoryJpa.save(capture(savedEntitySlot)) } answers { savedEntitySlot.captured }

            // When & Then
            assertThrows<QueryExecutionTimeoutException> {
                adHocExecutionService.executeSQL(
                    userId = testUserId,
                    sql = testSql,
                    engine = testEngine,
                    parameters = emptyMap(),
                    downloadFormat = null,
                    dryRun = false,
                )
            }

            // Verify failure was recorded
            verify(atLeast = 1) { executionRepositoryJpa.save(any()) }
            assertThat(savedEntitySlot.captured.status).isEqualTo(ExecutionStatus.TIMEOUT)
        }

        @Test
        @DisplayName("should throw ResultSizeLimitExceededException when result too large")
        fun `should throw ResultSizeLimitExceededException when result too large`() {
            // Given
            // Create a result that would exceed 100MB
            val largeRows =
                (1..1000).map { i ->
                    mapOf(
                        "id" to i,
                        "data" to "x".repeat(200 * 1024), // 200KB per row = 200MB total
                    )
                }
            val queryResult =
                QueryExecutionResponse(
                    rows = largeRows,
                    bytesScanned = 1024L * 1024L * 200L,
                    costUsd = BigDecimal("1.00"),
                    executionTimeSeconds = 5.0,
                )
            every { executionPolicyService.validateExecution(testUserId, testSql, testEngine) } just runs
            every { queryEngineClient.execute(any(), any(), any(), any()) } returns queryResult
            every { executionRepositoryJpa.save(any()) } answers { firstArg() }

            // When & Then
            assertThrows<ResultSizeLimitExceededException> {
                adHocExecutionService.executeSQL(
                    userId = testUserId,
                    sql = testSql,
                    engine = testEngine,
                    parameters = emptyMap(),
                    downloadFormat = null,
                    dryRun = false,
                )
            }
        }

        @Test
        @DisplayName("should wrap generic exceptions in AdHocExecutionException")
        fun `should wrap generic exceptions in AdHocExecutionException`() {
            // Given
            every { executionPolicyService.validateExecution(testUserId, testSql, testEngine) } just runs
            every { queryEngineClient.execute(any(), any(), any(), any()) } throws RuntimeException("Network error")
            every { executionRepositoryJpa.save(any()) } answers { firstArg() }

            // When & Then
            val exception =
                assertThrows<AdHocExecutionException> {
                    adHocExecutionService.executeSQL(
                        userId = testUserId,
                        sql = testSql,
                        engine = testEngine,
                        parameters = emptyMap(),
                        downloadFormat = null,
                        dryRun = false,
                    )
                }

            assertThat(exception.queryId).startsWith("adhoc_")
            assertThat(exception.reason).contains("Network error")
        }

        @Test
        @DisplayName("should set download format when specified")
        fun `should set download format when specified`() {
            // Given
            val queryResult =
                QueryExecutionResponse(
                    rows = listOf(mapOf("id" to 1)),
                    bytesScanned = 256L,
                    costUsd = null,
                    executionTimeSeconds = 0.5,
                )
            every { executionPolicyService.validateExecution(any(), any(), any()) } just runs
            every { executionPolicyService.incrementUsage(any()) } just runs
            every { queryEngineClient.execute(any(), any(), any(), any()) } returns queryResult
            every { executionRepositoryJpa.save(any()) } answers { firstArg() }

            // When
            val result =
                adHocExecutionService.executeSQL(
                    userId = testUserId,
                    sql = testSql,
                    engine = testEngine,
                    parameters = emptyMap(),
                    downloadFormat = "csv",
                    dryRun = false,
                )

            // Then
            assertThat(result.downloadFormat).isEqualTo("csv")
        }
    }

    @Nested
    @DisplayName("getExecution")
    inner class GetExecution {
        @Test
        @DisplayName("should return execution entity when found")
        fun `should return execution entity when found`() {
            // Given
            val queryId = "adhoc_20260101_120000_abc12345"
            val entity =
                AdHocExecutionEntity(
                    id = "entity-1",
                    queryId = queryId,
                    userId = testUserId,
                    sqlQuery = testSql,
                    renderedSql = testSql,
                    engine = testEngine,
                    status = ExecutionStatus.COMPLETED,
                )
            every { executionRepositoryJpa.findByQueryId(queryId) } returns entity

            // When
            val result = adHocExecutionService.getExecution(queryId)

            // Then
            assertThat(result).isNotNull
            assertThat(result?.queryId).isEqualTo(queryId)
            assertThat(result?.status).isEqualTo(ExecutionStatus.COMPLETED)
        }

        @Test
        @DisplayName("should return null when not found")
        fun `should return null when not found`() {
            // Given
            val queryId = "nonexistent_query_id"
            every { executionRepositoryJpa.findByQueryId(queryId) } returns null

            // When
            val result = adHocExecutionService.getExecution(queryId)

            // Then
            assertThat(result).isNull()
        }
    }

    @Nested
    @DisplayName("getExecutionOrThrow")
    inner class GetExecutionOrThrow {
        @Test
        @DisplayName("should return execution entity when found")
        fun `should return execution entity when found`() {
            // Given
            val queryId = "adhoc_20260101_120000_abc12345"
            val entity =
                AdHocExecutionEntity(
                    id = "entity-1",
                    queryId = queryId,
                    userId = testUserId,
                    sqlQuery = testSql,
                    renderedSql = testSql,
                    engine = testEngine,
                    status = ExecutionStatus.COMPLETED,
                )
            every { executionRepositoryJpa.findByQueryId(queryId) } returns entity

            // When
            val result = adHocExecutionService.getExecutionOrThrow(queryId)

            // Then
            assertThat(result.queryId).isEqualTo(queryId)
        }

        @Test
        @DisplayName("should throw ResultNotFoundException when not found")
        fun `should throw ResultNotFoundException when not found`() {
            // Given
            val queryId = "nonexistent_query_id"
            every { executionRepositoryJpa.findByQueryId(queryId) } returns null

            // When & Then
            val exception =
                assertThrows<ResultNotFoundException> {
                    adHocExecutionService.getExecutionOrThrow(queryId)
                }

            assertThat(exception.queryId).isEqualTo(queryId)
        }
    }
}
