package com.dataops.basecamp.controller

import com.dataops.basecamp.common.exception.DatasetAlreadyExistsException
import com.dataops.basecamp.domain.entity.dataset.DatasetEntity
import com.dataops.basecamp.domain.service.DatasetService
import com.dataops.basecamp.dto.dataset.*
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Bean
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.http.MediaType
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.validation.beanvalidation.MethodValidationPostProcessor
import tools.jackson.databind.json.JsonMapper

/**
 * DatasetController REST API Tests
 *
 * Spring Boot 4.x patterns:
 * - @WebMvcTest: Slice test for web layer only (faster than full integration test)
 * - @MockkBean: springmockk 5.0.1 (Spring Boot 4 compatible)
 * - @Import: Include SecurityConfig and GlobalExceptionHandler for proper security and exception handling
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Execution(ExecutionMode.SAME_THREAD)
class DatasetControllerTest {
    /**
     * Test configuration to enable method-level validation for @Min, @Max, @Size annotations
     * on controller method parameters. Required for @WebMvcTest since it doesn't auto-configure this.
     */
    @TestConfiguration
    class ValidationConfig {
        @Bean
        fun methodValidationPostProcessor(): MethodValidationPostProcessor = MethodValidationPostProcessor()
    }

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var jsonMapper: JsonMapper

    @MockkBean(relaxed = true)
    private lateinit var datasetService: DatasetService

    private lateinit var testDatasetEntity: DatasetEntity
    private lateinit var testDatasetDto: DatasetDto
    private lateinit var testDatasetListDto: DatasetListDto
    private lateinit var testExecutionResultDto: ExecutionResultDto

    @BeforeEach
    fun setUp() {
        testDatasetEntity =
            DatasetEntity(
                name = "test_catalog.test_schema.test_dataset",
                owner = "test@example.com",
                team = "data-team",
                description = "Test dataset description",
                sql = "SELECT id, name, created_at FROM users WHERE created_at >= '{{date}}'",
                tags = setOf("test", "user"),
                dependencies = setOf("users"),
                scheduleCron = "0 9 * * *",
                scheduleTimezone = "UTC",
            )

        testDatasetDto =
            DatasetDto(
                name = "test_catalog.test_schema.test_dataset",
                type = "Dataset",
                owner = "test@example.com",
                team = "data-team",
                description = "Test dataset description",
                tags = listOf("test", "user"),
                sql = "SELECT id, name, created_at FROM users WHERE created_at >= '{{date}}'",
                dependencies = listOf("users"),
                schedule = ScheduleDto(cron = "0 9 * * *", timezone = "UTC"),
                createdAt = "2024-01-01T09:00:00Z",
                updatedAt = "2024-01-01T09:00:00Z",
            )

        // List response without SQL and dependencies (for list view)
        testDatasetListDto =
            DatasetListDto(
                name = "test_catalog.test_schema.test_dataset",
                type = "Dataset",
                owner = "test@example.com",
                team = "data-team",
                description = "Test dataset description",
                tags = listOf("test", "user"),
                createdAt = "2024-01-01T09:00:00Z",
                updatedAt = "2024-01-01T09:00:00Z",
            )

        testExecutionResultDto =
            ExecutionResultDto(
                rows =
                    listOf(
                        mapOf("id" to 1, "name" to "Alice", "created_at" to "2024-01-01T09:00:00Z"),
                        mapOf("id" to 2, "name" to "Bob", "created_at" to "2024-01-01T09:00:00Z"),
                    ),
                rowCount = 2,
                durationSeconds = 0.5,
                renderedSql = "SELECT id, name, created_at FROM users WHERE created_at >= '2024-01-01'",
            )
    }

    @Nested
    @DisplayName("GET /api/v1/datasets")
    inner class ListDatasets {
        @Test
        @DisplayName("should return empty list when no datasets exist")
        fun `should return empty list when no datasets exist`() {
            // Given
            val pageable = PageRequest.of(0, 50, Sort.by(Sort.Direction.DESC, "updatedAt"))
            every { datasetService.listDatasets(null, null, null, pageable) } returns PageImpl(emptyList())

            // When & Then
            mockMvc
                .perform(get("/api/v1/datasets"))
                .andExpect(status().isOk)
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0))

            verify(exactly = 1) { datasetService.listDatasets(null, null, null, pageable) }
        }

        @Test
        @DisplayName("should return datasets list")
        fun `should return datasets list`() {
            // Given
            val datasets = listOf(testDatasetEntity)
            val pageable = PageRequest.of(0, 50, Sort.by(Sort.Direction.DESC, "updatedAt"))
            every { datasetService.listDatasets(null, null, null, pageable) } returns PageImpl(datasets)

            // When & Then
            mockMvc
                .perform(get("/api/v1/datasets"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("test_catalog.test_schema.test_dataset"))
                .andExpect(jsonPath("$[0].owner").value("test@example.com"))
                .andExpect(jsonPath("$[0].type").value("Dataset"))

            verify(exactly = 1) { datasetService.listDatasets(null, null, null, pageable) }
        }

        @Test
        @DisplayName("should filter datasets by tag")
        fun `should filter datasets by tag`() {
            // Given
            val tag = "test"
            val datasets = listOf(testDatasetEntity)
            val pageable = PageRequest.of(0, 50, Sort.by(Sort.Direction.DESC, "updatedAt"))
            every { datasetService.listDatasets(tag, null, null, pageable) } returns PageImpl(datasets)

            // When & Then
            mockMvc
                .perform(
                    get("/api/v1/datasets")
                        .param("tag", tag),
                ).andExpect(status().isOk)
                .andExpect(jsonPath("$.length()").value(1))

            verify(exactly = 1) { datasetService.listDatasets(tag, null, null, pageable) }
        }

        @Test
        @DisplayName("should filter datasets by owner")
        fun `should filter datasets by owner`() {
            // Given
            val owner = "test@example.com"
            val datasets = listOf(testDatasetEntity)
            val pageable = PageRequest.of(0, 50, Sort.by(Sort.Direction.DESC, "updatedAt"))
            every { datasetService.listDatasets(null, owner, null, pageable) } returns PageImpl(datasets)

            // When & Then
            mockMvc
                .perform(
                    get("/api/v1/datasets")
                        .param("owner", owner),
                ).andExpect(status().isOk)
                .andExpect(jsonPath("$.length()").value(1))

            verify(exactly = 1) { datasetService.listDatasets(null, owner, null, pageable) }
        }

        @Test
        @DisplayName("should filter datasets by search term")
        fun `should filter datasets by search term`() {
            // Given
            val search = "user"
            val datasets = listOf(testDatasetEntity)
            val pageable = PageRequest.of(0, 50, Sort.by(Sort.Direction.DESC, "updatedAt"))
            every { datasetService.listDatasets(null, null, search, pageable) } returns PageImpl(datasets)

            // When & Then
            mockMvc
                .perform(
                    get("/api/v1/datasets")
                        .param("search", search),
                ).andExpect(status().isOk)
                .andExpect(jsonPath("$.length()").value(1))

            verify(exactly = 1) { datasetService.listDatasets(null, null, search, pageable) }
        }

        @Test
        @DisplayName("should apply limit and offset")
        fun `should apply limit and offset`() {
            // Given
            val limit = 10
            val offset = 5
            val pageable = PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "updatedAt"))
            every { datasetService.listDatasets(null, null, null, pageable) } returns PageImpl(emptyList())

            // When & Then
            mockMvc
                .perform(
                    get("/api/v1/datasets")
                        .param("limit", limit.toString())
                        .param("offset", offset.toString()),
                ).andExpect(status().isOk)

            verify(exactly = 1) { datasetService.listDatasets(null, null, null, pageable) }
        }

        @Test
        @DisplayName("should combine multiple filters")
        fun `should combine multiple filters`() {
            // Given
            val tag = "test"
            val owner = "test@example.com"
            val search = "user"
            val limit = 25
            val offset = 10
            val datasets = listOf(testDatasetEntity)
            val pageable = PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "updatedAt"))
            every { datasetService.listDatasets(tag, owner, search, pageable) } returns PageImpl(datasets)

            // When & Then
            mockMvc
                .perform(
                    get("/api/v1/datasets")
                        .param("tag", tag)
                        .param("owner", owner)
                        .param("search", search)
                        .param("limit", limit.toString())
                        .param("offset", offset.toString()),
                ).andExpect(status().isOk)
                .andExpect(jsonPath("$.length()").value(1))

            verify(exactly = 1) { datasetService.listDatasets(tag, owner, search, pageable) }
        }

        @Test
        @DisplayName("should return 400 when limit exceeds maximum")
        fun `should return 400 when limit exceeds maximum`() {
            // When & Then
            mockMvc
                .perform(
                    get("/api/v1/datasets")
                        .param("limit", "501"), // Exceeds max 500
                ).andExpect(status().isBadRequest)
        }

        @Test
        @DisplayName("should return 400 when offset is negative")
        fun `should return 400 when offset is negative`() {
            // When & Then
            mockMvc
                .perform(
                    get("/api/v1/datasets")
                        .param("offset", "-1"),
                ).andExpect(status().isBadRequest)
        }
    }

    @Nested
    @DisplayName("GET /api/v1/datasets/{name}")
    inner class GetDataset {
        @Test
        @DisplayName("should return dataset by name")
        fun `should return dataset by name`() {
            // Given
            val name = "test_catalog.test_schema.test_dataset"
            every { datasetService.getDataset(name) } returns testDatasetEntity

            // When & Then
            mockMvc
                .perform(get("/api/v1/datasets/$name"))
                .andExpect(status().isOk)
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.name").value(name))
                .andExpect(jsonPath("$.owner").value("test@example.com"))
                .andExpect(jsonPath("$.sql").exists())
                .andExpect(jsonPath("$.dependencies").isArray())
                .andExpect(jsonPath("$.schedule.cron").value("0 9 * * *"))

            verify(exactly = 1) { datasetService.getDataset(name) }
        }

        @Test
        @DisplayName("should return 404 when dataset not found")
        fun `should return 404 when dataset not found`() {
            // Given
            val name = "nonexistent_catalog.schema.dataset"
            every { datasetService.getDataset(name) } returns null

            // When & Then
            mockMvc
                .perform(get("/api/v1/datasets/$name"))
                .andExpect(status().isNotFound)

            verify(exactly = 1) { datasetService.getDataset(name) }
        }
    }

    @Nested
    @DisplayName("POST /api/v1/datasets")
    inner class RegisterDataset {
        @Test
        @DisplayName("should register dataset successfully")
        fun `should register dataset successfully`() {
            // Given
            val request =
                CreateDatasetRequest(
                    name = "new_catalog.new_schema.new_dataset",
                    owner = "new@example.com",
                    sql = "SELECT * FROM new_table",
                    description = "New dataset",
                    tags = listOf("new"),
                    schedule = ScheduleRequest(cron = "0 8 * * *"),
                )

            every { datasetService.registerDataset(any()) } returns testDatasetEntity

            // When & Then
            mockMvc
                .perform(
                    post("/api/v1/datasets")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)),
                ).andExpect(status().isCreated)
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.name").value(testDatasetEntity.name))
                .andExpect(jsonPath("$.message").exists())

            verify(exactly = 1) { datasetService.registerDataset(any()) }
        }

        @Test
        @DisplayName("should return 400 for invalid request - missing name")
        fun `should return 400 for invalid request - missing name`() {
            // Given
            val invalidRequest =
                mapOf(
                    "owner" to "test@example.com",
                    "sql" to "SELECT 1",
                )

            // When & Then
            mockMvc
                .perform(
                    post("/api/v1/datasets")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(invalidRequest)),
                ).andExpect(status().isBadRequest)
        }

        @Test
        @DisplayName("should return 400 for invalid dataset name format")
        fun `should return 400 for invalid dataset name format`() {
            // Given
            val invalidRequest =
                mapOf(
                    "name" to "invalid-name-without-dots",
                    "owner" to "test@example.com",
                    "sql" to "SELECT 1",
                )

            // When & Then
            mockMvc
                .perform(
                    post("/api/v1/datasets")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(invalidRequest)),
                ).andExpect(status().isBadRequest)
        }

        @Test
        @DisplayName("should return 400 for invalid email format")
        fun `should return 400 for invalid email format`() {
            // Given
            val invalidRequest =
                mapOf(
                    "name" to "catalog.schema.dataset",
                    "owner" to "not-an-email",
                    "sql" to "SELECT 1",
                )

            // When & Then
            mockMvc
                .perform(
                    post("/api/v1/datasets")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(invalidRequest)),
                ).andExpect(status().isBadRequest)
        }

        @Test
        @DisplayName("should return 400 when tags exceed maximum limit")
        fun `should return 400 when tags exceed maximum limit`() {
            // Given
            val tooManyTags = (1..15).map { "tag$it" } // 15 tags, limit is 10
            val invalidRequest =
                mapOf(
                    "name" to "catalog.schema.dataset",
                    "owner" to "test@example.com",
                    "sql" to "SELECT 1",
                    "tags" to tooManyTags,
                )

            // When & Then
            mockMvc
                .perform(
                    post("/api/v1/datasets")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(invalidRequest)),
                ).andExpect(status().isBadRequest)
        }

        @Test
        @DisplayName("should return 400 when description exceeds maximum length")
        fun `should return 400 when description exceeds maximum length`() {
            // Given
            val tooLongDescription = "x".repeat(1001) // Description > 1000 chars
            val invalidRequest =
                mapOf(
                    "name" to "catalog.schema.dataset",
                    "owner" to "test@example.com",
                    "sql" to "SELECT 1",
                    "description" to tooLongDescription,
                )

            // When & Then
            mockMvc
                .perform(
                    post("/api/v1/datasets")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(invalidRequest)),
                ).andExpect(status().isBadRequest)
        }

        @Test
        @DisplayName("should return 400 when team name exceeds maximum length")
        fun `should return 400 when team name exceeds maximum length`() {
            // Given
            val tooLongTeam = "x".repeat(101) // Team > 100 chars
            val invalidRequest =
                mapOf(
                    "name" to "catalog.schema.dataset",
                    "owner" to "test@example.com",
                    "sql" to "SELECT 1",
                    "team" to tooLongTeam,
                )

            // When & Then
            mockMvc
                .perform(
                    post("/api/v1/datasets")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(invalidRequest)),
                ).andExpect(status().isBadRequest)
        }

        @Test
        @DisplayName("should return 409 when dataset already exists")
        fun `should return 409 when dataset already exists`() {
            // Given
            val request =
                CreateDatasetRequest(
                    name = "existing_catalog.schema.dataset",
                    owner = "test@example.com",
                    sql = "SELECT 1",
                )

            every { datasetService.registerDataset(any()) } throws DatasetAlreadyExistsException(request.name)

            // When & Then
            mockMvc
                .perform(
                    post("/api/v1/datasets")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)),
                ).andExpect(status().isConflict)

            verify(exactly = 1) { datasetService.registerDataset(any()) }
        }
    }

    @Nested
    @DisplayName("POST /api/v1/datasets/{name}/run")
    inner class ExecuteDataset {
        @Test
        @DisplayName("should execute dataset successfully")
        fun `should execute dataset successfully`() {
            // Given
            val name = "test_catalog.test_schema.test_dataset"
            val request =
                ExecuteDatasetRequest(
                    parameters = mapOf("date" to "2024-01-01"),
                    limit = 100,
                    timeout = 60,
                )

            every { datasetService.getDataset(name) } returns testDatasetEntity

            // When & Then
            mockMvc
                .perform(
                    post("/api/v1/datasets/$name/run")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)),
                ).andExpect(status().isOk)
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.rows").isArray())
                .andExpect(jsonPath("$.rowCount").isNumber())
                .andExpect(jsonPath("$.renderedSql").exists())

            verify(exactly = 1) { datasetService.getDataset(name) }
        }

        @Test
        @DisplayName("should return 404 when executing non-existent dataset")
        fun `should return 404 when executing non-existent dataset`() {
            // Given
            val name = "nonexistent_catalog.schema.dataset"
            val request = ExecuteDatasetRequest()

            every { datasetService.getDataset(name) } returns null

            // When & Then
            mockMvc
                .perform(
                    post("/api/v1/datasets/$name/run")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)),
                ).andExpect(status().isNotFound)

            verify(exactly = 1) { datasetService.getDataset(name) }
        }

        @Test
        @DisplayName("should return 400 when limit is less than 1")
        fun `should return 400 when limit is less than 1`() {
            // Given
            val name = "test_catalog.test_schema.test_dataset"
            val invalidRequest =
                mapOf(
                    "parameters" to emptyMap<String, Any>(),
                    "limit" to 0, // Invalid - must be at least 1
                )

            // When & Then
            mockMvc
                .perform(
                    post("/api/v1/datasets/$name/run")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(invalidRequest)),
                ).andExpect(status().isBadRequest)
        }

        @Test
        @DisplayName("should return 400 when limit exceeds maximum")
        fun `should return 400 when limit exceeds maximum`() {
            // Given
            val name = "test_catalog.test_schema.test_dataset"
            val invalidRequest =
                mapOf(
                    "parameters" to emptyMap<String, Any>(),
                    "limit" to 10001, // Invalid - must not exceed 10000
                )

            // When & Then
            mockMvc
                .perform(
                    post("/api/v1/datasets/$name/run")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(invalidRequest)),
                ).andExpect(status().isBadRequest)
        }

        @Test
        @DisplayName("should return 400 when timeout is less than 1")
        fun `should return 400 when timeout is less than 1`() {
            // Given
            val name = "test_catalog.test_schema.test_dataset"
            val invalidRequest =
                mapOf(
                    "parameters" to emptyMap<String, Any>(),
                    "timeout" to 0, // Invalid - must be at least 1
                )

            // When & Then
            mockMvc
                .perform(
                    post("/api/v1/datasets/$name/run")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(invalidRequest)),
                ).andExpect(status().isBadRequest)
        }

        @Test
        @DisplayName("should return 400 when timeout exceeds maximum")
        fun `should return 400 when timeout exceeds maximum`() {
            // Given
            val name = "test_catalog.test_schema.test_dataset"
            val invalidRequest =
                mapOf(
                    "parameters" to emptyMap<String, Any>(),
                    "timeout" to 3601, // Invalid - must not exceed 3600
                )

            // When & Then
            mockMvc
                .perform(
                    post("/api/v1/datasets/$name/run")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(invalidRequest)),
                ).andExpect(status().isBadRequest)
        }

        @Test
        @DisplayName("should execute with default parameters when request body is empty")
        fun `should execute with default parameters when request body is empty`() {
            // Given
            val name = "test_catalog.test_schema.test_dataset"
            val request = ExecuteDatasetRequest()

            every { datasetService.getDataset(name) } returns testDatasetEntity

            // When & Then
            mockMvc
                .perform(
                    post("/api/v1/datasets/$name/run")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)),
                ).andExpect(status().isOk)

            verify(exactly = 1) { datasetService.getDataset(name) }
        }
    }

    @Nested
    @DisplayName("Integration Tests")
    inner class IntegrationTests {
        @Test
        @DisplayName("should handle complete register-get-run flow")
        fun `should handle complete register-get-run flow`() {
            // Given - Setup for register
            val registerRequest =
                CreateDatasetRequest(
                    name = "integration_catalog.schema.dataset",
                    owner = "integration@example.com",
                    sql = "SELECT COUNT(*) FROM test_table WHERE date = '{{date}}'",
                    description = "Integration test dataset",
                )

            val registeredDataset =
                DatasetEntity(
                    name = registerRequest.name,
                    owner = registerRequest.owner,
                    sql = registerRequest.sql,
                    description = registerRequest.description,
                )

            every { datasetService.registerDataset(any()) } returns registeredDataset

            // When & Then - Register
            mockMvc
                .perform(
                    post("/api/v1/datasets")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(registerRequest)),
                ).andExpect(status().isCreated)
                .andExpect(jsonPath("$.name").value(registerRequest.name))

            // Given - Setup for get
            every { datasetService.getDataset(registerRequest.name) } returns registeredDataset

            // When & Then - Get
            mockMvc
                .perform(get("/api/v1/datasets/${registerRequest.name}"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.name").value(registerRequest.name))

            // Given - Setup for run
            val runRequest =
                ExecuteDatasetRequest(
                    parameters = mapOf("date" to "2024-01-01"),
                )

            // When & Then - Run
            mockMvc
                .perform(
                    post("/api/v1/datasets/${registerRequest.name}/run")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(runRequest)),
                ).andExpect(status().isOk)

            // Verify all calls
            verify(exactly = 1) { datasetService.registerDataset(any()) }
            verify(exactly = 2) { datasetService.getDataset(registerRequest.name) } // Called in get and run
        }
    }
}
