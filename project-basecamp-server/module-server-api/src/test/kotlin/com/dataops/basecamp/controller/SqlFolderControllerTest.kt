package com.dataops.basecamp.controller

import com.dataops.basecamp.common.exception.ProjectNotFoundException
import com.dataops.basecamp.common.exception.SqlFolderAlreadyExistsException
import com.dataops.basecamp.common.exception.SqlFolderNotFoundException
import com.dataops.basecamp.domain.entity.sql.SqlFolderEntity
import com.dataops.basecamp.domain.service.ProjectService
import com.dataops.basecamp.domain.service.SqlFolderService
import com.dataops.basecamp.dto.sql.CreateSqlFolderRequest
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
import org.springframework.http.MediaType
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.validation.beanvalidation.MethodValidationPostProcessor
import tools.jackson.databind.json.JsonMapper
import java.time.LocalDateTime

/**
 * SQL Folder Controller REST API Tests
 *
 * Tests for SQL Folder endpoints within ProjectController.
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
@DisplayName("SQL Folder Controller Tests")
class SqlFolderControllerTest {
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

    private lateinit var testFolder: SqlFolderEntity

    private val projectId = 1L
    private val folderId = 100L

    @BeforeEach
    fun setUp() {
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
    }

    @Nested
    @DisplayName("GET /api/v1/projects/{projectId}/sql/folders")
    inner class ListSqlFolders {
        @Test
        @DisplayName("should return empty list when no folders exist")
        fun `should return empty list when no folders exist`() {
            // Given
            every { sqlFolderService.listFolders(projectId) } returns emptyList()

            // When & Then
            mockMvc
                .perform(get("/api/v1/projects/$projectId/sql/folders"))
                .andExpect(status().isOk)
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content.length()").value(0))
                .andExpect(jsonPath("$.projectId").value(projectId))

            verify(exactly = 1) { sqlFolderService.listFolders(projectId) }
        }

        @Test
        @DisplayName("should return folders list ordered by displayOrder")
        fun `should return folders list ordered by displayOrder`() {
            // Given
            val folder1 = createTestFolder(101L, "folder-1", 0)
            val folder2 = createTestFolder(102L, "folder-2", 1)
            val folders = listOf(folder1, folder2)

            every { sqlFolderService.listFolders(projectId) } returns folders

            // When & Then
            mockMvc
                .perform(get("/api/v1/projects/$projectId/sql/folders"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].name").value("folder-1"))
                .andExpect(jsonPath("$.content[0].displayOrder").value(0))
                .andExpect(jsonPath("$.content[1].name").value("folder-2"))
                .andExpect(jsonPath("$.content[1].displayOrder").value(1))
                .andExpect(jsonPath("$.projectId").value(projectId))

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
                .perform(get("/api/v1/projects/$nonExistentProjectId/sql/folders"))
                .andExpect(status().isNotFound)

            verify(exactly = 1) { sqlFolderService.listFolders(nonExistentProjectId) }
        }
    }

    @Nested
    @DisplayName("POST /api/v1/projects/{projectId}/sql/folders")
    inner class CreateSqlFolder {
        @Test
        @DisplayName("should create folder successfully")
        fun `should create folder successfully`() {
            // Given
            val request =
                CreateSqlFolderRequest(
                    name = "new-folder",
                    description = "New folder description",
                    displayOrder = 1,
                )

            val savedFolder =
                createTestFolder(
                    id = 103L,
                    name = request.name,
                    displayOrder = request.displayOrder ?: 0,
                    description = request.description,
                )

            every {
                sqlFolderService.createFolder(
                    projectId = projectId,
                    name = request.name,
                    description = request.description,
                    displayOrder = request.displayOrder ?: 0,
                )
            } returns savedFolder

            // When & Then
            mockMvc
                .perform(
                    post("/api/v1/projects/$projectId/sql/folders")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)),
                ).andExpect(status().isCreated)
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(103))
                .andExpect(jsonPath("$.projectId").value(projectId))
                .andExpect(jsonPath("$.name").value("new-folder"))
                .andExpect(jsonPath("$.description").value("New folder description"))
                .andExpect(jsonPath("$.displayOrder").value(1))

            verify(exactly = 1) {
                sqlFolderService.createFolder(
                    projectId = projectId,
                    name = request.name,
                    description = request.description,
                    displayOrder = request.displayOrder ?: 0,
                )
            }
        }

        @Test
        @DisplayName("should create folder without description")
        fun `should create folder without description`() {
            // Given
            val request =
                CreateSqlFolderRequest(
                    name = "folder-no-desc",
                )

            val savedFolder =
                createTestFolder(
                    id = 104L,
                    name = request.name,
                    displayOrder = 0,
                    description = null,
                )

            every {
                sqlFolderService.createFolder(
                    projectId = projectId,
                    name = request.name,
                    description = null,
                    displayOrder = 0,
                )
            } returns savedFolder

            // When & Then
            mockMvc
                .perform(
                    post("/api/v1/projects/$projectId/sql/folders")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)),
                ).andExpect(status().isCreated)
                .andExpect(jsonPath("$.name").value("folder-no-desc"))
                .andExpect(jsonPath("$.description").doesNotExist())
        }

        @Test
        @DisplayName("should return 400 for missing name")
        fun `should return 400 for missing name`() {
            // Given
            val invalidRequest =
                mapOf(
                    "description" to "Some description",
                )

            // When & Then
            mockMvc
                .perform(
                    post("/api/v1/projects/$projectId/sql/folders")
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
                    "name" to "",
                )

            // When & Then
            mockMvc
                .perform(
                    post("/api/v1/projects/$projectId/sql/folders")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(invalidRequest)),
                ).andExpect(status().isBadRequest)
        }

        @Test
        @DisplayName("should return 400 when name exceeds maximum length")
        fun `should return 400 when name exceeds maximum length`() {
            // Given
            val tooLongName = "a".repeat(101) // Name > 100 chars
            val invalidRequest =
                mapOf(
                    "name" to tooLongName,
                )

            // When & Then
            mockMvc
                .perform(
                    post("/api/v1/projects/$projectId/sql/folders")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(invalidRequest)),
                ).andExpect(status().isBadRequest)
        }

        @Test
        @DisplayName("should return 400 when description exceeds maximum length")
        fun `should return 400 when description exceeds maximum length`() {
            // Given
            val tooLongDescription = "x".repeat(501) // Description > 500 chars
            val invalidRequest =
                mapOf(
                    "name" to "valid-name",
                    "description" to tooLongDescription,
                )

            // When & Then
            mockMvc
                .perform(
                    post("/api/v1/projects/$projectId/sql/folders")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(invalidRequest)),
                ).andExpect(status().isBadRequest)
        }

        @Test
        @DisplayName("should return 409 when folder name already exists")
        fun `should return 409 when folder name already exists`() {
            // Given
            val request =
                CreateSqlFolderRequest(
                    name = "existing-folder",
                )

            every {
                sqlFolderService.createFolder(
                    projectId = projectId,
                    name = request.name,
                    description = null,
                    displayOrder = 0,
                )
            } throws SqlFolderAlreadyExistsException(request.name, projectId)

            // When & Then
            mockMvc
                .perform(
                    post("/api/v1/projects/$projectId/sql/folders")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)),
                ).andExpect(status().isConflict)

            verify(exactly = 1) {
                sqlFolderService.createFolder(
                    projectId = projectId,
                    name = request.name,
                    description = null,
                    displayOrder = 0,
                )
            }
        }

        @Test
        @DisplayName("should return 404 when project not found")
        fun `should return 404 when project not found`() {
            // Given
            val nonExistentProjectId = 999L
            val request =
                CreateSqlFolderRequest(
                    name = "new-folder",
                )

            every {
                sqlFolderService.createFolder(
                    projectId = nonExistentProjectId,
                    name = request.name,
                    description = null,
                    displayOrder = 0,
                )
            } throws ProjectNotFoundException(nonExistentProjectId)

            // When & Then
            mockMvc
                .perform(
                    post("/api/v1/projects/$nonExistentProjectId/sql/folders")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)),
                ).andExpect(status().isNotFound)
        }
    }

    @Nested
    @DisplayName("GET /api/v1/projects/{projectId}/sql/folders/{folderId}")
    inner class GetSqlFolder {
        @Test
        @DisplayName("should return folder by id")
        fun `should return folder by id`() {
            // Given
            every { sqlFolderService.getFolderById(projectId, folderId) } returns testFolder

            // When & Then
            mockMvc
                .perform(get("/api/v1/projects/$projectId/sql/folders/$folderId"))
                .andExpect(status().isOk)
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(folderId))
                .andExpect(jsonPath("$.projectId").value(projectId))
                .andExpect(jsonPath("$.name").value("test-folder"))
                .andExpect(jsonPath("$.description").value("Test folder description"))
                .andExpect(jsonPath("$.displayOrder").value(0))
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.updatedAt").exists())

            verify(exactly = 1) { sqlFolderService.getFolderById(projectId, folderId) }
        }

        @Test
        @DisplayName("should return 404 when folder not found")
        fun `should return 404 when folder not found`() {
            // Given
            val nonExistentFolderId = 999L
            every { sqlFolderService.getFolderById(projectId, nonExistentFolderId) } returns null

            // When & Then
            mockMvc
                .perform(get("/api/v1/projects/$projectId/sql/folders/$nonExistentFolderId"))
                .andExpect(status().isNotFound)

            verify(exactly = 1) { sqlFolderService.getFolderById(projectId, nonExistentFolderId) }
        }

        @Test
        @DisplayName("should return 404 when project not found")
        fun `should return 404 when project not found`() {
            // Given
            val nonExistentProjectId = 999L

            every {
                sqlFolderService.getFolderById(nonExistentProjectId, folderId)
            } throws ProjectNotFoundException(nonExistentProjectId)

            // When & Then
            mockMvc
                .perform(get("/api/v1/projects/$nonExistentProjectId/sql/folders/$folderId"))
                .andExpect(status().isNotFound)
        }

        @Test
        @DisplayName("should return 404 when folder belongs to different project")
        fun `should return 404 when folder belongs to different project`() {
            // Given
            val differentProjectId = 2L

            // Service returns null because folder doesn't belong to this project
            every { sqlFolderService.getFolderById(differentProjectId, folderId) } returns null

            // When & Then
            mockMvc
                .perform(get("/api/v1/projects/$differentProjectId/sql/folders/$folderId"))
                .andExpect(status().isNotFound)
        }
    }

    @Nested
    @DisplayName("DELETE /api/v1/projects/{projectId}/sql/folders/{folderId}")
    inner class DeleteSqlFolder {
        @Test
        @DisplayName("should delete folder successfully")
        fun `should delete folder successfully`() {
            // Given
            every { sqlFolderService.deleteFolder(projectId, folderId) } returns Unit

            // When & Then
            mockMvc
                .perform(
                    delete("/api/v1/projects/$projectId/sql/folders/$folderId")
                        .with(csrf()),
                ).andExpect(status().isNoContent)

            verify(exactly = 1) { sqlFolderService.deleteFolder(projectId, folderId) }
        }

        @Test
        @DisplayName("should return 404 when folder not found")
        fun `should return 404 when folder not found`() {
            // Given
            val nonExistentFolderId = 999L

            every {
                sqlFolderService.deleteFolder(projectId, nonExistentFolderId)
            } throws SqlFolderNotFoundException(nonExistentFolderId, projectId)

            // When & Then
            mockMvc
                .perform(
                    delete("/api/v1/projects/$projectId/sql/folders/$nonExistentFolderId")
                        .with(csrf()),
                ).andExpect(status().isNotFound)

            verify(exactly = 1) { sqlFolderService.deleteFolder(projectId, nonExistentFolderId) }
        }

        @Test
        @DisplayName("should return 404 when project not found")
        fun `should return 404 when project not found`() {
            // Given
            val nonExistentProjectId = 999L

            every {
                sqlFolderService.deleteFolder(nonExistentProjectId, folderId)
            } throws ProjectNotFoundException(nonExistentProjectId)

            // When & Then
            mockMvc
                .perform(
                    delete("/api/v1/projects/$nonExistentProjectId/sql/folders/$folderId")
                        .with(csrf()),
                ).andExpect(status().isNotFound)
        }

        @Test
        @DisplayName("should return 404 when folder belongs to different project")
        fun `should return 404 when folder belongs to different project`() {
            // Given
            val differentProjectId = 2L

            // Service throws exception because folder doesn't belong to this project
            every {
                sqlFolderService.deleteFolder(differentProjectId, folderId)
            } throws SqlFolderNotFoundException(folderId, differentProjectId)

            // When & Then
            mockMvc
                .perform(
                    delete("/api/v1/projects/$differentProjectId/sql/folders/$folderId")
                        .with(csrf()),
                ).andExpect(status().isNotFound)
        }
    }

    @Nested
    @DisplayName("Integration Tests")
    inner class IntegrationTests {
        @Test
        @DisplayName("should handle complete create-get-delete flow")
        fun `should handle complete create-get-delete flow`() {
            // === CREATE ===
            val createRequest =
                CreateSqlFolderRequest(
                    name = "integration-test-folder",
                    description = "Folder for integration testing",
                    displayOrder = 5,
                )

            val createdFolder =
                createTestFolder(
                    id = 200L,
                    name = createRequest.name,
                    displayOrder = createRequest.displayOrder ?: 0,
                    description = createRequest.description,
                )

            every {
                sqlFolderService.createFolder(
                    projectId = projectId,
                    name = createRequest.name,
                    description = createRequest.description,
                    displayOrder = createRequest.displayOrder ?: 0,
                )
            } returns createdFolder

            mockMvc
                .perform(
                    post("/api/v1/projects/$projectId/sql/folders")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(createRequest)),
                ).andExpect(status().isCreated)
                .andExpect(jsonPath("$.name").value("integration-test-folder"))

            // === GET ===
            every { sqlFolderService.getFolderById(projectId, 200L) } returns createdFolder

            mockMvc
                .perform(get("/api/v1/projects/$projectId/sql/folders/200"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.name").value("integration-test-folder"))

            // === LIST ===
            every { sqlFolderService.listFolders(projectId) } returns listOf(createdFolder)

            mockMvc
                .perform(get("/api/v1/projects/$projectId/sql/folders"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].name").value("integration-test-folder"))

            // === DELETE ===
            every { sqlFolderService.deleteFolder(projectId, 200L) } returns Unit

            mockMvc
                .perform(
                    delete("/api/v1/projects/$projectId/sql/folders/200")
                        .with(csrf()),
                ).andExpect(status().isNoContent)

            // Verify all calls
            verify(exactly = 1) {
                sqlFolderService.createFolder(
                    projectId = projectId,
                    name = createRequest.name,
                    description = createRequest.description,
                    displayOrder = createRequest.displayOrder ?: 0,
                )
            }
            verify(exactly = 1) { sqlFolderService.getFolderById(projectId, 200L) }
            verify(exactly = 1) { sqlFolderService.listFolders(projectId) }
            verify(exactly = 1) { sqlFolderService.deleteFolder(projectId, 200L) }
        }
    }

    // Helper function to create test folders
    private fun createTestFolder(
        id: Long,
        name: String,
        displayOrder: Int,
        description: String? = null,
    ): SqlFolderEntity =
        SqlFolderEntity(
            projectId = projectId,
            name = name,
            description = description,
            displayOrder = displayOrder,
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
