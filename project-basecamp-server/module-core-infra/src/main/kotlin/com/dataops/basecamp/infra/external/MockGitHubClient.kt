package com.dataops.basecamp.infra.external

import com.dataops.basecamp.domain.command.github.PullRequestFilter
import com.dataops.basecamp.domain.external.github.BranchComparisonResponse
import com.dataops.basecamp.domain.external.github.GitHubBranchResponse
import com.dataops.basecamp.domain.external.github.GitHubClient
import com.dataops.basecamp.domain.external.github.GitHubPullRequestResponse
import com.dataops.basecamp.domain.external.github.GitHubRepositoryInfoResponse
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
                GitHubBranchResponse(
                    name = "main",
                    sha = "abc123def456789012345678901234567890abcd",
                    isProtected = true,
                    lastCommitDate = LocalDateTime.now().minusDays(1),
                    lastCommitAuthor = "developer@example.com",
                    lastCommitMessage = "Merge pull request #42 from feature/user-metrics",
                ),
            "develop" to
                GitHubBranchResponse(
                    name = "develop",
                    sha = "def789ghi012345678901234567890123456abcd",
                    isProtected = true,
                    lastCommitDate = LocalDateTime.now().minusHours(6),
                    lastCommitAuthor = "analyst@example.com",
                    lastCommitMessage = "feat: add user metrics dataset",
                ),
            "feature/user-metrics" to
                GitHubBranchResponse(
                    name = "feature/user-metrics",
                    sha = "ghi345jkl678901234567890123456789012abcd",
                    isProtected = false,
                    lastCommitDate = LocalDateTime.now().minusHours(2),
                    lastCommitAuthor = "analyst@example.com",
                    lastCommitMessage = "WIP: user metrics implementation",
                ),
            "feature/sales-dashboard" to
                GitHubBranchResponse(
                    name = "feature/sales-dashboard",
                    sha = "jkl901mno234567890123456789012345678abcd",
                    isProtected = false,
                    lastCommitDate = LocalDateTime.now().minusDays(3),
                    lastCommitAuthor = "data-engineer@example.com",
                    lastCommitMessage = "feat: add sales aggregation metrics",
                ),
            "fix/data-quality" to
                GitHubBranchResponse(
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
            GitHubPullRequestResponse(
                id = 1001L,
                number = 42L,
                title = "feat: Add user activity metrics",
                body = "This PR adds user activity metrics for tracking engagement.",
                state = "open",
                isDraft = false,
                headBranch = "feature/user-metrics",
                baseBranch = "develop",
                author = "analyst@example.com",
                assignees = listOf("lead@example.com"),
                reviewers = listOf("lead@example.com", "senior@example.com"),
                url = "https://github.com/example/data-specs/pull/42",
                createdAt = LocalDateTime.now().minusDays(2),
                updatedAt = LocalDateTime.now().minusHours(1),
                mergedAt = null,
                closedAt = null,
                isMerged = false,
                isClosed = false,
                mergeCommitSha = null,
                changedFiles = 5,
                additions = 150,
                deletions = 20,
            ),
            GitHubPullRequestResponse(
                id = 1002L,
                number = 41L,
                title = "fix: Correct sales aggregation logic",
                body = "Fixed the sales aggregation calculation bug.",
                state = "merged",
                isDraft = false,
                headBranch = "fix/sales-agg",
                baseBranch = "develop",
                author = "developer@example.com",
                assignees = emptyList(),
                reviewers = listOf("lead@example.com"),
                url = "https://github.com/example/data-specs/pull/41",
                createdAt = LocalDateTime.now().minusDays(5),
                updatedAt = LocalDateTime.now().minusDays(3),
                mergedAt = LocalDateTime.now().minusDays(3),
                closedAt = LocalDateTime.now().minusDays(3),
                isMerged = true,
                isClosed = true,
                mergeCommitSha = "merge123abc456def789012345678901234567890",
                changedFiles = 2,
                additions = 25,
                deletions = 10,
            ),
            GitHubPullRequestResponse(
                id = 1003L,
                number = 40L,
                title = "feat: Add sales dashboard metrics",
                body = "Adding new metrics for the sales dashboard.",
                state = "open",
                isDraft = true,
                headBranch = "feature/sales-dashboard",
                baseBranch = "develop",
                author = "data-engineer@example.com",
                assignees = listOf("data-engineer@example.com"),
                reviewers = listOf("analyst@example.com"),
                url = "https://github.com/example/data-specs/pull/40",
                createdAt = LocalDateTime.now().minusDays(4),
                updatedAt = LocalDateTime.now().minusDays(2),
                mergedAt = null,
                closedAt = null,
                isMerged = false,
                isClosed = false,
                mergeCommitSha = null,
                changedFiles = 8,
                additions = 200,
                deletions = 0,
            ),
            GitHubPullRequestResponse(
                id = 1004L,
                number = 39L,
                title = "chore: Update data quality tests",
                body = "Updating quality test configurations.",
                state = "closed",
                isDraft = false,
                headBranch = "chore/quality-update",
                baseBranch = "develop",
                author = "qa@example.com",
                assignees = emptyList(),
                reviewers = listOf("developer@example.com"),
                url = "https://github.com/example/data-specs/pull/39",
                createdAt = LocalDateTime.now().minusDays(7),
                updatedAt = LocalDateTime.now().minusDays(6),
                mergedAt = null,
                closedAt = LocalDateTime.now().minusDays(6),
                isMerged = false,
                isClosed = true,
                mergeCommitSha = null,
                changedFiles = 3,
                additions = 50,
                deletions = 30,
            ),
            GitHubPullRequestResponse(
                id = 1005L,
                number = 38L,
                title = "fix: Data quality null handling",
                body = "Fix null handling issues in data quality tests.",
                state = "open",
                isDraft = false,
                headBranch = "fix/data-quality",
                baseBranch = "develop",
                author = "qa@example.com",
                assignees = listOf("qa@example.com"),
                reviewers = listOf("developer@example.com", "analyst@example.com"),
                url = "https://github.com/example/data-specs/pull/38",
                createdAt = LocalDateTime.now().minusHours(4),
                updatedAt = LocalDateTime.now().minusHours(1),
                mergedAt = null,
                closedAt = null,
                isMerged = false,
                isClosed = false,
                mergeCommitSha = null,
                changedFiles = 2,
                additions = 35,
                deletions = 15,
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
    ): GitHubRepositoryInfoResponse {
        log.debug("Mock GitHub: Getting repository info - owner: {}, repoName: {}", owner, repoName)
        return GitHubRepositoryInfoResponse(
            id = 123456789L,
            name = repoName,
            fullName = "$owner/$repoName",
            description = "Data platform specifications repository for $owner",
            defaultBranch = "main",
            isPrivate = true,
            language = "SQL",
            url = "https://github.com/$owner/$repoName",
            cloneUrl = "https://github.com/$owner/$repoName.git",
            sshUrl = "git@github.com:$owner/$repoName.git",
            createdAt = LocalDateTime.now().minusYears(1),
            updatedAt = LocalDateTime.now().minusDays(1),
            stargazersCount = 0,
            forksCount = 0,
        )
    }

    // ========================
    // Branch Operations
    // ========================

    override fun listBranches(
        owner: String,
        repoName: String,
    ): List<GitHubBranchResponse> {
        log.debug("Mock GitHub: Listing branches - owner: {}, repoName: {}", owner, repoName)
        return mockBranches.values.toList().sortedBy { it.name }
    }

    override fun getBranch(
        owner: String,
        repoName: String,
        branchName: String,
    ): GitHubBranchResponse? {
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
    ): BranchComparisonResponse? {
        log.debug(
            "Mock GitHub: Comparing branches - owner: {}, repoName: {}, base: {}, head: {}",
            owner,
            repoName,
            baseBranch,
            headBranch,
        )

        // Check if both branches exist
        mockBranches[baseBranch] ?: return null
        mockBranches[headBranch] ?: return null

        // Simulate comparison based on branch names
        val (aheadBy, behindBy, status) =
            when {
                baseBranch == headBranch -> Triple(0, 0, "identical")
                baseBranch == "main" && headBranch == "develop" -> Triple(5, 0, "ahead")
                baseBranch == "develop" && headBranch == "main" -> Triple(0, 5, "behind")
                baseBranch == "develop" && headBranch.startsWith("feature/") -> Triple(3, 2, "diverged")
                baseBranch == "develop" && headBranch.startsWith("fix/") -> Triple(2, 1, "diverged")
                else -> Triple(1, 0, "ahead")
            }

        return BranchComparisonResponse(
            baseBranch = baseBranch,
            headBranch = headBranch,
            aheadBy = aheadBy,
            behindBy = behindBy,
            status = status,
            totalCommits = aheadBy + behindBy,
            filesChanged = (aheadBy + behindBy) * 2,
            additions = (aheadBy + behindBy) * 50,
            deletions = (aheadBy + behindBy) * 10,
        )
    }

    // ========================
    // Pull Request Operations
    // ========================

    override fun listPullRequests(
        owner: String,
        repoName: String,
        filter: PullRequestFilter,
    ): List<GitHubPullRequestResponse> {
        log.debug(
            "Mock GitHub: Listing pull requests - owner: {}, repoName: {}, filter: {}",
            owner,
            repoName,
            filter,
        )

        var result = mockPullRequests

        // Apply filters - convert enum to lowercase string for comparison
        filter.state?.let { state ->
            result = result.filter { it.state == state.name.lowercase() }
        }
        filter.author?.let { author ->
            result = result.filter { it.author.contains(author, ignoreCase = true) }
        }
        filter.targetBranch?.let { branch ->
            result = result.filter { it.baseBranch == branch }
        }

        return result
            .sortedByDescending { it.updatedAt }
            .take(filter.limit)
    }

    override fun getPullRequest(
        owner: String,
        repoName: String,
        prNumber: Long,
    ): GitHubPullRequestResponse? {
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
