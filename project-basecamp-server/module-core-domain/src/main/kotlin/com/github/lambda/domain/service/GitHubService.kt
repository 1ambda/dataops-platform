package com.github.lambda.domain.service

import com.github.lambda.common.exception.GitHubRepositoryAlreadyExistsException
import com.github.lambda.common.exception.GitHubRepositoryNotFoundException
import com.github.lambda.common.exception.GitHubRepositoryUrlAlreadyExistsException
import com.github.lambda.domain.entity.github.GitHubRepositoryEntity
import com.github.lambda.domain.external.GitHubClient
import com.github.lambda.domain.model.github.BranchComparison
import com.github.lambda.domain.model.github.GitHubBranch
import com.github.lambda.domain.model.github.GitHubPullRequest
import com.github.lambda.domain.model.github.PullRequestFilter
import com.github.lambda.domain.model.github.PullRequestState
import com.github.lambda.domain.repository.GitHubRepositoryDsl
import com.github.lambda.domain.repository.GitHubRepositoryJpa
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

/**
 * GitHub Service
 *
 * GitHub Repository CRUD 관리 및 Branch/PR 조회를 담당합니다.
 * Services are concrete classes (no interfaces) following Pure Hexagonal Architecture.
 */
@Service
@Transactional(readOnly = true)
class GitHubService(
    private val gitHubRepositoryJpa: GitHubRepositoryJpa,
    private val gitHubRepositoryDsl: GitHubRepositoryDsl,
    private val gitHubClient: GitHubClient,
) {
    private val log = LoggerFactory.getLogger(GitHubService::class.java)

    /**
     * Register a new GitHub repository
     *
     * @param team Team name (unique)
     * @param owner GitHub owner (org or user)
     * @param repoName Repository name
     * @param repositoryUrl GitHub repository URL
     * @param defaultBranch Default branch (default: main)
     * @param developBranch Development branch (default: develop)
     * @param s3DevPath S3 path for development
     * @param s3ProdPath S3 path for production
     * @param description Repository description
     * @return Created GitHub repository entity
     * @throws GitHubRepositoryAlreadyExistsException if team already has a repository
     * @throws GitHubRepositoryUrlAlreadyExistsException if repository URL is already registered
     */
    @Transactional
    fun registerRepository(
        team: String,
        owner: String,
        repoName: String,
        repositoryUrl: String,
        defaultBranch: String = "main",
        developBranch: String = "develop",
        s3DevPath: String,
        s3ProdPath: String,
        description: String? = null,
    ): GitHubRepositoryEntity {
        log.info("Registering GitHub repository for team: $team, url: $repositoryUrl")

        // Check if team already has a repository
        if (gitHubRepositoryJpa.existsByTeam(team)) {
            throw GitHubRepositoryAlreadyExistsException(team)
        }

        // Check if repository URL is already registered
        if (gitHubRepositoryJpa.existsByRepositoryUrl(repositoryUrl)) {
            throw GitHubRepositoryUrlAlreadyExistsException(repositoryUrl)
        }

        // Check if owner/repoName combination already exists
        gitHubRepositoryDsl.findByOwnerAndRepoName(owner, repoName)?.let {
            throw GitHubRepositoryUrlAlreadyExistsException("$owner/$repoName")
        }

        val repository =
            GitHubRepositoryEntity(
                team = team,
                owner = owner,
                repoName = repoName,
                repositoryUrl = repositoryUrl,
                defaultBranch = defaultBranch,
                developBranch = developBranch,
                s3DevPath = s3DevPath,
                s3ProdPath = s3ProdPath,
                description = description,
            )

        val saved = gitHubRepositoryJpa.save(repository)
        log.info("GitHub repository registered successfully: id=${saved.id}, team=$team")

        return saved
    }

    /**
     * Get all repositories ordered by updatedAt desc
     *
     * @return List of all GitHub repositories
     */
    fun getAllRepositories(): List<GitHubRepositoryEntity> =
        gitHubRepositoryJpa
            .findAllByOrderByUpdatedAtDesc()
            .filter { !it.isDeleted }

    /**
     * Get active repositories only
     *
     * @return List of active GitHub repositories
     */
    fun getActiveRepositories(): List<GitHubRepositoryEntity> = gitHubRepositoryDsl.findAllActive()

    /**
     * Get repository by ID
     *
     * @param id Repository ID
     * @return GitHub repository entity or null if not found
     */
    fun getRepository(id: Long): GitHubRepositoryEntity? =
        gitHubRepositoryJpa.findByIdOrNull(id)?.takeIf { !it.isDeleted }

    /**
     * Get repository by ID (throws exception if not found)
     *
     * @param id Repository ID
     * @return GitHub repository entity
     * @throws GitHubRepositoryNotFoundException if repository not found
     */
    fun getRepositoryOrThrow(id: Long): GitHubRepositoryEntity =
        getRepository(id) ?: throw GitHubRepositoryNotFoundException("id=$id")

    /**
     * Get repository by team
     *
     * @param team Team name
     * @return GitHub repository entity or null if not found
     */
    fun getRepositoryByTeam(team: String): GitHubRepositoryEntity? =
        gitHubRepositoryJpa.findByTeam(team)?.takeIf { !it.isDeleted }

    /**
     * Get repository by team (throws exception if not found)
     *
     * @param team Team name
     * @return GitHub repository entity
     * @throws GitHubRepositoryNotFoundException if repository not found
     */
    fun getRepositoryByTeamOrThrow(team: String): GitHubRepositoryEntity =
        getRepositoryByTeam(team) ?: throw GitHubRepositoryNotFoundException("team=$team")

    /**
     * Search repositories by keyword
     *
     * @param keyword Search keyword
     * @return List of matching GitHub repositories
     */
    fun searchRepositories(keyword: String): List<GitHubRepositoryEntity> = gitHubRepositoryDsl.searchByKeyword(keyword)

    /**
     * Update repository
     *
     * @param id Repository ID
     * @param defaultBranch Updated default branch
     * @param developBranch Updated develop branch
     * @param s3DevPath Updated S3 dev path
     * @param s3ProdPath Updated S3 prod path
     * @param description Updated description
     * @param isActive Updated active status
     * @return Updated GitHub repository entity
     * @throws GitHubRepositoryNotFoundException if repository not found
     */
    @Transactional
    fun updateRepository(
        id: Long,
        defaultBranch: String? = null,
        developBranch: String? = null,
        s3DevPath: String? = null,
        s3ProdPath: String? = null,
        description: String? = null,
        isActive: Boolean? = null,
    ): GitHubRepositoryEntity {
        log.info("Updating GitHub repository: id=$id")

        val repository = getRepositoryOrThrow(id)
        repository.update(
            defaultBranch = defaultBranch,
            developBranch = developBranch,
            s3DevPath = s3DevPath,
            s3ProdPath = s3ProdPath,
            description = description,
            isActive = isActive,
        )

        val saved = gitHubRepositoryJpa.save(repository)
        log.info("GitHub repository updated successfully: id=${saved.id}")

        return saved
    }

    /**
     * Delete repository (soft delete)
     *
     * @param id Repository ID
     * @throws GitHubRepositoryNotFoundException if repository not found
     */
    @Transactional
    fun deleteRepository(id: Long) {
        log.info("Deleting GitHub repository: id=$id")

        val repository = getRepositoryOrThrow(id)
        repository.deletedAt = LocalDateTime.now()
        repository.deactivate()

        gitHubRepositoryJpa.save(repository)
        log.info("GitHub repository soft-deleted successfully: id=$id")
    }

    /**
     * Get repositories by owner
     *
     * @param owner GitHub owner
     * @return List of repositories owned by the specified owner
     */
    fun getRepositoriesByOwner(owner: String): List<GitHubRepositoryEntity> =
        gitHubRepositoryJpa.findByOwner(owner).filter { !it.isDeleted }

    // ========================
    // Branch Operations (via GitHubClient)
    // ========================

    /**
     * List all branches of a repository
     *
     * @param repositoryId Repository ID
     * @return List of branches
     * @throws GitHubRepositoryNotFoundException if repository not found
     */
    fun listBranches(repositoryId: Long): List<GitHubBranch> {
        val repository = getRepositoryOrThrow(repositoryId)
        log.debug(
            "Listing branches for repository: id={}, owner={}, repoName={}",
            repositoryId,
            repository.owner,
            repository.repoName,
        )

        return gitHubClient.listBranches(repository.owner, repository.repoName)
    }

    /**
     * Get a specific branch of a repository
     *
     * @param repositoryId Repository ID
     * @param branchName Branch name
     * @return Branch information or null if not found
     * @throws GitHubRepositoryNotFoundException if repository not found
     */
    fun getBranch(
        repositoryId: Long,
        branchName: String,
    ): GitHubBranch? {
        val repository = getRepositoryOrThrow(repositoryId)
        log.debug(
            "Getting branch for repository: id={}, owner={}, repoName={}, branchName={}",
            repositoryId,
            repository.owner,
            repository.repoName,
            branchName,
        )

        return gitHubClient.getBranch(repository.owner, repository.repoName, branchName)
    }

    /**
     * Compare two branches of a repository
     *
     * @param repositoryId Repository ID
     * @param baseBranch Base branch (reference branch)
     * @param headBranch Head branch (branch to compare)
     * @return Branch comparison result or null if branches not found
     * @throws GitHubRepositoryNotFoundException if repository not found
     */
    fun compareBranches(
        repositoryId: Long,
        baseBranch: String,
        headBranch: String,
    ): BranchComparison? {
        val repository = getRepositoryOrThrow(repositoryId)
        log.debug(
            "Comparing branches for repository: id={}, owner={}, repoName={}, base={}, head={}",
            repositoryId,
            repository.owner,
            repository.repoName,
            baseBranch,
            headBranch,
        )

        return gitHubClient.compareBranches(repository.owner, repository.repoName, baseBranch, headBranch)
    }

    // ========================
    // Pull Request Operations (via GitHubClient)
    // ========================

    /**
     * List pull requests of a repository
     *
     * @param repositoryId Repository ID
     * @param state Filter by PR state (OPEN, CLOSED, MERGED) or null for all
     * @param targetBranch Filter by target branch
     * @param limit Maximum number of PRs to return (default: 30)
     * @return List of pull requests
     * @throws GitHubRepositoryNotFoundException if repository not found
     */
    fun listPullRequests(
        repositoryId: Long,
        state: PullRequestState? = null,
        targetBranch: String? = null,
        limit: Int = 30,
    ): List<GitHubPullRequest> {
        val repository = getRepositoryOrThrow(repositoryId)
        log.debug(
            "Listing pull requests for repository: id={}, owner={}, repoName={}, state={}, targetBranch={}, limit={}",
            repositoryId,
            repository.owner,
            repository.repoName,
            state,
            targetBranch,
            limit,
        )

        val filter =
            PullRequestFilter(
                state = state,
                targetBranch = targetBranch,
                limit = limit,
            )

        return gitHubClient.listPullRequests(repository.owner, repository.repoName, filter)
    }

    /**
     * Get a specific pull request of a repository
     *
     * @param repositoryId Repository ID
     * @param prNumber Pull request number
     * @return Pull request information or null if not found
     * @throws GitHubRepositoryNotFoundException if repository not found
     */
    fun getPullRequest(
        repositoryId: Long,
        prNumber: Long,
    ): GitHubPullRequest? {
        val repository = getRepositoryOrThrow(repositoryId)
        log.debug(
            "Getting pull request for repository: id={}, owner={}, repoName={}, prNumber={}",
            repositoryId,
            repository.owner,
            repository.repoName,
            prNumber,
        )

        return gitHubClient.getPullRequest(repository.owner, repository.repoName, prNumber)
    }

    /**
     * Check if GitHub API is available
     *
     * @return True if GitHub API is available
     */
    fun isGitHubAvailable(): Boolean = gitHubClient.isAvailable()
}
