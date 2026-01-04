package com.github.lambda.domain.service

import com.github.lambda.common.enums.HealthStatus
import com.github.lambda.domain.external.HealthIndicator
import com.github.lambda.domain.model.health.ComponentHealth
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * HealthService Unit Tests
 *
 * Uses MockK to isolate dependencies and test pure business logic.
 * No Spring context required - pure unit testing.
 */
@DisplayName("HealthService Unit Tests")
class HealthServiceTest {
    private val databaseIndicator: HealthIndicator = mockk()
    private val redisIndicator: HealthIndicator = mockk()
    private val airflowIndicator: HealthIndicator = mockk()

    private lateinit var healthService: HealthService

    @BeforeEach
    fun setUp() {
        // Setup indicator names
        every { databaseIndicator.name() } returns "database"
        every { redisIndicator.name() } returns "redis"
        every { airflowIndicator.name() } returns "airflow"

        // Create service with mock indicators
        healthService =
            HealthService(
                healthIndicators = listOf(databaseIndicator, redisIndicator, airflowIndicator),
            )
    }

    @Nested
    @DisplayName("checkHealth")
    inner class CheckHealth {
        @Test
        @DisplayName("should return health status for all registered indicators")
        fun `should return health status for all registered indicators`() {
            // Given
            every { databaseIndicator.check() } returns ComponentHealth.up(mapOf("type" to "mysql"))
            every { redisIndicator.check() } returns ComponentHealth.up(mapOf("mode" to "standalone"))
            every { airflowIndicator.check() } returns ComponentHealth.unknown()

            // When
            val result = healthService.checkHealth()

            // Then
            assertThat(result).hasSize(3)
            assertThat(result).containsKeys("database", "redis", "airflow")

            assertThat(result["database"]?.status).isEqualTo(HealthStatus.UP)
            assertThat(result["database"]?.details).containsEntry("type", "mysql")

            assertThat(result["redis"]?.status).isEqualTo(HealthStatus.UP)
            assertThat(result["redis"]?.details).containsEntry("mode", "standalone")

            assertThat(result["airflow"]?.status).isEqualTo(HealthStatus.UNKNOWN)

            verify(exactly = 1) { databaseIndicator.check() }
            verify(exactly = 1) { redisIndicator.check() }
            verify(exactly = 1) { airflowIndicator.check() }
        }

        @Test
        @DisplayName("should return empty map when no indicators are registered")
        fun `should return empty map when no indicators are registered`() {
            // Given
            val emptyService = HealthService(emptyList())

            // When
            val result = emptyService.checkHealth()

            // Then
            assertThat(result).isEmpty()
        }

        @Test
        @DisplayName("should include DOWN status with error details")
        fun `should include DOWN status with error details`() {
            // Given
            every { databaseIndicator.check() } returns
                ComponentHealth.down(
                    error = "Connection timeout",
                    details = mapOf("type" to "mysql"),
                )
            every { redisIndicator.check() } returns ComponentHealth.up()
            every { airflowIndicator.check() } returns ComponentHealth.unknown()

            // When
            val result = healthService.checkHealth()

            // Then
            assertThat(result["database"]?.status).isEqualTo(HealthStatus.DOWN)
            assertThat(result["database"]?.details).containsEntry("error", "Connection timeout")
            assertThat(result["database"]?.details).containsEntry("type", "mysql")
        }

        @Test
        @DisplayName("should handle all indicators returning UP status")
        fun `should handle all indicators returning UP status`() {
            // Given
            every { databaseIndicator.check() } returns
                ComponentHealth.up(
                    mapOf("type" to "mysql", "pool" to mapOf("active" to 5, "idle" to 10, "max" to 20)),
                )
            every { redisIndicator.check() } returns
                ComponentHealth.up(
                    mapOf("mode" to "standalone", "version" to "7.0.0"),
                )
            every { airflowIndicator.check() } returns
                ComponentHealth.up(
                    mapOf("version" to "2.8.0", "dagCount" to 150),
                )

            // When
            val result = healthService.checkHealth()

            // Then
            assertThat(result.values).allMatch { it.status == HealthStatus.UP }
        }

        @Test
        @DisplayName("should handle all indicators returning DOWN status")
        fun `should handle all indicators returning DOWN status`() {
            // Given
            every { databaseIndicator.check() } returns ComponentHealth.down("Database connection failed")
            every { redisIndicator.check() } returns ComponentHealth.down("Redis connection refused")
            every { airflowIndicator.check() } returns ComponentHealth.down("Airflow API unreachable")

            // When
            val result = healthService.checkHealth()

            // Then
            assertThat(result.values).allMatch { it.status == HealthStatus.DOWN }
            assertThat(result["database"]?.details).containsKey("error")
            assertThat(result["redis"]?.details).containsKey("error")
            assertThat(result["airflow"]?.details).containsKey("error")
        }
    }

    @Nested
    @DisplayName("getOverallStatus")
    inner class GetOverallStatus {
        @Test
        @DisplayName("should return UP when all components are UP")
        fun `should return UP when all components are UP`() {
            // Given
            val components =
                mapOf(
                    "database" to ComponentHealth.up(),
                    "redis" to ComponentHealth.up(),
                    "airflow" to ComponentHealth.up(),
                )

            // When
            val result = healthService.getOverallStatus(components)

            // Then
            assertThat(result).isEqualTo(HealthStatus.UP)
        }

        @Test
        @DisplayName("should return DOWN when any component is DOWN")
        fun `should return DOWN when any component is DOWN`() {
            // Given
            val components =
                mapOf(
                    "database" to ComponentHealth.up(),
                    "redis" to ComponentHealth.down("Connection refused"),
                    "airflow" to ComponentHealth.up(),
                )

            // When
            val result = healthService.getOverallStatus(components)

            // Then
            assertThat(result).isEqualTo(HealthStatus.DOWN)
        }

        @Test
        @DisplayName("should return DOWN when multiple components are DOWN")
        fun `should return DOWN when multiple components are DOWN`() {
            // Given
            val components =
                mapOf(
                    "database" to ComponentHealth.down("Connection timeout"),
                    "redis" to ComponentHealth.down("Connection refused"),
                    "airflow" to ComponentHealth.up(),
                )

            // When
            val result = healthService.getOverallStatus(components)

            // Then
            assertThat(result).isEqualTo(HealthStatus.DOWN)
        }

        @Test
        @DisplayName("should return UNKNOWN when no components are DOWN but some are UNKNOWN")
        fun `should return UNKNOWN when no components are DOWN but some are UNKNOWN`() {
            // Given
            val components =
                mapOf(
                    "database" to ComponentHealth.up(),
                    "redis" to ComponentHealth.up(),
                    "airflow" to ComponentHealth.unknown(),
                )

            // When
            val result = healthService.getOverallStatus(components)

            // Then
            assertThat(result).isEqualTo(HealthStatus.UNKNOWN)
        }

        @Test
        @DisplayName("should return UNKNOWN for empty components map")
        fun `should return UNKNOWN for empty components map`() {
            // Given
            val components = emptyMap<String, ComponentHealth>()

            // When
            val result = healthService.getOverallStatus(components)

            // Then
            assertThat(result).isEqualTo(HealthStatus.UNKNOWN)
        }

        @Test
        @DisplayName("should return DOWN when UNKNOWN and DOWN are mixed")
        fun `should return DOWN when UNKNOWN and DOWN are mixed`() {
            // Given
            val components =
                mapOf(
                    "database" to ComponentHealth.unknown(),
                    "redis" to ComponentHealth.down("Error"),
                    "airflow" to ComponentHealth.up(),
                )

            // When
            val result = healthService.getOverallStatus(components)

            // Then
            assertThat(result).isEqualTo(HealthStatus.DOWN)
        }

        @Test
        @DisplayName("should return UNKNOWN when only UNKNOWN components exist")
        fun `should return UNKNOWN when only UNKNOWN components exist`() {
            // Given
            val components =
                mapOf(
                    "database" to ComponentHealth.unknown(),
                    "redis" to ComponentHealth.unknown(),
                    "airflow" to ComponentHealth.unknown(),
                )

            // When
            val result = healthService.getOverallStatus(components)

            // Then
            assertThat(result).isEqualTo(HealthStatus.UNKNOWN)
        }
    }

    @Nested
    @DisplayName("checkComponent")
    inner class CheckComponent {
        @Test
        @DisplayName("should return health for existing component")
        fun `should return health for existing component`() {
            // Given
            val expectedHealth = ComponentHealth.up(mapOf("type" to "mysql"))
            every { databaseIndicator.check() } returns expectedHealth

            // When
            val result = healthService.checkComponent("database")

            // Then
            assertThat(result).isNotNull
            assertThat(result?.status).isEqualTo(HealthStatus.UP)
            assertThat(result?.details).containsEntry("type", "mysql")
            verify(exactly = 1) { databaseIndicator.check() }
        }

        @Test
        @DisplayName("should return null for non-existent component")
        fun `should return null for non-existent component`() {
            // When
            val result = healthService.checkComponent("nonexistent")

            // Then
            assertThat(result).isNull()
        }

        @Test
        @DisplayName("should return DOWN status for failing component")
        fun `should return DOWN status for failing component`() {
            // Given
            val expectedHealth = ComponentHealth.down("Connection failed")
            every { redisIndicator.check() } returns expectedHealth

            // When
            val result = healthService.checkComponent("redis")

            // Then
            assertThat(result).isNotNull
            assertThat(result?.status).isEqualTo(HealthStatus.DOWN)
            assertThat(result?.details).containsEntry("error", "Connection failed")
        }

        @Test
        @DisplayName("should return UNKNOWN status for mock component")
        fun `should return UNKNOWN status for mock component`() {
            // Given
            val expectedHealth = ComponentHealth.unknown(mapOf("note" to "Integration pending"))
            every { airflowIndicator.check() } returns expectedHealth

            // When
            val result = healthService.checkComponent("airflow")

            // Then
            assertThat(result).isNotNull
            assertThat(result?.status).isEqualTo(HealthStatus.UNKNOWN)
            assertThat(result?.details).containsEntry("note", "Integration pending")
        }
    }
}
