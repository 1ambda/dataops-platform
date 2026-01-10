package com.dataops.basecamp.domain.service

import com.dataops.basecamp.common.exception.WorksheetFolderAlreadyExistsException
import com.dataops.basecamp.common.exception.WorksheetFolderNotFoundException
import com.dataops.basecamp.domain.entity.sql.WorksheetFolderEntity
import com.dataops.basecamp.domain.repository.sql.WorksheetFolderRepositoryDsl
import com.dataops.basecamp.domain.repository.sql.WorksheetFolderRepositoryJpa
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
 * WorksheetFolderService Unit Tests
 *
 * Uses MockK to isolate dependencies and test pure business logic.
 * v3.0.0: Migrated from project-based to team-based architecture.
 */
@DisplayName("WorksheetFolderService Unit Tests")
class WorksheetFolderServiceTest {
    private val worksheetFolderRepositoryJpa: WorksheetFolderRepositoryJpa = mockk()
    private val worksheetFolderRepositoryDsl: WorksheetFolderRepositoryDsl = mockk()

    private val worksheetFolderService =
        WorksheetFolderService(
            worksheetFolderRepositoryJpa,
            worksheetFolderRepositoryDsl,
        )

    private lateinit var testFolder: WorksheetFolderEntity

    private val teamId = 1L
    private val folderId = 100L

    @BeforeEach
    fun setUp() {
        testFolder =
            WorksheetFolderEntity(
                teamId = teamId,
                name = "test-folder",
                description = "Test folder description",
                displayOrder = 0,
            ).apply {
                val idField = this::class.java.superclass.getDeclaredField("id")
                idField.isAccessible = true
                idField.set(this, folderId)

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
            val folderName = "new-folder"
            val description = "New folder description"
            val displayOrder = 1

            every {
                worksheetFolderRepositoryJpa.existsByNameAndTeamIdAndDeletedAtIsNull(folderName, teamId)
            } returns false

            val savedFolderSlot = slot<WorksheetFolderEntity>()
            every { worksheetFolderRepositoryJpa.save(capture(savedFolderSlot)) } answers {
                savedFolderSlot.captured.apply {
                    val idField = this::class.java.superclass.getDeclaredField("id")
                    idField.isAccessible = true
                    idField.set(this, 101L)
                }
            }

            // When
            val result =
                worksheetFolderService.createFolder(
                    teamId = teamId,
                    name = folderName,
                    description = description,
                    displayOrder = displayOrder,
                )

            // Then
            assertThat(result.teamId).isEqualTo(teamId)
            assertThat(result.name).isEqualTo(folderName)
            assertThat(result.description).isEqualTo(description)
            assertThat(result.displayOrder).isEqualTo(displayOrder)

            verify(exactly = 1) {
                worksheetFolderRepositoryJpa.existsByNameAndTeamIdAndDeletedAtIsNull(folderName, teamId)
            }
            verify(exactly = 1) { worksheetFolderRepositoryJpa.save(any()) }
        }

        @Test
        @DisplayName("should create folder without description")
        fun `should create folder without description`() {
            // Given
            val folderName = "folder-no-desc"

            every {
                worksheetFolderRepositoryJpa.existsByNameAndTeamIdAndDeletedAtIsNull(folderName, teamId)
            } returns false

            val savedFolderSlot = slot<WorksheetFolderEntity>()
            every { worksheetFolderRepositoryJpa.save(capture(savedFolderSlot)) } answers {
                savedFolderSlot.captured.apply {
                    val idField = this::class.java.superclass.getDeclaredField("id")
                    idField.isAccessible = true
                    idField.set(this, 102L)
                }
            }

            // When
            val result =
                worksheetFolderService.createFolder(
                    teamId = teamId,
                    name = folderName,
                )

            // Then
            assertThat(result.name).isEqualTo(folderName)
            assertThat(result.description).isNull()
            assertThat(result.displayOrder).isEqualTo(0) // default value
        }

        @Test
        @DisplayName("should throw WorksheetFolderAlreadyExistsException when folder name already exists")
        fun `should throw WorksheetFolderAlreadyExistsException when folder name already exists`() {
            // Given
            val folderName = "existing-folder"

            every {
                worksheetFolderRepositoryJpa.existsByNameAndTeamIdAndDeletedAtIsNull(folderName, teamId)
            } returns true

            // When & Then
            val exception =
                assertThrows<WorksheetFolderAlreadyExistsException> {
                    worksheetFolderService.createFolder(
                        teamId = teamId,
                        name = folderName,
                    )
                }

            assertThat(exception.folderName).isEqualTo(folderName)
            assertThat(exception.teamId).isEqualTo(teamId)

            verify(exactly = 1) {
                worksheetFolderRepositoryJpa.existsByNameAndTeamIdAndDeletedAtIsNull(folderName, teamId)
            }
            verify(exactly = 0) { worksheetFolderRepositoryJpa.save(any()) }
        }
    }

    @Nested
    @DisplayName("deleteFolder")
    inner class DeleteFolder {
        @Test
        @DisplayName("should soft delete folder successfully")
        fun `should soft delete folder successfully`() {
            // Given
            every {
                worksheetFolderRepositoryJpa.findByIdAndTeamIdAndDeletedAtIsNull(folderId, teamId)
            } returns testFolder
            every { worksheetFolderRepositoryJpa.save(any()) } answers { firstArg() }

            // When
            worksheetFolderService.deleteFolder(teamId, folderId)

            // Then
            assertThat(testFolder.deletedAt).isNotNull()
            verify(exactly = 1) {
                worksheetFolderRepositoryJpa.findByIdAndTeamIdAndDeletedAtIsNull(folderId, teamId)
            }
            verify(exactly = 1) { worksheetFolderRepositoryJpa.save(any()) }
        }

        @Test
        @DisplayName("should throw WorksheetFolderNotFoundException when folder does not exist")
        fun `should throw WorksheetFolderNotFoundException when folder does not exist`() {
            // Given
            val nonExistentFolderId = 999L

            every {
                worksheetFolderRepositoryJpa.findByIdAndTeamIdAndDeletedAtIsNull(nonExistentFolderId, teamId)
            } returns null

            // When & Then
            val exception =
                assertThrows<WorksheetFolderNotFoundException> {
                    worksheetFolderService.deleteFolder(teamId, nonExistentFolderId)
                }

            assertThat(exception.folderId).isEqualTo(nonExistentFolderId)
            assertThat(exception.teamId).isEqualTo(teamId)

            verify(exactly = 1) {
                worksheetFolderRepositoryJpa.findByIdAndTeamIdAndDeletedAtIsNull(nonExistentFolderId, teamId)
            }
            verify(exactly = 0) { worksheetFolderRepositoryJpa.save(any()) }
        }

        @Test
        @DisplayName("should set deletedAt timestamp when soft deleting")
        fun `should set deletedAt timestamp when soft deleting`() {
            // Given
            val beforeDelete = LocalDateTime.now()

            every {
                worksheetFolderRepositoryJpa.findByIdAndTeamIdAndDeletedAtIsNull(folderId, teamId)
            } returns testFolder
            every { worksheetFolderRepositoryJpa.save(any()) } answers { firstArg() }

            // When
            worksheetFolderService.deleteFolder(teamId, folderId)

            // Then
            assertThat(testFolder.deletedAt).isNotNull()
            assertThat(testFolder.deletedAt).isAfterOrEqualTo(beforeDelete)
        }
    }

    @Nested
    @DisplayName("getFolderById")
    inner class GetFolderById {
        @Test
        @DisplayName("should return folder when found by id and teamId")
        fun `should return folder when found by id and teamId`() {
            // Given
            every {
                worksheetFolderRepositoryJpa.findByIdAndTeamIdAndDeletedAtIsNull(folderId, teamId)
            } returns testFolder

            // When
            val result = worksheetFolderService.getFolderById(teamId, folderId)

            // Then
            assertThat(result).isNotNull
            assertThat(result?.id).isEqualTo(folderId)
            assertThat(result?.teamId).isEqualTo(teamId)
            assertThat(result?.name).isEqualTo(testFolder.name)

            verify(exactly = 1) {
                worksheetFolderRepositoryJpa.findByIdAndTeamIdAndDeletedAtIsNull(folderId, teamId)
            }
        }

        @Test
        @DisplayName("should return null when folder not found")
        fun `should return null when folder not found`() {
            // Given
            val nonExistentFolderId = 999L

            every {
                worksheetFolderRepositoryJpa.findByIdAndTeamIdAndDeletedAtIsNull(nonExistentFolderId, teamId)
            } returns null

            // When
            val result = worksheetFolderService.getFolderById(teamId, nonExistentFolderId)

            // Then
            assertThat(result).isNull()
        }

        @Test
        @DisplayName("should not return soft deleted folder")
        fun `should not return soft deleted folder`() {
            // Given
            // findByIdAndTeamIdAndDeletedAtIsNull returns null for soft deleted folders
            every {
                worksheetFolderRepositoryJpa.findByIdAndTeamIdAndDeletedAtIsNull(folderId, teamId)
            } returns null

            // When
            val result = worksheetFolderService.getFolderById(teamId, folderId)

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
            every {
                worksheetFolderRepositoryJpa.findByIdAndTeamIdAndDeletedAtIsNull(folderId, teamId)
            } returns testFolder

            // When
            val result = worksheetFolderService.getFolderByIdOrThrow(teamId, folderId)

            // Then
            assertThat(result.id).isEqualTo(folderId)
            assertThat(result.name).isEqualTo(testFolder.name)
        }

        @Test
        @DisplayName("should throw WorksheetFolderNotFoundException when not found")
        fun `should throw WorksheetFolderNotFoundException when not found`() {
            // Given
            val nonExistentFolderId = 999L

            every {
                worksheetFolderRepositoryJpa.findByIdAndTeamIdAndDeletedAtIsNull(nonExistentFolderId, teamId)
            } returns null

            // When & Then
            val exception =
                assertThrows<WorksheetFolderNotFoundException> {
                    worksheetFolderService.getFolderByIdOrThrow(teamId, nonExistentFolderId)
                }

            assertThat(exception.folderId).isEqualTo(nonExistentFolderId)
            assertThat(exception.teamId).isEqualTo(teamId)
        }
    }

    @Nested
    @DisplayName("listFolders")
    inner class ListFolders {
        @Test
        @DisplayName("should return all folders for team ordered by displayOrder")
        fun `should return all folders for team ordered by displayOrder`() {
            // Given
            val folder1 =
                WorksheetFolderEntity(
                    teamId = teamId,
                    name = "folder-1",
                    displayOrder = 0,
                ).apply {
                    val idField = this::class.java.superclass.getDeclaredField("id")
                    idField.isAccessible = true
                    idField.set(this, 101L)
                }
            val folder2 =
                WorksheetFolderEntity(
                    teamId = teamId,
                    name = "folder-2",
                    displayOrder = 1,
                ).apply {
                    val idField = this::class.java.superclass.getDeclaredField("id")
                    idField.isAccessible = true
                    idField.set(this, 102L)
                }
            val folders = listOf(folder1, folder2)

            every {
                worksheetFolderRepositoryDsl.findAllByTeamIdOrderByDisplayOrder(teamId)
            } returns folders

            // When
            val result = worksheetFolderService.listFolders(teamId)

            // Then
            assertThat(result).hasSize(2)
            assertThat(result[0].name).isEqualTo("folder-1")
            assertThat(result[1].name).isEqualTo("folder-2")

            verify(exactly = 1) {
                worksheetFolderRepositoryDsl.findAllByTeamIdOrderByDisplayOrder(teamId)
            }
        }

        @Test
        @DisplayName("should return empty list when no folders exist")
        fun `should return empty list when no folders exist`() {
            // Given
            every {
                worksheetFolderRepositoryDsl.findAllByTeamIdOrderByDisplayOrder(teamId)
            } returns emptyList()

            // When
            val result = worksheetFolderService.listFolders(teamId)

            // Then
            assertThat(result).isEmpty()
        }
    }

    @Nested
    @DisplayName("countFolders")
    inner class CountFolders {
        @Test
        @DisplayName("should return count of non-deleted folders")
        fun `should return count of non-deleted folders`() {
            // Given
            every {
                worksheetFolderRepositoryJpa.countByTeamIdAndDeletedAtIsNull(teamId)
            } returns 5L

            // When
            val result = worksheetFolderService.countFolders(teamId)

            // Then
            assertThat(result).isEqualTo(5L)
            verify(exactly = 1) {
                worksheetFolderRepositoryJpa.countByTeamIdAndDeletedAtIsNull(teamId)
            }
        }

        @Test
        @DisplayName("should return 0 when no folders exist")
        fun `should return 0 when no folders exist`() {
            // Given
            every {
                worksheetFolderRepositoryJpa.countByTeamIdAndDeletedAtIsNull(teamId)
            } returns 0L

            // When
            val result = worksheetFolderService.countFolders(teamId)

            // Then
            assertThat(result).isEqualTo(0L)
        }
    }
}
