package com.github.lambda.domain.service

import com.github.lambda.common.exception.GitHubRepositoryAlreadyExistsException
import com.github.lambda.common.exception.GitHubRepositoryNotFoundException
import com.github.lambda.common.exception.GitHubRepositoryUrlAlreadyExistsException
import com.github.lambda.domain.entity.github.GitHubRepositoryEntity
import com.github.lambda.domain.external.GitHubClient
import com.github.lambda.domain.model.github.BranchComparison
import com.github.lambda.domain.model.github.CommitSummary
import com.github.lambda.domain.model.github.ComparisonStatus
import com.github.lambda.domain.model.github.GitHubBranch
import com.github.lambda.domain.model.github.GitHubPullRequest
import com.github.lambda.domain.model.github.PullRequestState
import com.github.lambda.domain.repository.GitHubRepositoryDsl
import com.github.lambda.domain.repository.GitHubRepositoryJpa
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDateTime

/**
 * GitHubService Unit Tests
 *
 * Uses MockK to isolate dependencies and test pure business logic.
 */
@DisplayName("GitHubService Unit Tests")
class GitHubServiceTest {
    private val gitHubRepositoryJpa: GitHubRepositoryJpa = mockk()
    private val gitHubRepositoryDsl: GitHubRepositoryDsl = mockk()
    private val gitHubClient: GitHubClient = mockk()

    private val gitHubService =
        GitHubService(
            gitHubRepositoryJpa,
            gitHubRepositoryDsl,
            gitHubClient,
        )

    private lateinit var testRepository: GitHubRepositoryEntity
    private lateinit var testBranch: GitHubBranch
    private lateinit var testPullRequest: GitHubPullRequest
    private lateinit var testBranchComparison: BranchComparison

    @BeforeEach
    fun setUp() {
        testRepository =
            GitHubRepositoryEntity(
                team = "data-team",
                owner = "org-name",
                repoName = "data-models",
                repositoryUrl = "https://github.com/org-name/data-models",
                defaultBranch = "main",
                developBranch = "develop",
                s3DevPath = "s3://bucket/dev/data-models",
                s3ProdPath = "s3://bucket/prod/data-models",
                description = "Data models repository",
            )

        testBranch =
            GitHubBranch(
                name = "main",
                sha = "abc123def456",
                isProtected = true,
                lastCommitDate = LocalDateTime.now().minusHours(1),
                lastCommitAuthor = "developer@example.com",
                lastCommitMessage = "feat: add new feature",
            )

        testPullRequest =
            GitHubPullRequest(
                number = 42,
                title = "feat: add new feature",
                state = PullRequestState.OPEN,
                sourceBranch = "feature/new-feature",
                targetBranch = "main",
                author = "developer",
                createdAt = LocalDateTime.now().minusDays(1),
                updatedAt = LocalDateTime.now(),
                mergedAt = null,
                mergedBy = null,
                reviewers = listOf("reviewer1", "reviewer2"),
                labels = listOf("feature", "ready-for-review"),
                additions = 100,
                deletions = 50,
                changedFiles = 5,
                url = "https://github.com/org-name/data-models/pull/42",
            )

        testBranchComparison =
            BranchComparison(
                aheadBy = 5,
                behindBy = 2,
                status = ComparisonStatus.DIVERGED,
                commits =
                    listOf(
                        CommitSummary(
                            sha = "abc123",
                            message = "feat: add feature",
                            author = "developer",
                            date = LocalDateTime.now().minusHours(2),
                        ),
                    ),
            )
    }

    @Nested
    @DisplayName("registerRepository")
    inner class RegisterRepository {
        @Test
        @DisplayName("should register repository successfully when team and URL are new")
        fun `should register repository successfully when team and URL are new`() {
            // Given
            val team = "new-team"
            val owner = "new-org"
            val repoName = "new-repo"
            val repositoryUrl = "https://github.com/new-org/new-repo"
            val s3DevPath = "s3://bucket/dev/new-repo"
            val s3ProdPath = "s3://bucket/prod/new-repo"

            every { gitHubRepositoryJpa.existsByTeam(team) } returns false
            every { gitHubRepositoryJpa.existsByRepositoryUrl(repositoryUrl) } returns false
            every { gitHubRepositoryDsl.findByOwnerAndRepoName(owner, repoName) } returns null

            val savedRepoSlot = slot<GitHubRepositoryEntity>()
            every { gitHubRepositoryJpa.save(capture(savedRepoSlot)) } answers { savedRepoSlot.captured }

            // When
            val result =
                gitHubService.registerRepository(
                    team = team,
                    owner = owner,
                    repoName = repoName,
                    repositoryUrl = repositoryUrl,
                    s3DevPath = s3DevPath,
                    s3ProdPath = s3ProdPath,
                    description = "New repository",
                )

            // Then
            assertThat(result.team).isEqualTo(team)
            assertThat(result.owner).isEqualTo(owner)
            assertThat(result.repoName).isEqualTo(repoName)
            assertThat(result.repositoryUrl).isEqualTo(repositoryUrl)
            assertThat(result.s3DevPath).isEqualTo(s3DevPath)
            assertThat(result.s3ProdPath).isEqualTo(s3ProdPath)
            assertThat(result.isActive).isTrue()

            verify(exactly = 1) { gitHubRepositoryJpa.existsByTeam(team) }
            verify(exactly = 1) { gitHubRepositoryJpa.existsByRepositoryUrl(repositoryUrl) }
            verify(exactly = 1) { gitHubRepositoryDsl.findByOwnerAndRepoName(owner, repoName) }
            verify(exactly = 1) { gitHubRepositoryJpa.save(any()) }
        }

        @Test
        @DisplayName("should throw GitHubRepositoryAlreadyExistsException when team already has a repository")
        fun `should throw GitHubRepositoryAlreadyExistsException when team already has a repository`() {
            // Given
            val team = "existing-team"
            every { gitHubRepositoryJpa.existsByTeam(team) } returns true

            // When & Then
            val exception =
                assertThrows<GitHubRepositoryAlreadyExistsException> {
                    gitHubService.registerRepository(
                        team = team,
                        owner = "org",
                        repoName = "repo",
                        repositoryUrl = "https://github.com/org/repo",
                        s3DevPath = "s3://bucket/dev/repo",
                        s3ProdPath = "s3://bucket/prod/repo",
                    )
                }

            assertThat(exception.team).isEqualTo(team)
            verify(exactly = 1) { gitHubRepositoryJpa.existsByTeam(team) }
            verify(exactly = 0) { gitHubRepositoryJpa.save(any()) }
        }

        @Test
        @DisplayName("should throw GitHubRepositoryUrlAlreadyExistsException when URL is already registered")
        fun `should throw GitHubRepositoryUrlAlreadyExistsException when URL is already registered`() {
            // Given
            val team = "new-team"
            val repositoryUrl = "https://github.com/org/existing-repo"

            every { gitHubRepositoryJpa.existsByTeam(team) } returns false
            every { gitHubRepositoryJpa.existsByRepositoryUrl(repositoryUrl) } returns true

            // When & Then
            val exception =
                assertThrows<GitHubRepositoryUrlAlreadyExistsException> {
                    gitHubService.registerRepository(
                        team = team,
                        owner = "org",
                        repoName = "existing-repo",
                        repositoryUrl = repositoryUrl,
                        s3DevPath = "s3://bucket/dev/repo",
                        s3ProdPath = "s3://bucket/prod/repo",
                    )
                }

            assertThat(exception.repositoryUrl).isEqualTo(repositoryUrl)
            verify(exactly = 0) { gitHubRepositoryJpa.save(any()) }
        }

        @Test
        @DisplayName("should throw GitHubRepositoryUrlAlreadyExistsException when owner/repoName combination exists")
        fun `should throw GitHubRepositoryUrlAlreadyExistsException when owner-repoName combination exists`() {
            // Given
            val team = "new-team"
            val owner = "existing-org"
            val repoName = "existing-repo"
            val repositoryUrl = "https://github.com/existing-org/existing-repo-2"

            every { gitHubRepositoryJpa.existsByTeam(team) } returns false
            every { gitHubRepositoryJpa.existsByRepositoryUrl(repositoryUrl) } returns false
            every { gitHubRepositoryDsl.findByOwnerAndRepoName(owner, repoName) } returns testRepository

            // When & Then
            val exception =
                assertThrows<GitHubRepositoryUrlAlreadyExistsException> {
                    gitHubService.registerRepository(
                        team = team,
                        owner = owner,
                        repoName = repoName,
                        repositoryUrl = repositoryUrl,
                        s3DevPath = "s3://bucket/dev/repo",
                        s3ProdPath = "s3://bucket/prod/repo",
                    )
                }

            assertThat(exception.repositoryUrl).isEqualTo("$owner/$repoName")
            verify(exactly = 0) { gitHubRepositoryJpa.save(any()) }
        }

        @Test
        @DisplayName("should use default branch names when not specified")
        fun `should use default branch names when not specified`() {
            // Given
            val team = "new-team"
            val owner = "org"
            val repoName = "repo"
            val repositoryUrl = "https://github.com/org/repo"

            every { gitHubRepositoryJpa.existsByTeam(team) } returns false
            every { gitHubRepositoryJpa.existsByRepositoryUrl(repositoryUrl) } returns false
            every { gitHubRepositoryDsl.findByOwnerAndRepoName(owner, repoName) } returns null

            val savedRepoSlot = slot<GitHubRepositoryEntity>()
            every { gitHubRepositoryJpa.save(capture(savedRepoSlot)) } answers { savedRepoSlot.captured }

            // When
            val result =
                gitHubService.registerRepository(
                    team = team,
                    owner = owner,
                    repoName = repoName,
                    repositoryUrl = repositoryUrl,
                    s3DevPath = "s3://bucket/dev/repo",
                    s3ProdPath = "s3://bucket/prod/repo",
                )

            // Then
            assertThat(result.defaultBranch).isEqualTo("main")
            assertThat(result.developBranch).isEqualTo("develop")
        }
    }

    @Nested
    @DisplayName("getRepository")
    inner class GetRepository {
        @Test
        @DisplayName("should return repository when found by ID")
        fun `should return repository when found by ID`() {
            // Given
            val id = 1L
            every { gitHubRepositoryJpa.findByIdOrNull(id) } returns testRepository

            // When
            val result = gitHubService.getRepository(id)

            // Then
            assertThat(result).isNotNull()
            assertThat(result?.team).isEqualTo(testRepository.team)
            assertThat(result?.owner).isEqualTo(testRepository.owner)
            verify(exactly = 1) { gitHubRepositoryJpa.findByIdOrNull(id) }
        }

        @Test
        @DisplayName("should return null when repository not found")
        fun `should return null when repository not found`() {
            // Given
            val id = 999L
            every { gitHubRepositoryJpa.findByIdOrNull(id) } returns null

            // When
            val result = gitHubService.getRepository(id)

            // Then
            assertThat(result).isNull()
            verify(exactly = 1) { gitHubRepositoryJpa.findByIdOrNull(id) }
        }

        @Test
        @DisplayName("should return null for soft-deleted repository")
        fun `should return null for soft-deleted repository`() {
            // Given
            val id = 1L
            val deletedRepository =
                GitHubRepositoryEntity(
                    team = "deleted-team",
                    owner = "org",
                    repoName = "repo",
                    repositoryUrl = "https://github.com/org/repo",
                    s3DevPath = "s3://bucket/dev/repo",
                    s3ProdPath = "s3://bucket/prod/repo",
                ).apply {
                    deletedAt = LocalDateTime.now()
                }
            every { gitHubRepositoryJpa.findByIdOrNull(id) } returns deletedRepository

            // When
            val result = gitHubService.getRepository(id)

            // Then
            assertThat(result).isNull()
        }
    }

    @Nested
    @DisplayName("getRepositoryOrThrow")
    inner class GetRepositoryOrThrow {
        @Test
        @DisplayName("should return repository when found")
        fun `should return repository when found`() {
            // Given
            val id = 1L
            every { gitHubRepositoryJpa.findByIdOrNull(id) } returns testRepository

            // When
            val result = gitHubService.getRepositoryOrThrow(id)

            // Then
            assertThat(result.team).isEqualTo(testRepository.team)
        }

        @Test
        @DisplayName("should throw GitHubRepositoryNotFoundException when not found")
        fun `should throw GitHubRepositoryNotFoundException when not found`() {
            // Given
            val id = 999L
            every { gitHubRepositoryJpa.findByIdOrNull(id) } returns null

            // When & Then
            val exception =
                assertThrows<GitHubRepositoryNotFoundException> {
                    gitHubService.getRepositoryOrThrow(id)
                }

            assertThat(exception.identifier).contains("id=$id")
        }
    }

    @Nested
    @DisplayName("getRepositoryByTeam")
    inner class GetRepositoryByTeam {
        @Test
        @DisplayName("should return repository when found by team")
        fun `should return repository when found by team`() {
            // Given
            val team = "data-team"
            every { gitHubRepositoryJpa.findByTeam(team) } returns testRepository

            // When
            val result = gitHubService.getRepositoryByTeam(team)

            // Then
            assertThat(result).isNotNull()
            assertThat(result?.team).isEqualTo(team)
            verify(exactly = 1) { gitHubRepositoryJpa.findByTeam(team) }
        }

        @Test
        @DisplayName("should return null when repository not found by team")
        fun `should return null when repository not found by team`() {
            // Given
            val team = "nonexistent-team"
            every { gitHubRepositoryJpa.findByTeam(team) } returns null

            // When
            val result = gitHubService.getRepositoryByTeam(team)

            // Then
            assertThat(result).isNull()
            verify(exactly = 1) { gitHubRepositoryJpa.findByTeam(team) }
        }

        @Test
        @DisplayName("should return null for soft-deleted repository by team")
        fun `should return null for soft-deleted repository by team`() {
            // Given
            val team = "deleted-team"
            val deletedRepository =
                GitHubRepositoryEntity(
                    team = team,
                    owner = "org",
                    repoName = "repo",
                    repositoryUrl = "https://github.com/org/repo",
                    s3DevPath = "s3://bucket/dev/repo",
                    s3ProdPath = "s3://bucket/prod/repo",
                ).apply {
                    deletedAt = LocalDateTime.now()
                }
            every { gitHubRepositoryJpa.findByTeam(team) } returns deletedRepository

            // When
            val result = gitHubService.getRepositoryByTeam(team)

            // Then
            assertThat(result).isNull()
        }
    }

    @Nested
    @DisplayName("getRepositoryByTeamOrThrow")
    inner class GetRepositoryByTeamOrThrow {
        @Test
        @DisplayName("should return repository when found by team")
        fun `should return repository when found by team`() {
            // Given
            val team = "data-team"
            every { gitHubRepositoryJpa.findByTeam(team) } returns testRepository

            // When
            val result = gitHubService.getRepositoryByTeamOrThrow(team)

            // Then
            assertThat(result.team).isEqualTo(team)
        }

        @Test
        @DisplayName("should throw GitHubRepositoryNotFoundException when not found by team")
        fun `should throw GitHubRepositoryNotFoundException when not found by team`() {
            // Given
            val team = "nonexistent-team"
            every { gitHubRepositoryJpa.findByTeam(team) } returns null

            // When & Then
            val exception =
                assertThrows<GitHubRepositoryNotFoundException> {
                    gitHubService.getRepositoryByTeamOrThrow(team)
                }

            assertThat(exception.identifier).contains("team=$team")
        }
    }

    @Nested
    @DisplayName("getAllRepositories")
    inner class GetAllRepositories {
        @Test
        @DisplayName("should return all non-deleted repositories")
        fun `should return all non-deleted repositories`() {
            // Given
            val repositories = listOf(testRepository)
            every { gitHubRepositoryJpa.findAllByOrderByUpdatedAtDesc() } returns repositories

            // When
            val result = gitHubService.getAllRepositories()

            // Then
            assertThat(result).hasSize(1)
            assertThat(result[0].team).isEqualTo(testRepository.team)
            verify(exactly = 1) { gitHubRepositoryJpa.findAllByOrderByUpdatedAtDesc() }
        }

        @Test
        @DisplayName("should return empty list when no repositories exist")
        fun `should return empty list when no repositories exist`() {
            // Given
            every { gitHubRepositoryJpa.findAllByOrderByUpdatedAtDesc() } returns emptyList()

            // When
            val result = gitHubService.getAllRepositories()

            // Then
            assertThat(result).isEmpty()
        }

        @Test
        @DisplayName("should filter out soft-deleted repositories")
        fun `should filter out soft-deleted repositories`() {
            // Given
            val deletedRepository =
                GitHubRepositoryEntity(
                    team = "deleted-team",
                    owner = "org",
                    repoName = "repo",
                    repositoryUrl = "https://github.com/org/repo",
                    s3DevPath = "s3://bucket/dev/repo",
                    s3ProdPath = "s3://bucket/prod/repo",
                ).apply {
                    deletedAt = LocalDateTime.now()
                }
            val repositories = listOf(testRepository, deletedRepository)
            every { gitHubRepositoryJpa.findAllByOrderByUpdatedAtDesc() } returns repositories

            // When
            val result = gitHubService.getAllRepositories()

            // Then
            assertThat(result).hasSize(1)
            assertThat(result[0].team).isEqualTo(testRepository.team)
        }
    }

    @Nested
    @DisplayName("getActiveRepositories")
    inner class GetActiveRepositories {
        @Test
        @DisplayName("should return active repositories")
        fun `should return active repositories`() {
            // Given
            val repositories = listOf(testRepository)
            every { gitHubRepositoryDsl.findAllActive() } returns repositories

            // When
            val result = gitHubService.getActiveRepositories()

            // Then
            assertThat(result).hasSize(1)
            verify(exactly = 1) { gitHubRepositoryDsl.findAllActive() }
        }
    }

    @Nested
    @DisplayName("searchRepositories")
    inner class SearchRepositories {
        @Test
        @DisplayName("should search repositories by keyword")
        fun `should search repositories by keyword`() {
            // Given
            val keyword = "data"
            val repositories = listOf(testRepository)
            every { gitHubRepositoryDsl.searchByKeyword(keyword) } returns repositories

            // When
            val result = gitHubService.searchRepositories(keyword)

            // Then
            assertThat(result).hasSize(1)
            verify(exactly = 1) { gitHubRepositoryDsl.searchByKeyword(keyword) }
        }
    }

    @Nested
    @DisplayName("updateRepository")
    inner class UpdateRepository {
        @Test
        @DisplayName("should update repository successfully")
        fun `should update repository successfully`() {
            // Given
            val id = 1L
            val newDefaultBranch = "master"
            val newDevelopBranch = "dev"
            val newS3DevPath = "s3://bucket/dev/updated"
            val newS3ProdPath = "s3://bucket/prod/updated"
            val newDescription = "Updated description"

            every { gitHubRepositoryJpa.findByIdOrNull(id) } returns testRepository
            every { gitHubRepositoryJpa.save(any()) } answers { firstArg() }

            // When
            val result =
                gitHubService.updateRepository(
                    id = id,
                    defaultBranch = newDefaultBranch,
                    developBranch = newDevelopBranch,
                    s3DevPath = newS3DevPath,
                    s3ProdPath = newS3ProdPath,
                    description = newDescription,
                )

            // Then
            assertThat(result.defaultBranch).isEqualTo(newDefaultBranch)
            assertThat(result.developBranch).isEqualTo(newDevelopBranch)
            assertThat(result.s3DevPath).isEqualTo(newS3DevPath)
            assertThat(result.s3ProdPath).isEqualTo(newS3ProdPath)
            assertThat(result.description).isEqualTo(newDescription)

            verify(exactly = 1) { gitHubRepositoryJpa.findByIdOrNull(id) }
            verify(exactly = 1) { gitHubRepositoryJpa.save(any()) }
        }

        @Test
        @DisplayName("should throw GitHubRepositoryNotFoundException when repository not found")
        fun `should throw GitHubRepositoryNotFoundException when repository not found`() {
            // Given
            val id = 999L
            every { gitHubRepositoryJpa.findByIdOrNull(id) } returns null

            // When & Then
            assertThrows<GitHubRepositoryNotFoundException> {
                gitHubService.updateRepository(id = id, defaultBranch = "master")
            }

            verify(exactly = 0) { gitHubRepositoryJpa.save(any()) }
        }

        @Test
        @DisplayName("should handle partial update correctly")
        fun `should handle partial update correctly`() {
            // Given
            val id = 1L
            val originalDefaultBranch = testRepository.defaultBranch
            val originalDevelopBranch = testRepository.developBranch
            val newDescription = "Only description updated"

            every { gitHubRepositoryJpa.findByIdOrNull(id) } returns testRepository
            every { gitHubRepositoryJpa.save(any()) } answers { firstArg() }

            // When
            val result =
                gitHubService.updateRepository(
                    id = id,
                    description = newDescription,
                )

            // Then
            assertThat(result.defaultBranch).isEqualTo(originalDefaultBranch)
            assertThat(result.developBranch).isEqualTo(originalDevelopBranch)
            assertThat(result.description).isEqualTo(newDescription)
        }

        @Test
        @DisplayName("should update isActive status correctly")
        fun `should update isActive status correctly`() {
            // Given
            val id = 1L
            every { gitHubRepositoryJpa.findByIdOrNull(id) } returns testRepository
            every { gitHubRepositoryJpa.save(any()) } answers { firstArg() }

            // When
            val result = gitHubService.updateRepository(id = id, isActive = false)

            // Then
            assertThat(result.isActive).isFalse()
        }
    }

    @Nested
    @DisplayName("deleteRepository")
    inner class DeleteRepository {
        @Test
        @DisplayName("should soft delete repository successfully")
        fun `should soft delete repository successfully`() {
            // Given
            val id = 1L
            every { gitHubRepositoryJpa.findByIdOrNull(id) } returns testRepository
            every { gitHubRepositoryJpa.save(any()) } answers { firstArg() }

            // When
            gitHubService.deleteRepository(id)

            // Then
            assertThat(testRepository.deletedAt).isNotNull()
            assertThat(testRepository.isActive).isFalse()
            verify(exactly = 1) { gitHubRepositoryJpa.save(any()) }
        }

        @Test
        @DisplayName("should throw GitHubRepositoryNotFoundException when repository not found")
        fun `should throw GitHubRepositoryNotFoundException when repository not found`() {
            // Given
            val id = 999L
            every { gitHubRepositoryJpa.findByIdOrNull(id) } returns null

            // When & Then
            assertThrows<GitHubRepositoryNotFoundException> {
                gitHubService.deleteRepository(id)
            }

            verify(exactly = 0) { gitHubRepositoryJpa.save(any()) }
        }
    }

    @Nested
    @DisplayName("getRepositoriesByOwner")
    inner class GetRepositoriesByOwner {
        @Test
        @DisplayName("should return repositories by owner")
        fun `should return repositories by owner`() {
            // Given
            val owner = "org-name"
            val repositories = listOf(testRepository)
            every { gitHubRepositoryJpa.findByOwner(owner) } returns repositories

            // When
            val result = gitHubService.getRepositoriesByOwner(owner)

            // Then
            assertThat(result).hasSize(1)
            assertThat(result[0].owner).isEqualTo(owner)
            verify(exactly = 1) { gitHubRepositoryJpa.findByOwner(owner) }
        }

        @Test
        @DisplayName("should filter out soft-deleted repositories by owner")
        fun `should filter out soft-deleted repositories by owner`() {
            // Given
            val owner = "org-name"
            val deletedRepository =
                GitHubRepositoryEntity(
                    team = "deleted-team",
                    owner = owner,
                    repoName = "deleted-repo",
                    repositoryUrl = "https://github.com/org-name/deleted-repo",
                    s3DevPath = "s3://bucket/dev/deleted-repo",
                    s3ProdPath = "s3://bucket/prod/deleted-repo",
                ).apply {
                    deletedAt = LocalDateTime.now()
                }
            val repositories = listOf(testRepository, deletedRepository)
            every { gitHubRepositoryJpa.findByOwner(owner) } returns repositories

            // When
            val result = gitHubService.getRepositoriesByOwner(owner)

            // Then
            assertThat(result).hasSize(1)
            assertThat(result[0].repoName).isEqualTo(testRepository.repoName)
        }
    }

    @Nested
    @DisplayName("listBranches")
    inner class ListBranches {
        @Test
        @DisplayName("should return branches for repository")
        fun `should return branches for repository`() {
            // Given
            val repositoryId = 1L
            val branches = listOf(testBranch)

            every { gitHubRepositoryJpa.findByIdOrNull(repositoryId) } returns testRepository
            every { gitHubClient.listBranches(testRepository.owner, testRepository.repoName) } returns branches

            // When
            val result = gitHubService.listBranches(repositoryId)

            // Then
            assertThat(result).hasSize(1)
            assertThat(result[0].name).isEqualTo(testBranch.name)
            assertThat(result[0].sha).isEqualTo(testBranch.sha)
            verify(exactly = 1) { gitHubClient.listBranches(testRepository.owner, testRepository.repoName) }
        }

        @Test
        @DisplayName("should throw GitHubRepositoryNotFoundException when repository not found")
        fun `should throw GitHubRepositoryNotFoundException when repository not found`() {
            // Given
            val repositoryId = 999L
            every { gitHubRepositoryJpa.findByIdOrNull(repositoryId) } returns null

            // When & Then
            assertThrows<GitHubRepositoryNotFoundException> {
                gitHubService.listBranches(repositoryId)
            }

            verify(exactly = 0) { gitHubClient.listBranches(any(), any()) }
        }

        @Test
        @DisplayName("should return empty list when repository has no branches")
        fun `should return empty list when repository has no branches`() {
            // Given
            val repositoryId = 1L

            every { gitHubRepositoryJpa.findByIdOrNull(repositoryId) } returns testRepository
            every { gitHubClient.listBranches(testRepository.owner, testRepository.repoName) } returns emptyList()

            // When
            val result = gitHubService.listBranches(repositoryId)

            // Then
            assertThat(result).isEmpty()
        }
    }

    @Nested
    @DisplayName("getBranch")
    inner class GetBranch {
        @Test
        @DisplayName("should return branch when found")
        fun `should return branch when found`() {
            // Given
            val repositoryId = 1L
            val branchName = "main"

            every { gitHubRepositoryJpa.findByIdOrNull(repositoryId) } returns testRepository
            every { gitHubClient.getBranch(testRepository.owner, testRepository.repoName, branchName) } returns
                testBranch

            // When
            val result = gitHubService.getBranch(repositoryId, branchName)

            // Then
            assertThat(result).isNotNull()
            assertThat(result?.name).isEqualTo(branchName)
            verify(exactly = 1) { gitHubClient.getBranch(testRepository.owner, testRepository.repoName, branchName) }
        }

        @Test
        @DisplayName("should return null when branch not found")
        fun `should return null when branch not found`() {
            // Given
            val repositoryId = 1L
            val branchName = "nonexistent-branch"

            every { gitHubRepositoryJpa.findByIdOrNull(repositoryId) } returns testRepository
            every {
                gitHubClient.getBranch(
                    testRepository.owner,
                    testRepository.repoName,
                    branchName,
                )
            } returns null

            // When
            val result = gitHubService.getBranch(repositoryId, branchName)

            // Then
            assertThat(result).isNull()
        }

        @Test
        @DisplayName("should throw GitHubRepositoryNotFoundException when repository not found")
        fun `should throw GitHubRepositoryNotFoundException when repository not found`() {
            // Given
            val repositoryId = 999L
            every { gitHubRepositoryJpa.findByIdOrNull(repositoryId) } returns null

            // When & Then
            assertThrows<GitHubRepositoryNotFoundException> {
                gitHubService.getBranch(repositoryId, "main")
            }

            verify(exactly = 0) { gitHubClient.getBranch(any(), any(), any()) }
        }
    }

    @Nested
    @DisplayName("compareBranches")
    inner class CompareBranches {
        @Test
        @DisplayName("should return branch comparison successfully")
        fun `should return branch comparison successfully`() {
            // Given
            val repositoryId = 1L
            val baseBranch = "main"
            val headBranch = "feature/new-feature"

            every { gitHubRepositoryJpa.findByIdOrNull(repositoryId) } returns testRepository
            every {
                gitHubClient.compareBranches(
                    testRepository.owner,
                    testRepository.repoName,
                    baseBranch,
                    headBranch,
                )
            } returns testBranchComparison

            // When
            val result = gitHubService.compareBranches(repositoryId, baseBranch, headBranch)

            // Then
            assertThat(result).isNotNull()
            assertThat(result?.aheadBy).isEqualTo(5)
            assertThat(result?.behindBy).isEqualTo(2)
            assertThat(result?.status).isEqualTo(ComparisonStatus.DIVERGED)
            verify(exactly = 1) {
                gitHubClient.compareBranches(
                    testRepository.owner,
                    testRepository.repoName,
                    baseBranch,
                    headBranch,
                )
            }
        }

        @Test
        @DisplayName("should return null when branches not found")
        fun `should return null when branches not found`() {
            // Given
            val repositoryId = 1L
            val baseBranch = "main"
            val headBranch = "nonexistent-branch"

            every { gitHubRepositoryJpa.findByIdOrNull(repositoryId) } returns testRepository
            every {
                gitHubClient.compareBranches(
                    testRepository.owner,
                    testRepository.repoName,
                    baseBranch,
                    headBranch,
                )
            } returns null

            // When
            val result = gitHubService.compareBranches(repositoryId, baseBranch, headBranch)

            // Then
            assertThat(result).isNull()
        }

        @Test
        @DisplayName("should throw GitHubRepositoryNotFoundException when repository not found")
        fun `should throw GitHubRepositoryNotFoundException when repository not found`() {
            // Given
            val repositoryId = 999L
            every { gitHubRepositoryJpa.findByIdOrNull(repositoryId) } returns null

            // When & Then
            assertThrows<GitHubRepositoryNotFoundException> {
                gitHubService.compareBranches(repositoryId, "main", "develop")
            }

            verify(exactly = 0) { gitHubClient.compareBranches(any(), any(), any(), any()) }
        }
    }

    @Nested
    @DisplayName("listPullRequests")
    inner class ListPullRequests {
        @Test
        @DisplayName("should return pull requests without filters")
        fun `should return pull requests without filters`() {
            // Given
            val repositoryId = 1L
            val pullRequests = listOf(testPullRequest)

            every { gitHubRepositoryJpa.findByIdOrNull(repositoryId) } returns testRepository
            every {
                gitHubClient.listPullRequests(
                    testRepository.owner,
                    testRepository.repoName,
                    any(),
                )
            } returns pullRequests

            // When
            val result = gitHubService.listPullRequests(repositoryId)

            // Then
            assertThat(result).hasSize(1)
            assertThat(result[0].number).isEqualTo(42)
            assertThat(result[0].title).isEqualTo(testPullRequest.title)
            verify(exactly = 1) {
                gitHubClient.listPullRequests(
                    testRepository.owner,
                    testRepository.repoName,
                    match { filter -> filter.state == null && filter.targetBranch == null && filter.limit == 30 },
                )
            }
        }

        @Test
        @DisplayName("should return pull requests with state filter")
        fun `should return pull requests with state filter`() {
            // Given
            val repositoryId = 1L
            val state = PullRequestState.OPEN
            val pullRequests = listOf(testPullRequest)

            every { gitHubRepositoryJpa.findByIdOrNull(repositoryId) } returns testRepository
            every {
                gitHubClient.listPullRequests(
                    testRepository.owner,
                    testRepository.repoName,
                    any(),
                )
            } returns pullRequests

            // When
            val result = gitHubService.listPullRequests(repositoryId, state = state)

            // Then
            assertThat(result).hasSize(1)
            verify(exactly = 1) {
                gitHubClient.listPullRequests(
                    testRepository.owner,
                    testRepository.repoName,
                    match { filter -> filter.state == state },
                )
            }
        }

        @Test
        @DisplayName("should return pull requests with target branch filter")
        fun `should return pull requests with target branch filter`() {
            // Given
            val repositoryId = 1L
            val targetBranch = "main"
            val pullRequests = listOf(testPullRequest)

            every { gitHubRepositoryJpa.findByIdOrNull(repositoryId) } returns testRepository
            every {
                gitHubClient.listPullRequests(
                    testRepository.owner,
                    testRepository.repoName,
                    any(),
                )
            } returns pullRequests

            // When
            val result = gitHubService.listPullRequests(repositoryId, targetBranch = targetBranch)

            // Then
            assertThat(result).hasSize(1)
            verify(exactly = 1) {
                gitHubClient.listPullRequests(
                    testRepository.owner,
                    testRepository.repoName,
                    match { filter -> filter.targetBranch == targetBranch },
                )
            }
        }

        @Test
        @DisplayName("should return pull requests with limit")
        fun `should return pull requests with limit`() {
            // Given
            val repositoryId = 1L
            val limit = 10
            val pullRequests = listOf(testPullRequest)

            every { gitHubRepositoryJpa.findByIdOrNull(repositoryId) } returns testRepository
            every {
                gitHubClient.listPullRequests(
                    testRepository.owner,
                    testRepository.repoName,
                    any(),
                )
            } returns pullRequests

            // When
            val result = gitHubService.listPullRequests(repositoryId, limit = limit)

            // Then
            assertThat(result).hasSize(1)
            verify(exactly = 1) {
                gitHubClient.listPullRequests(
                    testRepository.owner,
                    testRepository.repoName,
                    match { filter -> filter.limit == limit },
                )
            }
        }

        @Test
        @DisplayName("should throw GitHubRepositoryNotFoundException when repository not found")
        fun `should throw GitHubRepositoryNotFoundException when repository not found`() {
            // Given
            val repositoryId = 999L
            every { gitHubRepositoryJpa.findByIdOrNull(repositoryId) } returns null

            // When & Then
            assertThrows<GitHubRepositoryNotFoundException> {
                gitHubService.listPullRequests(repositoryId)
            }

            verify(exactly = 0) { gitHubClient.listPullRequests(any(), any(), any()) }
        }
    }

    @Nested
    @DisplayName("getPullRequest")
    inner class GetPullRequest {
        @Test
        @DisplayName("should return pull request when found")
        fun `should return pull request when found`() {
            // Given
            val repositoryId = 1L
            val prNumber = 42L

            every { gitHubRepositoryJpa.findByIdOrNull(repositoryId) } returns testRepository
            every {
                gitHubClient.getPullRequest(
                    testRepository.owner,
                    testRepository.repoName,
                    prNumber,
                )
            } returns testPullRequest

            // When
            val result = gitHubService.getPullRequest(repositoryId, prNumber)

            // Then
            assertThat(result).isNotNull()
            assertThat(result?.number).isEqualTo(prNumber)
            assertThat(result?.title).isEqualTo(testPullRequest.title)
            assertThat(result?.state).isEqualTo(PullRequestState.OPEN)
            verify(exactly = 1) {
                gitHubClient.getPullRequest(
                    testRepository.owner,
                    testRepository.repoName,
                    prNumber,
                )
            }
        }

        @Test
        @DisplayName("should return null when pull request not found")
        fun `should return null when pull request not found`() {
            // Given
            val repositoryId = 1L
            val prNumber = 999L

            every { gitHubRepositoryJpa.findByIdOrNull(repositoryId) } returns testRepository
            every {
                gitHubClient.getPullRequest(
                    testRepository.owner,
                    testRepository.repoName,
                    prNumber,
                )
            } returns null

            // When
            val result = gitHubService.getPullRequest(repositoryId, prNumber)

            // Then
            assertThat(result).isNull()
        }

        @Test
        @DisplayName("should throw GitHubRepositoryNotFoundException when repository not found")
        fun `should throw GitHubRepositoryNotFoundException when repository not found`() {
            // Given
            val repositoryId = 999L
            every { gitHubRepositoryJpa.findByIdOrNull(repositoryId) } returns null

            // When & Then
            assertThrows<GitHubRepositoryNotFoundException> {
                gitHubService.getPullRequest(repositoryId, 42L)
            }

            verify(exactly = 0) { gitHubClient.getPullRequest(any(), any(), any()) }
        }
    }

    @Nested
    @DisplayName("isGitHubAvailable")
    inner class IsGitHubAvailable {
        @Test
        @DisplayName("should return true when GitHub API is available")
        fun `should return true when GitHub API is available`() {
            // Given
            every { gitHubClient.isAvailable() } returns true

            // When
            val result = gitHubService.isGitHubAvailable()

            // Then
            assertThat(result).isTrue()
            verify(exactly = 1) { gitHubClient.isAvailable() }
        }

        @Test
        @DisplayName("should return false when GitHub API is not available")
        fun `should return false when GitHub API is not available`() {
            // Given
            every { gitHubClient.isAvailable() } returns false

            // When
            val result = gitHubService.isGitHubAvailable()

            // Then
            assertThat(result).isFalse()
        }
    }
}
