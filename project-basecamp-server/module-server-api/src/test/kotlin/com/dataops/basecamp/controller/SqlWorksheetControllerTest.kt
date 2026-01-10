package com.dataops.basecamp.controller

import com.dataops.basecamp.common.enums.SqlDialect
import com.dataops.basecamp.common.exception.SqlWorksheetAlreadyExistsException
import com.dataops.basecamp.common.exception.WorksheetFolderNotFoundException
import com.dataops.basecamp.domain.entity.sql.SqlWorksheetEntity
import com.dataops.basecamp.domain.entity.sql.WorksheetFolderEntity
import com.dataops.basecamp.domain.service.SqlWorksheetService
import com.dataops.basecamp.domain.service.WorksheetFolderService
import com.dataops.basecamp.dto.sql.CreateSqlWorksheetRequest
import com.dataops.basecamp.dto.sql.UpdateSqlWorksheetRequest
import com.ninjasquad.springmockk.MockkBean
import io.mockk.clearMocks
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.validation.beanvalidation.MethodValidationPostProcessor
import tools.jackson.databind.json.JsonMapper
import java.time.LocalDateTime

/**
 * SQL Worksheet Controller REST API Tests
 *
 * Tests for SQL Worksheet endpoints within TeamSqlController.
 *
 * Spring Boot 4.x patterns:
 * - @SpringBootTest: Full context for proper security and exception handling
 * - @MockkBean: springmockk 5.0.1 (Spring Boot 4 compatible)
 * - @AutoConfigureMockMvc: Auto-configure MockMvc
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Execution(ExecutionMode.SAME_THREAD)
@DisplayName("SQL Worksheet Controller Tests")
class SqlWorksheetControllerTest {
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
    private lateinit var worksheetFolderService: WorksheetFolderService

    @MockkBean(relaxed = true)
    private lateinit var sqlWorksheetService: SqlWorksheetService

    private val teamId = 1L
    private val folderId = 100L
    private val worksheetId = 1000L

    private lateinit var testFolder: WorksheetFolderEntity
    private lateinit var testWorksheet: SqlWorksheetEntity

    @BeforeEach
    fun setUp() {
        // Clear mocks between tests to avoid mock pollution
        clearMocks(worksheetFolderService, sqlWorksheetService)

        testFolder =
            WorksheetFolderEntity(
                teamId = teamId,
                name = "test-folder",
                description = "Test folder description",
                displayOrder = 0,
            ).apply {
                val idField = this::class.java.superclass.getDeclaredField("id")
                idField.isAccessible = true
                idField.set(this, folderId)

                val createdAtField = this::class.java.superclass.getDeclaredField("createdAt")
                createdAtField.isAccessible = true
                createdAtField.set(this, LocalDateTime.of(2024, 1, 1, 9, 0, 0))

                val updatedAtField = this::class.java.superclass.getDeclaredField("updatedAt")
                updatedAtField.isAccessible = true
                updatedAtField.set(this, LocalDateTime.of(2024, 1, 1, 10, 0, 0))
            }

        testWorksheet =
            SqlWorksheetEntity(
                folderId = folderId,
                name = "test-worksheet",
                description = "Test worksheet description",
                sqlText = "SELECT * FROM users",
                dialect = SqlDialect.BIGQUERY,
            ).apply {
                val idField = this::class.java.superclass.getDeclaredField("id")
                idField.isAccessible = true
                idField.set(this, worksheetId)

                val createdAtField = this::class.java.superclass.getDeclaredField("createdAt")
                createdAtField.isAccessible = true
                createdAtField.set(this, LocalDateTime.of(2024, 1, 1, 9, 0, 0))

                val updatedAtField = this::class.java.superclass.getDeclaredField("updatedAt")
                updatedAtField.isAccessible = true
                updatedAtField.set(this, LocalDateTime.of(2024, 1, 1, 10, 0, 0))
            }
    }

    @Nested
    @DisplayName("GET /api/v1/teams/{teamId}/sql/worksheets")
    inner class ListSqlWorksheets {
        @Test
        @DisplayName("should return empty list when no worksheets exist")
        fun `should return empty list when no worksheets exist`() {
            // Given
            val pageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "updatedAt"))
            val emptyPage = PageImpl<SqlWorksheetEntity>(emptyList(), pageable, 0)

            every {
                sqlWorksheetService.listWorksheets(
                    projectId = teamId,
                    folderId = null,
                    searchText = null,
                    starred = null,
                    dialect = null,
                    pageable = any(),
                )
            } returns emptyPage

            // When & Then
            mockMvc
                .perform(get("/api/v1/teams/$teamId/sql/worksheets"))
                .andExpect(status().isOk)
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content.length()").value(0))
                .andExpect(jsonPath("$.totalElements").value(0))
        }

        @Test
        @DisplayName("should return worksheets list with pagination")
        fun `should return worksheets list with pagination`() {
            // Given
            val worksheet1 = createTestWorksheet(1001L, folderId, "worksheet-1")
            val worksheet2 = createTestWorksheet(1002L, folderId, "worksheet-2")
            val worksheets = listOf(worksheet1, worksheet2)
            val pageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "updatedAt"))
            val page = PageImpl(worksheets, pageable, 2)

            every {
                sqlWorksheetService.listWorksheets(
                    projectId = teamId,
                    folderId = null,
                    searchText = null,
                    starred = null,
                    dialect = null,
                    pageable = any(),
                )
            } returns page

            every { worksheetFolderService.getFolderById(teamId, folderId) } returns testFolder

            // When & Then
            mockMvc
                .perform(get("/api/v1/teams/$teamId/sql/worksheets"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].name").value("worksheet-1"))
                .andExpect(jsonPath("$.content[1].name").value("worksheet-2"))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
        }

        @Test
        @DisplayName("should filter worksheets by folderId")
        fun `should filter worksheets by folderId`() {
            // Given
            val worksheet1 = createTestWorksheet(1001L, folderId, "worksheet-in-folder")
            val worksheets = listOf(worksheet1)
            val pageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "updatedAt"))
            val page = PageImpl(worksheets, pageable, 1)

            every {
                sqlWorksheetService.listWorksheets(
                    projectId = teamId,
                    folderId = folderId,
                    searchText = null,
                    starred = null,
                    dialect = null,
                    pageable = any(),
                )
            } returns page

            every { worksheetFolderService.getFolderById(teamId, folderId) } returns testFolder

            // When & Then
            mockMvc
                .perform(
                    get("/api/v1/teams/$teamId/sql/worksheets")
                        .param("folderId", folderId.toString()),
                ).andExpect(status().isOk)
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].folderId").value(folderId))

            verify(exactly = 1) {
                sqlWorksheetService.listWorksheets(
                    projectId = teamId,
                    folderId = folderId,
                    searchText = null,
                    starred = null,
                    dialect = null,
                    pageable = any(),
                )
            }
        }

        @Test
        @DisplayName("should filter worksheets by searchText")
        fun `should filter worksheets by searchText`() {
            // Given
            val searchText = "user"
            val worksheet1 = createTestWorksheet(1001L, folderId, "user-query")
            val worksheets = listOf(worksheet1)
            val pageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "updatedAt"))
            val page = PageImpl(worksheets, pageable, 1)

            every {
                sqlWorksheetService.listWorksheets(
                    projectId = teamId,
                    folderId = null,
                    searchText = searchText,
                    starred = null,
                    dialect = null,
                    pageable = any(),
                )
            } returns page

            every { worksheetFolderService.getFolderById(teamId, folderId) } returns testFolder

            // When & Then
            mockMvc
                .perform(
                    get("/api/v1/teams/$teamId/sql/worksheets")
                        .param("searchText", searchText),
                ).andExpect(status().isOk)
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].name").value("user-query"))

            verify(exactly = 1) {
                sqlWorksheetService.listWorksheets(
                    projectId = teamId,
                    folderId = null,
                    searchText = searchText,
                    starred = null,
                    dialect = null,
                    pageable = any(),
                )
            }
        }

        @Test
        @DisplayName("should filter worksheets by starred")
        fun `should filter worksheets by starred`() {
            // Given
            val worksheet1 =
                createTestWorksheet(1001L, folderId, "starred-worksheet").apply {
                    this.isStarred = true
                }
            val worksheets = listOf(worksheet1)
            val pageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "updatedAt"))
            val page = PageImpl(worksheets, pageable, 1)

            every {
                sqlWorksheetService.listWorksheets(
                    projectId = teamId,
                    folderId = null,
                    searchText = null,
                    starred = true,
                    dialect = null,
                    pageable = any(),
                )
            } returns page

            every { worksheetFolderService.getFolderById(teamId, folderId) } returns testFolder

            // When & Then
            mockMvc
                .perform(
                    get("/api/v1/teams/$teamId/sql/worksheets")
                        .param("starred", "true"),
                ).andExpect(status().isOk)
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].isStarred").value(true))

            verify(exactly = 1) {
                sqlWorksheetService.listWorksheets(
                    projectId = teamId,
                    folderId = null,
                    searchText = null,
                    starred = true,
                    dialect = null,
                    pageable = any(),
                )
            }
        }

        @Test
        @DisplayName("should filter worksheets by dialect")
        fun `should filter worksheets by dialect`() {
            // Given
            val worksheet1 =
                createTestWorksheet(1001L, folderId, "trino-worksheet").apply {
                    this.dialect = SqlDialect.TRINO
                }
            val worksheets = listOf(worksheet1)
            val pageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "updatedAt"))
            val page = PageImpl(worksheets, pageable, 1)

            every {
                sqlWorksheetService.listWorksheets(
                    projectId = teamId,
                    folderId = null,
                    searchText = null,
                    starred = null,
                    dialect = SqlDialect.TRINO,
                    pageable = any(),
                )
            } returns page

            every { worksheetFolderService.getFolderById(teamId, folderId) } returns testFolder

            // When & Then
            mockMvc
                .perform(
                    get("/api/v1/teams/$teamId/sql/worksheets")
                        .param("dialect", "TRINO"),
                ).andExpect(status().isOk)
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].dialect").value("TRINO"))

            verify(exactly = 1) {
                sqlWorksheetService.listWorksheets(
                    projectId = teamId,
                    folderId = null,
                    searchText = null,
                    starred = null,
                    dialect = SqlDialect.TRINO,
                    pageable = any(),
                )
            }
        }

        @Test
        @DisplayName("should handle pagination parameters")
        fun `should handle pagination parameters`() {
            // Given
            val worksheet1 = createTestWorksheet(1003L, folderId, "worksheet-page2")
            val worksheets = listOf(worksheet1)
            val pageable = PageRequest.of(1, 10, Sort.by(Sort.Direction.DESC, "updatedAt"))
            val page = PageImpl(worksheets, pageable, 25) // 25 total elements

            every {
                sqlWorksheetService.listWorksheets(
                    projectId = teamId,
                    folderId = null,
                    searchText = null,
                    starred = null,
                    dialect = null,
                    pageable = any(),
                )
            } returns page

            every { worksheetFolderService.getFolderById(teamId, folderId) } returns testFolder

            // When & Then
            mockMvc
                .perform(
                    get("/api/v1/teams/$teamId/sql/worksheets")
                        .param("page", "1")
                        .param("size", "10"),
                ).andExpect(status().isOk)
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(10))
                .andExpect(jsonPath("$.totalElements").isNumber())
                .andExpect(jsonPath("$.totalPages").isNumber())
        }
    }

    @Nested
    @DisplayName("POST /api/v1/teams/{teamId}/sql/worksheets")
    inner class CreateSqlWorksheet {
        @Test
        @DisplayName("should create worksheet successfully")
        fun `should create worksheet successfully`() {
            // Given
            val request =
                CreateSqlWorksheetRequest(
                    folderId = folderId,
                    name = "new-worksheet",
                    description = "New worksheet description",
                    sqlText = "SELECT id, name FROM users",
                    dialect = SqlDialect.TRINO,
                )

            val savedWorksheet =
                createTestWorksheet(
                    id = 1003L,
                    folderId = folderId,
                    name = request.name,
                ).apply {
                    this.description = request.description
                    this.sqlText = request.sqlText
                    this.dialect = request.dialect
                }

            every {
                sqlWorksheetService.createWorksheet(
                    projectId = teamId,
                    folderId = request.folderId,
                    name = request.name,
                    description = request.description,
                    sqlText = request.sqlText,
                    dialect = request.dialect,
                )
            } returns savedWorksheet

            // When & Then
            mockMvc
                .perform(
                    post("/api/v1/teams/$teamId/sql/worksheets")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)),
                ).andExpect(status().isCreated)
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(1003))
                .andExpect(jsonPath("$.folderId").value(folderId))
                .andExpect(jsonPath("$.folderName").exists())
                .andExpect(jsonPath("$.name").value("new-worksheet"))
                .andExpect(jsonPath("$.description").value("New worksheet description"))
                .andExpect(jsonPath("$.sqlText").value("SELECT id, name FROM users"))
                .andExpect(jsonPath("$.dialect").value("TRINO"))

            verify(exactly = 1) {
                sqlWorksheetService.createWorksheet(
                    projectId = teamId,
                    folderId = request.folderId,
                    name = request.name,
                    description = request.description,
                    sqlText = request.sqlText,
                    dialect = request.dialect,
                )
            }
        }

        @Test
        @DisplayName("should create worksheet without description")
        fun `should create worksheet without description`() {
            // Given
            val request =
                CreateSqlWorksheetRequest(
                    folderId = folderId,
                    name = "worksheet-no-desc",
                    sqlText = "SELECT 1",
                )

            val savedWorksheet =
                createTestWorksheet(
                    id = 1004L,
                    folderId = folderId,
                    name = request.name,
                ).apply {
                    this.description = null
                    this.sqlText = request.sqlText
                }

            every {
                sqlWorksheetService.createWorksheet(
                    projectId = teamId,
                    folderId = request.folderId,
                    name = request.name,
                    description = null,
                    sqlText = request.sqlText,
                    dialect = SqlDialect.BIGQUERY,
                )
            } returns savedWorksheet

            // When & Then
            mockMvc
                .perform(
                    post("/api/v1/teams/$teamId/sql/worksheets")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)),
                ).andExpect(status().isCreated)
                .andExpect(jsonPath("$.name").value("worksheet-no-desc"))
                .andExpect(jsonPath("$.description").doesNotExist())
        }

        @Test
        @DisplayName("should return 400 for missing name")
        fun `should return 400 for missing name`() {
            // Given
            val invalidRequest =
                mapOf(
                    "folderId" to folderId,
                    "sqlText" to "SELECT 1",
                )

            // When & Then
            mockMvc
                .perform(
                    post("/api/v1/teams/$teamId/sql/worksheets")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(invalidRequest)),
                ).andExpect(status().isBadRequest)
        }

        @Test
        @DisplayName("should return 400 for blank name")
        fun `should return 400 for blank name`() {
            // Given
            val invalidRequest =
                mapOf(
                    "folderId" to folderId,
                    "name" to "",
                    "sqlText" to "SELECT 1",
                )

            // When & Then
            mockMvc
                .perform(
                    post("/api/v1/teams/$teamId/sql/worksheets")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(invalidRequest)),
                ).andExpect(status().isBadRequest)
        }

        @Test
        @DisplayName("should return 400 for missing sqlText")
        fun `should return 400 for missing sqlText`() {
            // Given
            val invalidRequest =
                mapOf(
                    "folderId" to folderId,
                    "name" to "valid-name",
                )

            // When & Then
            mockMvc
                .perform(
                    post("/api/v1/teams/$teamId/sql/worksheets")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(invalidRequest)),
                ).andExpect(status().isBadRequest)
        }

        @Test
        @DisplayName("should return 400 for blank sqlText")
        fun `should return 400 for blank sqlText`() {
            // Given
            val invalidRequest =
                mapOf(
                    "folderId" to folderId,
                    "name" to "valid-name",
                    "sqlText" to "",
                )

            // When & Then
            mockMvc
                .perform(
                    post("/api/v1/teams/$teamId/sql/worksheets")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(invalidRequest)),
                ).andExpect(status().isBadRequest)
        }

        @Test
        @DisplayName("should return 400 when name exceeds maximum length")
        fun `should return 400 when name exceeds maximum length`() {
            // Given
            val tooLongName = "a".repeat(201) // Name > 200 chars
            val invalidRequest =
                mapOf(
                    "folderId" to folderId,
                    "name" to tooLongName,
                    "sqlText" to "SELECT 1",
                )

            // When & Then
            mockMvc
                .perform(
                    post("/api/v1/teams/$teamId/sql/worksheets")
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
                    "folderId" to folderId,
                    "name" to "valid-name",
                    "sqlText" to "SELECT 1",
                    "description" to tooLongDescription,
                )

            // When & Then
            mockMvc
                .perform(
                    post("/api/v1/teams/$teamId/sql/worksheets")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(invalidRequest)),
                ).andExpect(status().isBadRequest)
        }

        @Test
        @DisplayName("should return 404 when folder not found")
        fun `should return 404 when folder not found`() {
            // Given
            val nonExistentFolderId = 999L
            val request =
                CreateSqlWorksheetRequest(
                    folderId = nonExistentFolderId,
                    name = "new-worksheet",
                    sqlText = "SELECT 1",
                )

            every {
                sqlWorksheetService.createWorksheet(
                    projectId = teamId,
                    folderId = nonExistentFolderId,
                    name = request.name,
                    description = null,
                    sqlText = request.sqlText,
                    dialect = SqlDialect.BIGQUERY,
                )
            } throws WorksheetFolderNotFoundException(nonExistentFolderId, teamId)

            // When & Then
            mockMvc
                .perform(
                    post("/api/v1/teams/$teamId/sql/worksheets")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)),
                ).andExpect(status().isNotFound)
        }

        @Test
        @DisplayName("should return 409 when worksheet name already exists")
        fun `should return 409 when worksheet name already exists`() {
            // Given
            val request =
                CreateSqlWorksheetRequest(
                    folderId = folderId,
                    name = "existing-worksheet",
                    sqlText = "SELECT 1",
                )

            every {
                sqlWorksheetService.createWorksheet(
                    projectId = teamId,
                    folderId = request.folderId,
                    name = request.name,
                    description = null,
                    sqlText = request.sqlText,
                    dialect = SqlDialect.BIGQUERY,
                )
            } throws SqlWorksheetAlreadyExistsException(request.name, folderId)

            // When & Then
            mockMvc
                .perform(
                    post("/api/v1/teams/$teamId/sql/worksheets")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)),
                ).andExpect(status().isConflict)

            verify(exactly = 1) {
                sqlWorksheetService.createWorksheet(
                    projectId = teamId,
                    folderId = request.folderId,
                    name = request.name,
                    description = null,
                    sqlText = request.sqlText,
                    dialect = SqlDialect.BIGQUERY,
                )
            }
        }
    }

    @Nested
    @DisplayName("GET /api/v1/teams/{teamId}/sql/worksheets/{worksheetId}")
    inner class GetSqlWorksheet {
        @Test
        @DisplayName("should return worksheet by id")
        fun `should return worksheet by id`() {
            // Given - Controller now uses findWorksheetById directly
            every { sqlWorksheetService.findWorksheetById(worksheetId) } returns testWorksheet

            // When & Then
            mockMvc
                .perform(get("/api/v1/teams/$teamId/sql/worksheets/$worksheetId"))
                .andExpect(status().isOk)
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(worksheetId))
                .andExpect(jsonPath("$.folderId").value(folderId))
                .andExpect(jsonPath("$.folderName").exists())
                .andExpect(jsonPath("$.name").value("test-worksheet"))
                .andExpect(jsonPath("$.description").value("Test worksheet description"))
                .andExpect(jsonPath("$.sqlText").value("SELECT * FROM users"))
                .andExpect(jsonPath("$.dialect").value("BIGQUERY"))
                .andExpect(jsonPath("$.isStarred").value(false))
                .andExpect(jsonPath("$.runCount").value(0))
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.updatedAt").exists())

            verify(exactly = 1) { sqlWorksheetService.findWorksheetById(worksheetId) }
        }

        @Test
        @DisplayName("should return 404 when worksheet not found")
        fun `should return 404 when worksheet not found`() {
            // Given
            val nonExistentWorksheetId = 9999L

            every { sqlWorksheetService.findWorksheetById(nonExistentWorksheetId) } returns null

            // When & Then
            mockMvc
                .perform(get("/api/v1/teams/$teamId/sql/worksheets/$nonExistentWorksheetId"))
                .andExpect(status().isNotFound)

            verify(exactly = 1) { sqlWorksheetService.findWorksheetById(nonExistentWorksheetId) }
        }

        @Test
        @DisplayName("should return 404 when worksheet not found (previously team has no folders)")
        fun `should return 404 when worksheet not found (previously team has no folders)`() {
            // Given - Now worksheet lookup is direct, so this tests same scenario as "not found"
            every { sqlWorksheetService.findWorksheetById(worksheetId) } returns null

            // When & Then
            mockMvc
                .perform(get("/api/v1/teams/$teamId/sql/worksheets/$worksheetId"))
                .andExpect(status().isNotFound)

            verify(exactly = 1) { sqlWorksheetService.findWorksheetById(worksheetId) }
        }
    }

    @Nested
    @DisplayName("PUT /api/v1/teams/{teamId}/sql/worksheets/{worksheetId}")
    inner class UpdateSqlWorksheet {
        @Test
        @DisplayName("should update worksheet successfully")
        fun `should update worksheet successfully`() {
            // Given
            val request =
                UpdateSqlWorksheetRequest(
                    name = "updated-worksheet",
                    description = "Updated description",
                    sqlText = "SELECT id, name, email FROM users",
                    dialect = SqlDialect.TRINO,
                )

            val updatedWorksheet =
                createTestWorksheet(worksheetId, folderId, request.name!!).apply {
                    this.description = request.description
                    this.sqlText = request.sqlText!!
                    this.dialect = request.dialect!!
                }

            // Controller now uses findWorksheetById directly
            every { sqlWorksheetService.findWorksheetById(worksheetId) } returns testWorksheet
            every {
                sqlWorksheetService.updateWorksheet(
                    projectId = teamId,
                    folderId = folderId,
                    worksheetId = worksheetId,
                    name = request.name,
                    description = request.description,
                    sqlText = request.sqlText,
                    dialect = request.dialect,
                )
            } returns updatedWorksheet

            // When & Then
            mockMvc
                .perform(
                    put("/api/v1/teams/$teamId/sql/worksheets/$worksheetId")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)),
                ).andExpect(status().isOk)
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(worksheetId))
                .andExpect(jsonPath("$.name").value("updated-worksheet"))
                .andExpect(jsonPath("$.description").value("Updated description"))
                .andExpect(jsonPath("$.sqlText").value("SELECT id, name, email FROM users"))
                .andExpect(jsonPath("$.dialect").value("TRINO"))

            verify(exactly = 1) {
                sqlWorksheetService.updateWorksheet(
                    projectId = teamId,
                    folderId = folderId,
                    worksheetId = worksheetId,
                    name = request.name,
                    description = request.description,
                    sqlText = request.sqlText,
                    dialect = request.dialect,
                )
            }
        }

        @Test
        @DisplayName("should update worksheet with partial fields")
        fun `should update worksheet with partial fields`() {
            // Given
            val request =
                UpdateSqlWorksheetRequest(
                    description = "Only description updated",
                )

            val updatedWorksheet =
                createTestWorksheet(worksheetId, folderId, testWorksheet.name).apply {
                    this.description = request.description
                    this.sqlText = testWorksheet.sqlText
                }

            every { sqlWorksheetService.findWorksheetById(worksheetId) } returns testWorksheet
            every {
                sqlWorksheetService.updateWorksheet(
                    projectId = teamId,
                    folderId = folderId,
                    worksheetId = worksheetId,
                    name = null,
                    description = request.description,
                    sqlText = null,
                    dialect = null,
                )
            } returns updatedWorksheet

            // When & Then
            mockMvc
                .perform(
                    put("/api/v1/teams/$teamId/sql/worksheets/$worksheetId")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)),
                ).andExpect(status().isOk)
                .andExpect(jsonPath("$.description").value("Only description updated"))
        }

        @Test
        @DisplayName("should return 404 when worksheet not found")
        fun `should return 404 when worksheet not found`() {
            // Given
            val nonExistentWorksheetId = 9999L
            val request =
                UpdateSqlWorksheetRequest(
                    name = "new-name",
                )

            every { sqlWorksheetService.findWorksheetById(nonExistentWorksheetId) } returns null

            // When & Then
            mockMvc
                .perform(
                    put("/api/v1/teams/$teamId/sql/worksheets/$nonExistentWorksheetId")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)),
                ).andExpect(status().isNotFound)
        }

        @Test
        @DisplayName("should return 409 when new name already exists")
        fun `should return 409 when new name already exists`() {
            // Given
            val request =
                UpdateSqlWorksheetRequest(
                    name = "existing-name",
                )

            every { sqlWorksheetService.findWorksheetById(worksheetId) } returns testWorksheet
            every {
                sqlWorksheetService.updateWorksheet(
                    projectId = teamId,
                    folderId = folderId,
                    worksheetId = worksheetId,
                    name = request.name,
                    description = null,
                    sqlText = null,
                    dialect = null,
                )
            } throws SqlWorksheetAlreadyExistsException(request.name!!, folderId)

            // When & Then
            mockMvc
                .perform(
                    put("/api/v1/teams/$teamId/sql/worksheets/$worksheetId")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)),
                ).andExpect(status().isConflict)
        }

        @Test
        @DisplayName("should return 400 when name exceeds maximum length")
        fun `should return 400 when name exceeds maximum length`() {
            // Given
            val tooLongName = "a".repeat(201)
            val invalidRequest =
                mapOf(
                    "name" to tooLongName,
                )

            // When & Then
            mockMvc
                .perform(
                    put("/api/v1/teams/$teamId/sql/worksheets/$worksheetId")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(invalidRequest)),
                ).andExpect(status().isBadRequest)
        }
    }

    @Nested
    @DisplayName("DELETE /api/v1/teams/{teamId}/sql/worksheets/{worksheetId}")
    inner class DeleteSqlWorksheet {
        @Test
        @DisplayName("should delete worksheet successfully")
        fun `should delete worksheet successfully`() {
            // Given - Controller now uses findWorksheetById directly
            every { sqlWorksheetService.findWorksheetById(worksheetId) } returns testWorksheet
            every { sqlWorksheetService.deleteWorksheet(teamId, folderId, worksheetId) } returns Unit

            // When & Then
            mockMvc
                .perform(
                    delete("/api/v1/teams/$teamId/sql/worksheets/$worksheetId")
                        .with(csrf()),
                ).andExpect(status().isNoContent)

            verify(exactly = 1) { sqlWorksheetService.findWorksheetById(worksheetId) }
            verify(exactly = 1) { sqlWorksheetService.deleteWorksheet(teamId, folderId, worksheetId) }
        }

        @Test
        @DisplayName("should return 404 when worksheet not found")
        fun `should return 404 when worksheet not found`() {
            // Given
            val nonExistentWorksheetId = 9999L

            every { sqlWorksheetService.findWorksheetById(nonExistentWorksheetId) } returns null

            // When & Then
            mockMvc
                .perform(
                    delete("/api/v1/teams/$teamId/sql/worksheets/$nonExistentWorksheetId")
                        .with(csrf()),
                ).andExpect(status().isNotFound)

            verify(exactly = 1) { sqlWorksheetService.findWorksheetById(nonExistentWorksheetId) }
            verify(exactly = 0) { sqlWorksheetService.deleteWorksheet(any(), any(), any()) }
        }

        @Test
        @DisplayName("should return 404 when worksheet not found (previously team has no folders)")
        fun `should return 404 when worksheet not found (previously team has no folders)`() {
            // Given - Same behavior as "not found" now
            every { sqlWorksheetService.findWorksheetById(worksheetId) } returns null

            // When & Then
            mockMvc
                .perform(
                    delete("/api/v1/teams/$teamId/sql/worksheets/$worksheetId")
                        .with(csrf()),
                ).andExpect(status().isNotFound)

            verify(exactly = 1) { sqlWorksheetService.findWorksheetById(worksheetId) }
            verify(exactly = 0) { sqlWorksheetService.deleteWorksheet(any(), any(), any()) }
        }
    }

    @Nested
    @DisplayName("Integration Tests")
    inner class IntegrationTests {
        @Test
        @DisplayName("should handle complete create-get-update-delete flow")
        fun `should handle complete create-get-update-delete flow`() {
            // === CREATE ===
            val createRequest =
                CreateSqlWorksheetRequest(
                    folderId = folderId,
                    name = "integration-test-worksheet",
                    description = "Worksheet for integration testing",
                    sqlText = "SELECT * FROM integration_table",
                    dialect = SqlDialect.BIGQUERY,
                )

            val createdWorksheet =
                createTestWorksheet(
                    id = 2000L,
                    folderId = folderId,
                    name = createRequest.name,
                ).apply {
                    this.description = createRequest.description
                    this.sqlText = createRequest.sqlText
                    this.dialect = createRequest.dialect
                }

            every {
                sqlWorksheetService.createWorksheet(
                    projectId = teamId,
                    folderId = createRequest.folderId,
                    name = createRequest.name,
                    description = createRequest.description,
                    sqlText = createRequest.sqlText,
                    dialect = createRequest.dialect,
                )
            } returns createdWorksheet

            every { worksheetFolderService.getFolderById(teamId, folderId) } returns testFolder

            mockMvc
                .perform(
                    post("/api/v1/teams/$teamId/sql/worksheets")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(createRequest)),
                ).andExpect(status().isCreated)
                .andExpect(jsonPath("$.name").value("integration-test-worksheet"))

            // === GET ===
            // Controller now uses findWorksheetById directly
            every { sqlWorksheetService.findWorksheetById(2000L) } returns createdWorksheet

            mockMvc
                .perform(get("/api/v1/teams/$teamId/sql/worksheets/2000"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.name").value("integration-test-worksheet"))

            // === UPDATE ===
            val updateRequest =
                UpdateSqlWorksheetRequest(
                    name = "updated-integration-worksheet",
                    sqlText = "SELECT id, name FROM integration_table",
                )

            val updatedWorksheet =
                createTestWorksheet(2000L, folderId, updateRequest.name!!).apply {
                    this.description = createRequest.description
                    this.sqlText = updateRequest.sqlText!!
                }

            every {
                sqlWorksheetService.updateWorksheet(
                    projectId = teamId,
                    folderId = folderId,
                    worksheetId = 2000L,
                    name = updateRequest.name,
                    description = null,
                    sqlText = updateRequest.sqlText,
                    dialect = null,
                )
            } returns updatedWorksheet

            mockMvc
                .perform(
                    put("/api/v1/teams/$teamId/sql/worksheets/2000")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(updateRequest)),
                ).andExpect(status().isOk)
                .andExpect(jsonPath("$.name").value("updated-integration-worksheet"))

            // === DELETE ===
            every { sqlWorksheetService.deleteWorksheet(teamId, folderId, 2000L) } returns Unit

            mockMvc
                .perform(
                    delete("/api/v1/teams/$teamId/sql/worksheets/2000")
                        .with(csrf()),
                ).andExpect(status().isNoContent)

            // Verify all calls
            verify(exactly = 1) {
                sqlWorksheetService.createWorksheet(
                    projectId = teamId,
                    folderId = createRequest.folderId,
                    name = createRequest.name,
                    description = createRequest.description,
                    sqlText = createRequest.sqlText,
                    dialect = createRequest.dialect,
                )
            }
            verify(atLeast = 1) { sqlWorksheetService.findWorksheetById(2000L) }
            verify(exactly = 1) {
                sqlWorksheetService.updateWorksheet(
                    projectId = teamId,
                    folderId = folderId,
                    worksheetId = 2000L,
                    name = updateRequest.name,
                    description = null,
                    sqlText = updateRequest.sqlText,
                    dialect = null,
                )
            }
            verify(exactly = 1) { sqlWorksheetService.deleteWorksheet(teamId, folderId, 2000L) }
        }
    }

    // Helper function to create test worksheets
    private fun createTestWorksheet(
        id: Long,
        folderId: Long,
        name: String,
    ): SqlWorksheetEntity =
        SqlWorksheetEntity(
            folderId = folderId,
            name = name,
            description = "Test description for $name",
            sqlText = "SELECT * FROM $name",
            dialect = SqlDialect.BIGQUERY,
        ).apply {
            val idField = this::class.java.superclass.getDeclaredField("id")
            idField.isAccessible = true
            idField.set(this, id)

            val createdAtField = this::class.java.superclass.getDeclaredField("createdAt")
            createdAtField.isAccessible = true
            createdAtField.set(this, LocalDateTime.now())

            val updatedAtField = this::class.java.superclass.getDeclaredField("updatedAt")
            updatedAtField.isAccessible = true
            updatedAtField.set(this, LocalDateTime.now())
        }
}
