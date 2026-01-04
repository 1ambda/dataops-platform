package com.github.lambda.mapper

import com.github.lambda.domain.entity.dataset.DatasetEntity
import com.github.lambda.dto.dataset.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

/**
 * DatasetMapper Unit Tests
 *
 * Tests DTO ↔ Entity mapping functionality.
 */
@DisplayName("DatasetMapper Unit Tests")
class DatasetMapperTest {
    private lateinit var testDatasetEntity: DatasetEntity
    private lateinit var testCreateRequest: CreateDatasetRequest

    @BeforeEach
    fun setUp() {
        testDatasetEntity =
            DatasetEntity(
                id = "test-id-123",
                name = "test_catalog.test_schema.test_dataset",
                owner = "test@example.com",
                team = "data-team",
                description = "Test dataset description",
                sql = "SELECT id, name, created_at FROM users WHERE created_at >= '{{date}}'",
                tags = setOf("test", "user", "analytics"),
                dependencies = setOf("users", "user_profiles"),
                scheduleCron = "0 9 * * *",
                scheduleTimezone = "America/New_York",
                createdAt = LocalDateTime.of(2024, 1, 1, 9, 0, 0),
                updatedAt = LocalDateTime.of(2024, 1, 2, 10, 30, 0),
            )

        testCreateRequest =
            CreateDatasetRequest(
                name = "new_catalog.new_schema.new_dataset",
                owner = "new@example.com",
                team = "new-team",
                description = "New dataset description",
                sql = "SELECT * FROM new_table WHERE active = true",
                tags = listOf("new", "test"),
                dependencies = listOf("new_table", "lookup_table"),
                schedule =
                    ScheduleRequest(
                        cron = "0 8 * * *",
                        timezone = "UTC",
                    ),
            )
    }

    @Nested
    @DisplayName("Request DTO → Entity Mapping")
    inner class RequestToEntityMapping {
        @Test
        @DisplayName("should map CreateDatasetRequest to DatasetEntity with all fields")
        fun `should map CreateDatasetRequest to DatasetEntity with all fields`() {
            // When
            val entity = DatasetMapper.toEntity(testCreateRequest)

            // Then
            assertThat(entity.name).isEqualTo(testCreateRequest.name)
            assertThat(entity.owner).isEqualTo(testCreateRequest.owner)
            assertThat(entity.team).isEqualTo(testCreateRequest.team)
            assertThat(entity.description).isEqualTo(testCreateRequest.description)
            assertThat(entity.sql).isEqualTo(testCreateRequest.sql)
            assertThat(entity.tags).containsExactlyInAnyOrderElementsOf(testCreateRequest.tags)
            assertThat(entity.dependencies).containsExactlyInAnyOrderElementsOf(testCreateRequest.dependencies)
            assertThat(entity.scheduleCron).isEqualTo(testCreateRequest.schedule?.cron)
            assertThat(entity.scheduleTimezone).isEqualTo(testCreateRequest.schedule?.timezone)
            assertThat(entity.id).isNotNull()
            assertThat(entity.createdAt).isNotNull()
            assertThat(entity.updatedAt).isNotNull()
        }

        @Test
        @DisplayName("should map CreateDatasetRequest with minimal fields")
        fun `should map CreateDatasetRequest with minimal fields`() {
            // Given
            val minimalRequest =
                CreateDatasetRequest(
                    name = "catalog.schema.dataset",
                    owner = "test@example.com",
                    sql = "SELECT 1",
                )

            // When
            val entity = DatasetMapper.toEntity(minimalRequest)

            // Then
            assertThat(entity.name).isEqualTo(minimalRequest.name)
            assertThat(entity.owner).isEqualTo(minimalRequest.owner)
            assertThat(entity.sql).isEqualTo(minimalRequest.sql)
            assertThat(entity.team).isNull()
            assertThat(entity.description).isNull()
            assertThat(entity.tags).isEmpty()
            assertThat(entity.dependencies).isEmpty()
            assertThat(entity.scheduleCron).isNull()
            assertThat(entity.scheduleTimezone).isEqualTo("UTC") // Default value
        }

        @Test
        @DisplayName("should map schedule request when provided")
        fun `should map schedule request when provided`() {
            // Given
            val requestWithSchedule =
                CreateDatasetRequest(
                    name = "catalog.schema.dataset",
                    owner = "test@example.com",
                    sql = "SELECT 1",
                    schedule =
                        ScheduleRequest(
                            cron = "0 12 * * *",
                            timezone = "Europe/London",
                        ),
                )

            // When
            val entity = DatasetMapper.toEntity(requestWithSchedule)

            // Then
            assertThat(entity.scheduleCron).isEqualTo("0 12 * * *")
            assertThat(entity.scheduleTimezone).isEqualTo("Europe/London")
        }

        @Test
        @DisplayName("should use default timezone when schedule timezone is null")
        fun `should use default timezone when schedule timezone is null`() {
            // Given
            val requestWithScheduleNoTimezone =
                CreateDatasetRequest(
                    name = "catalog.schema.dataset",
                    owner = "test@example.com",
                    sql = "SELECT 1",
                    schedule =
                        ScheduleRequest(
                            cron = "0 12 * * *",
                            timezone = "UTC", // Explicit UTC
                        ),
                )

            // When
            val entity = DatasetMapper.toEntity(requestWithScheduleNoTimezone)

            // Then
            assertThat(entity.scheduleTimezone).isEqualTo("UTC")
        }

        @Test
        @DisplayName("should handle empty lists for tags and dependencies")
        fun `should handle empty lists for tags and dependencies`() {
            // Given
            val requestWithEmptyLists =
                CreateDatasetRequest(
                    name = "catalog.schema.dataset",
                    owner = "test@example.com",
                    sql = "SELECT 1",
                    tags = emptyList(),
                    dependencies = emptyList(),
                )

            // When
            val entity = DatasetMapper.toEntity(requestWithEmptyLists)

            // Then
            assertThat(entity.tags).isEmpty()
            assertThat(entity.dependencies).isEmpty()
        }
    }

    @Nested
    @DisplayName("Entity → DTO Mapping")
    inner class EntityToDtoMapping {
        @Test
        @DisplayName("should map DatasetEntity to DatasetDto with all fields")
        fun `should map DatasetEntity to DatasetDto with all fields`() {
            // When
            val dto = DatasetMapper.toDto(testDatasetEntity)

            // Then
            assertThat(dto.name).isEqualTo(testDatasetEntity.name)
            assertThat(dto.type).isEqualTo("Dataset")
            assertThat(dto.owner).isEqualTo(testDatasetEntity.owner)
            assertThat(dto.team).isEqualTo(testDatasetEntity.team)
            assertThat(dto.description).isEqualTo(testDatasetEntity.description)
            assertThat(dto.tags).containsExactlyInAnyOrderElementsOf(testDatasetEntity.tags.sorted())
            assertThat(dto.sql).isEqualTo(testDatasetEntity.sql)
            assertThat(dto.dependencies).containsExactlyInAnyOrderElementsOf(testDatasetEntity.dependencies.sorted())
            assertThat(dto.schedule?.cron).isEqualTo(testDatasetEntity.scheduleCron)
            assertThat(dto.schedule?.timezone).isEqualTo(testDatasetEntity.scheduleTimezone)
            assertThat(dto.createdAt).isEqualTo("2024-01-01T09:00:00Z")
            assertThat(dto.updatedAt).isEqualTo("2024-01-02T10:30:00Z")
        }

        @Test
        @DisplayName("should map DatasetEntity to DatasetListDto excluding sensitive fields")
        fun `should map DatasetEntity to DatasetListDto excluding sensitive fields`() {
            // When
            val listDto = DatasetMapper.toListDto(testDatasetEntity)

            // Then
            assertThat(listDto.name).isEqualTo(testDatasetEntity.name)
            assertThat(listDto.type).isEqualTo("Dataset")
            assertThat(listDto.owner).isEqualTo(testDatasetEntity.owner)
            assertThat(listDto.team).isEqualTo(testDatasetEntity.team)
            assertThat(listDto.description).isEqualTo(testDatasetEntity.description)
            assertThat(listDto.tags).containsExactlyInAnyOrderElementsOf(testDatasetEntity.tags.sorted())
            assertThat(listDto.createdAt).isEqualTo("2024-01-01T09:00:00Z")
            assertThat(listDto.updatedAt).isEqualTo("2024-01-02T10:30:00Z")

            // Note: DatasetListDto doesn't include sql, dependencies, or schedule
        }

        @Test
        @DisplayName("should handle entity with null optional fields")
        fun `should handle entity with null optional fields`() {
            // Given
            val minimalEntity =
                DatasetEntity(
                    name = "catalog.schema.dataset",
                    owner = "test@example.com",
                    sql = "SELECT 1",
                    team = null,
                    description = null,
                    scheduleCron = null,
                    scheduleTimezone = null,
                )

            // When
            val dto = DatasetMapper.toDto(minimalEntity)

            // Then
            assertThat(dto.name).isEqualTo(minimalEntity.name)
            assertThat(dto.owner).isEqualTo(minimalEntity.owner)
            assertThat(dto.sql).isEqualTo(minimalEntity.sql)
            assertThat(dto.team).isNull()
            assertThat(dto.description).isNull()
            assertThat(dto.tags).isEmpty()
            assertThat(dto.dependencies).isEmpty()
            assertThat(dto.schedule).isNull()
        }

        @Test
        @DisplayName("should sort tags and dependencies in DTO")
        fun `should sort tags and dependencies in DTO`() {
            // Given
            val entityWithUnsortedCollections =
                DatasetEntity(
                    name = "catalog.schema.dataset",
                    owner = "test@example.com",
                    sql = "SELECT 1",
                    tags = setOf("zebra", "apple", "banana"),
                    dependencies = setOf("table_z", "table_a", "table_b"),
                )

            // When
            val dto = DatasetMapper.toDto(entityWithUnsortedCollections)

            // Then
            assertThat(dto.tags).containsExactly("apple", "banana", "zebra")
            assertThat(dto.dependencies).containsExactly("table_a", "table_b", "table_z")
        }

        @Test
        @DisplayName("should format timestamps correctly")
        fun `should format timestamps correctly`() {
            // Given
            val specificEntity =
                DatasetEntity(
                    name = "catalog.schema.dataset",
                    owner = "test@example.com",
                    sql = "SELECT 1",
                    createdAt = LocalDateTime.of(2023, 12, 25, 14, 30, 45),
                    updatedAt = LocalDateTime.of(2024, 1, 15, 8, 15, 30),
                )

            // When
            val dto = DatasetMapper.toDto(specificEntity)

            // Then
            assertThat(dto.createdAt).isEqualTo("2023-12-25T14:30:45Z")
            assertThat(dto.updatedAt).isEqualTo("2024-01-15T08:15:30Z")
        }
    }

    @Nested
    @DisplayName("Response DTO Creation")
    inner class ResponseDtoCreation {
        @Test
        @DisplayName("should create registration response with dataset name")
        fun `should create registration response with dataset name`() {
            // Given
            val datasetName = "catalog.schema.new_dataset"

            // When
            val response = DatasetMapper.toRegistrationResponse(datasetName)

            // Then
            assertThat(response.name).isEqualTo(datasetName)
            assertThat(response.message).isEqualTo("Dataset 'catalog.schema.new_dataset' registered successfully")
        }

        @Test
        @DisplayName("should create execution result DTO")
        fun `should create execution result DTO`() {
            // Given
            val rows =
                listOf(
                    mapOf("id" to 1, "name" to "Alice"),
                    mapOf("id" to 2, "name" to "Bob"),
                )
            val durationSeconds = 1.5
            val renderedSql = "SELECT id, name FROM users WHERE active = true"

            // When
            val result = DatasetMapper.toExecutionResult(rows, durationSeconds, renderedSql)

            // Then
            assertThat(result.rows).isEqualTo(rows)
            assertThat(result.rowCount).isEqualTo(2)
            assertThat(result.durationSeconds).isEqualTo(1.5)
            assertThat(result.renderedSql).isEqualTo(renderedSql)
        }

        @Test
        @DisplayName("should create execution result DTO with empty rows")
        fun `should create execution result DTO with empty rows`() {
            // Given
            val emptyRows = emptyList<Map<String, Any>>()
            val durationSeconds = 0.1
            val renderedSql = "SELECT id FROM users WHERE 1=0"

            // When
            val result = DatasetMapper.toExecutionResult(emptyRows, durationSeconds, renderedSql)

            // Then
            assertThat(result.rows).isEmpty()
            assertThat(result.rowCount).isEqualTo(0)
            assertThat(result.durationSeconds).isEqualTo(0.1)
            assertThat(result.renderedSql).isEqualTo(renderedSql)
        }
    }

    @Nested
    @DisplayName("Error Response Creation")
    inner class ErrorResponseCreation {
        @Test
        @DisplayName("should create generic error response")
        fun `should create generic error response`() {
            // Given
            val code = "TEST_ERROR"
            val message = "Test error message"
            val details = mapOf("field" to "value", "count" to 42)

            // When
            val errorResponse = DatasetMapper.toErrorResponse(code, message, details)

            // Then
            assertThat(errorResponse.error.code).isEqualTo(code)
            assertThat(errorResponse.error.message).isEqualTo(message)
            assertThat(errorResponse.error.details).isEqualTo(details)
        }

        @Test
        @DisplayName("should create dataset not found error")
        fun `should create dataset not found error`() {
            // Given
            val datasetName = "catalog.schema.nonexistent"

            // When
            val errorResponse = DatasetMapper.toDatasetNotFoundError(datasetName)

            // Then
            assertThat(errorResponse.error.code).isEqualTo("DATASET_NOT_FOUND")
            assertThat(errorResponse.error.message).isEqualTo("Dataset 'catalog.schema.nonexistent' not found")
            assertThat(errorResponse.error.details["dataset_name"]).isEqualTo(datasetName)
        }

        @Test
        @DisplayName("should create dataset already exists error")
        fun `should create dataset already exists error`() {
            // Given
            val datasetName = "catalog.schema.existing"

            // When
            val errorResponse = DatasetMapper.toDatasetAlreadyExistsError(datasetName)

            // Then
            assertThat(errorResponse.error.code).isEqualTo("DATASET_ALREADY_EXISTS")
            assertThat(errorResponse.error.message).isEqualTo("Dataset 'catalog.schema.existing' already exists")
            assertThat(errorResponse.error.details["dataset_name"]).isEqualTo(datasetName)
        }

        @Test
        @DisplayName("should create execution timeout error")
        fun `should create execution timeout error`() {
            // Given
            val datasetName = "catalog.schema.slow_dataset"
            val timeoutSeconds = 600

            // When
            val errorResponse = DatasetMapper.toDatasetExecutionTimeoutError(datasetName, timeoutSeconds)

            // Then
            assertThat(errorResponse.error.code).isEqualTo("DATASET_EXECUTION_TIMEOUT")
            assertThat(errorResponse.error.message).isEqualTo("Dataset execution timed out after 600 seconds")
            assertThat(errorResponse.error.details["dataset_name"]).isEqualTo(datasetName)
            assertThat(errorResponse.error.details["timeout_seconds"]).isEqualTo(timeoutSeconds)
        }

        @Test
        @DisplayName("should create execution failed error")
        fun `should create execution failed error`() {
            // Given
            val datasetName = "catalog.schema.broken_dataset"
            val sqlError = "Syntax error: unexpected token 'SELECTT'"

            // When
            val errorResponse = DatasetMapper.toDatasetExecutionFailedError(datasetName, sqlError)

            // Then
            assertThat(errorResponse.error.code).isEqualTo("DATASET_EXECUTION_FAILED")
            assertThat(errorResponse.error.message).isEqualTo("Query execution failed")
            assertThat(errorResponse.error.details["dataset_name"]).isEqualTo(datasetName)
            assertThat(errorResponse.error.details["sql_error"]).isEqualTo(sqlError)
        }

        @Test
        @DisplayName("should create invalid parameter error")
        fun `should create invalid parameter error`() {
            // Given
            val parameterName = "limit"
            val value = -1
            val reason = "must be positive"

            // When
            val errorResponse = DatasetMapper.toInvalidParameterError(parameterName, value, reason)

            // Then
            assertThat(errorResponse.error.code).isEqualTo("INVALID_PARAMETER")
            assertThat(errorResponse.error.message).isEqualTo("Invalid limit value: must be positive")
            assertThat(errorResponse.error.details["parameter"]).isEqualTo(parameterName)
            assertThat(errorResponse.error.details["value"]).isEqualTo(value)
        }

        @Test
        @DisplayName("should create invalid dataset name error")
        fun `should create invalid dataset name error`() {
            // Given
            val invalidName = "invalid-name-format"

            // When
            val errorResponse = DatasetMapper.toInvalidDatasetNameError(invalidName)

            // Then
            assertThat(errorResponse.error.code).isEqualTo("INVALID_DATASET_NAME")
            assertThat(errorResponse.error.message).isEqualTo("Dataset name must follow pattern: catalog.schema.name")
            assertThat(errorResponse.error.details["dataset_name"]).isEqualTo(invalidName)
            assertThat(errorResponse.error.details["expected_pattern"]).isEqualTo("[catalog].[schema].[name]")
        }
    }

    @Nested
    @DisplayName("Edge Cases and Data Integrity")
    inner class EdgeCasesAndDataIntegrity {
        @Test
        @DisplayName("should preserve data integrity in round-trip mapping")
        fun `should preserve data integrity in round-trip mapping`() {
            // Given
            val originalRequest =
                CreateDatasetRequest(
                    name = "test.catalog.roundtrip",
                    owner = "roundtrip@example.com",
                    team = "test-team",
                    description = "Round-trip test",
                    sql = "SELECT * FROM test_table",
                    tags = listOf("test", "roundtrip"),
                    dependencies = listOf("test_table"),
                    schedule = ScheduleRequest(cron = "0 10 * * *", timezone = "UTC"),
                )

            // When
            val entity = DatasetMapper.toEntity(originalRequest)
            val dto = DatasetMapper.toDto(entity)

            // Then
            assertThat(dto.name).isEqualTo(originalRequest.name)
            assertThat(dto.owner).isEqualTo(originalRequest.owner)
            assertThat(dto.team).isEqualTo(originalRequest.team)
            assertThat(dto.description).isEqualTo(originalRequest.description)
            assertThat(dto.sql).isEqualTo(originalRequest.sql)
            assertThat(dto.tags).containsExactlyInAnyOrderElementsOf(originalRequest.tags)
            assertThat(dto.dependencies).containsExactlyInAnyOrderElementsOf(originalRequest.dependencies)
            assertThat(dto.schedule?.cron).isEqualTo(originalRequest.schedule?.cron)
            assertThat(dto.schedule?.timezone).isEqualTo(originalRequest.schedule?.timezone)
        }

        @Test
        @DisplayName("should handle special characters in all string fields")
        fun `should handle special characters in all string fields`() {
            // Given
            val requestWithSpecialChars =
                CreateDatasetRequest(
                    name = "test_catalog.test_schema.special_dataset",
                    owner = "test+special@example.com",
                    team = "team-with-dashes_and_underscores",
                    description = "Description with special chars: @#\$%^&*()[]{}|\\:;\"'<>?,./ and unicode: 🚀 ✨",
                    sql = "SELECT * FROM table WHERE field = 'value with ''quotes'' and \\'escapes\\'",
                    tags = listOf("tag-with-dash", "tag_with_underscore", "tag@with@at"),
                    dependencies = listOf("table_name", "schema.table", "catalog.schema.table"),
                )

            // When
            val entity = DatasetMapper.toEntity(requestWithSpecialChars)
            val dto = DatasetMapper.toDto(entity)

            // Then - All special characters should be preserved
            assertThat(dto.name).isEqualTo(requestWithSpecialChars.name)
            assertThat(dto.owner).isEqualTo(requestWithSpecialChars.owner)
            assertThat(dto.team).isEqualTo(requestWithSpecialChars.team)
            assertThat(dto.description).isEqualTo(requestWithSpecialChars.description)
            assertThat(dto.sql).isEqualTo(requestWithSpecialChars.sql)
            assertThat(dto.tags).containsExactlyInAnyOrderElementsOf(requestWithSpecialChars.tags)
            assertThat(dto.dependencies).containsExactlyInAnyOrderElementsOf(requestWithSpecialChars.dependencies)
        }

        @Test
        @DisplayName("should handle extremely long strings")
        fun `should handle extremely long strings`() {
            // Given
            val longSql = "SELECT " + "very_long_column_name, ".repeat(100) + "id FROM table"
            val longDescription = "This is a ".repeat(50) + "very long description"

            val requestWithLongStrings =
                CreateDatasetRequest(
                    name = "catalog.schema.long_dataset",
                    owner = "test@example.com",
                    description = longDescription,
                    sql = longSql,
                )

            // When
            val entity = DatasetMapper.toEntity(requestWithLongStrings)
            val dto = DatasetMapper.toDto(entity)

            // Then
            assertThat(dto.sql).isEqualTo(longSql)
            assertThat(dto.description).isEqualTo(longDescription)
            assertThat(dto.sql?.length ?: 0).isGreaterThan(1000)
            assertThat(dto.description?.length ?: 0).isGreaterThan(500)
        }
    }
}
