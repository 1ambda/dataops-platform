package com.github.lambda.domain.model.github

import com.github.lambda.domain.entity.github.GitHubRepositoryEntity
import org.assertj.core.api.Assertions.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

/**
 * GitHubRepositoryEntity Unit Tests
 *
 * Tests entity creation, update methods, and business logic.
 */
@DisplayName("GitHubRepositoryEntity Unit Tests")
class GitHubRepositoryEntityTest {
    private lateinit var testRepository: GitHubRepositoryEntity

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
                isActive = true,
                description = "Data models repository",
            )
    }

    @Nested
    @DisplayName("Entity Creation")
    inner class EntityCreation {
        @Test
        @DisplayName("should create entity with all required fields")
        fun `should create entity with all required fields`() {
            // Given & When
            val repository =
                GitHubRepositoryEntity(
                    team = "test-team",
                    owner = "test-org",
                    repoName = "test-repo",
                    repositoryUrl = "https://github.com/test-org/test-repo",
                    s3DevPath = "s3://bucket/dev/test-repo",
                    s3ProdPath = "s3://bucket/prod/test-repo",
                )

            // Then
            assertThat(repository.team).isEqualTo("test-team")
            assertThat(repository.owner).isEqualTo("test-org")
            assertThat(repository.repoName).isEqualTo("test-repo")
            assertThat(repository.repositoryUrl).isEqualTo("https://github.com/test-org/test-repo")
            assertThat(repository.s3DevPath).isEqualTo("s3://bucket/dev/test-repo")
            assertThat(repository.s3ProdPath).isEqualTo("s3://bucket/prod/test-repo")
        }

        @Test
        @DisplayName("should use default values for optional fields")
        fun `should use default values for optional fields`() {
            // Given & When
            val repository =
                GitHubRepositoryEntity(
                    team = "test-team",
                    owner = "test-org",
                    repoName = "test-repo",
                    repositoryUrl = "https://github.com/test-org/test-repo",
                    s3DevPath = "s3://bucket/dev/test-repo",
                    s3ProdPath = "s3://bucket/prod/test-repo",
                )

            // Then
            assertThat(repository.defaultBranch).isEqualTo("main")
            assertThat(repository.developBranch).isEqualTo("develop")
            assertThat(repository.isActive).isTrue()
            assertThat(repository.description).isNull()
        }

        @Test
        @DisplayName("should create entity with custom branch names")
        fun `should create entity with custom branch names`() {
            // Given & When
            val repository =
                GitHubRepositoryEntity(
                    team = "test-team",
                    owner = "test-org",
                    repoName = "test-repo",
                    repositoryUrl = "https://github.com/test-org/test-repo",
                    defaultBranch = "master",
                    developBranch = "dev",
                    s3DevPath = "s3://bucket/dev/test-repo",
                    s3ProdPath = "s3://bucket/prod/test-repo",
                )

            // Then
            assertThat(repository.defaultBranch).isEqualTo("master")
            assertThat(repository.developBranch).isEqualTo("dev")
        }
    }

    @Nested
    @DisplayName("getFullName")
    inner class GetFullName {
        @Test
        @DisplayName("should return owner/repoName format")
        fun `should return owner-repoName format`() {
            // When
            val fullName = testRepository.getFullName()

            // Then
            assertThat(fullName).isEqualTo("org-name/data-models")
        }

        @Test
        @DisplayName("should handle different owner and repo names")
        fun `should handle different owner and repo names`() {
            // Given
            val repository =
                GitHubRepositoryEntity(
                    team = "test-team",
                    owner = "my-organization",
                    repoName = "my-awesome-repo",
                    repositoryUrl = "https://github.com/my-organization/my-awesome-repo",
                    s3DevPath = "s3://bucket/dev/repo",
                    s3ProdPath = "s3://bucket/prod/repo",
                )

            // When
            val fullName = repository.getFullName()

            // Then
            assertThat(fullName).isEqualTo("my-organization/my-awesome-repo")
        }
    }

    @Nested
    @DisplayName("isActiveRepository")
    inner class IsActiveRepository {
        @Test
        @DisplayName("should return true when active and not deleted")
        fun `should return true when active and not deleted`() {
            // Given
            testRepository.isActive = true
            // deletedAt is null by default

            // When
            val result = testRepository.isActiveRepository()

            // Then
            assertThat(result).isTrue()
        }

        @Test
        @DisplayName("should return false when inactive")
        fun `should return false when inactive`() {
            // Given
            testRepository.isActive = false

            // When
            val result = testRepository.isActiveRepository()

            // Then
            assertThat(result).isFalse()
        }

        @Test
        @DisplayName("should return false when soft deleted")
        fun `should return false when soft deleted`() {
            // Given
            testRepository.isActive = true
            testRepository.deletedAt = LocalDateTime.now()

            // When
            val result = testRepository.isActiveRepository()

            // Then
            assertThat(result).isFalse()
        }

        @Test
        @DisplayName("should return false when inactive and deleted")
        fun `should return false when inactive and deleted`() {
            // Given
            testRepository.isActive = false
            testRepository.deletedAt = LocalDateTime.now()

            // When
            val result = testRepository.isActiveRepository()

            // Then
            assertThat(result).isFalse()
        }
    }

    @Nested
    @DisplayName("deactivate")
    inner class Deactivate {
        @Test
        @DisplayName("should set isActive to false")
        fun `should set isActive to false`() {
            // Given
            assertThat(testRepository.isActive).isTrue()

            // When
            testRepository.deactivate()

            // Then
            assertThat(testRepository.isActive).isFalse()
        }

        @Test
        @DisplayName("should remain false when already inactive")
        fun `should remain false when already inactive`() {
            // Given
            testRepository.isActive = false

            // When
            testRepository.deactivate()

            // Then
            assertThat(testRepository.isActive).isFalse()
        }
    }

    @Nested
    @DisplayName("activate")
    inner class Activate {
        @Test
        @DisplayName("should set isActive to true")
        fun `should set isActive to true`() {
            // Given
            testRepository.isActive = false

            // When
            testRepository.activate()

            // Then
            assertThat(testRepository.isActive).isTrue()
        }

        @Test
        @DisplayName("should remain true when already active")
        fun `should remain true when already active`() {
            // Given
            assertThat(testRepository.isActive).isTrue()

            // When
            testRepository.activate()

            // Then
            assertThat(testRepository.isActive).isTrue()
        }
    }

    @Nested
    @DisplayName("update")
    inner class Update {
        @Test
        @DisplayName("should update all provided fields")
        fun `should update all provided fields`() {
            // Given
            val newDefaultBranch = "master"
            val newDevelopBranch = "dev"
            val newS3DevPath = "s3://new-bucket/dev/repo"
            val newS3ProdPath = "s3://new-bucket/prod/repo"
            val newDescription = "Updated description"
            val newIsActive = false

            // When
            testRepository.update(
                defaultBranch = newDefaultBranch,
                developBranch = newDevelopBranch,
                s3DevPath = newS3DevPath,
                s3ProdPath = newS3ProdPath,
                description = newDescription,
                isActive = newIsActive,
            )

            // Then
            assertThat(testRepository.defaultBranch).isEqualTo(newDefaultBranch)
            assertThat(testRepository.developBranch).isEqualTo(newDevelopBranch)
            assertThat(testRepository.s3DevPath).isEqualTo(newS3DevPath)
            assertThat(testRepository.s3ProdPath).isEqualTo(newS3ProdPath)
            assertThat(testRepository.description).isEqualTo(newDescription)
            assertThat(testRepository.isActive).isEqualTo(newIsActive)
        }

        @Test
        @DisplayName("should only update provided fields (partial update)")
        fun `should only update provided fields (partial update)`() {
            // Given
            val originalDefaultBranch = testRepository.defaultBranch
            val originalDevelopBranch = testRepository.developBranch
            val originalS3DevPath = testRepository.s3DevPath
            val originalS3ProdPath = testRepository.s3ProdPath
            val originalIsActive = testRepository.isActive

            val newDescription = "Only description updated"

            // When
            testRepository.update(description = newDescription)

            // Then
            assertThat(testRepository.defaultBranch).isEqualTo(originalDefaultBranch)
            assertThat(testRepository.developBranch).isEqualTo(originalDevelopBranch)
            assertThat(testRepository.s3DevPath).isEqualTo(originalS3DevPath)
            assertThat(testRepository.s3ProdPath).isEqualTo(originalS3ProdPath)
            assertThat(testRepository.isActive).isEqualTo(originalIsActive)
            assertThat(testRepository.description).isEqualTo(newDescription)
        }

        @Test
        @DisplayName("should not modify any field when no parameters provided")
        fun `should not modify any field when no parameters provided`() {
            // Given
            val originalDefaultBranch = testRepository.defaultBranch
            val originalDevelopBranch = testRepository.developBranch
            val originalS3DevPath = testRepository.s3DevPath
            val originalS3ProdPath = testRepository.s3ProdPath
            val originalDescription = testRepository.description
            val originalIsActive = testRepository.isActive

            // When
            testRepository.update()

            // Then
            assertThat(testRepository.defaultBranch).isEqualTo(originalDefaultBranch)
            assertThat(testRepository.developBranch).isEqualTo(originalDevelopBranch)
            assertThat(testRepository.s3DevPath).isEqualTo(originalS3DevPath)
            assertThat(testRepository.s3ProdPath).isEqualTo(originalS3ProdPath)
            assertThat(testRepository.description).isEqualTo(originalDescription)
            assertThat(testRepository.isActive).isEqualTo(originalIsActive)
        }

        @Test
        @DisplayName("should update only defaultBranch")
        fun `should update only defaultBranch`() {
            // Given
            val originalDevelopBranch = testRepository.developBranch
            val newDefaultBranch = "master"

            // When
            testRepository.update(defaultBranch = newDefaultBranch)

            // Then
            assertThat(testRepository.defaultBranch).isEqualTo(newDefaultBranch)
            assertThat(testRepository.developBranch).isEqualTo(originalDevelopBranch)
        }

        @Test
        @DisplayName("should update only developBranch")
        fun `should update only developBranch`() {
            // Given
            val originalDefaultBranch = testRepository.defaultBranch
            val newDevelopBranch = "dev"

            // When
            testRepository.update(developBranch = newDevelopBranch)

            // Then
            assertThat(testRepository.defaultBranch).isEqualTo(originalDefaultBranch)
            assertThat(testRepository.developBranch).isEqualTo(newDevelopBranch)
        }

        @Test
        @DisplayName("should update only S3 paths")
        fun `should update only S3 paths`() {
            // Given
            val newS3DevPath = "s3://new-bucket/dev/repo"
            val newS3ProdPath = "s3://new-bucket/prod/repo"

            // When
            testRepository.update(s3DevPath = newS3DevPath, s3ProdPath = newS3ProdPath)

            // Then
            assertThat(testRepository.s3DevPath).isEqualTo(newS3DevPath)
            assertThat(testRepository.s3ProdPath).isEqualTo(newS3ProdPath)
        }

        @Test
        @DisplayName("should update isActive to false")
        fun `should update isActive to false`() {
            // Given
            assertThat(testRepository.isActive).isTrue()

            // When
            testRepository.update(isActive = false)

            // Then
            assertThat(testRepository.isActive).isFalse()
        }

        @Test
        @DisplayName("should update isActive to true")
        fun `should update isActive to true`() {
            // Given
            testRepository.isActive = false

            // When
            testRepository.update(isActive = true)

            // Then
            assertThat(testRepository.isActive).isTrue()
        }
    }

    @Nested
    @DisplayName("isDeleted")
    inner class IsDeleted {
        @Test
        @DisplayName("should return false when deletedAt is null")
        fun `should return false when deletedAt is null`() {
            // Given
            testRepository.deletedAt = null

            // When
            val result = testRepository.isDeleted

            // Then
            assertThat(result).isFalse()
        }

        @Test
        @DisplayName("should return true when deletedAt is set")
        fun `should return true when deletedAt is set`() {
            // Given
            testRepository.deletedAt = LocalDateTime.now()

            // When
            val result = testRepository.isDeleted

            // Then
            assertThat(result).isTrue()
        }
    }
}
