package com.github.lambda.infra.health

import com.github.lambda.domain.model.health.HealthStatus
import com.zaxxer.hikari.HikariDataSource
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.sql.Connection
import java.sql.SQLException
import javax.sql.DataSource

/**
 * DatabaseHealthIndicator Unit Tests
 *
 * Tests the database health check functionality using MockK.
 * Verifies HikariCP pool monitoring and connection validation.
 */
@DisplayName("DatabaseHealthIndicator Unit Tests")
class DatabaseHealthIndicatorTest {
    private val dataSource: DataSource = mockk()

    private lateinit var healthIndicator: DatabaseHealthIndicator

    @BeforeEach
    fun setUp() {
        healthIndicator = DatabaseHealthIndicator(dataSource)
    }

    @Nested
    @DisplayName("name()")
    inner class Name {
        @Test
        @DisplayName("should return 'database' as component name")
        fun `should return database as component name`() {
            // When
            val result = healthIndicator.name()

            // Then
            assertThat(result).isEqualTo("database")
        }
    }

    @Nested
    @DisplayName("check() with HikariDataSource")
    inner class CheckWithHikari {
        @Test
        @DisplayName("should return UP status with pool details when HikariCP is healthy")
        fun `should return UP status with pool details when HikariCP is healthy`() {
            // Given - using relaxed mock to avoid mocking the inner HikariPoolMXBean interface
            val hikariDataSource: HikariDataSource = mockk(relaxed = true)

            every { hikariDataSource.hikariPoolMXBean?.activeConnections } returns 5
            every { hikariDataSource.hikariPoolMXBean?.idleConnections } returns 10
            every { hikariDataSource.maximumPoolSize } returns 20

            val indicator = DatabaseHealthIndicator(hikariDataSource)

            // When
            val result = indicator.check()

            // Then
            assertThat(result.status).isEqualTo(HealthStatus.UP)
            assertThat(result.details["type"]).isEqualTo("mysql")

            @Suppress("UNCHECKED_CAST")
            val pool = result.details["pool"] as Map<String, Any>
            assertThat(pool["active"]).isEqualTo(5)
            assertThat(pool["idle"]).isEqualTo(10)
            assertThat(pool["max"]).isEqualTo(20)
        }

        @Test
        @DisplayName("should handle null pool MXBean gracefully")
        fun `should handle null pool MXBean gracefully`() {
            // Given
            val hikariDataSource: HikariDataSource = mockk()

            every { hikariDataSource.hikariPoolMXBean } returns null
            every { hikariDataSource.maximumPoolSize } returns 20

            val indicator = DatabaseHealthIndicator(hikariDataSource)

            // When
            val result = indicator.check()

            // Then
            assertThat(result.status).isEqualTo(HealthStatus.UP)
            assertThat(result.details["type"]).isEqualTo("mysql")

            @Suppress("UNCHECKED_CAST")
            val pool = result.details["pool"] as Map<String, Any>
            assertThat(pool["active"]).isEqualTo(0)
            assertThat(pool["idle"]).isEqualTo(0)
            assertThat(pool["max"]).isEqualTo(20)
        }
    }

    @Nested
    @DisplayName("check() with non-HikariDataSource")
    inner class CheckWithGenericDataSource {
        @Test
        @DisplayName("should return UP status when connection is valid")
        fun `should return UP status when connection is valid`() {
            // Given
            val connection: Connection = mockk()
            every { dataSource.connection } returns connection
            every { connection.isValid(5) } returns true
            every { connection.close() } returns Unit

            // When
            val result = healthIndicator.check()

            // Then
            assertThat(result.status).isEqualTo(HealthStatus.UP)
            assertThat(result.details["type"]).isEqualTo("unknown")

            verify(exactly = 1) { connection.isValid(5) }
            verify(exactly = 1) { connection.close() }
        }

        @Test
        @DisplayName("should return DOWN status when connection validation fails")
        fun `should return DOWN status when connection validation fails`() {
            // Given
            val connection: Connection = mockk()
            every { dataSource.connection } returns connection
            every { connection.isValid(5) } returns false
            every { connection.close() } returns Unit

            // When
            val result = healthIndicator.check()

            // Then
            assertThat(result.status).isEqualTo(HealthStatus.DOWN)
            assertThat(result.details["error"]).isEqualTo("Connection validation failed")
        }
    }

    @Nested
    @DisplayName("check() with connection failures")
    inner class CheckWithConnectionFailures {
        @Test
        @DisplayName("should return DOWN status when SQLException occurs")
        fun `should return DOWN status when SQLException occurs`() {
            // Given
            every { dataSource.connection } throws SQLException("Connection refused")

            // When
            val result = healthIndicator.check()

            // Then
            assertThat(result.status).isEqualTo(HealthStatus.DOWN)
            assertThat(result.details["error"]).isEqualTo("Connection refused")
        }

        @Test
        @DisplayName("should return DOWN status when RuntimeException occurs")
        fun `should return DOWN status when RuntimeException occurs`() {
            // Given
            every { dataSource.connection } throws RuntimeException("Pool exhausted")

            // When
            val result = healthIndicator.check()

            // Then
            assertThat(result.status).isEqualTo(HealthStatus.DOWN)
            assertThat(result.details["error"]).isEqualTo("Pool exhausted")
        }

        @Test
        @DisplayName("should handle null error message gracefully")
        fun `should handle null error message gracefully`() {
            // Given
            every { dataSource.connection } throws RuntimeException()

            // When
            val result = healthIndicator.check()

            // Then
            assertThat(result.status).isEqualTo(HealthStatus.DOWN)
            // Error message may be null
        }
    }
}
