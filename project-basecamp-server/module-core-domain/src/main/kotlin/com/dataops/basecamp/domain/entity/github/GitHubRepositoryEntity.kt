package com.dataops.basecamp.domain.entity.github

import com.dataops.basecamp.domain.entity.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Index
import jakarta.persistence.Table
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

/**
 * GitHub Repository Entity
 *
 * 팀별 GitHub Repository 정보를 관리합니다.
 * 각 팀은 하나의 Repository만 가질 수 있습니다 (1:1 관계).
 */
@Entity
@Table(
    name = "github_repositories",
    indexes = [
        Index(name = "uk_github_repo_team", columnList = "team", unique = true),
        Index(name = "uk_github_repo_url", columnList = "repository_url", unique = true),
        Index(name = "uk_github_repo_owner_name", columnList = "owner, repo_name", unique = true),
        Index(name = "idx_github_repo_active", columnList = "is_active"),
        Index(name = "idx_github_repo_owner", columnList = "owner"),
    ],
)
class GitHubRepositoryEntity(
    @NotBlank(message = "Team is required")
    @Size(max = 255, message = "Team must not exceed 255 characters")
    @Column(name = "team", nullable = false, unique = true, length = 255)
    var team: String = "",
    @NotBlank(message = "Owner is required")
    @Size(max = 255, message = "Owner must not exceed 255 characters")
    @Column(name = "owner", nullable = false, length = 255)
    var owner: String = "",
    @NotBlank(message = "Repository name is required")
    @Size(max = 255, message = "Repository name must not exceed 255 characters")
    @Column(name = "repo_name", nullable = false, length = 255)
    var repoName: String = "",
    @NotBlank(message = "Repository URL is required")
    @Size(max = 500, message = "Repository URL must not exceed 500 characters")
    @Column(name = "repository_url", nullable = false, unique = true, length = 500)
    var repositoryUrl: String = "",
    @Size(max = 100, message = "Default branch must not exceed 100 characters")
    @Column(name = "default_branch", nullable = false, length = 100)
    var defaultBranch: String = "main",
    @Size(max = 100, message = "Develop branch must not exceed 100 characters")
    @Column(name = "develop_branch", nullable = false, length = 100)
    var developBranch: String = "develop",
    @NotBlank(message = "S3 dev path is required")
    @Size(max = 500, message = "S3 dev path must not exceed 500 characters")
    @Column(name = "s3_dev_path", nullable = false, length = 500)
    var s3DevPath: String = "",
    @NotBlank(message = "S3 prod path is required")
    @Size(max = 500, message = "S3 prod path must not exceed 500 characters")
    @Column(name = "s3_prod_path", nullable = false, length = 500)
    var s3ProdPath: String = "",
    @Column(name = "is_active", nullable = false)
    var isActive: Boolean = true,
    @Size(max = 1000, message = "Description must not exceed 1000 characters")
    @Column(name = "description", length = 1000)
    var description: String? = null,
) : BaseEntity() {
    /**
     * Repository가 활성 상태인지 확인
     */
    fun isActiveRepository(): Boolean = isActive && !isDeleted

    /**
     * Repository 비활성화
     */
    fun deactivate() {
        isActive = false
    }

    /**
     * Repository 활성화
     */
    fun activate() {
        isActive = true
    }

    /**
     * GitHub 전체 Repository 이름 (owner/repoName)
     */
    fun getFullName(): String = "$owner/$repoName"

    /**
     * Repository 정보 업데이트
     */
    fun update(
        defaultBranch: String? = null,
        developBranch: String? = null,
        s3DevPath: String? = null,
        s3ProdPath: String? = null,
        description: String? = null,
        isActive: Boolean? = null,
    ) {
        defaultBranch?.let { this.defaultBranch = it }
        developBranch?.let { this.developBranch = it }
        s3DevPath?.let { this.s3DevPath = it }
        s3ProdPath?.let { this.s3ProdPath = it }
        description?.let { this.description = it }
        isActive?.let { this.isActive = it }
    }
}
