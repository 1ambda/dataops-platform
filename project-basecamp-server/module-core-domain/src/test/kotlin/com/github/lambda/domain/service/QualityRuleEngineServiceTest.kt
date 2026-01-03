package com.github.lambda.domain.service

import com.github.lambda.common.exception.QualityRuleEngineException
import com.github.lambda.domain.external.QualityRuleEngineClient
import com.github.lambda.domain.model.quality.GenerateSqlRequest
import com.github.lambda.domain.model.quality.GenerateSqlResponse
import com.github.lambda.domain.model.quality.TestType
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class QualityRuleEngineServiceTest {
    private val client: QualityRuleEngineClient = mockk()
    private lateinit var service: QualityRuleEngineService

    @BeforeEach
    fun setUp() {
        service = QualityRuleEngineService(client)
    }

    @Test
    fun `generateNotNullTestSql should create correct request and return response`() {
        // given
        val expectedRequest =
            GenerateSqlRequest(
                testType = TestType.NOT_NULL,
                resourceName = "iceberg.analytics.users",
                column = "user_id",
            )
        val expectedResponse =
            GenerateSqlResponse(
                sql = "SELECT COUNT(*) FILTER (WHERE user_id IS NULL) as failed_rows FROM iceberg.analytics.users",
                sampleFailuresSql = "SELECT * FROM iceberg.analytics.users WHERE user_id IS NULL LIMIT 5",
            )

        every { client.generateSql(expectedRequest) } returns expectedResponse

        // when
        val result = service.generateNotNullTestSql("iceberg.analytics.users", "user_id")

        // then
        assertThat(result).isEqualTo(expectedResponse)
        verify { client.generateSql(expectedRequest) }
    }

    @Test
    fun `generateUniqueTestSql should create correct request and return response`() {
        // given
        val expectedRequest =
            GenerateSqlRequest(
                testType = TestType.UNIQUE,
                resourceName = "iceberg.analytics.users",
                column = "email",
            )
        val expectedResponse =
            GenerateSqlResponse(
                sql = "SELECT COUNT(*) - COUNT(DISTINCT email) as failed_rows FROM iceberg.analytics.users",
            )

        every { client.generateSql(expectedRequest) } returns expectedResponse

        // when
        val result = service.generateUniqueTestSql("iceberg.analytics.users", "email")

        // then
        assertThat(result).isEqualTo(expectedResponse)
        verify { client.generateSql(expectedRequest) }
    }

    @Test
    fun `generateAcceptedValuesTestSql should create correct request with config`() {
        // given
        val acceptedValues = listOf("active", "inactive", "pending")
        val expectedRequest =
            GenerateSqlRequest(
                testType = TestType.ACCEPTED_VALUES,
                resourceName = "iceberg.analytics.users",
                column = "status",
                config = mapOf("values" to acceptedValues),
            )
        val expectedResponse =
            GenerateSqlResponse(
                sql = "SELECT COUNT(*) FILTER (WHERE status NOT IN ('active', 'inactive', 'pending')) as failed_rows",
            )

        every { client.generateSql(expectedRequest) } returns expectedResponse

        // when
        val result =
            service.generateAcceptedValuesTestSql(
                "iceberg.analytics.users",
                "status",
                acceptedValues,
            )

        // then
        assertThat(result).isEqualTo(expectedResponse)
        verify { client.generateSql(expectedRequest) }
    }

    @Test
    fun `generateRelationshipsTestSql should create correct request with config`() {
        // given
        val expectedRequest =
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
        val expectedResponse =
            GenerateSqlResponse(
                sql = "SELECT COUNT(*) FILTER (...) as failed_rows FROM iceberg.analytics.orders",
            )

        every { client.generateSql(expectedRequest) } returns expectedResponse

        // when
        val result =
            service.generateRelationshipsTestSql(
                "iceberg.analytics.orders",
                "user_id",
                "iceberg.analytics.users",
                "id",
            )

        // then
        assertThat(result).isEqualTo(expectedResponse)
        verify { client.generateSql(expectedRequest) }
    }

    @Test
    fun `generateExpressionTestSql should create correct request with expression`() {
        // given
        val expression = "age >= 18"
        val description = "Users must be adults"
        val expectedRequest =
            GenerateSqlRequest(
                testType = TestType.EXPRESSION,
                resourceName = "iceberg.analytics.users",
                config =
                    mapOf(
                        "expression" to expression,
                        "description" to description,
                    ),
            )
        val expectedResponse =
            GenerateSqlResponse(
                sql = "SELECT COUNT(*) FILTER (WHERE NOT (age >= 18)) as failed_rows",
            )

        every { client.generateSql(expectedRequest) } returns expectedResponse

        // when
        val result =
            service.generateExpressionTestSql(
                "iceberg.analytics.users",
                expression,
                description,
            )

        // then
        assertThat(result).isEqualTo(expectedResponse)
        verify { client.generateSql(expectedRequest) }
    }

    @Test
    fun `generateExpressionTestSql should work without description`() {
        // given
        val expression = "price > 0"
        val expectedRequest =
            GenerateSqlRequest(
                testType = TestType.EXPRESSION,
                resourceName = "iceberg.analytics.products",
                config = mapOf("expression" to expression),
            )
        val expectedResponse =
            GenerateSqlResponse(
                sql = "SELECT COUNT(*) FILTER (WHERE NOT (price > 0)) as failed_rows",
            )

        every { client.generateSql(expectedRequest) } returns expectedResponse

        // when
        val result = service.generateExpressionTestSql("iceberg.analytics.products", expression)

        // then
        assertThat(result).isEqualTo(expectedResponse)
        verify { client.generateSql(expectedRequest) }
    }

    @Test
    fun `generateRowCountTestSql should create correct request with min and max`() {
        // given
        val expectedRequest =
            GenerateSqlRequest(
                testType = TestType.ROW_COUNT,
                resourceName = "iceberg.analytics.users",
                config =
                    mapOf(
                        "min" to 100L,
                        "max" to 10000L,
                    ),
            )
        val expectedResponse =
            GenerateSqlResponse(
                sql = "WITH row_count_check AS (...) SELECT CASE WHEN ... THEN 1 ELSE 0 END as failed_rows",
            )

        every { client.generateSql(expectedRequest) } returns expectedResponse

        // when
        val result = service.generateRowCountTestSql("iceberg.analytics.users", 100L, 10000L)

        // then
        assertThat(result).isEqualTo(expectedResponse)
        verify { client.generateSql(expectedRequest) }
    }

    @Test
    fun `generateRowCountTestSql should work with only min`() {
        // given
        val expectedRequest =
            GenerateSqlRequest(
                testType = TestType.ROW_COUNT,
                resourceName = "iceberg.analytics.users",
                config = mapOf("min" to 100L),
            )
        val expectedResponse =
            GenerateSqlResponse(
                sql = "WITH row_count_check AS (...) SELECT CASE WHEN row_count < 100 ...",
            )

        every { client.generateSql(expectedRequest) } returns expectedResponse

        // when
        val result = service.generateRowCountTestSql("iceberg.analytics.users", minRows = 100L)

        // then
        assertThat(result).isEqualTo(expectedResponse)
        verify { client.generateSql(expectedRequest) }
    }

    @Test
    fun `generateSingularTestSql should create correct request`() {
        // given
        val expectedRequest =
            GenerateSqlRequest(
                testType = TestType.SINGULAR,
                resourceName = "iceberg.analytics.system_config",
            )
        val expectedResponse =
            GenerateSqlResponse(
                sql = "SELECT CASE WHEN row_count != 1 THEN 1 ELSE 0 END as failed_rows",
            )

        every { client.generateSql(expectedRequest) } returns expectedResponse

        // when
        val result = service.generateSingularTestSql("iceberg.analytics.system_config")

        // then
        assertThat(result).isEqualTo(expectedResponse)
        verify { client.generateSql(expectedRequest) }
    }

    @Test
    fun `generateCustomTestSql should delegate to client with custom request`() {
        // given
        val customRequest =
            GenerateSqlRequest(
                testType = TestType.EXPRESSION,
                resourceName = "iceberg.analytics.custom_table",
                config = mapOf("custom_param" to "custom_value"),
            )
        val expectedResponse =
            GenerateSqlResponse(
                sql = "SELECT custom_sql...",
            )

        every { client.generateSql(customRequest) } returns expectedResponse

        // when
        val result = service.generateCustomTestSql(customRequest)

        // then
        assertThat(result).isEqualTo(expectedResponse)
        verify { client.generateSql(customRequest) }
    }

    @Test
    fun `isRuleEngineAvailable should return true when client is available`() {
        // given
        every { client.isAvailable() } returns true

        // when
        val result = service.isRuleEngineAvailable()

        // then
        assertThat(result).isTrue
        verify { client.isAvailable() }
    }

    @Test
    fun `isRuleEngineAvailable should return false when client throws exception`() {
        // given
        every { client.isAvailable() } throws RuntimeException("Service unavailable")

        // when
        val result = service.isRuleEngineAvailable()

        // then
        assertThat(result).isFalse
        verify { client.isAvailable() }
    }

    @Test
    fun `service methods should propagate client exceptions`() {
        // given
        val request =
            GenerateSqlRequest(
                testType = TestType.NOT_NULL,
                resourceName = "iceberg.analytics.users",
                column = "user_id",
            )
        val expectedException = QualityRuleEngineException("External service failed")

        every { client.generateSql(request) } throws expectedException

        // when & then
        assertThatThrownBy {
            service.generateNotNullTestSql("iceberg.analytics.users", "user_id")
        }.isSameAs(expectedException)

        verify { client.generateSql(request) }
    }
}
