package com.github.lambda.domain.service

import com.github.lambda.common.exception.QueryEngineNotSupportedException
import com.github.lambda.common.exception.QueryTooLargeException
import com.github.lambda.common.exception.RateLimitExceededException
import com.github.lambda.domain.model.adhoc.RunExecutionConfig
import com.github.lambda.domain.model.adhoc.UserExecutionQuotaEntity
import com.github.lambda.domain.repository.UserExecutionQuotaRepositoryJpa
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * ExecutionPolicyService Unit Tests
 *
 * Tests for rate limiting, policy validation, and quota management.
 * Uses MockK to isolate dependencies and test pure business logic.
 */
@DisplayName("ExecutionPolicyService Unit Tests")
class ExecutionPolicyServiceTest {
    private val quotaRepositoryJpa: UserExecutionQuotaRepositoryJpa = mockk()
    private val config: RunExecutionConfig = mockk()
    private val clock: Clock = Clock.fixed(Instant.parse("2026-01-01T10:00:00Z"), ZoneId.of("UTC"))

    private lateinit var executionPolicyService: ExecutionPolicyService

    private val testUserId = "test-user@example.com"

    @BeforeEach
    fun setUp() {
        // Setup default config values (per RUN_FEATURE.md spec)
        every { config.maxQueryDurationSeconds } returns 1800 // 30 min per spec
        every { config.maxResultRows } returns 10000
        every { config.maxResultSizeMb } returns 100
        every { config.allowedEngines } returns listOf("bigquery", "trino")
        every { config.allowedFileTypes } returns listOf("csv")
        every { config.maxFileSizeMb } returns 10
        every { config.queriesPerHour } returns 50 // Per spec: 50/hour
        every { config.queriesPerDay } returns 200 // Per spec: 200/day
        every { config.resultExpirationHours } returns 8 // Per spec: 8 hours

        executionPolicyService = ExecutionPolicyService(config, quotaRepositoryJpa, clock)
    }

    // Helper to get LocalDateTime from clock
    private fun now(): LocalDateTime = LocalDateTime.now(clock)

    // Helper to get LocalDate from clock
    private fun today(): LocalDate = LocalDate.now(clock)

    @Nested
    @DisplayName("getPolicy")
    inner class GetPolicy {
        @Test
        @DisplayName("should return policy with current usage for existing user")
        fun `should return policy with current usage for existing user`() {
            // Given
            val existingQuota =
                UserExecutionQuotaEntity(
                    id = "quota-1",
                    userId = testUserId,
                    queriesToday = 10,
                    queriesThisHour = 3,
                    lastQueryDate = today(),
                    lastQueryHour = now().hour,
                )
            every { quotaRepositoryJpa.findByUserId(testUserId) } returns existingQuota

            // When
            val result = executionPolicyService.getPolicy(testUserId)

            // Then (per RUN_FEATURE.md spec)
            assertThat(result.maxQueryDurationSeconds).isEqualTo(1800)
            assertThat(result.maxResultRows).isEqualTo(10000)
            assertThat(result.maxResultSizeMb).isEqualTo(100)
            assertThat(result.allowedEngines).containsExactly("bigquery", "trino")
            assertThat(result.allowedFileTypes).containsExactly("csv")
            assertThat(result.maxFileSizeMb).isEqualTo(10)
            assertThat(result.rateLimits.queriesPerHour).isEqualTo(50)
            assertThat(result.rateLimits.queriesPerDay).isEqualTo(200)
            assertThat(result.currentUsage.queriesToday).isEqualTo(10)
            assertThat(result.currentUsage.queriesThisHour).isEqualTo(3)

            verify(exactly = 1) { quotaRepositoryJpa.findByUserId(testUserId) }
        }

        @Test
        @DisplayName("should create new quota for new user")
        fun `should create new quota for new user`() {
            // Given
            every { quotaRepositoryJpa.findByUserId(testUserId) } returns null
            val savedQuotaSlot = slot<UserExecutionQuotaEntity>()
            every { quotaRepositoryJpa.save(capture(savedQuotaSlot)) } answers { savedQuotaSlot.captured }

            // When
            val result = executionPolicyService.getPolicy(testUserId)

            // Then
            assertThat(result.currentUsage.queriesToday).isEqualTo(0)
            assertThat(result.currentUsage.queriesThisHour).isEqualTo(0)
            assertThat(savedQuotaSlot.captured.userId).isEqualTo(testUserId)

            verify(exactly = 1) { quotaRepositoryJpa.findByUserId(testUserId) }
            verify(exactly = 1) { quotaRepositoryJpa.save(any()) }
        }

        @Test
        @DisplayName("should reset daily counter when day changes")
        fun `should reset daily counter when day changes`() {
            // Given
            val oldQuota =
                UserExecutionQuotaEntity(
                    id = "quota-1",
                    userId = testUserId,
                    queriesToday = 50,
                    queriesThisHour = 5,
                    lastQueryDate = today().minusDays(1), // Yesterday
                    lastQueryHour = now().hour,
                )
            every { quotaRepositoryJpa.findByUserId(testUserId) } returns oldQuota

            // When
            val result = executionPolicyService.getPolicy(testUserId)

            // Then
            assertThat(result.currentUsage.queriesToday).isEqualTo(0) // Reset
            assertThat(result.currentUsage.queriesThisHour).isEqualTo(5) // Same hour
        }

        @Test
        @DisplayName("should reset hourly counter when hour changes")
        fun `should reset hourly counter when hour changes`() {
            // Given
            val currentHour = now().hour
            val previousHour = if (currentHour == 0) 23 else currentHour - 1
            val oldQuota =
                UserExecutionQuotaEntity(
                    id = "quota-1",
                    userId = testUserId,
                    queriesToday = 10,
                    queriesThisHour = 15,
                    lastQueryDate = today(),
                    lastQueryHour = previousHour, // Previous hour
                )
            every { quotaRepositoryJpa.findByUserId(testUserId) } returns oldQuota

            // When
            val result = executionPolicyService.getPolicy(testUserId)

            // Then
            assertThat(result.currentUsage.queriesToday).isEqualTo(10) // Same day
            assertThat(result.currentUsage.queriesThisHour).isEqualTo(0) // Reset
        }
    }

    @Nested
    @DisplayName("validateExecution")
    inner class ValidateExecution {
        @Test
        @DisplayName("should pass validation with valid parameters")
        fun `should pass validation with valid parameters`() {
            // Given
            val quota =
                UserExecutionQuotaEntity(
                    id = "quota-1",
                    userId = testUserId,
                    queriesToday = 5,
                    queriesThisHour = 2,
                    lastQueryDate = today(),
                    lastQueryHour = now().hour,
                )
            every { quotaRepositoryJpa.findByUserId(testUserId) } returns quota
            every { quotaRepositoryJpa.save(any()) } answers { firstArg() }

            // When & Then - no exception
            executionPolicyService.validateExecution(
                userId = testUserId,
                sql = "SELECT * FROM users",
                engine = "bigquery",
            )

            verify(exactly = 1) { quotaRepositoryJpa.save(any()) }
        }

        @Test
        @DisplayName("should throw RateLimitExceededException when hourly limit reached")
        fun `should throw RateLimitExceededException when hourly limit reached`() {
            // Given (per RUN_FEATURE.md spec: 50 queries/hour)
            val quota =
                UserExecutionQuotaEntity(
                    id = "quota-1",
                    userId = testUserId,
                    queriesToday = 5,
                    queriesThisHour = 50, // At limit (50/hour per spec)
                    lastQueryDate = today(),
                    lastQueryHour = now().hour,
                )
            every { quotaRepositoryJpa.findByUserId(testUserId) } returns quota

            // When & Then
            val exception =
                assertThrows<RateLimitExceededException> {
                    executionPolicyService.validateExecution(
                        userId = testUserId,
                        sql = "SELECT * FROM users",
                        engine = "bigquery",
                    )
                }

            assertThat(exception.limitType).isEqualTo("queries_per_hour")
            assertThat(exception.limit).isEqualTo(50)
            assertThat(exception.currentUsage).isEqualTo(50)
            assertThat(exception.resetAt).isNotNull()

            verify(exactly = 0) { quotaRepositoryJpa.save(any()) }
        }

        @Test
        @DisplayName("should throw RateLimitExceededException when daily limit reached")
        fun `should throw RateLimitExceededException when daily limit reached`() {
            // Given (per RUN_FEATURE.md spec: 200 queries/day)
            val quota =
                UserExecutionQuotaEntity(
                    id = "quota-1",
                    userId = testUserId,
                    queriesToday = 200, // At limit (200/day per spec)
                    queriesThisHour = 5,
                    lastQueryDate = today(),
                    lastQueryHour = now().hour,
                )
            every { quotaRepositoryJpa.findByUserId(testUserId) } returns quota

            // When & Then
            val exception =
                assertThrows<RateLimitExceededException> {
                    executionPolicyService.validateExecution(
                        userId = testUserId,
                        sql = "SELECT * FROM users",
                        engine = "bigquery",
                    )
                }

            assertThat(exception.limitType).isEqualTo("queries_per_day")
            assertThat(exception.limit).isEqualTo(200)
            assertThat(exception.currentUsage).isEqualTo(200)
            assertThat(exception.resetAt).isNotNull()
        }

        @Test
        @DisplayName("should throw QueryEngineNotSupportedException for invalid engine")
        fun `should throw QueryEngineNotSupportedException for invalid engine`() {
            // Given
            val quota =
                UserExecutionQuotaEntity.create(testUserId, clock).apply {
                    queriesToday = 0
                    queriesThisHour = 0
                }
            every { quotaRepositoryJpa.findByUserId(testUserId) } returns quota
            every { quotaRepositoryJpa.save(any()) } answers { firstArg() }

            // When & Then
            val exception =
                assertThrows<QueryEngineNotSupportedException> {
                    executionPolicyService.validateExecution(
                        userId = testUserId,
                        sql = "SELECT * FROM users",
                        engine = "mysql", // Not allowed
                    )
                }

            assertThat(exception.engine).isEqualTo("mysql")
            assertThat(exception.allowedEngines).containsExactly("bigquery", "trino")
        }

        @Test
        @DisplayName("should throw QueryTooLargeException for oversized SQL")
        fun `should throw QueryTooLargeException for oversized SQL`() {
            // Given
            val quota = UserExecutionQuotaEntity.create(testUserId, clock)
            every { quotaRepositoryJpa.findByUserId(testUserId) } returns quota
            every { quotaRepositoryJpa.save(any()) } answers { firstArg() }

            // Create SQL larger than 10MB
            val largeSql = "SELECT " + "x".repeat(11 * 1024 * 1024)

            // When & Then
            val exception =
                assertThrows<QueryTooLargeException> {
                    executionPolicyService.validateExecution(
                        userId = testUserId,
                        sql = largeSql,
                        engine = "bigquery",
                    )
                }

            assertThat(exception.actualSizeBytes).isGreaterThan(10 * 1024 * 1024L)
            assertThat(exception.maxSizeBytes).isEqualTo(10 * 1024 * 1024L)
        }

        @Test
        @DisplayName("should accept engine case-insensitively")
        fun `should accept engine case-insensitively`() {
            // Given
            val quota = UserExecutionQuotaEntity.create(testUserId, clock)
            every { quotaRepositoryJpa.findByUserId(testUserId) } returns quota
            every { quotaRepositoryJpa.save(any()) } answers { firstArg() }

            // When & Then - no exception for uppercase engine
            executionPolicyService.validateExecution(
                userId = testUserId,
                sql = "SELECT * FROM users",
                engine = "BIGQUERY",
            )
        }
    }

    @Nested
    @DisplayName("incrementUsage")
    inner class IncrementUsage {
        @Test
        @DisplayName("should increment usage correctly")
        fun `should increment usage correctly`() {
            // Given
            val quota =
                UserExecutionQuotaEntity(
                    id = "quota-1",
                    userId = testUserId,
                    queriesToday = 5,
                    queriesThisHour = 2,
                    lastQueryDate = today(),
                    lastQueryHour = now().hour,
                )
            every { quotaRepositoryJpa.findByUserId(testUserId) } returns quota
            val savedQuotaSlot = slot<UserExecutionQuotaEntity>()
            every { quotaRepositoryJpa.save(capture(savedQuotaSlot)) } answers { savedQuotaSlot.captured }

            // When
            executionPolicyService.incrementUsage(testUserId)

            // Then
            assertThat(savedQuotaSlot.captured.queriesToday).isEqualTo(6)
            assertThat(savedQuotaSlot.captured.queriesThisHour).isEqualTo(3)

            verify(exactly = 1) { quotaRepositoryJpa.save(any()) }
        }

        @Test
        @DisplayName("should create quota if not exists and increment")
        fun `should create quota if not exists and increment`() {
            // Given
            every { quotaRepositoryJpa.findByUserId(testUserId) } returns null
            val savedQuotaSlot = slot<UserExecutionQuotaEntity>()
            every { quotaRepositoryJpa.save(capture(savedQuotaSlot)) } answers { savedQuotaSlot.captured }

            // When
            executionPolicyService.incrementUsage(testUserId)

            // Then
            assertThat(savedQuotaSlot.captured.queriesToday).isEqualTo(1)
            assertThat(savedQuotaSlot.captured.queriesThisHour).isEqualTo(1)
        }
    }

    @Nested
    @DisplayName("getConfig")
    inner class GetConfig {
        @Test
        @DisplayName("should return configuration")
        fun `should return configuration`() {
            // When
            val result = executionPolicyService.getConfig()

            // Then
            assertThat(result).isEqualTo(config)
        }
    }
}
