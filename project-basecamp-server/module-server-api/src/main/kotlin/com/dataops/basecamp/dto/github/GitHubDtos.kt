package com.dataops.basecamp.dto.github

import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.LocalDateTime

// ========================
// Request DTOs
// ========================

/**
 * Request to register a new GitHub repository
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class RegisterGitHubRepositoryRequest(
    @field:NotBlank(message = "Team is required")
    @field:Size(max = 255, message = "Team must not exceed 255 characters")
    val team: String,
    @field:NotBlank(message = "Owner is required")
    @field:Size(max = 255, message = "Owner must not exceed 255 characters")
    val owner: String,
    @field:NotBlank(message = "Repository name is required")
    @field:Size(max = 255, message = "Repository name must not exceed 255 characters")
    @JsonProperty("repo_name")
    val repoName: String,
    @field:NotBlank(message = "Repository URL is required")
    @field:Size(max = 500, message = "Repository URL must not exceed 500 characters")
    @JsonProperty("repository_url")
    val repositoryUrl: String,
    @field:Size(max = 100, message = "Default branch must not exceed 100 characters")
    @JsonProperty("default_branch")
    val defaultBranch: String = "main",
    @field:Size(max = 100, message = "Develop branch must not exceed 100 characters")
    @JsonProperty("develop_branch")
    val developBranch: String = "develop",
    @field:NotBlank(message = "S3 dev path is required")
    @field:Size(max = 500, message = "S3 dev path must not exceed 500 characters")
    @JsonProperty("s3_dev_path")
    val s3DevPath: String,
    @field:NotBlank(message = "S3 prod path is required")
    @field:Size(max = 500, message = "S3 prod path must not exceed 500 characters")
    @JsonProperty("s3_prod_path")
    val s3ProdPath: String,
    @field:Size(max = 1000, message = "Description must not exceed 1000 characters")
    val description: String? = null,
)

/**
 * Request to update a GitHub repository
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class UpdateGitHubRepositoryRequest(
    @field:Size(max = 100, message = "Default branch must not exceed 100 characters")
    @JsonProperty("default_branch")
    val defaultBranch: String? = null,
    @field:Size(max = 100, message = "Develop branch must not exceed 100 characters")
    @JsonProperty("develop_branch")
    val developBranch: String? = null,
    @field:Size(max = 500, message = "S3 dev path must not exceed 500 characters")
    @JsonProperty("s3_dev_path")
    val s3DevPath: String? = null,
    @field:Size(max = 500, message = "S3 prod path must not exceed 500 characters")
    @JsonProperty("s3_prod_path")
    val s3ProdPath: String? = null,
    @field:Size(max = 1000, message = "Description must not exceed 1000 characters")
    val description: String? = null,
    @JsonProperty("is_active")
    val isActive: Boolean? = null,
)

// ========================
// Response DTOs
// ========================

/**
 * GitHub Repository Response DTO
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class GitHubRepositoryResponse(
    val id: Long,
    val team: String,
    val owner: String,
    @JsonProperty("repo_name")
    val repoName: String,
    @JsonProperty("repository_url")
    val repositoryUrl: String,
    @JsonProperty("default_branch")
    val defaultBranch: String,
    @JsonProperty("develop_branch")
    val developBranch: String,
    @JsonProperty("s3_dev_path")
    val s3DevPath: String,
    @JsonProperty("s3_prod_path")
    val s3ProdPath: String,
    @JsonProperty("is_active")
    val isActive: Boolean,
    val description: String?,
    @JsonProperty("created_at")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'")
    val createdAt: LocalDateTime?,
    @JsonProperty("updated_at")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'")
    val updatedAt: LocalDateTime?,
)

// ========================
// Branch Response DTOs
// ========================

/**
 * GitHub Branch Response DTO
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class GitHubBranchResponse(
    val name: String,
    val sha: String,
    @JsonProperty("is_protected")
    val isProtected: Boolean,
    @JsonProperty("last_commit_date")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'")
    val lastCommitDate: LocalDateTime?,
    @JsonProperty("last_commit_author")
    val lastCommitAuthor: String?,
    @JsonProperty("last_commit_message")
    val lastCommitMessage: String?,
)

/**
 * Branch Comparison Response DTO
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class BranchComparisonResponse(
    @JsonProperty("base_branch")
    val baseBranch: String,
    @JsonProperty("head_branch")
    val headBranch: String,
    @JsonProperty("ahead_by")
    val aheadBy: Int,
    @JsonProperty("behind_by")
    val behindBy: Int,
    val status: String,
    val commits: List<CommitSummaryResponse>,
)

/**
 * Commit Summary Response DTO
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class CommitSummaryResponse(
    val sha: String,
    val message: String,
    val author: String,
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'")
    val date: LocalDateTime,
)

// ========================
// Pull Request Response DTOs
// ========================

/**
 * GitHub Pull Request Response DTO
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class GitHubPullRequestResponse(
    val number: Long,
    val title: String,
    val state: String,
    @JsonProperty("source_branch")
    val sourceBranch: String,
    @JsonProperty("target_branch")
    val targetBranch: String,
    val author: String,
    @JsonProperty("created_at")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'")
    val createdAt: LocalDateTime,
    @JsonProperty("updated_at")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'")
    val updatedAt: LocalDateTime,
    @JsonProperty("merged_at")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'")
    val mergedAt: LocalDateTime?,
    @JsonProperty("merged_by")
    val mergedBy: String?,
    val reviewers: List<String>,
    val labels: List<String>,
    val additions: Int,
    val deletions: Int,
    @JsonProperty("changed_files")
    val changedFiles: Int,
    val url: String,
)

/**
 * GitHub Pull Request List Response DTO (with metadata)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class GitHubPullRequestListResponse(
    val data: List<GitHubPullRequestResponse>,
    val metadata: ListMetadata,
)

/**
 * List Metadata DTO
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class ListMetadata(
    val total: Int,
    val limit: Int,
    @JsonProperty("has_more")
    val hasMore: Boolean,
)
