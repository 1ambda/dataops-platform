package com.github.lambda.infra.health

import com.github.lambda.domain.model.health.HealthStatus
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * AirflowHealthIndicator Unit Tests
 *
 * Tests the Airflow health check functionality.
 * Currently tests the MVP mock implementation.
 */
@DisplayName("AirflowHealthIndicator Unit Tests")
class AirflowHealthIndicatorTest {
    private lateinit var healthIndicator: AirflowHealthIndicator

    @BeforeEach
    fun setUp() {
        healthIndicator = AirflowHealthIndicator()
    }

    @Nested
    @DisplayName("name()")
    inner class Name {
        @Test
        @DisplayName("should return 'airflow' as component name")
        fun `should return airflow as component name`() {
            // When
            val result = healthIndicator.name()

            // Then
            assertThat(result).isEqualTo("airflow")
        }
    }

    @Nested
    @DisplayName("check()")
    inner class Check {
        @Test
        @DisplayName("should return UNKNOWN status for MVP mock implementation")
        fun `should return UNKNOWN status for MVP mock implementation`() {
            // When
            val result = healthIndicator.check()

            // Then
            assertThat(result.status).isEqualTo(HealthStatus.UNKNOWN)
        }

        @Test
        @DisplayName("should include note about pending integration in details")
        fun `should include note about pending integration in details`() {
            // When
            val result = healthIndicator.check()

            // Then
            assertThat(result.details["note"]).isEqualTo("Airflow integration pending")
        }

        @Test
        @DisplayName("should include null version in details")
        fun `should include null version in details`() {
            // When
            val result = healthIndicator.check()

            // Then
            assertThat(result.details).containsKey("version")
            assertThat(result.details["version"]).isNull()
        }

        @Test
        @DisplayName("should include null dagCount in details")
        fun `should include null dagCount in details`() {
            // When
            val result = healthIndicator.check()

            // Then
            assertThat(result.details).containsKey("dagCount")
            assertThat(result.details["dagCount"]).isNull()
        }

        @Test
        @DisplayName("should return consistent results on multiple calls")
        fun `should return consistent results on multiple calls`() {
            // When
            val result1 = healthIndicator.check()
            val result2 = healthIndicator.check()
            val result3 = healthIndicator.check()

            // Then
            assertThat(result1.status).isEqualTo(result2.status).isEqualTo(result3.status)
            assertThat(result1.details["note"]).isEqualTo(result2.details["note"]).isEqualTo(result3.details["note"])
        }
    }
}
