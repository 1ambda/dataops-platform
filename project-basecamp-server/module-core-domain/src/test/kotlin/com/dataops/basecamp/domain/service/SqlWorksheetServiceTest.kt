package com.dataops.basecamp.domain.service

import com.dataops.basecamp.common.enums.SqlDialect
import com.dataops.basecamp.common.exception.SqlWorksheetAlreadyExistsException
import com.dataops.basecamp.common.exception.SqlWorksheetNotFoundException
import com.dataops.basecamp.common.exception.WorksheetFolderNotFoundException
import com.dataops.basecamp.domain.entity.sql.SqlWorksheetEntity
import com.dataops.basecamp.domain.entity.sql.WorksheetFolderEntity
import com.dataops.basecamp.domain.repository.sql.SqlWorksheetRepositoryDsl
import com.dataops.basecamp.domain.repository.sql.SqlWorksheetRepositoryJpa
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
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import java.time.LocalDateTime

/**
 * SqlWorksheetService Unit Tests
 *
 * Uses MockK to isolate dependencies and test pure business logic.
 * v3.0.0: SqlFolder migrated to team-based, SqlWorksheet still uses projectId (deprecated).
 */
@DisplayName("SqlWorksheetService Unit Tests")
class SqlWorksheetServiceTest {
    private val sqlWorksheetRepositoryJpa: SqlWorksheetRepositoryJpa = mockk()
    private val sqlWorksheetRepositoryDsl: SqlWorksheetRepositoryDsl = mockk()
    private val worksheetFolderRepositoryJpa: WorksheetFolderRepositoryJpa = mockk()

    private val sqlWorksheetService =
        SqlWorksheetService(
            sqlWorksheetRepositoryJpa,
            sqlWorksheetRepositoryDsl,
            worksheetFolderRepositoryJpa,
        )

    private val projectId = 1L // Deprecated but still used in SqlWorksheetService
    private val teamId = 1L // SqlFolder now uses teamId
    private val folderId = 100L
    private val worksheetId = 1000L

    private lateinit var testFolder: WorksheetFolderEntity
    private lateinit var testWorksheet: SqlWorksheetEntity

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

        testWorksheet =
            SqlWorksheetEntity(
                folderId = folderId,
                name = "test-worksheet",
                description = "Test worksheet description",
                sqlText = "SELECT * FROM users",
                dialect = SqlDialect.BIGQUERY,
            ).apply {
                val idField = this::class.java.superclass.getDeclaredField("id")
                idField.isAccessible = true
                idField.set(this, worksheetId)

                val createdAtField = this::class.java.superclass.getDeclaredField("createdAt")
                createdAtField.isAccessible = true
                createdAtField.set(this, LocalDateTime.now())

                val updatedAtField = this::class.java.superclass.getDeclaredField("updatedAt")
                updatedAtField.isAccessible = true
                updatedAtField.set(this, LocalDateTime.now())
            }
    }

    @Nested
    @DisplayName("createWorksheet")
    inner class CreateWorksheet {
        @Test
        @DisplayName("should create worksheet successfully when name does not exist")
        fun `should create worksheet successfully when name does not exist`() {
            // Given
            val worksheetName = "new-worksheet"
            val description = "New worksheet description"
            val sqlText = "SELECT id, name FROM users"
            val dialect = SqlDialect.TRINO

            every { worksheetFolderRepositoryJpa.findByIdAndDeletedAtIsNull(folderId) } returns testFolder
            every {
                sqlWorksheetRepositoryJpa.existsByNameAndFolderIdAndDeletedAtIsNull(worksheetName, folderId)
            } returns false

            val savedWorksheetSlot = slot<SqlWorksheetEntity>()
            every { sqlWorksheetRepositoryJpa.save(capture(savedWorksheetSlot)) } answers {
                savedWorksheetSlot.captured.apply {
                    val idField = this::class.java.superclass.getDeclaredField("id")
                    idField.isAccessible = true
                    idField.set(this, 1001L)
                }
            }

            // When
            val result =
                sqlWorksheetService.createWorksheet(
                    projectId = projectId,
                    folderId = folderId,
                    name = worksheetName,
                    description = description,
                    sqlText = sqlText,
                    dialect = dialect,
                )

            // Then
            assertThat(result.folderId).isEqualTo(folderId)
            assertThat(result.name).isEqualTo(worksheetName)
            assertThat(result.description).isEqualTo(description)
            assertThat(result.sqlText).isEqualTo(sqlText)
            assertThat(result.dialect).isEqualTo(dialect)

            verify(exactly = 1) { worksheetFolderRepositoryJpa.findByIdAndDeletedAtIsNull(folderId) }
            verify(exactly = 1) {
                sqlWorksheetRepositoryJpa.existsByNameAndFolderIdAndDeletedAtIsNull(worksheetName, folderId)
            }
            verify(exactly = 1) { sqlWorksheetRepositoryJpa.save(any()) }
        }

        @Test
        @DisplayName("should create worksheet without description")
        fun `should create worksheet without description`() {
            // Given
            val worksheetName = "worksheet-no-desc"
            val sqlText = "SELECT 1"

            every { worksheetFolderRepositoryJpa.findByIdAndDeletedAtIsNull(folderId) } returns testFolder
            every {
                sqlWorksheetRepositoryJpa.existsByNameAndFolderIdAndDeletedAtIsNull(worksheetName, folderId)
            } returns false

            val savedWorksheetSlot = slot<SqlWorksheetEntity>()
            every { sqlWorksheetRepositoryJpa.save(capture(savedWorksheetSlot)) } answers {
                savedWorksheetSlot.captured.apply {
                    val idField = this::class.java.superclass.getDeclaredField("id")
                    idField.isAccessible = true
                    idField.set(this, 1002L)
                }
            }

            // When
            val result =
                sqlWorksheetService.createWorksheet(
                    projectId = projectId,
                    folderId = folderId,
                    name = worksheetName,
                    sqlText = sqlText,
                )

            // Then
            assertThat(result.name).isEqualTo(worksheetName)
            assertThat(result.description).isNull()
            assertThat(result.dialect).isEqualTo(SqlDialect.BIGQUERY) // default value
        }

        @Test
        @DisplayName("should throw WorksheetFolderNotFoundException when folder does not exist")
        fun `should throw WorksheetFolderNotFoundException when folder does not exist`() {
            // Given
            val nonExistentFolderId = 999L

            every { worksheetFolderRepositoryJpa.findByIdAndDeletedAtIsNull(nonExistentFolderId) } returns null

            // When & Then
            val exception =
                assertThrows<WorksheetFolderNotFoundException> {
                    sqlWorksheetService.createWorksheet(
                        projectId = projectId,
                        folderId = nonExistentFolderId,
                        name = "new-worksheet",
                        sqlText = "SELECT 1",
                    )
                }

            assertThat(exception.folderId).isEqualTo(nonExistentFolderId)

            verify(exactly = 1) { worksheetFolderRepositoryJpa.findByIdAndDeletedAtIsNull(nonExistentFolderId) }
            verify(exactly = 0) {
                sqlWorksheetRepositoryJpa.existsByNameAndFolderIdAndDeletedAtIsNull(any(), any())
            }
            verify(exactly = 0) { sqlWorksheetRepositoryJpa.save(any()) }
        }

        @Test
        @DisplayName("should throw SqlWorksheetAlreadyExistsException when worksheet name already exists")
        fun `should throw SqlWorksheetAlreadyExistsException when worksheet name already exists`() {
            // Given
            val existingName = "existing-worksheet"

            every { worksheetFolderRepositoryJpa.findByIdAndDeletedAtIsNull(folderId) } returns testFolder
            every {
                sqlWorksheetRepositoryJpa.existsByNameAndFolderIdAndDeletedAtIsNull(existingName, folderId)
            } returns true

            // When & Then
            val exception =
                assertThrows<SqlWorksheetAlreadyExistsException> {
                    sqlWorksheetService.createWorksheet(
                        projectId = projectId,
                        folderId = folderId,
                        name = existingName,
                        sqlText = "SELECT 1",
                    )
                }

            assertThat(exception.worksheetName).isEqualTo(existingName)
            assertThat(exception.folderId).isEqualTo(folderId)

            verify(exactly = 1) { worksheetFolderRepositoryJpa.findByIdAndDeletedAtIsNull(folderId) }
            verify(exactly = 1) {
                sqlWorksheetRepositoryJpa.existsByNameAndFolderIdAndDeletedAtIsNull(existingName, folderId)
            }
            verify(exactly = 0) { sqlWorksheetRepositoryJpa.save(any()) }
        }
    }

    @Nested
    @DisplayName("updateWorksheet")
    inner class UpdateWorksheet {
        @Test
        @DisplayName("should update worksheet successfully")
        fun `should update worksheet successfully`() {
            // Given
            val newName = "updated-worksheet"
            val newDescription = "Updated description"
            val newSqlText = "SELECT id, name, email FROM users"
            val newDialect = SqlDialect.TRINO

            every { worksheetFolderRepositoryJpa.findByIdAndDeletedAtIsNull(folderId) } returns testFolder
            every {
                sqlWorksheetRepositoryJpa.findByIdAndFolderIdAndDeletedAtIsNull(worksheetId, folderId)
            } returns testWorksheet
            every {
                sqlWorksheetRepositoryJpa.existsByNameAndFolderIdAndDeletedAtIsNull(newName, folderId)
            } returns false
            every { sqlWorksheetRepositoryJpa.save(any()) } answers { firstArg() }

            // When
            val result =
                sqlWorksheetService.updateWorksheet(
                    projectId = projectId,
                    folderId = folderId,
                    worksheetId = worksheetId,
                    name = newName,
                    description = newDescription,
                    sqlText = newSqlText,
                    dialect = newDialect,
                )

            // Then
            assertThat(result.name).isEqualTo(newName)
            assertThat(result.description).isEqualTo(newDescription)
            assertThat(result.sqlText).isEqualTo(newSqlText)
            assertThat(result.dialect).isEqualTo(newDialect)

            verify(exactly = 1) { worksheetFolderRepositoryJpa.findByIdAndDeletedAtIsNull(folderId) }
            verify(exactly = 1) {
                sqlWorksheetRepositoryJpa.findByIdAndFolderIdAndDeletedAtIsNull(worksheetId, folderId)
            }
            verify(exactly = 1) { sqlWorksheetRepositoryJpa.save(any()) }
        }

        @Test
        @DisplayName("should update worksheet with partial fields")
        fun `should update worksheet with partial fields`() {
            // Given
            val originalName = testWorksheet.name
            val originalSqlText = testWorksheet.sqlText
            val newDescription = "Only description updated"

            every { worksheetFolderRepositoryJpa.findByIdAndDeletedAtIsNull(folderId) } returns testFolder
            every {
                sqlWorksheetRepositoryJpa.findByIdAndFolderIdAndDeletedAtIsNull(worksheetId, folderId)
            } returns testWorksheet
            every { sqlWorksheetRepositoryJpa.save(any()) } answers { firstArg() }

            // When
            val result =
                sqlWorksheetService.updateWorksheet(
                    projectId = projectId,
                    folderId = folderId,
                    worksheetId = worksheetId,
                    description = newDescription,
                )

            // Then
            assertThat(result.name).isEqualTo(originalName)
            assertThat(result.description).isEqualTo(newDescription)
            assertThat(result.sqlText).isEqualTo(originalSqlText)

            verify(exactly = 1) { sqlWorksheetRepositoryJpa.save(any()) }
        }

        @Test
        @DisplayName("should throw SqlWorksheetNotFoundException when worksheet does not exist")
        fun `should throw SqlWorksheetNotFoundException when worksheet does not exist`() {
            // Given
            val nonExistentWorksheetId = 9999L

            every { worksheetFolderRepositoryJpa.findByIdAndDeletedAtIsNull(folderId) } returns testFolder
            every {
                sqlWorksheetRepositoryJpa.findByIdAndFolderIdAndDeletedAtIsNull(nonExistentWorksheetId, folderId)
            } returns null

            // When & Then
            val exception =
                assertThrows<SqlWorksheetNotFoundException> {
                    sqlWorksheetService.updateWorksheet(
                        projectId = projectId,
                        folderId = folderId,
                        worksheetId = nonExistentWorksheetId,
                        name = "new-name",
                    )
                }

            assertThat(exception.worksheetId).isEqualTo(nonExistentWorksheetId)
            assertThat(exception.folderId).isEqualTo(folderId)

            verify(exactly = 1) { worksheetFolderRepositoryJpa.findByIdAndDeletedAtIsNull(folderId) }
            verify(exactly = 1) {
                sqlWorksheetRepositoryJpa.findByIdAndFolderIdAndDeletedAtIsNull(nonExistentWorksheetId, folderId)
            }
            verify(exactly = 0) { sqlWorksheetRepositoryJpa.save(any()) }
        }

        @Test
        @DisplayName("should throw SqlWorksheetAlreadyExistsException when new name already exists")
        fun `should throw SqlWorksheetAlreadyExistsException when new name already exists`() {
            // Given
            val existingName = "existing-name"

            every { worksheetFolderRepositoryJpa.findByIdAndDeletedAtIsNull(folderId) } returns testFolder
            every {
                sqlWorksheetRepositoryJpa.findByIdAndFolderIdAndDeletedAtIsNull(worksheetId, folderId)
            } returns testWorksheet
            every {
                sqlWorksheetRepositoryJpa.existsByNameAndFolderIdAndDeletedAtIsNull(existingName, folderId)
            } returns true

            // When & Then
            val exception =
                assertThrows<SqlWorksheetAlreadyExistsException> {
                    sqlWorksheetService.updateWorksheet(
                        projectId = projectId,
                        folderId = folderId,
                        worksheetId = worksheetId,
                        name = existingName,
                    )
                }

            assertThat(exception.worksheetName).isEqualTo(existingName)
            assertThat(exception.folderId).isEqualTo(folderId)

            verify(exactly = 0) { sqlWorksheetRepositoryJpa.save(any()) }
        }

        @Test
        @DisplayName("should not check for duplicate name when name unchanged")
        fun `should not check for duplicate name when name unchanged`() {
            // Given
            val currentName = testWorksheet.name
            val newSqlText = "SELECT 1"

            every { worksheetFolderRepositoryJpa.findByIdAndDeletedAtIsNull(folderId) } returns testFolder
            every {
                sqlWorksheetRepositoryJpa.findByIdAndFolderIdAndDeletedAtIsNull(worksheetId, folderId)
            } returns testWorksheet
            every { sqlWorksheetRepositoryJpa.save(any()) } answers { firstArg() }

            // When
            val result =
                sqlWorksheetService.updateWorksheet(
                    projectId = projectId,
                    folderId = folderId,
                    worksheetId = worksheetId,
                    name = currentName, // Same name
                    sqlText = newSqlText,
                )

            // Then
            assertThat(result.sqlText).isEqualTo(newSqlText)

            // Should not check for duplicate since name is unchanged
            verify(exactly = 0) {
                sqlWorksheetRepositoryJpa.existsByNameAndFolderIdAndDeletedAtIsNull(any(), any())
            }
        }
    }

    @Nested
    @DisplayName("deleteWorksheet")
    inner class DeleteWorksheet {
        @Test
        @DisplayName("should soft delete worksheet successfully")
        fun `should soft delete worksheet successfully`() {
            // Given
            every { worksheetFolderRepositoryJpa.findByIdAndDeletedAtIsNull(folderId) } returns testFolder
            every {
                sqlWorksheetRepositoryJpa.findByIdAndFolderIdAndDeletedAtIsNull(worksheetId, folderId)
            } returns testWorksheet
            every { sqlWorksheetRepositoryJpa.save(any()) } answers { firstArg() }

            // When
            sqlWorksheetService.deleteWorksheet(projectId, folderId, worksheetId)

            // Then
            assertThat(testWorksheet.deletedAt).isNotNull()
            verify(exactly = 1) { worksheetFolderRepositoryJpa.findByIdAndDeletedAtIsNull(folderId) }
            verify(exactly = 1) {
                sqlWorksheetRepositoryJpa.findByIdAndFolderIdAndDeletedAtIsNull(worksheetId, folderId)
            }
            verify(exactly = 1) { sqlWorksheetRepositoryJpa.save(any()) }
        }

        @Test
        @DisplayName("should throw SqlWorksheetNotFoundException when worksheet does not exist")
        fun `should throw SqlWorksheetNotFoundException when worksheet does not exist`() {
            // Given
            val nonExistentWorksheetId = 9999L

            every { worksheetFolderRepositoryJpa.findByIdAndDeletedAtIsNull(folderId) } returns testFolder
            every {
                sqlWorksheetRepositoryJpa.findByIdAndFolderIdAndDeletedAtIsNull(nonExistentWorksheetId, folderId)
            } returns null

            // When & Then
            val exception =
                assertThrows<SqlWorksheetNotFoundException> {
                    sqlWorksheetService.deleteWorksheet(projectId, folderId, nonExistentWorksheetId)
                }

            assertThat(exception.worksheetId).isEqualTo(nonExistentWorksheetId)
            assertThat(exception.folderId).isEqualTo(folderId)

            verify(exactly = 0) { sqlWorksheetRepositoryJpa.save(any()) }
        }

        @Test
        @DisplayName("should throw WorksheetFolderNotFoundException when folder does not exist")
        fun `should throw WorksheetFolderNotFoundException when folder does not exist`() {
            // Given
            val nonExistentFolderId = 999L

            every { worksheetFolderRepositoryJpa.findByIdAndDeletedAtIsNull(nonExistentFolderId) } returns null

            // When & Then
            val exception =
                assertThrows<WorksheetFolderNotFoundException> {
                    sqlWorksheetService.deleteWorksheet(projectId, nonExistentFolderId, worksheetId)
                }

            assertThat(exception.folderId).isEqualTo(nonExistentFolderId)

            verify(exactly = 0) {
                sqlWorksheetRepositoryJpa.findByIdAndFolderIdAndDeletedAtIsNull(any(), any())
            }
        }

        @Test
        @DisplayName("should set deletedAt timestamp when soft deleting")
        fun `should set deletedAt timestamp when soft deleting`() {
            // Given
            val beforeDelete = LocalDateTime.now()

            every { worksheetFolderRepositoryJpa.findByIdAndDeletedAtIsNull(folderId) } returns testFolder
            every {
                sqlWorksheetRepositoryJpa.findByIdAndFolderIdAndDeletedAtIsNull(worksheetId, folderId)
            } returns testWorksheet
            every { sqlWorksheetRepositoryJpa.save(any()) } answers { firstArg() }

            // When
            sqlWorksheetService.deleteWorksheet(projectId, folderId, worksheetId)

            // Then
            assertThat(testWorksheet.deletedAt).isNotNull()
            assertThat(testWorksheet.deletedAt).isAfterOrEqualTo(beforeDelete)
        }
    }

    @Nested
    @DisplayName("getWorksheetById")
    inner class GetWorksheetById {
        @Test
        @DisplayName("should return worksheet when found by id and folderId")
        fun `should return worksheet when found by id and folderId`() {
            // Given
            every {
                sqlWorksheetRepositoryJpa.findByIdAndFolderIdAndDeletedAtIsNull(worksheetId, folderId)
            } returns testWorksheet

            // When
            val result = sqlWorksheetService.getWorksheetById(folderId, worksheetId)

            // Then
            assertThat(result).isNotNull
            assertThat(result?.id).isEqualTo(worksheetId)
            assertThat(result?.folderId).isEqualTo(folderId)
            assertThat(result?.name).isEqualTo(testWorksheet.name)

            verify(exactly = 1) {
                sqlWorksheetRepositoryJpa.findByIdAndFolderIdAndDeletedAtIsNull(worksheetId, folderId)
            }
        }

        @Test
        @DisplayName("should return null when worksheet not found")
        fun `should return null when worksheet not found`() {
            // Given
            val nonExistentWorksheetId = 9999L

            every {
                sqlWorksheetRepositoryJpa.findByIdAndFolderIdAndDeletedAtIsNull(nonExistentWorksheetId, folderId)
            } returns null

            // When
            val result = sqlWorksheetService.getWorksheetById(folderId, nonExistentWorksheetId)

            // Then
            assertThat(result).isNull()
        }

        @Test
        @DisplayName("should not return soft deleted worksheet")
        fun `should not return soft deleted worksheet`() {
            // Given
            // findByIdAndFolderIdAndDeletedAtIsNull returns null for soft deleted worksheets
            every {
                sqlWorksheetRepositoryJpa.findByIdAndFolderIdAndDeletedAtIsNull(worksheetId, folderId)
            } returns null

            // When
            val result = sqlWorksheetService.getWorksheetById(folderId, worksheetId)

            // Then
            assertThat(result).isNull()
        }
    }

    @Nested
    @DisplayName("getWorksheetByIdOrThrow")
    inner class GetWorksheetByIdOrThrow {
        @Test
        @DisplayName("should return worksheet when found")
        fun `should return worksheet when found`() {
            // Given
            every {
                sqlWorksheetRepositoryJpa.findByIdAndFolderIdAndDeletedAtIsNull(worksheetId, folderId)
            } returns testWorksheet

            // When
            val result = sqlWorksheetService.getWorksheetByIdOrThrow(folderId, worksheetId)

            // Then
            assertThat(result.id).isEqualTo(worksheetId)
            assertThat(result.name).isEqualTo(testWorksheet.name)
        }

        @Test
        @DisplayName("should throw SqlWorksheetNotFoundException when not found")
        fun `should throw SqlWorksheetNotFoundException when not found`() {
            // Given
            val nonExistentWorksheetId = 9999L

            every {
                sqlWorksheetRepositoryJpa.findByIdAndFolderIdAndDeletedAtIsNull(nonExistentWorksheetId, folderId)
            } returns null

            // When & Then
            val exception =
                assertThrows<SqlWorksheetNotFoundException> {
                    sqlWorksheetService.getWorksheetByIdOrThrow(folderId, nonExistentWorksheetId)
                }

            assertThat(exception.worksheetId).isEqualTo(nonExistentWorksheetId)
            assertThat(exception.folderId).isEqualTo(folderId)
        }
    }

    @Nested
    @DisplayName("listWorksheets")
    inner class ListWorksheets {
        @Test
        @DisplayName("should return all worksheets for project")
        fun `should return all worksheets for project`() {
            // Given
            val worksheet1 = createTestWorksheet(1001L, folderId, "worksheet-1")
            val worksheet2 = createTestWorksheet(1002L, folderId, "worksheet-2")
            val worksheets = listOf(worksheet1, worksheet2)
            val pageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "updatedAt"))
            val page = PageImpl(worksheets, pageable, 2)

            every {
                sqlWorksheetRepositoryDsl.findByConditions(
                    projectId = projectId,
                    folderId = null,
                    searchText = null,
                    starred = null,
                    dialect = null,
                    pageable = pageable,
                )
            } returns page

            // When
            val result =
                sqlWorksheetService.listWorksheets(
                    projectId = projectId,
                    folderId = null,
                    searchText = null,
                    starred = null,
                    dialect = null,
                    pageable = pageable,
                )

            // Then
            assertThat(result.content).hasSize(2)
            assertThat(result.content[0].name).isEqualTo("worksheet-1")
            assertThat(result.content[1].name).isEqualTo("worksheet-2")

            verify(exactly = 1) {
                sqlWorksheetRepositoryDsl.findByConditions(
                    projectId = projectId,
                    folderId = null,
                    searchText = null,
                    starred = null,
                    dialect = null,
                    pageable = pageable,
                )
            }
        }

        @Test
        @DisplayName("should filter worksheets by folderId")
        fun `should filter worksheets by folderId`() {
            // Given
            val worksheet1 = createTestWorksheet(1001L, folderId, "worksheet-in-folder")
            val worksheets = listOf(worksheet1)
            val pageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "updatedAt"))
            val page = PageImpl(worksheets, pageable, 1)

            every {
                sqlWorksheetRepositoryDsl.findByConditions(
                    projectId = projectId,
                    folderId = folderId,
                    searchText = null,
                    starred = null,
                    dialect = null,
                    pageable = pageable,
                )
            } returns page

            // When
            val result =
                sqlWorksheetService.listWorksheets(
                    projectId = projectId,
                    folderId = folderId,
                    searchText = null,
                    starred = null,
                    dialect = null,
                    pageable = pageable,
                )

            // Then
            assertThat(result.content).hasSize(1)
            assertThat(result.content[0].folderId).isEqualTo(folderId)
        }

        @Test
        @DisplayName("should filter worksheets by searchText")
        fun `should filter worksheets by searchText`() {
            // Given
            val searchText = "user"
            val worksheet1 = createTestWorksheet(1001L, folderId, "user-query")
            val worksheets = listOf(worksheet1)
            val pageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "updatedAt"))
            val page = PageImpl(worksheets, pageable, 1)

            every {
                sqlWorksheetRepositoryDsl.findByConditions(
                    projectId = projectId,
                    folderId = null,
                    searchText = searchText,
                    starred = null,
                    dialect = null,
                    pageable = pageable,
                )
            } returns page

            // When
            val result =
                sqlWorksheetService.listWorksheets(
                    projectId = projectId,
                    folderId = null,
                    searchText = searchText,
                    starred = null,
                    dialect = null,
                    pageable = pageable,
                )

            // Then
            assertThat(result.content).hasSize(1)
            assertThat(result.content[0].name).contains("user")
        }

        @Test
        @DisplayName("should filter worksheets by starred")
        fun `should filter worksheets by starred`() {
            // Given
            val worksheet1 =
                createTestWorksheet(1001L, folderId, "starred-worksheet").apply {
                    this.isStarred = true
                }
            val worksheets = listOf(worksheet1)
            val pageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "updatedAt"))
            val page = PageImpl(worksheets, pageable, 1)

            every {
                sqlWorksheetRepositoryDsl.findByConditions(
                    projectId = projectId,
                    folderId = null,
                    searchText = null,
                    starred = true,
                    dialect = null,
                    pageable = pageable,
                )
            } returns page

            // When
            val result =
                sqlWorksheetService.listWorksheets(
                    projectId = projectId,
                    folderId = null,
                    searchText = null,
                    starred = true,
                    dialect = null,
                    pageable = pageable,
                )

            // Then
            assertThat(result.content).hasSize(1)
            assertThat(result.content[0].isStarred).isTrue()
        }

        @Test
        @DisplayName("should filter worksheets by dialect")
        fun `should filter worksheets by dialect`() {
            // Given
            val worksheet1 =
                createTestWorksheet(1001L, folderId, "trino-worksheet").apply {
                    this.dialect = SqlDialect.TRINO
                }
            val worksheets = listOf(worksheet1)
            val pageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "updatedAt"))
            val page = PageImpl(worksheets, pageable, 1)

            every {
                sqlWorksheetRepositoryDsl.findByConditions(
                    projectId = projectId,
                    folderId = null,
                    searchText = null,
                    starred = null,
                    dialect = SqlDialect.TRINO,
                    pageable = pageable,
                )
            } returns page

            // When
            val result =
                sqlWorksheetService.listWorksheets(
                    projectId = projectId,
                    folderId = null,
                    searchText = null,
                    starred = null,
                    dialect = SqlDialect.TRINO,
                    pageable = pageable,
                )

            // Then
            assertThat(result.content).hasSize(1)
            assertThat(result.content[0].dialect).isEqualTo(SqlDialect.TRINO)
        }

        @Test
        @DisplayName("should return empty page when no worksheets match")
        fun `should return empty page when no worksheets match`() {
            // Given
            val pageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "updatedAt"))
            val page = PageImpl<SqlWorksheetEntity>(emptyList(), pageable, 0)

            every {
                sqlWorksheetRepositoryDsl.findByConditions(
                    projectId = projectId,
                    folderId = null,
                    searchText = "nonexistent",
                    starred = null,
                    dialect = null,
                    pageable = pageable,
                )
            } returns page

            // When
            val result =
                sqlWorksheetService.listWorksheets(
                    projectId = projectId,
                    folderId = null,
                    searchText = "nonexistent",
                    starred = null,
                    dialect = null,
                    pageable = pageable,
                )

            // Then
            assertThat(result.content).isEmpty()
            assertThat(result.totalElements).isEqualTo(0)
        }

        @Test
        @DisplayName("should handle pagination correctly")
        fun `should handle pagination correctly`() {
            // Given
            val worksheet1 = createTestWorksheet(1003L, folderId, "worksheet-page2")
            val worksheets = listOf(worksheet1)
            val pageable = PageRequest.of(1, 2, Sort.by(Sort.Direction.DESC, "updatedAt"))
            val page = PageImpl(worksheets, pageable, 5) // Total 5 items, page 2 has 1

            every {
                sqlWorksheetRepositoryDsl.findByConditions(
                    projectId = projectId,
                    folderId = null,
                    searchText = null,
                    starred = null,
                    dialect = null,
                    pageable = pageable,
                )
            } returns page

            // When
            val result =
                sqlWorksheetService.listWorksheets(
                    projectId = projectId,
                    folderId = null,
                    searchText = null,
                    starred = null,
                    dialect = null,
                    pageable = pageable,
                )

            // Then
            assertThat(result.content).hasSize(1)
            assertThat(result.totalElements).isEqualTo(5)
            assertThat(result.totalPages).isEqualTo(3) // 5 items / 2 per page = 3 pages
            assertThat(result.number).isEqualTo(1) // 0-indexed page 1
        }
    }

    @Nested
    @DisplayName("countWorksheets")
    inner class CountWorksheets {
        @Test
        @DisplayName("should return count of non-deleted worksheets in folder")
        fun `should return count of non-deleted worksheets in folder`() {
            // Given
            every {
                sqlWorksheetRepositoryJpa.countByFolderIdAndDeletedAtIsNull(folderId)
            } returns 5L

            // When
            val result = sqlWorksheetService.countWorksheets(folderId)

            // Then
            assertThat(result).isEqualTo(5L)
            verify(exactly = 1) {
                sqlWorksheetRepositoryJpa.countByFolderIdAndDeletedAtIsNull(folderId)
            }
        }

        @Test
        @DisplayName("should return 0 when no worksheets exist")
        fun `should return 0 when no worksheets exist`() {
            // Given
            every {
                sqlWorksheetRepositoryJpa.countByFolderIdAndDeletedAtIsNull(folderId)
            } returns 0L

            // When
            val result = sqlWorksheetService.countWorksheets(folderId)

            // Then
            assertThat(result).isEqualTo(0L)
        }
    }

    @Nested
    @DisplayName("countWorksheetsByFolderIds")
    inner class CountWorksheetsByFolderIds {
        @Test
        @DisplayName("should return count map for multiple folders")
        fun `should return count map for multiple folders`() {
            // Given
            val folderIds = listOf(100L, 101L, 102L)
            val countMap = mapOf(100L to 5L, 101L to 3L, 102L to 0L)

            every {
                sqlWorksheetRepositoryDsl.countByFolderIds(folderIds)
            } returns countMap

            // When
            val result = sqlWorksheetService.countWorksheetsByFolderIds(folderIds)

            // Then
            assertThat(result).hasSize(3)
            assertThat(result[100L]).isEqualTo(5L)
            assertThat(result[101L]).isEqualTo(3L)
            assertThat(result[102L]).isEqualTo(0L)
        }

        @Test
        @DisplayName("should return empty map for empty folder list")
        fun `should return empty map for empty folder list`() {
            // Given
            every {
                sqlWorksheetRepositoryDsl.countByFolderIds(emptyList())
            } returns emptyMap()

            // When
            val result = sqlWorksheetService.countWorksheetsByFolderIds(emptyList())

            // Then
            assertThat(result).isEmpty()
        }
    }

    // Helper function to create test worksheets
    private fun createTestWorksheet(
        id: Long,
        folderId: Long,
        name: String,
    ): SqlWorksheetEntity =
        SqlWorksheetEntity(
            folderId = folderId,
            name = name,
            description = "Test description for $name",
            sqlText = "SELECT * FROM $name",
            dialect = SqlDialect.BIGQUERY,
        ).apply {
            val idField = this::class.java.superclass.getDeclaredField("id")
            idField.isAccessible = true
            idField.set(this, id)

            val createdAtField = this::class.java.superclass.getDeclaredField("createdAt")
            createdAtField.isAccessible = true
            createdAtField.set(this, LocalDateTime.now())

            val updatedAtField = this::class.java.superclass.getDeclaredField("updatedAt")
            updatedAtField.isAccessible = true
            updatedAtField.set(this, LocalDateTime.now())
        }
}
