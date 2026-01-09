package com.dataops.basecamp.controller

import com.dataops.basecamp.common.exception.ProjectAlreadyExistsException
import com.dataops.basecamp.common.exception.ProjectNotFoundException
import com.dataops.basecamp.domain.entity.project.ProjectEntity
import com.dataops.basecamp.domain.service.ProjectService
import com.dataops.basecamp.dto.project.CreateProjectRequest
import com.dataops.basecamp.dto.project.UpdateProjectRequest
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
import org.springframework.data.domain.Pageable
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
 * ProjectController REST API Tests
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
class ProjectControllerTest {
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

    private lateinit var testProjectEntity: ProjectEntity

    @BeforeEach
    fun setUp() {
        testProjectEntity =
            ProjectEntity(
                name = "marketing-analytics",
                displayName = "Marketing Analytics",
                description = "Marketing analytics project for tracking campaign performance",
            ).apply {
                val idField = this::class.java.superclass.getDeclaredField("id")
                idField.isAccessible = true
                idField.set(this, 1L)

                val createdAtField = this::class.java.superclass.getDeclaredField("createdAt")
                createdAtField.isAccessible = true
                createdAtField.set(this, LocalDateTime.of(2024, 1, 1, 9, 0, 0))

                val updatedAtField = this::class.java.superclass.getDeclaredField("updatedAt")
                updatedAtField.isAccessible = true
                updatedAtField.set(this, LocalDateTime.of(2024, 1, 1, 10, 0, 0))
            }
    }

    @Nested
    @DisplayName("GET /api/v1/projects")
    inner class ListProjects {
        @Test
        @DisplayName("should return empty list when no projects exist")
        fun `should return empty list when no projects exist`() {
            // Given
            val pageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "updatedAt"))
            every { projectService.listProjects(null, pageable) } returns PageImpl(emptyList())

            // When & Then
            mockMvc
                .perform(get("/api/v1/projects"))
                .andExpect(status().isOk)
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content.length()").value(0))
                .andExpect(jsonPath("$.totalElements").value(0))

            verify(exactly = 1) { projectService.listProjects(null, any<Pageable>()) }
        }

        @Test
        @DisplayName("should return projects list")
        fun `should return projects list`() {
            // Given
            val projects = listOf(testProjectEntity)
            val pageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "updatedAt"))
            every { projectService.listProjects(null, pageable) } returns PageImpl(projects, pageable, 1)

            // When & Then
            mockMvc
                .perform(get("/api/v1/projects"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].name").value("marketing-analytics"))
                .andExpect(jsonPath("$.content[0].displayName").value("Marketing Analytics"))
                .andExpect(jsonPath("$.totalElements").value(1))

            verify(exactly = 1) { projectService.listProjects(null, any<Pageable>()) }
        }

        @Test
        @DisplayName("should filter projects by search term")
        fun `should filter projects by search term`() {
            // Given
            val search = "marketing"
            val projects = listOf(testProjectEntity)
            val pageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "updatedAt"))
            every { projectService.listProjects(search, pageable) } returns PageImpl(projects, pageable, 1)

            // When & Then
            mockMvc
                .perform(
                    get("/api/v1/projects")
                        .param("search", search),
                ).andExpect(status().isOk)
                .andExpect(jsonPath("$.content.length()").value(1))

            verify(exactly = 1) { projectService.listProjects(search, any<Pageable>()) }
        }

        @Test
        @DisplayName("should apply pagination correctly")
        fun `should apply pagination correctly`() {
            // Given
            val page = 1
            val size = 10
            val pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "updatedAt"))
            every { projectService.listProjects(null, pageable) } returns PageImpl(emptyList(), pageable, 25)

            // When & Then
            mockMvc
                .perform(
                    get("/api/v1/projects")
                        .param("page", page.toString())
                        .param("size", size.toString()),
                ).andExpect(status().isOk)
                .andExpect(jsonPath("$.page").value(page))
                .andExpect(jsonPath("$.size").value(size))
                .andExpect(jsonPath("$.totalElements").value(25))
                .andExpect(jsonPath("$.totalPages").value(3))

            verify(exactly = 1) { projectService.listProjects(null, pageable) }
        }

        @Test
        @DisplayName("should return 400 when page is negative")
        fun `should return 400 when page is negative`() {
            // When & Then
            mockMvc
                .perform(
                    get("/api/v1/projects")
                        .param("page", "-1"),
                ).andExpect(status().isBadRequest)
        }

        @Test
        @DisplayName("should return 400 when size is less than 1")
        fun `should return 400 when size is less than 1`() {
            // When & Then
            mockMvc
                .perform(
                    get("/api/v1/projects")
                        .param("size", "0"),
                ).andExpect(status().isBadRequest)
        }

        @Test
        @DisplayName("should return 400 when size exceeds maximum")
        fun `should return 400 when size exceeds maximum`() {
            // When & Then
            mockMvc
                .perform(
                    get("/api/v1/projects")
                        .param("size", "101"), // Exceeds max 100
                ).andExpect(status().isBadRequest)
        }
    }

    @Nested
    @DisplayName("POST /api/v1/projects")
    inner class CreateProject {
        @Test
        @DisplayName("should create project successfully")
        fun `should create project successfully`() {
            // Given
            val request =
                CreateProjectRequest(
                    name = "new-project",
                    displayName = "New Project",
                    description = "A new project description",
                )

            val savedProject =
                ProjectEntity(
                    name = request.name,
                    displayName = request.displayName,
                    description = request.description,
                ).apply {
                    val idField = this::class.java.superclass.getDeclaredField("id")
                    idField.isAccessible = true
                    idField.set(this, 2L)

                    val createdAtField = this::class.java.superclass.getDeclaredField("createdAt")
                    createdAtField.isAccessible = true
                    createdAtField.set(this, LocalDateTime.now())

                    val updatedAtField = this::class.java.superclass.getDeclaredField("updatedAt")
                    updatedAtField.isAccessible = true
                    updatedAtField.set(this, LocalDateTime.now())
                }

            every { projectService.createProject(any()) } returns savedProject

            // When & Then
            mockMvc
                .perform(
                    post("/api/v1/projects")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)),
                ).andExpect(status().isCreated)
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(2))
                .andExpect(jsonPath("$.name").value("new-project"))
                .andExpect(jsonPath("$.displayName").value("New Project"))
                .andExpect(jsonPath("$.description").value("A new project description"))

            verify(exactly = 1) { projectService.createProject(any()) }
        }

        @Test
        @DisplayName("should return 400 for missing name")
        fun `should return 400 for missing name`() {
            // Given
            val invalidRequest =
                mapOf(
                    "displayName" to "New Project",
                )

            // When & Then
            mockMvc
                .perform(
                    post("/api/v1/projects")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(invalidRequest)),
                ).andExpect(status().isBadRequest)
        }

        @Test
        @DisplayName("should return 400 for missing displayName")
        fun `should return 400 for missing displayName`() {
            // Given
            val invalidRequest =
                mapOf(
                    "name" to "new-project",
                )

            // When & Then
            mockMvc
                .perform(
                    post("/api/v1/projects")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(invalidRequest)),
                ).andExpect(status().isBadRequest)
        }

        @Test
        @DisplayName("should return 400 for invalid name format - uppercase")
        fun `should return 400 for invalid name format - uppercase`() {
            // Given
            val invalidRequest =
                mapOf(
                    "name" to "Invalid-Name", // Contains uppercase
                    "displayName" to "Invalid Project",
                )

            // When & Then
            mockMvc
                .perform(
                    post("/api/v1/projects")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(invalidRequest)),
                ).andExpect(status().isBadRequest)
        }

        @Test
        @DisplayName("should return 400 for invalid name format - underscores")
        fun `should return 400 for invalid name format - underscores`() {
            // Given
            val invalidRequest =
                mapOf(
                    "name" to "invalid_name", // Uses underscore instead of hyphen
                    "displayName" to "Invalid Project",
                )

            // When & Then
            mockMvc
                .perform(
                    post("/api/v1/projects")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(invalidRequest)),
                ).andExpect(status().isBadRequest)
        }

        @Test
        @DisplayName("should return 400 for invalid name format - starts with number")
        fun `should return 400 for invalid name format - starts with number`() {
            // Given
            val invalidRequest =
                mapOf(
                    "name" to "1invalid-name", // Starts with number
                    "displayName" to "Invalid Project",
                )

            // When & Then
            mockMvc
                .perform(
                    post("/api/v1/projects")
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
                    "displayName" to "Valid Display Name",
                )

            // When & Then
            mockMvc
                .perform(
                    post("/api/v1/projects")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(invalidRequest)),
                ).andExpect(status().isBadRequest)
        }

        @Test
        @DisplayName("should return 400 when displayName exceeds maximum length")
        fun `should return 400 when displayName exceeds maximum length`() {
            // Given
            val tooLongDisplayName = "x".repeat(201) // Display name > 200 chars
            val invalidRequest =
                mapOf(
                    "name" to "valid-name",
                    "displayName" to tooLongDisplayName,
                )

            // When & Then
            mockMvc
                .perform(
                    post("/api/v1/projects")
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
                    "name" to "valid-name",
                    "displayName" to "Valid Display Name",
                    "description" to tooLongDescription,
                )

            // When & Then
            mockMvc
                .perform(
                    post("/api/v1/projects")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(invalidRequest)),
                ).andExpect(status().isBadRequest)
        }

        @Test
        @DisplayName("should return 409 when project already exists")
        fun `should return 409 when project already exists`() {
            // Given
            val request =
                CreateProjectRequest(
                    name = "existing-project",
                    displayName = "Existing Project",
                )

            every { projectService.createProject(any()) } throws
                ProjectAlreadyExistsException(request.name)

            // When & Then
            mockMvc
                .perform(
                    post("/api/v1/projects")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)),
                ).andExpect(status().isConflict)

            verify(exactly = 1) { projectService.createProject(any()) }
        }

        @Test
        @DisplayName("should create project without description")
        fun `should create project without description`() {
            // Given
            val request =
                CreateProjectRequest(
                    name = "project-no-desc",
                    displayName = "Project Without Description",
                    description = null,
                )

            val savedProject =
                ProjectEntity(
                    name = request.name,
                    displayName = request.displayName,
                    description = null,
                ).apply {
                    val idField = this::class.java.superclass.getDeclaredField("id")
                    idField.isAccessible = true
                    idField.set(this, 3L)

                    val createdAtField = this::class.java.superclass.getDeclaredField("createdAt")
                    createdAtField.isAccessible = true
                    createdAtField.set(this, LocalDateTime.now())

                    val updatedAtField = this::class.java.superclass.getDeclaredField("updatedAt")
                    updatedAtField.isAccessible = true
                    updatedAtField.set(this, LocalDateTime.now())
                }

            every { projectService.createProject(any()) } returns savedProject

            // When & Then
            mockMvc
                .perform(
                    post("/api/v1/projects")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)),
                ).andExpect(status().isCreated)
                .andExpect(jsonPath("$.name").value("project-no-desc"))
                .andExpect(jsonPath("$.description").doesNotExist())
        }
    }

    @Nested
    @DisplayName("GET /api/v1/projects/{projectId}")
    inner class GetProject {
        @Test
        @DisplayName("should return project by id")
        fun `should return project by id`() {
            // Given
            val projectId = 1L
            every { projectService.getProjectById(projectId) } returns testProjectEntity

            // When & Then
            mockMvc
                .perform(get("/api/v1/projects/$projectId"))
                .andExpect(status().isOk)
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("marketing-analytics"))
                .andExpect(jsonPath("$.displayName").value("Marketing Analytics"))
                .andExpect(
                    jsonPath("$.description").value("Marketing analytics project for tracking campaign performance"),
                ).andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.updatedAt").exists())

            verify(exactly = 1) { projectService.getProjectById(projectId) }
        }

        @Test
        @DisplayName("should return 404 when project not found")
        fun `should return 404 when project not found`() {
            // Given
            val projectId = 999L
            every { projectService.getProjectById(projectId) } returns null

            // When & Then
            mockMvc
                .perform(get("/api/v1/projects/$projectId"))
                .andExpect(status().isNotFound)

            verify(exactly = 1) { projectService.getProjectById(projectId) }
        }
    }

    @Nested
    @DisplayName("PUT /api/v1/projects/{projectId}")
    inner class UpdateProject {
        @Test
        @DisplayName("should update project displayName successfully")
        fun `should update project displayName successfully`() {
            // Given
            val projectId = 1L
            val request =
                UpdateProjectRequest(
                    displayName = "Updated Marketing Analytics",
                )

            val updatedProject =
                ProjectEntity(
                    name = testProjectEntity.name,
                    displayName = "Updated Marketing Analytics",
                    description = testProjectEntity.description,
                ).apply {
                    val idField = this::class.java.superclass.getDeclaredField("id")
                    idField.isAccessible = true
                    idField.set(this, projectId)

                    val createdAtField = this::class.java.superclass.getDeclaredField("createdAt")
                    createdAtField.isAccessible = true
                    createdAtField.set(this, LocalDateTime.now())

                    val updatedAtField = this::class.java.superclass.getDeclaredField("updatedAt")
                    updatedAtField.isAccessible = true
                    updatedAtField.set(this, LocalDateTime.now())
                }

            every {
                projectService.updateProject(projectId, request.displayName, request.description)
            } returns updatedProject

            // When & Then
            mockMvc
                .perform(
                    put("/api/v1/projects/$projectId")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)),
                ).andExpect(status().isOk)
                .andExpect(jsonPath("$.id").value(projectId))
                .andExpect(jsonPath("$.displayName").value("Updated Marketing Analytics"))

            verify(exactly = 1) { projectService.updateProject(projectId, request.displayName, request.description) }
        }

        @Test
        @DisplayName("should update project description successfully")
        fun `should update project description successfully`() {
            // Given
            val projectId = 1L
            val request =
                UpdateProjectRequest(
                    description = "Updated description",
                )

            val updatedProject =
                ProjectEntity(
                    name = testProjectEntity.name,
                    displayName = testProjectEntity.displayName,
                    description = "Updated description",
                ).apply {
                    val idField = this::class.java.superclass.getDeclaredField("id")
                    idField.isAccessible = true
                    idField.set(this, projectId)

                    val createdAtField = this::class.java.superclass.getDeclaredField("createdAt")
                    createdAtField.isAccessible = true
                    createdAtField.set(this, LocalDateTime.now())

                    val updatedAtField = this::class.java.superclass.getDeclaredField("updatedAt")
                    updatedAtField.isAccessible = true
                    updatedAtField.set(this, LocalDateTime.now())
                }

            every {
                projectService.updateProject(projectId, request.displayName, request.description)
            } returns updatedProject

            // When & Then
            mockMvc
                .perform(
                    put("/api/v1/projects/$projectId")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)),
                ).andExpect(status().isOk)
                .andExpect(jsonPath("$.description").value("Updated description"))
        }

        @Test
        @DisplayName("should update both displayName and description")
        fun `should update both displayName and description`() {
            // Given
            val projectId = 1L
            val request =
                UpdateProjectRequest(
                    displayName = "New Display Name",
                    description = "New description",
                )

            val updatedProject =
                ProjectEntity(
                    name = testProjectEntity.name,
                    displayName = "New Display Name",
                    description = "New description",
                ).apply {
                    val idField = this::class.java.superclass.getDeclaredField("id")
                    idField.isAccessible = true
                    idField.set(this, projectId)

                    val createdAtField = this::class.java.superclass.getDeclaredField("createdAt")
                    createdAtField.isAccessible = true
                    createdAtField.set(this, LocalDateTime.now())

                    val updatedAtField = this::class.java.superclass.getDeclaredField("updatedAt")
                    updatedAtField.isAccessible = true
                    updatedAtField.set(this, LocalDateTime.now())
                }

            every {
                projectService.updateProject(projectId, request.displayName, request.description)
            } returns updatedProject

            // When & Then
            mockMvc
                .perform(
                    put("/api/v1/projects/$projectId")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)),
                ).andExpect(status().isOk)
                .andExpect(jsonPath("$.displayName").value("New Display Name"))
                .andExpect(jsonPath("$.description").value("New description"))
        }

        @Test
        @DisplayName("should return 404 when updating non-existent project")
        fun `should return 404 when updating non-existent project`() {
            // Given
            val projectId = 999L
            val request =
                UpdateProjectRequest(
                    displayName = "Updated Name",
                )

            every { projectService.updateProject(projectId, request.displayName, request.description) } throws
                ProjectNotFoundException(projectId)

            // When & Then
            mockMvc
                .perform(
                    put("/api/v1/projects/$projectId")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)),
                ).andExpect(status().isNotFound)

            verify(exactly = 1) { projectService.updateProject(projectId, request.displayName, request.description) }
        }

        @Test
        @DisplayName("should return 400 when displayName exceeds maximum length")
        fun `should return 400 when displayName exceeds maximum length`() {
            // Given
            val projectId = 1L
            val tooLongDisplayName = "x".repeat(201)
            val invalidRequest =
                mapOf(
                    "displayName" to tooLongDisplayName,
                )

            // When & Then
            mockMvc
                .perform(
                    put("/api/v1/projects/$projectId")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(invalidRequest)),
                ).andExpect(status().isBadRequest)
        }

        @Test
        @DisplayName("should return 400 when description exceeds maximum length")
        fun `should return 400 when description exceeds maximum length`() {
            // Given
            val projectId = 1L
            val tooLongDescription = "x".repeat(1001)
            val invalidRequest =
                mapOf(
                    "description" to tooLongDescription,
                )

            // When & Then
            mockMvc
                .perform(
                    put("/api/v1/projects/$projectId")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(invalidRequest)),
                ).andExpect(status().isBadRequest)
        }

        @Test
        @DisplayName("should allow empty update request")
        fun `should allow empty update request`() {
            // Given
            val projectId = 1L
            val request = UpdateProjectRequest()

            every { projectService.updateProject(projectId, null, null) } returns testProjectEntity

            // When & Then
            mockMvc
                .perform(
                    put("/api/v1/projects/$projectId")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)),
                ).andExpect(status().isOk)
        }
    }

    @Nested
    @DisplayName("DELETE /api/v1/projects/{projectId}")
    inner class DeleteProject {
        @Test
        @DisplayName("should delete project successfully")
        fun `should delete project successfully`() {
            // Given
            val projectId = 1L

            every { projectService.deleteProject(projectId) } returns Unit

            // When & Then
            mockMvc
                .perform(
                    delete("/api/v1/projects/$projectId")
                        .with(csrf()),
                ).andExpect(status().isNoContent)

            verify(exactly = 1) { projectService.deleteProject(projectId) }
        }

        @Test
        @DisplayName("should return 404 when deleting non-existent project")
        fun `should return 404 when deleting non-existent project`() {
            // Given
            val projectId = 999L

            every { projectService.deleteProject(projectId) } throws
                ProjectNotFoundException(projectId)

            // When & Then
            mockMvc
                .perform(
                    delete("/api/v1/projects/$projectId")
                        .with(csrf()),
                ).andExpect(status().isNotFound)

            verify(exactly = 1) { projectService.deleteProject(projectId) }
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
                CreateProjectRequest(
                    name = "integration-test-project",
                    displayName = "Integration Test Project",
                    description = "Project for integration testing",
                )

            val createdProject =
                ProjectEntity(
                    name = createRequest.name,
                    displayName = createRequest.displayName,
                    description = createRequest.description,
                ).apply {
                    val idField = this::class.java.superclass.getDeclaredField("id")
                    idField.isAccessible = true
                    idField.set(this, 100L)

                    val createdAtField = this::class.java.superclass.getDeclaredField("createdAt")
                    createdAtField.isAccessible = true
                    createdAtField.set(this, LocalDateTime.now())

                    val updatedAtField = this::class.java.superclass.getDeclaredField("updatedAt")
                    updatedAtField.isAccessible = true
                    updatedAtField.set(this, LocalDateTime.now())
                }

            every { projectService.createProject(any()) } returns createdProject

            mockMvc
                .perform(
                    post("/api/v1/projects")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(createRequest)),
                ).andExpect(status().isCreated)
                .andExpect(jsonPath("$.name").value("integration-test-project"))

            // === GET ===
            every { projectService.getProjectById(100L) } returns createdProject

            mockMvc
                .perform(get("/api/v1/projects/100"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.name").value("integration-test-project"))

            // === UPDATE ===
            val updateRequest =
                UpdateProjectRequest(
                    displayName = "Updated Integration Test Project",
                )

            val updatedProject =
                ProjectEntity(
                    name = createdProject.name,
                    displayName = "Updated Integration Test Project",
                    description = createdProject.description,
                ).apply {
                    val idField = this::class.java.superclass.getDeclaredField("id")
                    idField.isAccessible = true
                    idField.set(this, 100L)

                    val createdAtField = this::class.java.superclass.getDeclaredField("createdAt")
                    createdAtField.isAccessible = true
                    createdAtField.set(this, LocalDateTime.now())

                    val updatedAtField = this::class.java.superclass.getDeclaredField("updatedAt")
                    updatedAtField.isAccessible = true
                    updatedAtField.set(this, LocalDateTime.now())
                }

            every {
                projectService.updateProject(100L, updateRequest.displayName, updateRequest.description)
            } returns updatedProject

            mockMvc
                .perform(
                    put("/api/v1/projects/100")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(updateRequest)),
                ).andExpect(status().isOk)
                .andExpect(jsonPath("$.displayName").value("Updated Integration Test Project"))

            // === DELETE ===
            every { projectService.deleteProject(100L) } returns Unit

            mockMvc
                .perform(
                    delete("/api/v1/projects/100")
                        .with(csrf()),
                ).andExpect(status().isNoContent)

            // Verify all calls
            verify(exactly = 1) { projectService.createProject(any()) }
            verify(exactly = 1) { projectService.getProjectById(100L) }
            verify(exactly = 1) { projectService.updateProject(100L, any(), any()) }
            verify(exactly = 1) { projectService.deleteProject(100L) }
        }
    }
}
