package com.dataops.basecamp.controller

import com.dataops.basecamp.common.enums.SqlDialect
import com.dataops.basecamp.common.exception.ProjectNotFoundException
import com.dataops.basecamp.common.exception.SqlFolderNotFoundException
import com.dataops.basecamp.common.exception.SqlSnippetAlreadyExistsException
import com.dataops.basecamp.domain.entity.sql.SqlFolderEntity
import com.dataops.basecamp.domain.entity.sql.SqlSnippetEntity
import com.dataops.basecamp.domain.service.ProjectService
import com.dataops.basecamp.domain.service.SqlFolderService
import com.dataops.basecamp.domain.service.SqlSnippetService
import com.dataops.basecamp.dto.sql.CreateSqlSnippetRequest
import com.dataops.basecamp.dto.sql.UpdateSqlSnippetRequest
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
 * SQL Snippet Controller REST API Tests
 *
 * Tests for SQL Snippet endpoints within ProjectController.
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
@DisplayName("SQL Snippet Controller Tests")
class SqlSnippetControllerTest {
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
    private lateinit var projectService: ProjectService

    @MockkBean(relaxed = true)
    private lateinit var sqlFolderService: SqlFolderService

    @MockkBean(relaxed = true)
    private lateinit var sqlSnippetService: SqlSnippetService

    private val projectId = 1L
    private val folderId = 100L
    private val snippetId = 1000L

    private lateinit var testFolder: SqlFolderEntity
    private lateinit var testSnippet: SqlSnippetEntity

    @BeforeEach
    fun setUp() {
        // Clear mocks between tests to avoid mock pollution
        clearMocks(projectService, sqlFolderService, sqlSnippetService)

        testFolder =
            SqlFolderEntity(
                projectId = projectId,
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

        testSnippet =
            SqlSnippetEntity(
                folderId = folderId,
                name = "test-snippet",
                description = "Test snippet description",
                sqlText = "SELECT * FROM users",
                dialect = SqlDialect.BIGQUERY,
            ).apply {
                val idField = this::class.java.superclass.getDeclaredField("id")
                idField.isAccessible = true
                idField.set(this, snippetId)

                val createdAtField = this::class.java.superclass.getDeclaredField("createdAt")
                createdAtField.isAccessible = true
                createdAtField.set(this, LocalDateTime.of(2024, 1, 1, 9, 0, 0))

                val updatedAtField = this::class.java.superclass.getDeclaredField("updatedAt")
                updatedAtField.isAccessible = true
                updatedAtField.set(this, LocalDateTime.of(2024, 1, 1, 10, 0, 0))
            }
    }

    @Nested
    @DisplayName("GET /api/v1/projects/{projectId}/sql/snippets")
    inner class ListSqlSnippets {
        @Test
        @DisplayName("should return empty list when no snippets exist")
        fun `should return empty list when no snippets exist`() {
            // Given
            val pageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "updatedAt"))
            val emptyPage = PageImpl<SqlSnippetEntity>(emptyList(), pageable, 0)

            every {
                sqlSnippetService.listSnippets(
                    projectId = projectId,
                    folderId = null,
                    searchText = null,
                    starred = null,
                    dialect = null,
                    pageable = any(),
                )
            } returns emptyPage

            // When & Then
            mockMvc
                .perform(get("/api/v1/projects/$projectId/sql/snippets"))
                .andExpect(status().isOk)
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content.length()").value(0))
                .andExpect(jsonPath("$.totalElements").value(0))
        }

        @Test
        @DisplayName("should return snippets list with pagination")
        fun `should return snippets list with pagination`() {
            // Given
            val snippet1 = createTestSnippet(1001L, folderId, "snippet-1")
            val snippet2 = createTestSnippet(1002L, folderId, "snippet-2")
            val snippets = listOf(snippet1, snippet2)
            val pageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "updatedAt"))
            val page = PageImpl(snippets, pageable, 2)

            every {
                sqlSnippetService.listSnippets(
                    projectId = projectId,
                    folderId = null,
                    searchText = null,
                    starred = null,
                    dialect = null,
                    pageable = any(),
                )
            } returns page

            every { sqlFolderService.getFolderById(projectId, folderId) } returns testFolder

            // When & Then
            mockMvc
                .perform(get("/api/v1/projects/$projectId/sql/snippets"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].name").value("snippet-1"))
                .andExpect(jsonPath("$.content[1].name").value("snippet-2"))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
        }

        @Test
        @DisplayName("should filter snippets by folderId")
        fun `should filter snippets by folderId`() {
            // Given
            val snippet1 = createTestSnippet(1001L, folderId, "snippet-in-folder")
            val snippets = listOf(snippet1)
            val pageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "updatedAt"))
            val page = PageImpl(snippets, pageable, 1)

            every {
                sqlSnippetService.listSnippets(
                    projectId = projectId,
                    folderId = folderId,
                    searchText = null,
                    starred = null,
                    dialect = null,
                    pageable = any(),
                )
            } returns page

            every { sqlFolderService.getFolderById(projectId, folderId) } returns testFolder

            // When & Then
            mockMvc
                .perform(
                    get("/api/v1/projects/$projectId/sql/snippets")
                        .param("folderId", folderId.toString()),
                ).andExpect(status().isOk)
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].folderId").value(folderId))

            verify(exactly = 1) {
                sqlSnippetService.listSnippets(
                    projectId = projectId,
                    folderId = folderId,
                    searchText = null,
                    starred = null,
                    dialect = null,
                    pageable = any(),
                )
            }
        }

        @Test
        @DisplayName("should filter snippets by searchText")
        fun `should filter snippets by searchText`() {
            // Given
            val searchText = "user"
            val snippet1 = createTestSnippet(1001L, folderId, "user-query")
            val snippets = listOf(snippet1)
            val pageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "updatedAt"))
            val page = PageImpl(snippets, pageable, 1)

            every {
                sqlSnippetService.listSnippets(
                    projectId = projectId,
                    folderId = null,
                    searchText = searchText,
                    starred = null,
                    dialect = null,
                    pageable = any(),
                )
            } returns page

            every { sqlFolderService.getFolderById(projectId, folderId) } returns testFolder

            // When & Then
            mockMvc
                .perform(
                    get("/api/v1/projects/$projectId/sql/snippets")
                        .param("searchText", searchText),
                ).andExpect(status().isOk)
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].name").value("user-query"))

            verify(exactly = 1) {
                sqlSnippetService.listSnippets(
                    projectId = projectId,
                    folderId = null,
                    searchText = searchText,
                    starred = null,
                    dialect = null,
                    pageable = any(),
                )
            }
        }

        @Test
        @DisplayName("should filter snippets by starred")
        fun `should filter snippets by starred`() {
            // Given
            val snippet1 =
                createTestSnippet(1001L, folderId, "starred-snippet").apply {
                    this.isStarred = true
                }
            val snippets = listOf(snippet1)
            val pageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "updatedAt"))
            val page = PageImpl(snippets, pageable, 1)

            every {
                sqlSnippetService.listSnippets(
                    projectId = projectId,
                    folderId = null,
                    searchText = null,
                    starred = true,
                    dialect = null,
                    pageable = any(),
                )
            } returns page

            every { sqlFolderService.getFolderById(projectId, folderId) } returns testFolder

            // When & Then
            mockMvc
                .perform(
                    get("/api/v1/projects/$projectId/sql/snippets")
                        .param("starred", "true"),
                ).andExpect(status().isOk)
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].isStarred").value(true))

            verify(exactly = 1) {
                sqlSnippetService.listSnippets(
                    projectId = projectId,
                    folderId = null,
                    searchText = null,
                    starred = true,
                    dialect = null,
                    pageable = any(),
                )
            }
        }

        @Test
        @DisplayName("should filter snippets by dialect")
        fun `should filter snippets by dialect`() {
            // Given
            val snippet1 =
                createTestSnippet(1001L, folderId, "trino-snippet").apply {
                    this.dialect = SqlDialect.TRINO
                }
            val snippets = listOf(snippet1)
            val pageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "updatedAt"))
            val page = PageImpl(snippets, pageable, 1)

            every {
                sqlSnippetService.listSnippets(
                    projectId = projectId,
                    folderId = null,
                    searchText = null,
                    starred = null,
                    dialect = SqlDialect.TRINO,
                    pageable = any(),
                )
            } returns page

            every { sqlFolderService.getFolderById(projectId, folderId) } returns testFolder

            // When & Then
            mockMvc
                .perform(
                    get("/api/v1/projects/$projectId/sql/snippets")
                        .param("dialect", "TRINO"),
                ).andExpect(status().isOk)
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].dialect").value("TRINO"))

            verify(exactly = 1) {
                sqlSnippetService.listSnippets(
                    projectId = projectId,
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
            val snippet1 = createTestSnippet(1003L, folderId, "snippet-page2")
            val snippets = listOf(snippet1)
            val pageable = PageRequest.of(1, 10, Sort.by(Sort.Direction.DESC, "updatedAt"))
            val page = PageImpl(snippets, pageable, 25) // 25 total elements

            every {
                sqlSnippetService.listSnippets(
                    projectId = projectId,
                    folderId = null,
                    searchText = null,
                    starred = null,
                    dialect = null,
                    pageable = any(),
                )
            } returns page

            every { sqlFolderService.getFolderById(projectId, folderId) } returns testFolder

            // When & Then
            mockMvc
                .perform(
                    get("/api/v1/projects/$projectId/sql/snippets")
                        .param("page", "1")
                        .param("size", "10"),
                ).andExpect(status().isOk)
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(10))
                .andExpect(jsonPath("$.totalElements").isNumber())
                .andExpect(jsonPath("$.totalPages").isNumber())
        }

        @Test
        @DisplayName("should return 404 when project not found")
        fun `should return 404 when project not found`() {
            // Given
            val nonExistentProjectId = 999L

            every {
                sqlSnippetService.listSnippets(
                    projectId = nonExistentProjectId,
                    folderId = null,
                    searchText = null,
                    starred = null,
                    dialect = null,
                    pageable = any(),
                )
            } throws ProjectNotFoundException(nonExistentProjectId)

            // When & Then
            mockMvc
                .perform(get("/api/v1/projects/$nonExistentProjectId/sql/snippets"))
                .andExpect(status().isNotFound)
        }
    }

    @Nested
    @DisplayName("POST /api/v1/projects/{projectId}/sql/snippets")
    inner class CreateSqlSnippet {
        @Test
        @DisplayName("should create snippet successfully")
        fun `should create snippet successfully`() {
            // Given
            val request =
                CreateSqlSnippetRequest(
                    folderId = folderId,
                    name = "new-snippet",
                    description = "New snippet description",
                    sqlText = "SELECT id, name FROM users",
                    dialect = SqlDialect.TRINO,
                )

            val savedSnippet =
                createTestSnippet(
                    id = 1003L,
                    folderId = folderId,
                    name = request.name,
                ).apply {
                    this.description = request.description
                    this.sqlText = request.sqlText
                    this.dialect = request.dialect
                }

            every {
                sqlSnippetService.createSnippet(
                    projectId = projectId,
                    folderId = request.folderId,
                    name = request.name,
                    description = request.description,
                    sqlText = request.sqlText,
                    dialect = request.dialect,
                )
            } returns savedSnippet

            every { sqlFolderService.getFolderById(projectId, folderId) } returns testFolder

            // When & Then
            mockMvc
                .perform(
                    post("/api/v1/projects/$projectId/sql/snippets")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)),
                ).andExpect(status().isCreated)
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(1003))
                .andExpect(jsonPath("$.folderId").value(folderId))
                .andExpect(jsonPath("$.folderName").value("test-folder"))
                .andExpect(jsonPath("$.name").value("new-snippet"))
                .andExpect(jsonPath("$.description").value("New snippet description"))
                .andExpect(jsonPath("$.sqlText").value("SELECT id, name FROM users"))
                .andExpect(jsonPath("$.dialect").value("TRINO"))

            verify(exactly = 1) {
                sqlSnippetService.createSnippet(
                    projectId = projectId,
                    folderId = request.folderId,
                    name = request.name,
                    description = request.description,
                    sqlText = request.sqlText,
                    dialect = request.dialect,
                )
            }
        }

        @Test
        @DisplayName("should create snippet without description")
        fun `should create snippet without description`() {
            // Given
            val request =
                CreateSqlSnippetRequest(
                    folderId = folderId,
                    name = "snippet-no-desc",
                    sqlText = "SELECT 1",
                )

            val savedSnippet =
                createTestSnippet(
                    id = 1004L,
                    folderId = folderId,
                    name = request.name,
                ).apply {
                    this.description = null
                    this.sqlText = request.sqlText
                }

            every {
                sqlSnippetService.createSnippet(
                    projectId = projectId,
                    folderId = request.folderId,
                    name = request.name,
                    description = null,
                    sqlText = request.sqlText,
                    dialect = SqlDialect.BIGQUERY,
                )
            } returns savedSnippet

            every { sqlFolderService.getFolderById(projectId, folderId) } returns testFolder

            // When & Then
            mockMvc
                .perform(
                    post("/api/v1/projects/$projectId/sql/snippets")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)),
                ).andExpect(status().isCreated)
                .andExpect(jsonPath("$.name").value("snippet-no-desc"))
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
                    post("/api/v1/projects/$projectId/sql/snippets")
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
                    post("/api/v1/projects/$projectId/sql/snippets")
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
                    post("/api/v1/projects/$projectId/sql/snippets")
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
                    post("/api/v1/projects/$projectId/sql/snippets")
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
                    post("/api/v1/projects/$projectId/sql/snippets")
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
                    post("/api/v1/projects/$projectId/sql/snippets")
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
                CreateSqlSnippetRequest(
                    folderId = nonExistentFolderId,
                    name = "new-snippet",
                    sqlText = "SELECT 1",
                )

            every {
                sqlSnippetService.createSnippet(
                    projectId = projectId,
                    folderId = nonExistentFolderId,
                    name = request.name,
                    description = null,
                    sqlText = request.sqlText,
                    dialect = SqlDialect.BIGQUERY,
                )
            } throws SqlFolderNotFoundException(nonExistentFolderId, projectId)

            // When & Then
            mockMvc
                .perform(
                    post("/api/v1/projects/$projectId/sql/snippets")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)),
                ).andExpect(status().isNotFound)
        }

        @Test
        @DisplayName("should return 409 when snippet name already exists")
        fun `should return 409 when snippet name already exists`() {
            // Given
            val request =
                CreateSqlSnippetRequest(
                    folderId = folderId,
                    name = "existing-snippet",
                    sqlText = "SELECT 1",
                )

            every {
                sqlSnippetService.createSnippet(
                    projectId = projectId,
                    folderId = request.folderId,
                    name = request.name,
                    description = null,
                    sqlText = request.sqlText,
                    dialect = SqlDialect.BIGQUERY,
                )
            } throws SqlSnippetAlreadyExistsException(request.name, folderId)

            // When & Then
            mockMvc
                .perform(
                    post("/api/v1/projects/$projectId/sql/snippets")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)),
                ).andExpect(status().isConflict)

            verify(exactly = 1) {
                sqlSnippetService.createSnippet(
                    projectId = projectId,
                    folderId = request.folderId,
                    name = request.name,
                    description = null,
                    sqlText = request.sqlText,
                    dialect = SqlDialect.BIGQUERY,
                )
            }
        }

        @Test
        @DisplayName("should return 404 when project not found")
        fun `should return 404 when project not found`() {
            // Given
            val nonExistentProjectId = 999L
            val request =
                CreateSqlSnippetRequest(
                    folderId = folderId,
                    name = "new-snippet",
                    sqlText = "SELECT 1",
                )

            every {
                sqlSnippetService.createSnippet(
                    projectId = nonExistentProjectId,
                    folderId = request.folderId,
                    name = request.name,
                    description = null,
                    sqlText = request.sqlText,
                    dialect = SqlDialect.BIGQUERY,
                )
            } throws ProjectNotFoundException(nonExistentProjectId)

            // When & Then
            mockMvc
                .perform(
                    post("/api/v1/projects/$nonExistentProjectId/sql/snippets")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)),
                ).andExpect(status().isNotFound)
        }
    }

    @Nested
    @DisplayName("GET /api/v1/projects/{projectId}/sql/snippets/{snippetId}")
    inner class GetSqlSnippet {
        @Test
        @DisplayName("should return snippet by id")
        fun `should return snippet by id`() {
            // Given
            every { sqlFolderService.listFolders(projectId) } returns listOf(testFolder)
            every { sqlSnippetService.getSnippetById(folderId, snippetId) } returns testSnippet

            // When & Then
            mockMvc
                .perform(get("/api/v1/projects/$projectId/sql/snippets/$snippetId"))
                .andExpect(status().isOk)
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(snippetId))
                .andExpect(jsonPath("$.folderId").value(folderId))
                .andExpect(jsonPath("$.folderName").value("test-folder"))
                .andExpect(jsonPath("$.name").value("test-snippet"))
                .andExpect(jsonPath("$.description").value("Test snippet description"))
                .andExpect(jsonPath("$.sqlText").value("SELECT * FROM users"))
                .andExpect(jsonPath("$.dialect").value("BIGQUERY"))
                .andExpect(jsonPath("$.isStarred").value(false))
                .andExpect(jsonPath("$.runCount").value(0))
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.updatedAt").exists())

            verify(exactly = 1) { sqlFolderService.listFolders(projectId) }
            verify(exactly = 1) { sqlSnippetService.getSnippetById(folderId, snippetId) }
        }

        @Test
        @DisplayName("should return 404 when snippet not found")
        fun `should return 404 when snippet not found`() {
            // Given
            val nonExistentSnippetId = 9999L

            every { sqlFolderService.listFolders(projectId) } returns listOf(testFolder)
            every { sqlSnippetService.getSnippetById(folderId, nonExistentSnippetId) } returns null

            // When & Then
            mockMvc
                .perform(get("/api/v1/projects/$projectId/sql/snippets/$nonExistentSnippetId"))
                .andExpect(status().isNotFound)

            verify(exactly = 1) { sqlFolderService.listFolders(projectId) }
            verify(exactly = 1) { sqlSnippetService.getSnippetById(folderId, nonExistentSnippetId) }
        }

        @Test
        @DisplayName("should return 404 when project has no folders")
        fun `should return 404 when project has no folders`() {
            // Given
            every { sqlFolderService.listFolders(projectId) } returns emptyList()

            // When & Then
            mockMvc
                .perform(get("/api/v1/projects/$projectId/sql/snippets/$snippetId"))
                .andExpect(status().isNotFound)

            verify(exactly = 1) { sqlFolderService.listFolders(projectId) }
        }

        @Test
        @DisplayName("should return 404 when project not found")
        fun `should return 404 when project not found`() {
            // Given
            val nonExistentProjectId = 999L

            every { sqlFolderService.listFolders(nonExistentProjectId) } throws
                ProjectNotFoundException(nonExistentProjectId)

            // When & Then
            mockMvc
                .perform(get("/api/v1/projects/$nonExistentProjectId/sql/snippets/$snippetId"))
                .andExpect(status().isNotFound)
        }
    }

    @Nested
    @DisplayName("PUT /api/v1/projects/{projectId}/sql/snippets/{snippetId}")
    inner class UpdateSqlSnippet {
        @Test
        @DisplayName("should update snippet successfully")
        fun `should update snippet successfully`() {
            // Given
            val request =
                UpdateSqlSnippetRequest(
                    name = "updated-snippet",
                    description = "Updated description",
                    sqlText = "SELECT id, name, email FROM users",
                    dialect = SqlDialect.TRINO,
                )

            val updatedSnippet =
                createTestSnippet(snippetId, folderId, request.name!!).apply {
                    this.description = request.description
                    this.sqlText = request.sqlText!!
                    this.dialect = request.dialect!!
                }

            every { sqlFolderService.listFolders(projectId) } returns listOf(testFolder)
            every { sqlSnippetService.getSnippetById(folderId, snippetId) } returns testSnippet
            every {
                sqlSnippetService.updateSnippet(
                    projectId = projectId,
                    folderId = folderId,
                    snippetId = snippetId,
                    name = request.name,
                    description = request.description,
                    sqlText = request.sqlText,
                    dialect = request.dialect,
                )
            } returns updatedSnippet

            // When & Then
            mockMvc
                .perform(
                    put("/api/v1/projects/$projectId/sql/snippets/$snippetId")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)),
                ).andExpect(status().isOk)
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(snippetId))
                .andExpect(jsonPath("$.name").value("updated-snippet"))
                .andExpect(jsonPath("$.description").value("Updated description"))
                .andExpect(jsonPath("$.sqlText").value("SELECT id, name, email FROM users"))
                .andExpect(jsonPath("$.dialect").value("TRINO"))

            verify(exactly = 1) {
                sqlSnippetService.updateSnippet(
                    projectId = projectId,
                    folderId = folderId,
                    snippetId = snippetId,
                    name = request.name,
                    description = request.description,
                    sqlText = request.sqlText,
                    dialect = request.dialect,
                )
            }
        }

        @Test
        @DisplayName("should update snippet with partial fields")
        fun `should update snippet with partial fields`() {
            // Given
            val request =
                UpdateSqlSnippetRequest(
                    description = "Only description updated",
                )

            val updatedSnippet =
                createTestSnippet(snippetId, folderId, testSnippet.name).apply {
                    this.description = request.description
                    this.sqlText = testSnippet.sqlText
                }

            every { sqlFolderService.listFolders(projectId) } returns listOf(testFolder)
            every { sqlSnippetService.getSnippetById(folderId, snippetId) } returns testSnippet
            every {
                sqlSnippetService.updateSnippet(
                    projectId = projectId,
                    folderId = folderId,
                    snippetId = snippetId,
                    name = null,
                    description = request.description,
                    sqlText = null,
                    dialect = null,
                )
            } returns updatedSnippet

            // When & Then
            mockMvc
                .perform(
                    put("/api/v1/projects/$projectId/sql/snippets/$snippetId")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)),
                ).andExpect(status().isOk)
                .andExpect(jsonPath("$.description").value("Only description updated"))
        }

        @Test
        @DisplayName("should return 404 when snippet not found")
        fun `should return 404 when snippet not found`() {
            // Given
            val nonExistentSnippetId = 9999L
            val request =
                UpdateSqlSnippetRequest(
                    name = "new-name",
                )

            every { sqlFolderService.listFolders(projectId) } returns listOf(testFolder)
            every { sqlSnippetService.getSnippetById(folderId, nonExistentSnippetId) } returns null

            // When & Then
            mockMvc
                .perform(
                    put("/api/v1/projects/$projectId/sql/snippets/$nonExistentSnippetId")
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
                UpdateSqlSnippetRequest(
                    name = "existing-name",
                )

            every { sqlFolderService.listFolders(projectId) } returns listOf(testFolder)
            every { sqlSnippetService.getSnippetById(folderId, snippetId) } returns testSnippet
            every {
                sqlSnippetService.updateSnippet(
                    projectId = projectId,
                    folderId = folderId,
                    snippetId = snippetId,
                    name = request.name,
                    description = null,
                    sqlText = null,
                    dialect = null,
                )
            } throws SqlSnippetAlreadyExistsException(request.name!!, folderId)

            // When & Then
            mockMvc
                .perform(
                    put("/api/v1/projects/$projectId/sql/snippets/$snippetId")
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
                    put("/api/v1/projects/$projectId/sql/snippets/$snippetId")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(invalidRequest)),
                ).andExpect(status().isBadRequest)
        }
    }

    @Nested
    @DisplayName("DELETE /api/v1/projects/{projectId}/sql/snippets/{snippetId}")
    inner class DeleteSqlSnippet {
        @Test
        @DisplayName("should delete snippet successfully")
        fun `should delete snippet successfully`() {
            // Given
            every { sqlFolderService.listFolders(projectId) } returns listOf(testFolder)
            every { sqlSnippetService.getSnippetById(folderId, snippetId) } returns testSnippet
            every { sqlSnippetService.deleteSnippet(projectId, folderId, snippetId) } returns Unit

            // When & Then
            mockMvc
                .perform(
                    delete("/api/v1/projects/$projectId/sql/snippets/$snippetId")
                        .with(csrf()),
                ).andExpect(status().isNoContent)

            verify(exactly = 1) { sqlFolderService.listFolders(projectId) }
            verify(exactly = 1) { sqlSnippetService.getSnippetById(folderId, snippetId) }
            verify(exactly = 1) { sqlSnippetService.deleteSnippet(projectId, folderId, snippetId) }
        }

        @Test
        @DisplayName("should return 404 when snippet not found")
        fun `should return 404 when snippet not found`() {
            // Given
            val nonExistentSnippetId = 9999L

            every { sqlFolderService.listFolders(projectId) } returns listOf(testFolder)
            every { sqlSnippetService.getSnippetById(folderId, nonExistentSnippetId) } returns null

            // When & Then
            mockMvc
                .perform(
                    delete("/api/v1/projects/$projectId/sql/snippets/$nonExistentSnippetId")
                        .with(csrf()),
                ).andExpect(status().isNotFound)

            verify(exactly = 1) { sqlFolderService.listFolders(projectId) }
            verify(exactly = 1) { sqlSnippetService.getSnippetById(folderId, nonExistentSnippetId) }
            verify(exactly = 0) { sqlSnippetService.deleteSnippet(any(), any(), any()) }
        }

        @Test
        @DisplayName("should return 404 when project not found")
        fun `should return 404 when project not found`() {
            // Given
            val nonExistentProjectId = 999L

            every { sqlFolderService.listFolders(nonExistentProjectId) } throws
                ProjectNotFoundException(nonExistentProjectId)

            // When & Then
            mockMvc
                .perform(
                    delete("/api/v1/projects/$nonExistentProjectId/sql/snippets/$snippetId")
                        .with(csrf()),
                ).andExpect(status().isNotFound)
        }

        @Test
        @DisplayName("should return 404 when project has no folders")
        fun `should return 404 when project has no folders`() {
            // Given
            every { sqlFolderService.listFolders(projectId) } returns emptyList()

            // When & Then
            mockMvc
                .perform(
                    delete("/api/v1/projects/$projectId/sql/snippets/$snippetId")
                        .with(csrf()),
                ).andExpect(status().isNotFound)

            verify(exactly = 1) { sqlFolderService.listFolders(projectId) }
            verify(exactly = 0) { sqlSnippetService.deleteSnippet(any(), any(), any()) }
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
                CreateSqlSnippetRequest(
                    folderId = folderId,
                    name = "integration-test-snippet",
                    description = "Snippet for integration testing",
                    sqlText = "SELECT * FROM integration_table",
                    dialect = SqlDialect.BIGQUERY,
                )

            val createdSnippet =
                createTestSnippet(
                    id = 2000L,
                    folderId = folderId,
                    name = createRequest.name,
                ).apply {
                    this.description = createRequest.description
                    this.sqlText = createRequest.sqlText
                    this.dialect = createRequest.dialect
                }

            every {
                sqlSnippetService.createSnippet(
                    projectId = projectId,
                    folderId = createRequest.folderId,
                    name = createRequest.name,
                    description = createRequest.description,
                    sqlText = createRequest.sqlText,
                    dialect = createRequest.dialect,
                )
            } returns createdSnippet

            every { sqlFolderService.getFolderById(projectId, folderId) } returns testFolder

            mockMvc
                .perform(
                    post("/api/v1/projects/$projectId/sql/snippets")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(createRequest)),
                ).andExpect(status().isCreated)
                .andExpect(jsonPath("$.name").value("integration-test-snippet"))

            // === GET ===
            every { sqlFolderService.listFolders(projectId) } returns listOf(testFolder)
            every { sqlSnippetService.getSnippetById(folderId, 2000L) } returns createdSnippet

            mockMvc
                .perform(get("/api/v1/projects/$projectId/sql/snippets/2000"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.name").value("integration-test-snippet"))

            // === UPDATE ===
            val updateRequest =
                UpdateSqlSnippetRequest(
                    name = "updated-integration-snippet",
                    sqlText = "SELECT id, name FROM integration_table",
                )

            val updatedSnippet =
                createTestSnippet(2000L, folderId, updateRequest.name!!).apply {
                    this.description = createRequest.description
                    this.sqlText = updateRequest.sqlText!!
                }

            every {
                sqlSnippetService.updateSnippet(
                    projectId = projectId,
                    folderId = folderId,
                    snippetId = 2000L,
                    name = updateRequest.name,
                    description = null,
                    sqlText = updateRequest.sqlText,
                    dialect = null,
                )
            } returns updatedSnippet

            mockMvc
                .perform(
                    put("/api/v1/projects/$projectId/sql/snippets/2000")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(updateRequest)),
                ).andExpect(status().isOk)
                .andExpect(jsonPath("$.name").value("updated-integration-snippet"))

            // === DELETE ===
            every { sqlSnippetService.deleteSnippet(projectId, folderId, 2000L) } returns Unit

            mockMvc
                .perform(
                    delete("/api/v1/projects/$projectId/sql/snippets/2000")
                        .with(csrf()),
                ).andExpect(status().isNoContent)

            // Verify all calls
            verify(exactly = 1) {
                sqlSnippetService.createSnippet(
                    projectId = projectId,
                    folderId = createRequest.folderId,
                    name = createRequest.name,
                    description = createRequest.description,
                    sqlText = createRequest.sqlText,
                    dialect = createRequest.dialect,
                )
            }
            verify(atLeast = 1) { sqlSnippetService.getSnippetById(folderId, 2000L) }
            verify(exactly = 1) {
                sqlSnippetService.updateSnippet(
                    projectId = projectId,
                    folderId = folderId,
                    snippetId = 2000L,
                    name = updateRequest.name,
                    description = null,
                    sqlText = updateRequest.sqlText,
                    dialect = null,
                )
            }
            verify(exactly = 1) { sqlSnippetService.deleteSnippet(projectId, folderId, 2000L) }
        }
    }

    // Helper function to create test snippets
    private fun createTestSnippet(
        id: Long,
        folderId: Long,
        name: String,
    ): SqlSnippetEntity =
        SqlSnippetEntity(
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
