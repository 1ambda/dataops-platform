package com.dataops.basecamp.domain.service

import com.dataops.basecamp.common.exception.ProjectNotFoundException
import com.dataops.basecamp.common.exception.SqlFolderAlreadyExistsException
import com.dataops.basecamp.common.exception.SqlFolderNotFoundException
import com.dataops.basecamp.domain.entity.project.ProjectEntity
import com.dataops.basecamp.domain.entity.sql.SqlFolderEntity
import com.dataops.basecamp.domain.repository.sql.SqlFolderRepositoryDsl
import com.dataops.basecamp.domain.repository.sql.SqlFolderRepositoryJpa
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
 * SqlFolderService Unit Tests
 *
 * Uses MockK to isolate dependencies and test pure business logic.
 */
@DisplayName("SqlFolderService Unit Tests")
class SqlFolderServiceTest {
    private val sqlFolderRepositoryJpa: SqlFolderRepositoryJpa = mockk()
    private val sqlFolderRepositoryDsl: SqlFolderRepositoryDsl = mockk()
    private val projectService: ProjectService = mockk()

    private val sqlFolderService =
        SqlFolderService(
            sqlFolderRepositoryJpa,
            sqlFolderRepositoryDsl,
            projectService,
        )

    private lateinit var testProject: ProjectEntity
    private lateinit var testFolder: SqlFolderEntity

    @BeforeEach
    fun setUp() {
        testProject =
            ProjectEntity(
                name = "test-project",
                displayName = "Test Project",
                description = "Test project description",
            ).apply {
                val idField = this::class.java.superclass.getDeclaredField("id")
                idField.isAccessible = true
                idField.set(this, 1L)
            }

        testFolder =
            SqlFolderEntity(
                projectId = 1L,
                name = "test-folder",
                description = "Test folder description",
                displayOrder = 0,
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
    }

    @Nested
    @DisplayName("createFolder")
    inner class CreateFolder {
        @Test
        @DisplayName("should create folder successfully when name does not exist")
        fun `should create folder successfully when name does not exist`() {
            // Given
            val projectId = 1L
            val folderName = "new-folder"
            val description = "New folder description"
            val displayOrder = 1

            every { projectService.getProjectByIdOrThrow(projectId) } returns testProject
            every {
                sqlFolderRepositoryJpa.existsByNameAndProjectIdAndDeletedAtIsNull(folderName, projectId)
            } returns false

            val savedFolderSlot = slot<SqlFolderEntity>()
            every { sqlFolderRepositoryJpa.save(capture(savedFolderSlot)) } answers {
                savedFolderSlot.captured.apply {
                    val idField = this::class.java.superclass.getDeclaredField("id")
                    idField.isAccessible = true
                    idField.set(this, 101L)
                }
            }

            // When
            val result =
                sqlFolderService.createFolder(
                    projectId = projectId,
                    name = folderName,
                    description = description,
                    displayOrder = displayOrder,
                )

            // Then
            assertThat(result.projectId).isEqualTo(projectId)
            assertThat(result.name).isEqualTo(folderName)
            assertThat(result.description).isEqualTo(description)
            assertThat(result.displayOrder).isEqualTo(displayOrder)

            verify(exactly = 1) { projectService.getProjectByIdOrThrow(projectId) }
            verify(exactly = 1) {
                sqlFolderRepositoryJpa.existsByNameAndProjectIdAndDeletedAtIsNull(folderName, projectId)
            }
            verify(exactly = 1) { sqlFolderRepositoryJpa.save(any()) }
        }

        @Test
        @DisplayName("should create folder without description")
        fun `should create folder without description`() {
            // Given
            val projectId = 1L
            val folderName = "folder-no-desc"

            every { projectService.getProjectByIdOrThrow(projectId) } returns testProject
            every {
                sqlFolderRepositoryJpa.existsByNameAndProjectIdAndDeletedAtIsNull(folderName, projectId)
            } returns false

            val savedFolderSlot = slot<SqlFolderEntity>()
            every { sqlFolderRepositoryJpa.save(capture(savedFolderSlot)) } answers {
                savedFolderSlot.captured.apply {
                    val idField = this::class.java.superclass.getDeclaredField("id")
                    idField.isAccessible = true
                    idField.set(this, 102L)
                }
            }

            // When
            val result =
                sqlFolderService.createFolder(
                    projectId = projectId,
                    name = folderName,
                )

            // Then
            assertThat(result.name).isEqualTo(folderName)
            assertThat(result.description).isNull()
            assertThat(result.displayOrder).isEqualTo(0) // default value
        }

        @Test
        @DisplayName("should throw SqlFolderAlreadyExistsException when folder name already exists")
        fun `should throw SqlFolderAlreadyExistsException when folder name already exists`() {
            // Given
            val projectId = 1L
            val folderName = "existing-folder"

            every { projectService.getProjectByIdOrThrow(projectId) } returns testProject
            every {
                sqlFolderRepositoryJpa.existsByNameAndProjectIdAndDeletedAtIsNull(folderName, projectId)
            } returns true

            // When & Then
            val exception =
                assertThrows<SqlFolderAlreadyExistsException> {
                    sqlFolderService.createFolder(
                        projectId = projectId,
                        name = folderName,
                    )
                }

            assertThat(exception.folderName).isEqualTo(folderName)
            assertThat(exception.projectId).isEqualTo(projectId)

            verify(exactly = 1) { projectService.getProjectByIdOrThrow(projectId) }
            verify(exactly = 1) {
                sqlFolderRepositoryJpa.existsByNameAndProjectIdAndDeletedAtIsNull(folderName, projectId)
            }
            verify(exactly = 0) { sqlFolderRepositoryJpa.save(any()) }
        }

        @Test
        @DisplayName("should throw ProjectNotFoundException when project does not exist")
        fun `should throw ProjectNotFoundException when project does not exist`() {
            // Given
            val projectId = 999L
            val folderName = "new-folder"

            every { projectService.getProjectByIdOrThrow(projectId) } throws
                ProjectNotFoundException(projectId)

            // When & Then
            val exception =
                assertThrows<ProjectNotFoundException> {
                    sqlFolderService.createFolder(
                        projectId = projectId,
                        name = folderName,
                    )
                }

            assertThat(exception.projectId).isEqualTo(projectId)

            verify(exactly = 1) { projectService.getProjectByIdOrThrow(projectId) }
            verify(exactly = 0) {
                sqlFolderRepositoryJpa.existsByNameAndProjectIdAndDeletedAtIsNull(any(), any())
            }
            verify(exactly = 0) { sqlFolderRepositoryJpa.save(any()) }
        }
    }

    @Nested
    @DisplayName("deleteFolder")
    inner class DeleteFolder {
        @Test
        @DisplayName("should soft delete folder successfully")
        fun `should soft delete folder successfully`() {
            // Given
            val projectId = 1L
            val folderId = 100L

            every { projectService.getProjectByIdOrThrow(projectId) } returns testProject
            every {
                sqlFolderRepositoryJpa.findByIdAndProjectIdAndDeletedAtIsNull(folderId, projectId)
            } returns testFolder
            every { sqlFolderRepositoryJpa.save(any()) } answers { firstArg() }

            // When
            sqlFolderService.deleteFolder(projectId, folderId)

            // Then
            assertThat(testFolder.deletedAt).isNotNull()
            verify(exactly = 1) { projectService.getProjectByIdOrThrow(projectId) }
            verify(exactly = 1) {
                sqlFolderRepositoryJpa.findByIdAndProjectIdAndDeletedAtIsNull(folderId, projectId)
            }
            verify(exactly = 1) { sqlFolderRepositoryJpa.save(any()) }
        }

        @Test
        @DisplayName("should throw SqlFolderNotFoundException when folder does not exist")
        fun `should throw SqlFolderNotFoundException when folder does not exist`() {
            // Given
            val projectId = 1L
            val folderId = 999L

            every { projectService.getProjectByIdOrThrow(projectId) } returns testProject
            every {
                sqlFolderRepositoryJpa.findByIdAndProjectIdAndDeletedAtIsNull(folderId, projectId)
            } returns null

            // When & Then
            val exception =
                assertThrows<SqlFolderNotFoundException> {
                    sqlFolderService.deleteFolder(projectId, folderId)
                }

            assertThat(exception.folderId).isEqualTo(folderId)
            assertThat(exception.projectId).isEqualTo(projectId)

            verify(exactly = 1) { projectService.getProjectByIdOrThrow(projectId) }
            verify(exactly = 1) {
                sqlFolderRepositoryJpa.findByIdAndProjectIdAndDeletedAtIsNull(folderId, projectId)
            }
            verify(exactly = 0) { sqlFolderRepositoryJpa.save(any()) }
        }

        @Test
        @DisplayName("should throw ProjectNotFoundException when project does not exist")
        fun `should throw ProjectNotFoundException when project does not exist`() {
            // Given
            val projectId = 999L
            val folderId = 100L

            every { projectService.getProjectByIdOrThrow(projectId) } throws
                ProjectNotFoundException(projectId)

            // When & Then
            val exception =
                assertThrows<ProjectNotFoundException> {
                    sqlFolderService.deleteFolder(projectId, folderId)
                }

            assertThat(exception.projectId).isEqualTo(projectId)

            verify(exactly = 1) { projectService.getProjectByIdOrThrow(projectId) }
            verify(exactly = 0) {
                sqlFolderRepositoryJpa.findByIdAndProjectIdAndDeletedAtIsNull(any(), any())
            }
        }

        @Test
        @DisplayName("should set deletedAt timestamp when soft deleting")
        fun `should set deletedAt timestamp when soft deleting`() {
            // Given
            val projectId = 1L
            val folderId = 100L
            val beforeDelete = LocalDateTime.now()

            every { projectService.getProjectByIdOrThrow(projectId) } returns testProject
            every {
                sqlFolderRepositoryJpa.findByIdAndProjectIdAndDeletedAtIsNull(folderId, projectId)
            } returns testFolder
            every { sqlFolderRepositoryJpa.save(any()) } answers { firstArg() }

            // When
            sqlFolderService.deleteFolder(projectId, folderId)

            // Then
            assertThat(testFolder.deletedAt).isNotNull()
            assertThat(testFolder.deletedAt).isAfterOrEqualTo(beforeDelete)
        }
    }

    @Nested
    @DisplayName("getFolderById")
    inner class GetFolderById {
        @Test
        @DisplayName("should return folder when found by id and projectId")
        fun `should return folder when found by id and projectId`() {
            // Given
            val projectId = 1L
            val folderId = 100L

            every { projectService.getProjectByIdOrThrow(projectId) } returns testProject
            every {
                sqlFolderRepositoryJpa.findByIdAndProjectIdAndDeletedAtIsNull(folderId, projectId)
            } returns testFolder

            // When
            val result = sqlFolderService.getFolderById(projectId, folderId)

            // Then
            assertThat(result).isNotNull
            assertThat(result?.id).isEqualTo(folderId)
            assertThat(result?.projectId).isEqualTo(projectId)
            assertThat(result?.name).isEqualTo(testFolder.name)

            verify(exactly = 1) { projectService.getProjectByIdOrThrow(projectId) }
            verify(exactly = 1) {
                sqlFolderRepositoryJpa.findByIdAndProjectIdAndDeletedAtIsNull(folderId, projectId)
            }
        }

        @Test
        @DisplayName("should return null when folder not found")
        fun `should return null when folder not found`() {
            // Given
            val projectId = 1L
            val folderId = 999L

            every { projectService.getProjectByIdOrThrow(projectId) } returns testProject
            every {
                sqlFolderRepositoryJpa.findByIdAndProjectIdAndDeletedAtIsNull(folderId, projectId)
            } returns null

            // When
            val result = sqlFolderService.getFolderById(projectId, folderId)

            // Then
            assertThat(result).isNull()
        }

        @Test
        @DisplayName("should not return soft deleted folder")
        fun `should not return soft deleted folder`() {
            // Given
            val projectId = 1L
            val folderId = 100L

            every { projectService.getProjectByIdOrThrow(projectId) } returns testProject
            // findByIdAndProjectIdAndDeletedAtIsNull returns null for soft deleted folders
            every {
                sqlFolderRepositoryJpa.findByIdAndProjectIdAndDeletedAtIsNull(folderId, projectId)
            } returns null

            // When
            val result = sqlFolderService.getFolderById(projectId, folderId)

            // Then
            assertThat(result).isNull()
        }
    }

    @Nested
    @DisplayName("getFolderByIdOrThrow")
    inner class GetFolderByIdOrThrow {
        @Test
        @DisplayName("should return folder when found")
        fun `should return folder when found`() {
            // Given
            val projectId = 1L
            val folderId = 100L

            every { projectService.getProjectByIdOrThrow(projectId) } returns testProject
            every {
                sqlFolderRepositoryJpa.findByIdAndProjectIdAndDeletedAtIsNull(folderId, projectId)
            } returns testFolder

            // When
            val result = sqlFolderService.getFolderByIdOrThrow(projectId, folderId)

            // Then
            assertThat(result.id).isEqualTo(folderId)
            assertThat(result.name).isEqualTo(testFolder.name)
        }

        @Test
        @DisplayName("should throw SqlFolderNotFoundException when not found")
        fun `should throw SqlFolderNotFoundException when not found`() {
            // Given
            val projectId = 1L
            val folderId = 999L

            every { projectService.getProjectByIdOrThrow(projectId) } returns testProject
            every {
                sqlFolderRepositoryJpa.findByIdAndProjectIdAndDeletedAtIsNull(folderId, projectId)
            } returns null

            // When & Then
            val exception =
                assertThrows<SqlFolderNotFoundException> {
                    sqlFolderService.getFolderByIdOrThrow(projectId, folderId)
                }

            assertThat(exception.folderId).isEqualTo(folderId)
            assertThat(exception.projectId).isEqualTo(projectId)
        }

        @Test
        @DisplayName("should throw ProjectNotFoundException when project does not exist")
        fun `should throw ProjectNotFoundException when project does not exist`() {
            // Given
            val projectId = 999L
            val folderId = 100L

            every { projectService.getProjectByIdOrThrow(projectId) } throws
                ProjectNotFoundException(projectId)

            // When & Then
            val exception =
                assertThrows<ProjectNotFoundException> {
                    sqlFolderService.getFolderByIdOrThrow(projectId, folderId)
                }

            assertThat(exception.projectId).isEqualTo(projectId)
        }
    }

    @Nested
    @DisplayName("listFolders")
    inner class ListFolders {
        @Test
        @DisplayName("should return all folders for project ordered by displayOrder")
        fun `should return all folders for project ordered by displayOrder`() {
            // Given
            val projectId = 1L
            val folder1 =
                SqlFolderEntity(
                    projectId = projectId,
                    name = "folder-1",
                    displayOrder = 0,
                ).apply {
                    val idField = this::class.java.superclass.getDeclaredField("id")
                    idField.isAccessible = true
                    idField.set(this, 101L)
                }
            val folder2 =
                SqlFolderEntity(
                    projectId = projectId,
                    name = "folder-2",
                    displayOrder = 1,
                ).apply {
                    val idField = this::class.java.superclass.getDeclaredField("id")
                    idField.isAccessible = true
                    idField.set(this, 102L)
                }
            val folders = listOf(folder1, folder2)

            every { projectService.getProjectByIdOrThrow(projectId) } returns testProject
            every {
                sqlFolderRepositoryDsl.findAllByProjectIdOrderByDisplayOrder(projectId)
            } returns folders

            // When
            val result = sqlFolderService.listFolders(projectId)

            // Then
            assertThat(result).hasSize(2)
            assertThat(result[0].name).isEqualTo("folder-1")
            assertThat(result[1].name).isEqualTo("folder-2")

            verify(exactly = 1) { projectService.getProjectByIdOrThrow(projectId) }
            verify(exactly = 1) {
                sqlFolderRepositoryDsl.findAllByProjectIdOrderByDisplayOrder(projectId)
            }
        }

        @Test
        @DisplayName("should return empty list when no folders exist")
        fun `should return empty list when no folders exist`() {
            // Given
            val projectId = 1L

            every { projectService.getProjectByIdOrThrow(projectId) } returns testProject
            every {
                sqlFolderRepositoryDsl.findAllByProjectIdOrderByDisplayOrder(projectId)
            } returns emptyList()

            // When
            val result = sqlFolderService.listFolders(projectId)

            // Then
            assertThat(result).isEmpty()
        }

        @Test
        @DisplayName("should throw ProjectNotFoundException when project does not exist")
        fun `should throw ProjectNotFoundException when project does not exist`() {
            // Given
            val projectId = 999L

            every { projectService.getProjectByIdOrThrow(projectId) } throws
                ProjectNotFoundException(projectId)

            // When & Then
            val exception =
                assertThrows<ProjectNotFoundException> {
                    sqlFolderService.listFolders(projectId)
                }

            assertThat(exception.projectId).isEqualTo(projectId)

            verify(exactly = 1) { projectService.getProjectByIdOrThrow(projectId) }
            verify(exactly = 0) {
                sqlFolderRepositoryDsl.findAllByProjectIdOrderByDisplayOrder(any())
            }
        }
    }

    @Nested
    @DisplayName("countFolders")
    inner class CountFolders {
        @Test
        @DisplayName("should return count of non-deleted folders")
        fun `should return count of non-deleted folders`() {
            // Given
            val projectId = 1L

            every {
                sqlFolderRepositoryJpa.countByProjectIdAndDeletedAtIsNull(projectId)
            } returns 5L

            // When
            val result = sqlFolderService.countFolders(projectId)

            // Then
            assertThat(result).isEqualTo(5L)
            verify(exactly = 1) {
                sqlFolderRepositoryJpa.countByProjectIdAndDeletedAtIsNull(projectId)
            }
        }

        @Test
        @DisplayName("should return 0 when no folders exist")
        fun `should return 0 when no folders exist`() {
            // Given
            val projectId = 1L

            every {
                sqlFolderRepositoryJpa.countByProjectIdAndDeletedAtIsNull(projectId)
            } returns 0L

            // When
            val result = sqlFolderService.countFolders(projectId)

            // Then
            assertThat(result).isEqualTo(0L)
        }
    }
}
