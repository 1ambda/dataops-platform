package com.github.lambda.controller

import com.github.lambda.common.constant.CommonConstants
import com.github.lambda.domain.model.github.PullRequestState
import com.github.lambda.domain.service.GitHubService
import com.github.lambda.dto.github.BranchComparisonResponse
import com.github.lambda.dto.github.GitHubBranchResponse
import com.github.lambda.dto.github.GitHubPullRequestListResponse
import com.github.lambda.dto.github.GitHubPullRequestResponse
import com.github.lambda.dto.github.GitHubRepositoryResponse
import com.github.lambda.dto.github.RegisterGitHubRepositoryRequest
import com.github.lambda.dto.github.UpdateGitHubRepositoryRequest
import com.github.lambda.mapper.GitHubMapper
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import mu.KotlinLogging
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.CrossOrigin
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import io.swagger.v3.oas.annotations.responses.ApiResponse as SwaggerApiResponse

/**
 * GitHub Repository Management REST API Controller
 *
 * Provides endpoints for GitHub repository management.
 * Phase 1: Core Repository CRUD (6 endpoints)
 * Phase 2: Branch/PR Operations (5 endpoints)
 */
@RestController
@RequestMapping("${CommonConstants.Api.V1_PATH}/github/repositories")
@CrossOrigin
@Validated
@Tag(name = "GitHub", description = "GitHub repository management API")
@PreAuthorize("hasRole('ROLE_USER')")
class GitHubController(
    private val gitHubService: GitHubService,
    private val gitHubMapper: GitHubMapper,
) {
    private val logger = KotlinLogging.logger {}

    /**
     * Register a new GitHub repository
     *
     * POST /api/v1/github/repositories
     */
    @Operation(
        summary = "Register GitHub repository",
        description = "Register a new GitHub repository for a team",
    )
    @SwaggerApiResponse(responseCode = "201", description = "Repository registered successfully")
    @SwaggerApiResponse(
        responseCode = "409",
        description = "Repository already exists for team or URL already registered",
    )
    @SwaggerApiResponse(responseCode = "400", description = "Invalid request")
    @PostMapping
    fun registerRepository(
        @Valid @RequestBody request: RegisterGitHubRepositoryRequest,
    ): ResponseEntity<GitHubRepositoryResponse> {
        logger.info {
            "POST /api/v1/github/repositories - team: ${request.team}, owner: ${request.owner}, repoName: ${request.repoName}"
        }

        val repository =
            gitHubService.registerRepository(
                team = request.team,
                owner = request.owner,
                repoName = request.repoName,
                repositoryUrl = request.repositoryUrl,
                defaultBranch = request.defaultBranch,
                developBranch = request.developBranch,
                s3DevPath = request.s3DevPath,
                s3ProdPath = request.s3ProdPath,
                description = request.description,
            )

        val response = gitHubMapper.toResponse(repository)
        return ResponseEntity.status(HttpStatus.CREATED).body(response)
    }

    /**
     * List all GitHub repositories
     *
     * GET /api/v1/github/repositories
     * GET /api/v1/github/repositories?team={team}
     */
    @Operation(
        summary = "List GitHub repositories",
        description = "List all GitHub repositories with optional team filter",
    )
    @SwaggerApiResponse(responseCode = "200", description = "Success")
    @GetMapping
    fun listRepositories(
        @Parameter(description = "Filter by team name")
        @RequestParam(required = false) team: String?,
    ): ResponseEntity<List<GitHubRepositoryResponse>> {
        logger.info { "GET /api/v1/github/repositories - team: $team" }

        val repositories =
            if (team != null) {
                listOfNotNull(gitHubService.getRepositoryByTeam(team))
            } else {
                gitHubService.getAllRepositories()
            }

        val response = repositories.map { gitHubMapper.toResponse(it) }
        return ResponseEntity.ok(response)
    }

    /**
     * Get GitHub repository by ID
     *
     * GET /api/v1/github/repositories/{id}
     */
    @Operation(
        summary = "Get GitHub repository",
        description = "Get GitHub repository details by ID",
    )
    @SwaggerApiResponse(responseCode = "200", description = "Success")
    @SwaggerApiResponse(responseCode = "404", description = "Repository not found")
    @GetMapping("/{id}")
    fun getRepository(
        @Parameter(description = "Repository ID")
        @PathVariable id: Long,
    ): ResponseEntity<GitHubRepositoryResponse> {
        logger.info { "GET /api/v1/github/repositories/$id" }

        val repository = gitHubService.getRepositoryOrThrow(id)
        val response = gitHubMapper.toResponse(repository)

        return ResponseEntity.ok(response)
    }

    /**
     * Update GitHub repository
     *
     * PUT /api/v1/github/repositories/{id}
     */
    @Operation(
        summary = "Update GitHub repository",
        description = "Update GitHub repository settings",
    )
    @SwaggerApiResponse(responseCode = "200", description = "Repository updated successfully")
    @SwaggerApiResponse(responseCode = "404", description = "Repository not found")
    @SwaggerApiResponse(responseCode = "400", description = "Invalid request")
    @PutMapping("/{id}")
    fun updateRepository(
        @Parameter(description = "Repository ID")
        @PathVariable id: Long,
        @Valid @RequestBody request: UpdateGitHubRepositoryRequest,
    ): ResponseEntity<GitHubRepositoryResponse> {
        logger.info { "PUT /api/v1/github/repositories/$id" }

        val repository =
            gitHubService.updateRepository(
                id = id,
                defaultBranch = request.defaultBranch,
                developBranch = request.developBranch,
                s3DevPath = request.s3DevPath,
                s3ProdPath = request.s3ProdPath,
                description = request.description,
                isActive = request.isActive,
            )

        val response = gitHubMapper.toResponse(repository)
        return ResponseEntity.ok(response)
    }

    /**
     * Delete GitHub repository
     *
     * DELETE /api/v1/github/repositories/{id}
     */
    @Operation(
        summary = "Delete GitHub repository",
        description = "Delete GitHub repository (soft delete)",
    )
    @SwaggerApiResponse(responseCode = "204", description = "Repository deleted successfully")
    @SwaggerApiResponse(responseCode = "404", description = "Repository not found")
    @DeleteMapping("/{id}")
    fun deleteRepository(
        @Parameter(description = "Repository ID")
        @PathVariable id: Long,
    ): ResponseEntity<Void> {
        logger.info { "DELETE /api/v1/github/repositories/$id" }

        gitHubService.deleteRepository(id)

        return ResponseEntity.noContent().build()
    }

    // ========================
    // Branch Operations (Phase 2)
    // ========================

    /**
     * List all branches of a repository
     *
     * GET /api/v1/github/repositories/{id}/branches
     */
    @Operation(
        summary = "List repository branches",
        description = "List all branches of a GitHub repository",
    )
    @SwaggerApiResponse(responseCode = "200", description = "Success")
    @SwaggerApiResponse(responseCode = "404", description = "Repository not found")
    @GetMapping("/{id}/branches")
    fun listBranches(
        @Parameter(description = "Repository ID")
        @PathVariable id: Long,
    ): ResponseEntity<List<GitHubBranchResponse>> {
        logger.info { "GET /api/v1/github/repositories/$id/branches" }

        val branches = gitHubService.listBranches(id)
        val response = gitHubMapper.toBranchResponseList(branches)

        return ResponseEntity.ok(response)
    }

    /**
     * Get a specific branch of a repository
     *
     * GET /api/v1/github/repositories/{id}/branches/{branchName}
     */
    @Operation(
        summary = "Get branch details",
        description = "Get details of a specific branch in a GitHub repository",
    )
    @SwaggerApiResponse(responseCode = "200", description = "Success")
    @SwaggerApiResponse(responseCode = "404", description = "Repository or branch not found")
    @GetMapping("/{id}/branches/{branchName}")
    fun getBranch(
        @Parameter(description = "Repository ID")
        @PathVariable id: Long,
        @Parameter(description = "Branch name")
        @PathVariable branchName: String,
    ): ResponseEntity<GitHubBranchResponse> {
        logger.info { "GET /api/v1/github/repositories/$id/branches/$branchName" }

        val branch =
            gitHubService.getBranch(id, branchName)
                ?: return ResponseEntity.notFound().build()

        val response = gitHubMapper.toResponse(branch)
        return ResponseEntity.ok(response)
    }

    /**
     * Compare two branches of a repository
     *
     * GET /api/v1/github/repositories/{id}/branches/compare?base={base}&head={head}
     */
    @Operation(
        summary = "Compare branches",
        description = "Compare two branches and see the difference in commits",
    )
    @SwaggerApiResponse(responseCode = "200", description = "Success")
    @SwaggerApiResponse(responseCode = "404", description = "Repository or branch not found")
    @GetMapping("/{id}/branches/compare")
    fun compareBranches(
        @Parameter(description = "Repository ID")
        @PathVariable id: Long,
        @Parameter(description = "Base branch (reference branch)")
        @RequestParam base: String,
        @Parameter(description = "Head branch (branch to compare)")
        @RequestParam head: String,
    ): ResponseEntity<BranchComparisonResponse> {
        logger.info { "GET /api/v1/github/repositories/$id/branches/compare - base: $base, head: $head" }

        val comparison =
            gitHubService.compareBranches(id, base, head)
                ?: return ResponseEntity.notFound().build()

        val response = gitHubMapper.toResponse(comparison, base, head)
        return ResponseEntity.ok(response)
    }

    // ========================
    // Pull Request Operations (Phase 2)
    // ========================

    /**
     * List pull requests of a repository
     *
     * GET /api/v1/github/repositories/{id}/pulls
     * GET /api/v1/github/repositories/{id}/pulls?state={state}&target_branch={branch}&limit={limit}
     */
    @Operation(
        summary = "List pull requests",
        description = "List pull requests of a GitHub repository with optional filters",
    )
    @SwaggerApiResponse(responseCode = "200", description = "Success")
    @SwaggerApiResponse(responseCode = "404", description = "Repository not found")
    @GetMapping("/{id}/pulls")
    fun listPullRequests(
        @Parameter(description = "Repository ID")
        @PathVariable id: Long,
        @Parameter(description = "Filter by PR state (OPEN, CLOSED, MERGED)")
        @RequestParam(required = false) state: PullRequestState?,
        @Parameter(description = "Filter by target branch")
        @RequestParam(name = "target_branch", required = false) targetBranch: String?,
        @Parameter(description = "Maximum number of PRs to return (default: 30)")
        @RequestParam(required = false, defaultValue = "30") limit: Int,
    ): ResponseEntity<GitHubPullRequestListResponse> {
        logger.info {
            "GET /api/v1/github/repositories/$id/pulls - " +
                "state: $state, targetBranch: $targetBranch, limit: $limit"
        }

        val pullRequests = gitHubService.listPullRequests(id, state, targetBranch, limit)
        val response = gitHubMapper.toPullRequestListResponse(pullRequests, limit)

        return ResponseEntity.ok(response)
    }

    /**
     * Get a specific pull request of a repository
     *
     * GET /api/v1/github/repositories/{id}/pulls/{prNumber}
     */
    @Operation(
        summary = "Get pull request details",
        description = "Get details of a specific pull request in a GitHub repository",
    )
    @SwaggerApiResponse(responseCode = "200", description = "Success")
    @SwaggerApiResponse(responseCode = "404", description = "Repository or pull request not found")
    @GetMapping("/{id}/pulls/{prNumber}")
    fun getPullRequest(
        @Parameter(description = "Repository ID")
        @PathVariable id: Long,
        @Parameter(description = "Pull request number")
        @PathVariable prNumber: Long,
    ): ResponseEntity<GitHubPullRequestResponse> {
        logger.info { "GET /api/v1/github/repositories/$id/pulls/$prNumber" }

        val pullRequest =
            gitHubService.getPullRequest(id, prNumber)
                ?: return ResponseEntity.notFound().build()

        val response = gitHubMapper.toResponse(pullRequest)
        return ResponseEntity.ok(response)
    }
}
