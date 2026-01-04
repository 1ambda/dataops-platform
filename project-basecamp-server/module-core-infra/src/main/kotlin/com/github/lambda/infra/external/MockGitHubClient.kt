package com.github.lambda.infra.external

import com.github.lambda.common.enums.ComparisonStatus
import com.github.lambda.common.enums.PullRequestState
import com.github.lambda.domain.external.GitHubClient
import com.github.lambda.domain.model.github.BranchComparison
import com.github.lambda.domain.model.github.CommitSummary
import com.github.lambda.domain.model.github.GitHubBranch
import com.github.lambda.domain.model.github.GitHubPullRequest
import com.github.lambda.domain.model.github.GitHubRepositoryInfo
import com.github.lambda.domain.model.github.PullRequestFilter
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDateTime

/**
 * Mock GitHub Client Implementation
 *
 * Provides mock GitHub API responses for development and testing.
 * Default implementation (no @Profile needed).
 *
 * When real GitHub API integration is required, create a separate implementation
 * with @Profile("github-api") and use @Profile("!github-api") on this class.
 */
@Service("gitHubClient")
class MockGitHubClient : GitHubClient {
    private val log = LoggerFactory.getLogger(MockGitHubClient::class.java)

    // Mock branch data - simulating realistic Git workflow branches
    private val mockBranches =
        mapOf(
            "main" to
                GitHubBranch(
                    name = "main",
                    sha = "abc123def456789012345678901234567890abcd",
                    isProtected = true,
                    lastCommitDate = LocalDateTime.now().minusDays(1),
                    lastCommitAuthor = "developer@example.com",
                    lastCommitMessage = "Merge pull request #42 from feature/user-metrics",
                ),
            "develop" to
                GitHubBranch(
                    name = "develop",
                    sha = "def789ghi012345678901234567890123456abcd",
                    isProtected = true,
                    lastCommitDate = LocalDateTime.now().minusHours(6),
                    lastCommitAuthor = "analyst@example.com",
                    lastCommitMessage = "feat: add user metrics dataset",
                ),
            "feature/user-metrics" to
                GitHubBranch(
                    name = "feature/user-metrics",
                    sha = "ghi345jkl678901234567890123456789012abcd",
                    isProtected = false,
                    lastCommitDate = LocalDateTime.now().minusHours(2),
                    lastCommitAuthor = "analyst@example.com",
                    lastCommitMessage = "WIP: user metrics implementation",
                ),
            "feature/sales-dashboard" to
                GitHubBranch(
                    name = "feature/sales-dashboard",
                    sha = "jkl901mno234567890123456789012345678abcd",
                    isProtected = false,
                    lastCommitDate = LocalDateTime.now().minusDays(3),
                    lastCommitAuthor = "data-engineer@example.com",
                    lastCommitMessage = "feat: add sales aggregation metrics",
                ),
            "fix/data-quality" to
                GitHubBranch(
                    name = "fix/data-quality",
                    sha = "mno567pqr890123456789012345678901234abcd",
                    isProtected = false,
                    lastCommitDate = LocalDateTime.now().minusHours(1),
                    lastCommitAuthor = "qa@example.com",
                    lastCommitMessage = "fix: correct null handling in quality tests",
                ),
        )

    // Mock PR data - simulating realistic PR workflows
    private val mockPullRequests =
        listOf(
            GitHubPullRequest(
                number = 42,
                title = "feat: Add user activity metrics",
                state = PullRequestState.OPEN,
                sourceBranch = "feature/user-metrics",
                targetBranch = "develop",
                author = "analyst@example.com",
                createdAt = LocalDateTime.now().minusDays(2),
                updatedAt = LocalDateTime.now().minusHours(1),
                mergedAt = null,
                mergedBy = null,
                reviewers = listOf("lead@example.com", "senior@example.com"),
                labels = listOf("enhancement", "metrics", "review-needed"),
                additions = 150,
                deletions = 20,
                changedFiles = 5,
                url = "https://github.com/example/data-specs/pull/42",
            ),
            GitHubPullRequest(
                number = 41,
                title = "fix: Correct sales aggregation logic",
                state = PullRequestState.MERGED,
                sourceBranch = "fix/sales-agg",
                targetBranch = "develop",
                author = "developer@example.com",
                createdAt = LocalDateTime.now().minusDays(5),
                updatedAt = LocalDateTime.now().minusDays(3),
                mergedAt = LocalDateTime.now().minusDays(3),
                mergedBy = "lead@example.com",
                reviewers = listOf("lead@example.com"),
                labels = listOf("bugfix", "sales"),
                additions = 25,
                deletions = 10,
                changedFiles = 2,
                url = "https://github.com/example/data-specs/pull/41",
            ),
            GitHubPullRequest(
                number = 40,
                title = "feat: Add sales dashboard metrics",
                state = PullRequestState.OPEN,
                sourceBranch = "feature/sales-dashboard",
                targetBranch = "develop",
                author = "data-engineer@example.com",
                createdAt = LocalDateTime.now().minusDays(4),
                updatedAt = LocalDateTime.now().minusDays(2),
                mergedAt = null,
                mergedBy = null,
                reviewers = listOf("analyst@example.com"),
                labels = listOf("enhancement", "dashboard"),
                additions = 200,
                deletions = 0,
                changedFiles = 8,
                url = "https://github.com/example/data-specs/pull/40",
            ),
            GitHubPullRequest(
                number = 39,
                title = "chore: Update data quality tests",
                state = PullRequestState.CLOSED,
                sourceBranch = "chore/quality-update",
                targetBranch = "develop",
                author = "qa@example.com",
                createdAt = LocalDateTime.now().minusDays(7),
                updatedAt = LocalDateTime.now().minusDays(6),
                mergedAt = null,
                mergedBy = null,
                reviewers = listOf("developer@example.com"),
                labels = listOf("chore", "wontfix"),
                additions = 50,
                deletions = 30,
                changedFiles = 3,
                url = "https://github.com/example/data-specs/pull/39",
            ),
            GitHubPullRequest(
                number = 38,
                title = "fix: Data quality null handling",
                state = PullRequestState.OPEN,
                sourceBranch = "fix/data-quality",
                targetBranch = "develop",
                author = "qa@example.com",
                createdAt = LocalDateTime.now().minusHours(4),
                updatedAt = LocalDateTime.now().minusHours(1),
                mergedAt = null,
                mergedBy = null,
                reviewers = listOf("developer@example.com", "analyst@example.com"),
                labels = listOf("bugfix", "quality", "urgent"),
                additions = 35,
                deletions = 15,
                changedFiles = 2,
                url = "https://github.com/example/data-specs/pull/38",
            ),
        )

    // ========================
    // Repository Operations
    // ========================

    override fun validateRepository(
        owner: String,
        repoName: String,
    ): Boolean {
        log.debug("Mock GitHub: Validating repository - owner: {}, repoName: {}", owner, repoName)
        return true
    }

    override fun getRepositoryInfo(
        owner: String,
        repoName: String,
    ): GitHubRepositoryInfo {
        log.debug("Mock GitHub: Getting repository info - owner: {}, repoName: {}", owner, repoName)
        return GitHubRepositoryInfo(
            fullName = "$owner/$repoName",
            description = "Data platform specifications repository for $owner",
            defaultBranch = "main",
            isPrivate = true,
            language = "SQL",
            starCount = 0,
            forkCount = 0,
        )
    }

    // ========================
    // Branch Operations
    // ========================

    override fun listBranches(
        owner: String,
        repoName: String,
    ): List<GitHubBranch> {
        log.debug("Mock GitHub: Listing branches - owner: {}, repoName: {}", owner, repoName)
        return mockBranches.values.toList().sortedBy { it.name }
    }

    override fun getBranch(
        owner: String,
        repoName: String,
        branchName: String,
    ): GitHubBranch? {
        log.debug(
            "Mock GitHub: Getting branch - owner: {}, repoName: {}, branchName: {}",
            owner,
            repoName,
            branchName,
        )
        return mockBranches[branchName]
    }

    override fun compareBranches(
        owner: String,
        repoName: String,
        baseBranch: String,
        headBranch: String,
    ): BranchComparison? {
        log.debug(
            "Mock GitHub: Comparing branches - owner: {}, repoName: {}, base: {}, head: {}",
            owner,
            repoName,
            baseBranch,
            headBranch,
        )

        // Check if both branches exist
        val base = mockBranches[baseBranch] ?: return null
        val head = mockBranches[headBranch] ?: return null

        // Simulate comparison based on branch names
        val (aheadBy, behindBy, status) =
            when {
                baseBranch == headBranch -> Triple(0, 0, ComparisonStatus.IDENTICAL)
                baseBranch == "main" && headBranch == "develop" -> Triple(5, 0, ComparisonStatus.AHEAD)
                baseBranch == "develop" && headBranch == "main" -> Triple(0, 5, ComparisonStatus.BEHIND)
                baseBranch == "develop" && headBranch.startsWith("feature/") -> Triple(3, 2, ComparisonStatus.DIVERGED)
                baseBranch == "develop" && headBranch.startsWith("fix/") -> Triple(2, 1, ComparisonStatus.DIVERGED)
                else -> Triple(1, 0, ComparisonStatus.AHEAD)
            }

        val commits =
            if (aheadBy > 0) {
                (1..aheadBy.coerceAtMost(5)).map { i ->
                    CommitSummary(
                        sha = "commit${i}abc${headBranch.hashCode().toString(16).take(8)}",
                        message = "Commit #$i on $headBranch",
                        author = head.lastCommitAuthor ?: "unknown@example.com",
                        date = LocalDateTime.now().minusHours(i.toLong()),
                    )
                }
            } else {
                emptyList()
            }

        return BranchComparison(
            aheadBy = aheadBy,
            behindBy = behindBy,
            status = status,
            commits = commits,
        )
    }

    // ========================
    // Pull Request Operations
    // ========================

    override fun listPullRequests(
        owner: String,
        repoName: String,
        filter: PullRequestFilter,
    ): List<GitHubPullRequest> {
        log.debug(
            "Mock GitHub: Listing pull requests - owner: {}, repoName: {}, filter: {}",
            owner,
            repoName,
            filter,
        )

        var result = mockPullRequests

        // Apply filters
        filter.state?.let { state ->
            result = result.filter { it.state == state }
        }
        filter.author?.let { author ->
            result = result.filter { it.author.contains(author, ignoreCase = true) }
        }
        filter.targetBranch?.let { branch ->
            result = result.filter { it.targetBranch == branch }
        }

        return result
            .sortedByDescending { it.updatedAt }
            .take(filter.limit)
    }

    override fun getPullRequest(
        owner: String,
        repoName: String,
        prNumber: Long,
    ): GitHubPullRequest? {
        log.debug(
            "Mock GitHub: Getting pull request - owner: {}, repoName: {}, prNumber: {}",
            owner,
            repoName,
            prNumber,
        )
        return mockPullRequests.find { it.number == prNumber }
    }

    // ========================
    // Connection Check
    // ========================

    override fun isAvailable(): Boolean {
        log.debug("Mock GitHub: Checking availability")
        return true
    }
}
