package com.dataops.basecamp.controller

import com.dataops.basecamp.common.enums.ComparisonStatus
import com.dataops.basecamp.common.enums.PullRequestState
import com.dataops.basecamp.common.exception.GitHubRepositoryAlreadyExistsException
import com.dataops.basecamp.common.exception.GitHubRepositoryNotFoundException
import com.dataops.basecamp.common.exception.GitHubRepositoryUrlAlreadyExistsException
import com.dataops.basecamp.domain.entity.github.GitHubRepositoryEntity
import com.dataops.basecamp.domain.projection.github.BranchComparison
import com.dataops.basecamp.domain.projection.github.CommitSummary
import com.dataops.basecamp.domain.projection.github.GitHubBranch
import com.dataops.basecamp.domain.projection.github.GitHubPullRequest
import com.dataops.basecamp.domain.service.GitHubService
import com.dataops.basecamp.dto.github.BranchComparisonResponse
import com.dataops.basecamp.dto.github.CommitSummaryResponse
import com.dataops.basecamp.dto.github.GitHubBranchResponse
import com.dataops.basecamp.dto.github.GitHubPullRequestListResponse
import com.dataops.basecamp.dto.github.GitHubPullRequestResponse
import com.dataops.basecamp.dto.github.GitHubRepositoryResponse
import com.dataops.basecamp.dto.github.ListMetadata
import com.dataops.basecamp.dto.github.RegisterGitHubRepositoryRequest
import com.dataops.basecamp.dto.github.UpdateGitHubRepositoryRequest
import com.dataops.basecamp.mapper.GitHubMapper
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
 * GitHubController REST API Tests
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
class GitHubControllerTest {
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

    private val jsonMapper: JsonMapper = JsonMapper.builder().build()

    @MockkBean(relaxed = true)
    private lateinit var gitHubService: GitHubService

    @MockkBean(relaxed = true)
    private lateinit var gitHubMapper: GitHubMapper

    // Test data
    private lateinit var testRepositoryEntity: GitHubRepositoryEntity
    private lateinit var testRepositoryResponse: GitHubRepositoryResponse
    private lateinit var testBranch: GitHubBranch
    private lateinit var testBranchResponse: GitHubBranchResponse
    private lateinit var testBranchComparison: BranchComparison
    private lateinit var testBranchComparisonResponse: BranchComparisonResponse
    private lateinit var testPullRequest: GitHubPullRequest
    private lateinit var testPullRequestResponse: GitHubPullRequestResponse
    private lateinit var testPullRequestListResponse: GitHubPullRequestListResponse

    private val testTeam = "@data-eng"
    private val testOwner = "1ambda"
    private val testRepoName = "dataops-platform"
    private val testRepoUrl = "https://github.com/1ambda/dataops-platform"
    private val testDescription = "DataOps Platform Repository"
    private val testTimestamp = LocalDateTime.of(2026, 1, 1, 12, 0, 0)

    @BeforeEach
    fun setUp() {
        // Setup GitHubRepositoryEntity
        testRepositoryEntity =
            GitHubRepositoryEntity(
                team = testTeam,
                owner = testOwner,
                repoName = testRepoName,
                repositoryUrl = testRepoUrl,
                defaultBranch = "main",
                developBranch = "develop",
                s3DevPath = "s3://bucket/dev",
                s3ProdPath = "s3://bucket/prod",
                isActive = true,
                description = testDescription,
            )

        // Setup GitHubRepositoryResponse
        testRepositoryResponse =
            GitHubRepositoryResponse(
                id = 1L,
                team = testTeam,
                owner = testOwner,
                repoName = testRepoName,
                repositoryUrl = testRepoUrl,
                defaultBranch = "main",
                developBranch = "develop",
                s3DevPath = "s3://bucket/dev",
                s3ProdPath = "s3://bucket/prod",
                isActive = true,
                description = testDescription,
                createdAt = testTimestamp,
                updatedAt = testTimestamp,
            )

        // Setup GitHubBranch
        testBranch =
            GitHubBranch(
                name = "main",
                sha = "abc123def456",
                isProtected = true,
                lastCommitDate = testTimestamp,
                lastCommitAuthor = "developer",
                lastCommitMessage = "Initial commit",
            )

        // Setup GitHubBranchResponse
        testBranchResponse =
            GitHubBranchResponse(
                name = "main",
                sha = "abc123def456",
                isProtected = true,
                lastCommitDate = testTimestamp,
                lastCommitAuthor = "developer",
                lastCommitMessage = "Initial commit",
            )

        // Setup BranchComparison
        testBranchComparison =
            BranchComparison(
                aheadBy = 2,
                behindBy = 0,
                status = ComparisonStatus.AHEAD,
                commits =
                    listOf(
                        CommitSummary(
                            sha = "commit1",
                            message = "First commit",
                            author = "developer",
                            date = testTimestamp,
                        ),
                    ),
            )

        // Setup BranchComparisonResponse
        testBranchComparisonResponse =
            BranchComparisonResponse(
                baseBranch = "main",
                headBranch = "feature/test",
                aheadBy = 2,
                behindBy = 0,
                status = "AHEAD",
                commits =
                    listOf(
                        CommitSummaryResponse(
                            sha = "commit1",
                            message = "First commit",
                            author = "developer",
                            date = testTimestamp,
                        ),
                    ),
            )

        // Setup GitHubPullRequest
        testPullRequest =
            GitHubPullRequest(
                number = 123L,
                title = "Feature: Add new functionality",
                state = PullRequestState.OPEN,
                sourceBranch = "feature/test",
                targetBranch = "main",
                author = "developer",
                createdAt = testTimestamp,
                updatedAt = testTimestamp,
                mergedAt = null,
                mergedBy = null,
                reviewers = listOf("reviewer1", "reviewer2"),
                labels = listOf("enhancement"),
                additions = 100,
                deletions = 50,
                changedFiles = 5,
                url = "https://github.com/1ambda/dataops-platform/pull/123",
            )

        // Setup GitHubPullRequestResponse
        testPullRequestResponse =
            GitHubPullRequestResponse(
                number = 123L,
                title = "Feature: Add new functionality",
                state = "OPEN",
                sourceBranch = "feature/test",
                targetBranch = "main",
                author = "developer",
                createdAt = testTimestamp,
                updatedAt = testTimestamp,
                mergedAt = null,
                mergedBy = null,
                reviewers = listOf("reviewer1", "reviewer2"),
                labels = listOf("enhancement"),
                additions = 100,
                deletions = 50,
                changedFiles = 5,
                url = "https://github.com/1ambda/dataops-platform/pull/123",
            )

        // Setup GitHubPullRequestListResponse
        testPullRequestListResponse =
            GitHubPullRequestListResponse(
                data = listOf(testPullRequestResponse),
                metadata =
                    ListMetadata(
                        total = 1,
                        limit = 30,
                        hasMore = false,
                    ),
            )
    }

    // ========================
    // Repository CRUD Tests
    // ========================

    @Nested
    @DisplayName("POST /api/v1/github/repositories")
    inner class RegisterRepository {
        @Test
        @DisplayName("should register repository successfully")
        fun `should register repository successfully`() {
            // Given
            val request =
                RegisterGitHubRepositoryRequest(
                    team = testTeam,
                    owner = testOwner,
                    repoName = testRepoName,
                    repositoryUrl = testRepoUrl,
                    defaultBranch = "main",
                    developBranch = "develop",
                    s3DevPath = "s3://bucket/dev",
                    s3ProdPath = "s3://bucket/prod",
                    description = testDescription,
                )

            every {
                gitHubService.registerRepository(
                    team = testTeam,
                    owner = testOwner,
                    repoName = testRepoName,
                    repositoryUrl = testRepoUrl,
                    defaultBranch = "main",
                    developBranch = "develop",
                    s3DevPath = "s3://bucket/dev",
                    s3ProdPath = "s3://bucket/prod",
                    description = testDescription,
                )
            } returns testRepositoryEntity
            every { gitHubMapper.toResponse(testRepositoryEntity) } returns testRepositoryResponse

            // When & Then
            mockMvc
                .perform(
                    post("/api/v1/github/repositories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)),
                ).andExpect(status().isCreated)
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.team").value(testTeam))
                .andExpect(jsonPath("$.owner").value(testOwner))
                .andExpect(jsonPath("$.repo_name").value(testRepoName))
                .andExpect(jsonPath("$.repository_url").value(testRepoUrl))
                .andExpect(jsonPath("$.default_branch").value("main"))
                .andExpect(jsonPath("$.develop_branch").value("develop"))
                .andExpect(jsonPath("$.is_active").value(true))

            verify(exactly = 1) {
                gitHubService.registerRepository(
                    team = testTeam,
                    owner = testOwner,
                    repoName = testRepoName,
                    repositoryUrl = testRepoUrl,
                    defaultBranch = "main",
                    developBranch = "develop",
                    s3DevPath = "s3://bucket/dev",
                    s3ProdPath = "s3://bucket/prod",
                    description = testDescription,
                )
            }
        }

        @Test
        @DisplayName("should return 409 when repository already exists for team")
        fun `should return 409 when repository already exists for team`() {
            // Given
            val request =
                RegisterGitHubRepositoryRequest(
                    team = testTeam,
                    owner = testOwner,
                    repoName = testRepoName,
                    repositoryUrl = testRepoUrl,
                    s3DevPath = "s3://bucket/dev",
                    s3ProdPath = "s3://bucket/prod",
                )

            every {
                gitHubService.registerRepository(any(), any(), any(), any(), any(), any(), any(), any(), any())
            } throws GitHubRepositoryAlreadyExistsException(testTeam)

            // When & Then
            mockMvc
                .perform(
                    post("/api/v1/github/repositories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)),
                ).andExpect(status().isConflict)
        }

        @Test
        @DisplayName("should return 409 when repository URL already registered")
        fun `should return 409 when repository URL already registered`() {
            // Given
            val request =
                RegisterGitHubRepositoryRequest(
                    team = testTeam,
                    owner = testOwner,
                    repoName = testRepoName,
                    repositoryUrl = testRepoUrl,
                    s3DevPath = "s3://bucket/dev",
                    s3ProdPath = "s3://bucket/prod",
                )

            every {
                gitHubService.registerRepository(any(), any(), any(), any(), any(), any(), any(), any(), any())
            } throws GitHubRepositoryUrlAlreadyExistsException(testRepoUrl)

            // When & Then
            mockMvc
                .perform(
                    post("/api/v1/github/repositories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)),
                ).andExpect(status().isConflict)
        }

        @Test
        @DisplayName("should return 400 when request is invalid - missing team")
        fun `should return 400 when request is invalid - missing team`() {
            // Given - empty team
            val request =
                mapOf(
                    "team" to "",
                    "owner" to testOwner,
                    "repo_name" to testRepoName,
                    "repository_url" to testRepoUrl,
                    "s3_dev_path" to "s3://bucket/dev",
                    "s3_prod_path" to "s3://bucket/prod",
                )

            // When & Then
            mockMvc
                .perform(
                    post("/api/v1/github/repositories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)),
                ).andExpect(status().isBadRequest)
        }

        @Test
        @DisplayName("should return 400 when request is invalid - missing required fields")
        fun `should return 400 when request is invalid - missing required fields`() {
            // Given - missing s3 paths
            val request =
                mapOf(
                    "team" to testTeam,
                    "owner" to testOwner,
                    "repo_name" to testRepoName,
                    "repository_url" to testRepoUrl,
                )

            // When & Then
            mockMvc
                .perform(
                    post("/api/v1/github/repositories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)),
                ).andExpect(status().isBadRequest)
        }
    }

    @Nested
    @DisplayName("GET /api/v1/github/repositories")
    inner class ListRepositories {
        @Test
        @DisplayName("should return empty list when no repositories exist")
        fun `should return empty list when no repositories exist`() {
            // Given
            every { gitHubService.getAllRepositories() } returns emptyList()

            // When & Then
            mockMvc
                .perform(get("/api/v1/github/repositories"))
                .andExpect(status().isOk)
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0))

            verify(exactly = 1) { gitHubService.getAllRepositories() }
        }

        @Test
        @DisplayName("should return repositories list")
        fun `should return repositories list`() {
            // Given
            every { gitHubService.getAllRepositories() } returns listOf(testRepositoryEntity)
            every { gitHubMapper.toResponse(testRepositoryEntity) } returns testRepositoryResponse

            // When & Then
            mockMvc
                .perform(get("/api/v1/github/repositories"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].team").value(testTeam))
                .andExpect(jsonPath("$[0].owner").value(testOwner))
                .andExpect(jsonPath("$[0].repo_name").value(testRepoName))

            verify(exactly = 1) { gitHubService.getAllRepositories() }
        }

        @Test
        @DisplayName("should filter repositories by team")
        fun `should filter repositories by team`() {
            // Given
            every { gitHubService.getRepositoryByTeam(testTeam) } returns testRepositoryEntity
            every { gitHubMapper.toResponse(testRepositoryEntity) } returns testRepositoryResponse

            // When & Then
            mockMvc
                .perform(
                    get("/api/v1/github/repositories")
                        .param("team", testTeam),
                ).andExpect(status().isOk)
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].team").value(testTeam))

            verify(exactly = 1) { gitHubService.getRepositoryByTeam(testTeam) }
            verify(exactly = 0) { gitHubService.getAllRepositories() }
        }

        @Test
        @DisplayName("should return empty list when team has no repository")
        fun `should return empty list when team has no repository`() {
            // Given
            every { gitHubService.getRepositoryByTeam("nonexistent-team") } returns null

            // When & Then
            mockMvc
                .perform(
                    get("/api/v1/github/repositories")
                        .param("team", "nonexistent-team"),
                ).andExpect(status().isOk)
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0))

            verify(exactly = 1) { gitHubService.getRepositoryByTeam("nonexistent-team") }
        }
    }

    @Nested
    @DisplayName("GET /api/v1/github/repositories/{id}")
    inner class GetRepository {
        @Test
        @DisplayName("should return repository by id")
        fun `should return repository by id`() {
            // Given
            val repositoryId = 1L
            every { gitHubService.getRepositoryOrThrow(repositoryId) } returns testRepositoryEntity
            every { gitHubMapper.toResponse(testRepositoryEntity) } returns testRepositoryResponse

            // When & Then
            mockMvc
                .perform(get("/api/v1/github/repositories/$repositoryId"))
                .andExpect(status().isOk)
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.team").value(testTeam))
                .andExpect(jsonPath("$.owner").value(testOwner))
                .andExpect(jsonPath("$.repo_name").value(testRepoName))
                .andExpect(jsonPath("$.is_active").value(true))

            verify(exactly = 1) { gitHubService.getRepositoryOrThrow(repositoryId) }
        }

        @Test
        @DisplayName("should return 404 when repository not found")
        fun `should return 404 when repository not found`() {
            // Given
            val repositoryId = 999L
            every { gitHubService.getRepositoryOrThrow(repositoryId) } throws
                GitHubRepositoryNotFoundException(repositoryId.toString())

            // When & Then
            mockMvc
                .perform(get("/api/v1/github/repositories/$repositoryId"))
                .andExpect(status().isNotFound)

            verify(exactly = 1) { gitHubService.getRepositoryOrThrow(repositoryId) }
        }
    }

    @Nested
    @DisplayName("PUT /api/v1/github/repositories/{id}")
    inner class UpdateRepository {
        @Test
        @DisplayName("should update repository successfully")
        fun `should update repository successfully`() {
            // Given
            val repositoryId = 1L
            val request =
                UpdateGitHubRepositoryRequest(
                    defaultBranch = "main",
                    developBranch = "development",
                    s3DevPath = "s3://new-bucket/dev",
                    s3ProdPath = "s3://new-bucket/prod",
                    description = "Updated description",
                    isActive = true,
                )

            val updatedEntity =
                GitHubRepositoryEntity(
                    team = testTeam,
                    owner = testOwner,
                    repoName = testRepoName,
                    repositoryUrl = testRepoUrl,
                    defaultBranch = "main",
                    developBranch = "development",
                    s3DevPath = "s3://new-bucket/dev",
                    s3ProdPath = "s3://new-bucket/prod",
                    isActive = true,
                    description = "Updated description",
                )

            val updatedResponse =
                testRepositoryResponse.copy(
                    developBranch = "development",
                    s3DevPath = "s3://new-bucket/dev",
                    s3ProdPath = "s3://new-bucket/prod",
                    description = "Updated description",
                )

            every {
                gitHubService.updateRepository(
                    id = repositoryId,
                    defaultBranch = "main",
                    developBranch = "development",
                    s3DevPath = "s3://new-bucket/dev",
                    s3ProdPath = "s3://new-bucket/prod",
                    description = "Updated description",
                    isActive = true,
                )
            } returns updatedEntity
            every { gitHubMapper.toResponse(updatedEntity) } returns updatedResponse

            // When & Then
            mockMvc
                .perform(
                    put("/api/v1/github/repositories/$repositoryId")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)),
                ).andExpect(status().isOk)
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.develop_branch").value("development"))
                .andExpect(jsonPath("$.s3_dev_path").value("s3://new-bucket/dev"))
                .andExpect(jsonPath("$.description").value("Updated description"))

            verify(exactly = 1) {
                gitHubService.updateRepository(
                    id = repositoryId,
                    defaultBranch = "main",
                    developBranch = "development",
                    s3DevPath = "s3://new-bucket/dev",
                    s3ProdPath = "s3://new-bucket/prod",
                    description = "Updated description",
                    isActive = true,
                )
            }
        }

        @Test
        @DisplayName("should return 404 when repository not found")
        fun `should return 404 when repository not found`() {
            // Given
            val repositoryId = 999L
            val request = UpdateGitHubRepositoryRequest(description = "test")

            every {
                gitHubService.updateRepository(any(), any(), any(), any(), any(), any(), any())
            } throws GitHubRepositoryNotFoundException(repositoryId.toString())

            // When & Then
            mockMvc
                .perform(
                    put("/api/v1/github/repositories/$repositoryId")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)),
                ).andExpect(status().isNotFound)
        }

        @Test
        @DisplayName("should update repository with partial fields")
        fun `should update repository with partial fields`() {
            // Given
            val repositoryId = 1L
            val request = UpdateGitHubRepositoryRequest(isActive = false)

            val updatedEntity =
                testRepositoryEntity.apply {
                    isActive = false
                }
            val updatedResponse = testRepositoryResponse.copy(isActive = false)

            every {
                gitHubService.updateRepository(
                    id = repositoryId,
                    defaultBranch = null,
                    developBranch = null,
                    s3DevPath = null,
                    s3ProdPath = null,
                    description = null,
                    isActive = false,
                )
            } returns updatedEntity
            every { gitHubMapper.toResponse(updatedEntity) } returns updatedResponse

            // When & Then
            mockMvc
                .perform(
                    put("/api/v1/github/repositories/$repositoryId")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)),
                ).andExpect(status().isOk)
                .andExpect(jsonPath("$.is_active").value(false))
        }
    }

    @Nested
    @DisplayName("DELETE /api/v1/github/repositories/{id}")
    inner class DeleteRepository {
        @Test
        @DisplayName("should delete repository successfully")
        fun `should delete repository successfully`() {
            // Given
            val repositoryId = 1L
            every { gitHubService.deleteRepository(repositoryId) } returns Unit

            // When & Then
            mockMvc
                .perform(delete("/api/v1/github/repositories/$repositoryId"))
                .andExpect(status().isNoContent)

            verify(exactly = 1) { gitHubService.deleteRepository(repositoryId) }
        }

        @Test
        @DisplayName("should return 404 when repository not found")
        fun `should return 404 when repository not found`() {
            // Given
            val repositoryId = 999L
            every {
                gitHubService.deleteRepository(repositoryId)
            } throws GitHubRepositoryNotFoundException(repositoryId.toString())

            // When & Then
            mockMvc
                .perform(delete("/api/v1/github/repositories/$repositoryId"))
                .andExpect(status().isNotFound)

            verify(exactly = 1) { gitHubService.deleteRepository(repositoryId) }
        }
    }

    // ========================
    // Branch Operations Tests
    // ========================

    @Nested
    @DisplayName("GET /api/v1/github/repositories/{id}/branches")
    inner class ListBranches {
        @Test
        @DisplayName("should return branches list")
        fun `should return branches list`() {
            // Given
            val repositoryId = 1L
            val branches = listOf(testBranch)
            val branchResponses = listOf(testBranchResponse)

            every { gitHubService.listBranches(repositoryId) } returns branches
            every { gitHubMapper.toBranchResponseList(branches) } returns branchResponses

            // When & Then
            mockMvc
                .perform(get("/api/v1/github/repositories/$repositoryId/branches"))
                .andExpect(status().isOk)
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("main"))
                .andExpect(jsonPath("$[0].sha").value("abc123def456"))
                .andExpect(jsonPath("$[0].is_protected").value(true))

            verify(exactly = 1) { gitHubService.listBranches(repositoryId) }
        }

        @Test
        @DisplayName("should return empty list when no branches")
        fun `should return empty list when no branches`() {
            // Given
            val repositoryId = 1L
            every { gitHubService.listBranches(repositoryId) } returns emptyList()
            every { gitHubMapper.toBranchResponseList(emptyList()) } returns emptyList()

            // When & Then
            mockMvc
                .perform(get("/api/v1/github/repositories/$repositoryId/branches"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0))
        }

        @Test
        @DisplayName("should return 404 when repository not found")
        fun `should return 404 when repository not found`() {
            // Given
            val repositoryId = 999L
            every { gitHubService.listBranches(repositoryId) } throws
                GitHubRepositoryNotFoundException(repositoryId.toString())

            // When & Then
            mockMvc
                .perform(get("/api/v1/github/repositories/$repositoryId/branches"))
                .andExpect(status().isNotFound)
        }
    }

    @Nested
    @DisplayName("GET /api/v1/github/repositories/{id}/branches/{branchName}")
    inner class GetBranch {
        @Test
        @DisplayName("should return branch by name")
        fun `should return branch by name`() {
            // Given
            val repositoryId = 1L
            val branchName = "main"

            every { gitHubService.getBranch(repositoryId, branchName) } returns testBranch
            every { gitHubMapper.toResponse(testBranch) } returns testBranchResponse

            // When & Then
            mockMvc
                .perform(get("/api/v1/github/repositories/$repositoryId/branches/$branchName"))
                .andExpect(status().isOk)
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.name").value("main"))
                .andExpect(jsonPath("$.sha").value("abc123def456"))
                .andExpect(jsonPath("$.is_protected").value(true))
                .andExpect(jsonPath("$.last_commit_author").value("developer"))

            verify(exactly = 1) { gitHubService.getBranch(repositoryId, branchName) }
        }

        @Test
        @DisplayName("should return 404 when branch not found")
        fun `should return 404 when branch not found`() {
            // Given
            val repositoryId = 1L
            val branchName = "nonexistent-branch"

            every { gitHubService.getBranch(repositoryId, branchName) } returns null

            // When & Then
            mockMvc
                .perform(get("/api/v1/github/repositories/$repositoryId/branches/$branchName"))
                .andExpect(status().isNotFound)

            verify(exactly = 1) { gitHubService.getBranch(repositoryId, branchName) }
        }

        @Test
        @DisplayName("should return 404 when repository not found")
        fun `should return 404 when repository not found`() {
            // Given
            val repositoryId = 999L
            val branchName = "main"

            every { gitHubService.getBranch(repositoryId, branchName) } throws
                GitHubRepositoryNotFoundException(repositoryId.toString())

            // When & Then
            mockMvc
                .perform(get("/api/v1/github/repositories/$repositoryId/branches/$branchName"))
                .andExpect(status().isNotFound)
        }
    }

    @Nested
    @DisplayName("GET /api/v1/github/repositories/{id}/branches/compare")
    inner class CompareBranches {
        @Test
        @DisplayName("should compare branches successfully")
        fun `should compare branches successfully`() {
            // Given
            val repositoryId = 1L
            val baseBranch = "main"
            val headBranch = "feature/test"

            every {
                gitHubService.compareBranches(repositoryId, baseBranch, headBranch)
            } returns testBranchComparison
            every {
                gitHubMapper.toResponse(testBranchComparison, baseBranch, headBranch)
            } returns testBranchComparisonResponse

            // When & Then
            mockMvc
                .perform(
                    get("/api/v1/github/repositories/$repositoryId/branches/compare")
                        .param("base", baseBranch)
                        .param("head", headBranch),
                ).andExpect(status().isOk)
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.base_branch").value("main"))
                .andExpect(jsonPath("$.head_branch").value("feature/test"))
                .andExpect(jsonPath("$.ahead_by").value(2))
                .andExpect(jsonPath("$.behind_by").value(0))
                .andExpect(jsonPath("$.status").value("AHEAD"))
                .andExpect(jsonPath("$.commits").isArray())
                .andExpect(jsonPath("$.commits.length()").value(1))

            verify(exactly = 1) { gitHubService.compareBranches(repositoryId, baseBranch, headBranch) }
        }

        @Test
        @DisplayName("should return 404 when comparison not available")
        fun `should return 404 when comparison not available`() {
            // Given
            val repositoryId = 1L
            val baseBranch = "main"
            val headBranch = "nonexistent-branch"

            every { gitHubService.compareBranches(repositoryId, baseBranch, headBranch) } returns null

            // When & Then
            mockMvc
                .perform(
                    get("/api/v1/github/repositories/$repositoryId/branches/compare")
                        .param("base", baseBranch)
                        .param("head", headBranch),
                ).andExpect(status().isNotFound)
        }

        @Test
        @DisplayName("should return 400 when base parameter is missing")
        fun `should return 400 when base parameter is missing`() {
            // Given
            val repositoryId = 1L

            // When & Then
            mockMvc
                .perform(
                    get("/api/v1/github/repositories/$repositoryId/branches/compare")
                        .param("head", "feature/test"),
                ).andExpect(status().isBadRequest)
        }

        @Test
        @DisplayName("should return 400 when head parameter is missing")
        fun `should return 400 when head parameter is missing`() {
            // Given
            val repositoryId = 1L

            // When & Then
            mockMvc
                .perform(
                    get("/api/v1/github/repositories/$repositoryId/branches/compare")
                        .param("base", "main"),
                ).andExpect(status().isBadRequest)
        }

        @Test
        @DisplayName("should return 404 when repository not found")
        fun `should return 404 when repository not found`() {
            // Given
            val repositoryId = 999L
            val baseBranch = "main"
            val headBranch = "feature/test"

            every {
                gitHubService.compareBranches(repositoryId, baseBranch, headBranch)
            } throws GitHubRepositoryNotFoundException(repositoryId.toString())

            // When & Then
            mockMvc
                .perform(
                    get("/api/v1/github/repositories/$repositoryId/branches/compare")
                        .param("base", baseBranch)
                        .param("head", headBranch),
                ).andExpect(status().isNotFound)
        }
    }

    // ========================
    // Pull Request Operations Tests
    // ========================

    @Nested
    @DisplayName("GET /api/v1/github/repositories/{id}/pulls")
    inner class ListPullRequests {
        @Test
        @DisplayName("should return pull requests list")
        fun `should return pull requests list`() {
            // Given
            val repositoryId = 1L
            val pullRequests = listOf(testPullRequest)

            every {
                gitHubService.listPullRequests(repositoryId, null, null, 30)
            } returns pullRequests
            every {
                gitHubMapper.toPullRequestListResponse(pullRequests, 30)
            } returns testPullRequestListResponse

            // When & Then
            mockMvc
                .perform(get("/api/v1/github/repositories/$repositoryId/pulls"))
                .andExpect(status().isOk)
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].number").value(123))
                .andExpect(jsonPath("$.data[0].title").value("Feature: Add new functionality"))
                .andExpect(jsonPath("$.data[0].state").value("OPEN"))
                .andExpect(jsonPath("$.data[0].source_branch").value("feature/test"))
                .andExpect(jsonPath("$.data[0].target_branch").value("main"))
                .andExpect(jsonPath("$.metadata.total").value(1))
                .andExpect(jsonPath("$.metadata.limit").value(30))
                .andExpect(jsonPath("$.metadata.has_more").value(false))

            verify(exactly = 1) { gitHubService.listPullRequests(repositoryId, null, null, 30) }
        }

        @Test
        @DisplayName("should filter pull requests by state")
        fun `should filter pull requests by state`() {
            // Given
            val repositoryId = 1L
            val state = PullRequestState.OPEN
            val pullRequests = listOf(testPullRequest)

            every {
                gitHubService.listPullRequests(repositoryId, state, null, 30)
            } returns pullRequests
            every {
                gitHubMapper.toPullRequestListResponse(pullRequests, 30)
            } returns testPullRequestListResponse

            // When & Then
            mockMvc
                .perform(
                    get("/api/v1/github/repositories/$repositoryId/pulls")
                        .param("state", "OPEN"),
                ).andExpect(status().isOk)
                .andExpect(jsonPath("$.data.length()").value(1))

            verify(exactly = 1) { gitHubService.listPullRequests(repositoryId, state, null, 30) }
        }

        @Test
        @DisplayName("should filter pull requests by target branch")
        fun `should filter pull requests by target branch`() {
            // Given
            val repositoryId = 1L
            val targetBranch = "main"
            val pullRequests = listOf(testPullRequest)

            every {
                gitHubService.listPullRequests(repositoryId, null, targetBranch, 30)
            } returns pullRequests
            every {
                gitHubMapper.toPullRequestListResponse(pullRequests, 30)
            } returns testPullRequestListResponse

            // When & Then
            mockMvc
                .perform(
                    get("/api/v1/github/repositories/$repositoryId/pulls")
                        .param("target_branch", targetBranch),
                ).andExpect(status().isOk)
                .andExpect(jsonPath("$.data.length()").value(1))

            verify(exactly = 1) { gitHubService.listPullRequests(repositoryId, null, targetBranch, 30) }
        }

        @Test
        @DisplayName("should apply limit parameter")
        fun `should apply limit parameter`() {
            // Given
            val repositoryId = 1L
            val limit = 10
            val pullRequests = listOf(testPullRequest)
            val response =
                testPullRequestListResponse.copy(
                    metadata = ListMetadata(total = 1, limit = limit, hasMore = false),
                )

            every {
                gitHubService.listPullRequests(repositoryId, null, null, limit)
            } returns pullRequests
            every {
                gitHubMapper.toPullRequestListResponse(pullRequests, limit)
            } returns response

            // When & Then
            mockMvc
                .perform(
                    get("/api/v1/github/repositories/$repositoryId/pulls")
                        .param("limit", limit.toString()),
                ).andExpect(status().isOk)
                .andExpect(jsonPath("$.metadata.limit").value(limit))

            verify(exactly = 1) { gitHubService.listPullRequests(repositoryId, null, null, limit) }
        }

        @Test
        @DisplayName("should return empty list when no pull requests")
        fun `should return empty list when no pull requests`() {
            // Given
            val repositoryId = 1L
            val emptyResponse =
                GitHubPullRequestListResponse(
                    data = emptyList(),
                    metadata = ListMetadata(total = 0, limit = 30, hasMore = false),
                )

            every { gitHubService.listPullRequests(repositoryId, null, null, 30) } returns emptyList()
            every { gitHubMapper.toPullRequestListResponse(emptyList(), 30) } returns emptyResponse

            // When & Then
            mockMvc
                .perform(get("/api/v1/github/repositories/$repositoryId/pulls"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(0))
                .andExpect(jsonPath("$.metadata.total").value(0))
        }

        @Test
        @DisplayName("should return 404 when repository not found")
        fun `should return 404 when repository not found`() {
            // Given
            val repositoryId = 999L
            every {
                gitHubService.listPullRequests(repositoryId, null, null, 30)
            } throws GitHubRepositoryNotFoundException(repositoryId.toString())

            // When & Then
            mockMvc
                .perform(get("/api/v1/github/repositories/$repositoryId/pulls"))
                .andExpect(status().isNotFound)
        }
    }

    @Nested
    @DisplayName("GET /api/v1/github/repositories/{id}/pulls/{prNumber}")
    inner class GetPullRequest {
        @Test
        @DisplayName("should return pull request by number")
        fun `should return pull request by number`() {
            // Given
            val repositoryId = 1L
            val prNumber = 123L

            every { gitHubService.getPullRequest(repositoryId, prNumber) } returns testPullRequest
            every { gitHubMapper.toResponse(testPullRequest) } returns testPullRequestResponse

            // When & Then
            mockMvc
                .perform(get("/api/v1/github/repositories/$repositoryId/pulls/$prNumber"))
                .andExpect(status().isOk)
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.number").value(123))
                .andExpect(jsonPath("$.title").value("Feature: Add new functionality"))
                .andExpect(jsonPath("$.state").value("OPEN"))
                .andExpect(jsonPath("$.source_branch").value("feature/test"))
                .andExpect(jsonPath("$.target_branch").value("main"))
                .andExpect(jsonPath("$.author").value("developer"))
                .andExpect(jsonPath("$.reviewers").isArray())
                .andExpect(jsonPath("$.reviewers.length()").value(2))
                .andExpect(jsonPath("$.labels").isArray())
                .andExpect(jsonPath("$.additions").value(100))
                .andExpect(jsonPath("$.deletions").value(50))
                .andExpect(jsonPath("$.changed_files").value(5))

            verify(exactly = 1) { gitHubService.getPullRequest(repositoryId, prNumber) }
        }

        @Test
        @DisplayName("should return 404 when pull request not found")
        fun `should return 404 when pull request not found`() {
            // Given
            val repositoryId = 1L
            val prNumber = 9999L

            every { gitHubService.getPullRequest(repositoryId, prNumber) } returns null

            // When & Then
            mockMvc
                .perform(get("/api/v1/github/repositories/$repositoryId/pulls/$prNumber"))
                .andExpect(status().isNotFound)

            verify(exactly = 1) { gitHubService.getPullRequest(repositoryId, prNumber) }
        }

        @Test
        @DisplayName("should return 404 when repository not found")
        fun `should return 404 when repository not found`() {
            // Given
            val repositoryId = 999L
            val prNumber = 123L

            every {
                gitHubService.getPullRequest(repositoryId, prNumber)
            } throws GitHubRepositoryNotFoundException(repositoryId.toString())

            // When & Then
            mockMvc
                .perform(get("/api/v1/github/repositories/$repositoryId/pulls/$prNumber"))
                .andExpect(status().isNotFound)
        }

        @Test
        @DisplayName("should return merged pull request details")
        fun `should return merged pull request details`() {
            // Given
            val repositoryId = 1L
            val prNumber = 100L
            val mergedPR =
                testPullRequest.copy(
                    number = 100L,
                    state = PullRequestState.MERGED,
                    mergedAt = testTimestamp,
                    mergedBy = "maintainer",
                )
            val mergedResponse =
                testPullRequestResponse.copy(
                    number = 100L,
                    state = "MERGED",
                    mergedAt = testTimestamp,
                    mergedBy = "maintainer",
                )

            every { gitHubService.getPullRequest(repositoryId, prNumber) } returns mergedPR
            every { gitHubMapper.toResponse(mergedPR) } returns mergedResponse

            // When & Then
            mockMvc
                .perform(get("/api/v1/github/repositories/$repositoryId/pulls/$prNumber"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.number").value(100))
                .andExpect(jsonPath("$.state").value("MERGED"))
                .andExpect(jsonPath("$.merged_by").value("maintainer"))

            verify(exactly = 1) { gitHubService.getPullRequest(repositoryId, prNumber) }
        }
    }
}
