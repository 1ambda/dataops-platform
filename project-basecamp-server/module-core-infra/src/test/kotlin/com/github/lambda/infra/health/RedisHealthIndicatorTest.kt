package com.github.lambda.infra.health

import com.github.lambda.domain.model.health.HealthStatus
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.data.redis.RedisConnectionFailureException
import org.springframework.data.redis.connection.RedisConnection
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.connection.RedisServerCommands
import java.util.Properties

/**
 * RedisHealthIndicator Unit Tests
 *
 * Tests the Redis health check functionality using MockK.
 * Verifies connection status and server info extraction.
 */
@DisplayName("RedisHealthIndicator Unit Tests")
class RedisHealthIndicatorTest {
    private val redisConnectionFactory: RedisConnectionFactory = mockk()

    private lateinit var healthIndicator: RedisHealthIndicator

    @BeforeEach
    fun setUp() {
        healthIndicator = RedisHealthIndicator(redisConnectionFactory)
    }

    @Nested
    @DisplayName("name()")
    inner class Name {
        @Test
        @DisplayName("should return 'redis' as component name")
        fun `should return redis as component name`() {
            // When
            val result = healthIndicator.name()

            // Then
            assertThat(result).isEqualTo("redis")
        }
    }

    @Nested
    @DisplayName("check() - successful connection")
    inner class CheckSuccessful {
        @Test
        @DisplayName("should return UP status with version info when Redis is connected")
        fun `should return UP status with version info when Redis is connected`() {
            // Given
            val connection: RedisConnection = mockk()
            val serverCommands: RedisServerCommands = mockk()
            val serverInfo =
                Properties().apply {
                    setProperty("redis_version", "7.0.11")
                    setProperty("cluster_enabled", "0")
                }

            every { redisConnectionFactory.connection } returns connection
            every { connection.serverCommands() } returns serverCommands
            every { serverCommands.info("server") } returns serverInfo
            every { connection.close() } returns Unit

            // When
            val result = healthIndicator.check()

            // Then
            assertThat(result.status).isEqualTo(HealthStatus.UP)
            assertThat(result.details["mode"]).isEqualTo("standalone")
            assertThat(result.details["version"]).isEqualTo("7.0.11")

            verify(exactly = 1) { connection.close() }
        }

        @Test
        @DisplayName("should detect cluster mode when cluster_enabled is 1")
        fun `should detect cluster mode when cluster_enabled is 1`() {
            // Given
            val connection: RedisConnection = mockk()
            val serverCommands: RedisServerCommands = mockk()
            val serverInfo =
                Properties().apply {
                    setProperty("redis_version", "7.2.0")
                    setProperty("cluster_enabled", "1")
                }

            every { redisConnectionFactory.connection } returns connection
            every { connection.serverCommands() } returns serverCommands
            every { serverCommands.info("server") } returns serverInfo
            every { connection.close() } returns Unit

            // When
            val result = healthIndicator.check()

            // Then
            assertThat(result.status).isEqualTo(HealthStatus.UP)
            assertThat(result.details["mode"]).isEqualTo("cluster")
            assertThat(result.details["version"]).isEqualTo("7.2.0")
        }

        @Test
        @DisplayName("should handle missing version gracefully")
        fun `should handle missing version gracefully`() {
            // Given
            val connection: RedisConnection = mockk()
            val serverCommands: RedisServerCommands = mockk()
            val serverInfo =
                Properties().apply {
                    setProperty("cluster_enabled", "0")
                }

            every { redisConnectionFactory.connection } returns connection
            every { connection.serverCommands() } returns serverCommands
            every { serverCommands.info("server") } returns serverInfo
            every { connection.close() } returns Unit

            // When
            val result = healthIndicator.check()

            // Then
            assertThat(result.status).isEqualTo(HealthStatus.UP)
            assertThat(result.details["mode"]).isEqualTo("standalone")
            assertThat(result.details["version"]).isEqualTo("unknown")
        }

        @Test
        @DisplayName("should handle null server info")
        fun `should handle null server info`() {
            // Given
            val connection: RedisConnection = mockk()
            val serverCommands: RedisServerCommands = mockk()

            every { redisConnectionFactory.connection } returns connection
            every { connection.serverCommands() } returns serverCommands
            every { serverCommands.info("server") } returns null
            every { connection.close() } returns Unit

            // When
            val result = healthIndicator.check()

            // Then
            assertThat(result.status).isEqualTo(HealthStatus.UP)
            assertThat(result.details["mode"]).isEqualTo("standalone")
            assertThat(result.details["version"]).isEqualTo("unknown")
        }
    }

    @Nested
    @DisplayName("check() - connection failures")
    inner class CheckFailures {
        @Test
        @DisplayName("should return DOWN status when connection fails")
        fun `should return DOWN status when connection fails`() {
            // Given
            every { redisConnectionFactory.connection } throws
                RedisConnectionFailureException("Cannot get Jedis connection")

            // When
            val result = healthIndicator.check()

            // Then
            assertThat(result.status).isEqualTo(HealthStatus.DOWN)
            assertThat(result.details["error"]).isEqualTo("Cannot get Jedis connection")
        }

        @Test
        @DisplayName("should return DOWN status when Redis is unavailable")
        fun `should return DOWN status when Redis is unavailable`() {
            // Given
            every { redisConnectionFactory.connection } throws
                RuntimeException("Connection refused: localhost:6379")

            // When
            val result = healthIndicator.check()

            // Then
            assertThat(result.status).isEqualTo(HealthStatus.DOWN)
            assertThat(result.details["error"]).isEqualTo("Connection refused: localhost:6379")
        }

        @Test
        @DisplayName("should handle timeout exceptions")
        fun `should handle timeout exceptions`() {
            // Given
            every { redisConnectionFactory.connection } throws
                RuntimeException("Read timed out")

            // When
            val result = healthIndicator.check()

            // Then
            assertThat(result.status).isEqualTo(HealthStatus.DOWN)
            assertThat(result.details["error"]).isEqualTo("Read timed out")
        }

        @Test
        @DisplayName("should handle authentication failures")
        fun `should handle authentication failures`() {
            // Given
            every { redisConnectionFactory.connection } throws
                RuntimeException("NOAUTH Authentication required")

            // When
            val result = healthIndicator.check()

            // Then
            assertThat(result.status).isEqualTo(HealthStatus.DOWN)
            assertThat(result.details["error"]).isEqualTo("NOAUTH Authentication required")
        }
    }

    @Nested
    @DisplayName("check() - edge cases")
    inner class CheckEdgeCases {
        @Test
        @DisplayName("should handle empty properties")
        fun `should handle empty properties`() {
            // Given
            val connection: RedisConnection = mockk()
            val serverCommands: RedisServerCommands = mockk()
            val serverInfo = Properties()

            every { redisConnectionFactory.connection } returns connection
            every { connection.serverCommands() } returns serverCommands
            every { serverCommands.info("server") } returns serverInfo
            every { connection.close() } returns Unit

            // When
            val result = healthIndicator.check()

            // Then
            assertThat(result.status).isEqualTo(HealthStatus.UP)
            assertThat(result.details["mode"]).isEqualTo("standalone")
            assertThat(result.details["version"]).isEqualTo("unknown")
        }
    }
}
