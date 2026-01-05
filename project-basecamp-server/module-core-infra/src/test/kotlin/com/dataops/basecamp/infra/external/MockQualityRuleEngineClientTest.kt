package com.dataops.basecamp.infra.external

import com.dataops.basecamp.common.enums.TestType
import com.dataops.basecamp.common.exception.QualityRuleEngineException
import com.dataops.basecamp.domain.external.quality.GenerateSqlRequest
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class MockQualityRuleEngineClientTest {
    private lateinit var client: MockQualityRuleEngineClient

    @BeforeEach
    fun setUp() {
        client = MockQualityRuleEngineClient()
    }

    @Test
    fun `isAvailable should return true`() {
        // when
        val result = client.isAvailable()

        // then
        assertThat(result).isTrue
    }

    @Test
    fun `generateSql should generate NOT_NULL test SQL`() {
        // given
        val request =
            GenerateSqlRequest(
                testType = TestType.NOT_NULL,
                resourceName = "iceberg.analytics.users",
                column = "user_id",
            )

        // when
        val response = client.generateSql(request)

        // then
        assertThat(response.sql).contains("COUNT(*) FILTER (WHERE user_id IS NULL)")
        assertThat(response.sql).contains("FROM iceberg.analytics.users")
        assertThat(response.sampleFailuresSql).contains("WHERE user_id IS NULL")
    }

    @Test
    fun `generateSql should generate UNIQUE test SQL`() {
        // given
        val request =
            GenerateSqlRequest(
                testType = TestType.UNIQUE,
                resourceName = "iceberg.analytics.users",
                column = "email",
            )

        // when
        val response = client.generateSql(request)

        // then
        assertThat(response.sql).contains("COUNT(*) - COUNT(DISTINCT email)")
        assertThat(response.sql).contains("FROM iceberg.analytics.users")
        assertThat(response.sampleFailuresSql).contains("GROUP BY email")
        assertThat(response.sampleFailuresSql).contains("HAVING COUNT(*) > 1")
    }

    @Test
    fun `generateSql should generate ACCEPTED_VALUES test SQL`() {
        // given
        val request =
            GenerateSqlRequest(
                testType = TestType.ACCEPTED_VALUES,
                resourceName = "iceberg.analytics.users",
                column = "status",
                config = mapOf("values" to listOf("active", "inactive", "pending")),
            )

        // when
        val response = client.generateSql(request)

        // then
        assertThat(response.sql).contains("status NOT IN ('active', 'inactive', 'pending')")
        assertThat(response.sql).contains("FROM iceberg.analytics.users")
        assertThat(response.sampleFailuresSql).contains("WHERE status NOT IN")
    }

    @Test
    fun `generateSql should generate RELATIONSHIPS test SQL`() {
        // given
        val request =
            GenerateSqlRequest(
                testType = TestType.RELATIONSHIPS,
                resourceName = "iceberg.analytics.orders",
                column = "user_id",
                config =
                    mapOf(
                        "to_table" to "iceberg.analytics.users",
                        "to_column" to "id",
                    ),
            )

        // when
        val response = client.generateSql(request)

        // then
        assertThat(response.sql).contains("LEFT JOIN iceberg.analytics.users ref")
        assertThat(response.sql).contains("ON src.user_id = ref.id")
        assertThat(response.sql).contains("FROM iceberg.analytics.orders src")
    }

    @Test
    fun `generateSql should generate EXPRESSION test SQL`() {
        // given
        val request =
            GenerateSqlRequest(
                testType = TestType.EXPRESSION,
                resourceName = "iceberg.analytics.users",
                config =
                    mapOf(
                        "expression" to "age >= 18",
                        "description" to "Users must be adults",
                    ),
            )

        // when
        val response = client.generateSql(request)

        // then
        assertThat(response.sql).contains("WHERE NOT (age >= 18)")
        assertThat(response.sql).contains("FROM iceberg.analytics.users")
        assertThat(response.sampleFailuresSql).contains("WHERE NOT (age >= 18)")
    }

    @Test
    fun `generateSql should generate ROW_COUNT test SQL with min and max`() {
        // given
        val request =
            GenerateSqlRequest(
                testType = TestType.ROW_COUNT,
                resourceName = "iceberg.analytics.users",
                config =
                    mapOf(
                        "min" to 100,
                        "max" to 10000,
                    ),
            )

        // when
        val response = client.generateSql(request)

        // then
        assertThat(response.sql).contains("WITH row_count_check")
        assertThat(response.sql).contains("row_count < 100 OR row_count > 10000")
        assertThat(response.sql).contains("FROM iceberg.analytics.users")
    }

    @Test
    fun `generateSql should generate SINGULAR test SQL`() {
        // given
        val request =
            GenerateSqlRequest(
                testType = TestType.SINGULAR,
                resourceName = "iceberg.analytics.system_config",
            )

        // when
        val response = client.generateSql(request)

        // then
        assertThat(response.sql).contains("CASE WHEN row_count != 1")
        assertThat(response.sql).contains("FROM iceberg.analytics.system_config")
        assertThat(response.sampleFailuresSql).contains("COUNT(*) as actual_row_count")
    }

    @Test
    fun `generateSql should throw exception for NOT_NULL test without column`() {
        // given
        val request =
            GenerateSqlRequest(
                testType = TestType.NOT_NULL,
                resourceName = "iceberg.analytics.users",
                column = null, // Missing required column
            )

        // when & then
        assertThatThrownBy { client.generateSql(request) }
            .isInstanceOf(QualityRuleEngineException::class.java)
            .hasMessageContaining("Column is required for NOT_NULL test")
    }

    @Test
    fun `generateSql should throw exception for ACCEPTED_VALUES test with missing config`() {
        // given
        val request =
            GenerateSqlRequest(
                testType = TestType.ACCEPTED_VALUES,
                resourceName = "iceberg.analytics.users",
                column = "status",
                config = emptyMap(), // Missing required values
            )

        // when & then
        assertThatThrownBy { client.generateSql(request) }
            .isInstanceOf(QualityRuleEngineException::class.java)
            .hasMessageContaining("Missing 'values' in accepted_values config")
    }

    @Test
    fun `generateSql should throw exception for RELATIONSHIPS test with missing config`() {
        // given
        val request =
            GenerateSqlRequest(
                testType = TestType.RELATIONSHIPS,
                resourceName = "iceberg.analytics.orders",
                column = "user_id",
                config = mapOf("to_table" to "users"), // Missing to_column
            )

        // when & then
        assertThatThrownBy { client.generateSql(request) }
            .isInstanceOf(QualityRuleEngineException::class.java)
            .hasMessageContaining("Missing 'to_column' in relationships config")
    }
}
