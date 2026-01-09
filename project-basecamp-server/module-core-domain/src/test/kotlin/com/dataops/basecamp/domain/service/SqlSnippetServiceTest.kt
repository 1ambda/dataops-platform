package com.dataops.basecamp.domain.service

import com.dataops.basecamp.common.enums.SqlDialect
import com.dataops.basecamp.common.exception.SqlFolderNotFoundException
import com.dataops.basecamp.common.exception.SqlSnippetAlreadyExistsException
import com.dataops.basecamp.common.exception.SqlSnippetNotFoundException
import com.dataops.basecamp.domain.entity.sql.SqlFolderEntity
import com.dataops.basecamp.domain.entity.sql.SqlSnippetEntity
import com.dataops.basecamp.domain.repository.sql.SqlSnippetRepositoryDsl
import com.dataops.basecamp.domain.repository.sql.SqlSnippetRepositoryJpa
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
 * SqlSnippetService Unit Tests
 *
 * Uses MockK to isolate dependencies and test pure business logic.
 */
@DisplayName("SqlSnippetService Unit Tests")
class SqlSnippetServiceTest {
    private val sqlSnippetRepositoryJpa: SqlSnippetRepositoryJpa = mockk()
    private val sqlSnippetRepositoryDsl: SqlSnippetRepositoryDsl = mockk()
    private val sqlFolderService: SqlFolderService = mockk()

    private val sqlSnippetService =
        SqlSnippetService(
            sqlSnippetRepositoryJpa,
            sqlSnippetRepositoryDsl,
            sqlFolderService,
        )

    private val projectId = 1L
    private val folderId = 100L
    private val snippetId = 1000L

    private lateinit var testFolder: SqlFolderEntity
    private lateinit var testSnippet: SqlSnippetEntity

    @BeforeEach
    fun setUp() {
        testFolder =
            SqlFolderEntity(
                projectId = projectId,
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

        testSnippet =
            SqlSnippetEntity(
                folderId = folderId,
                name = "test-snippet",
                description = "Test snippet description",
                sqlText = "SELECT * FROM users",
                dialect = SqlDialect.BIGQUERY,
            ).apply {
                val idField = this::class.java.superclass.getDeclaredField("id")
                idField.isAccessible = true
                idField.set(this, snippetId)

                val createdAtField = this::class.java.superclass.getDeclaredField("createdAt")
                createdAtField.isAccessible = true
                createdAtField.set(this, LocalDateTime.now())

                val updatedAtField = this::class.java.superclass.getDeclaredField("updatedAt")
                updatedAtField.isAccessible = true
                updatedAtField.set(this, LocalDateTime.now())
            }
    }

    @Nested
    @DisplayName("createSnippet")
    inner class CreateSnippet {
        @Test
        @DisplayName("should create snippet successfully when name does not exist")
        fun `should create snippet successfully when name does not exist`() {
            // Given
            val snippetName = "new-snippet"
            val description = "New snippet description"
            val sqlText = "SELECT id, name FROM users"
            val dialect = SqlDialect.TRINO

            every { sqlFolderService.getFolderByIdOrThrow(projectId, folderId) } returns testFolder
            every {
                sqlSnippetRepositoryJpa.existsByNameAndFolderIdAndDeletedAtIsNull(snippetName, folderId)
            } returns false

            val savedSnippetSlot = slot<SqlSnippetEntity>()
            every { sqlSnippetRepositoryJpa.save(capture(savedSnippetSlot)) } answers {
                savedSnippetSlot.captured.apply {
                    val idField = this::class.java.superclass.getDeclaredField("id")
                    idField.isAccessible = true
                    idField.set(this, 1001L)
                }
            }

            // When
            val result =
                sqlSnippetService.createSnippet(
                    projectId = projectId,
                    folderId = folderId,
                    name = snippetName,
                    description = description,
                    sqlText = sqlText,
                    dialect = dialect,
                )

            // Then
            assertThat(result.folderId).isEqualTo(folderId)
            assertThat(result.name).isEqualTo(snippetName)
            assertThat(result.description).isEqualTo(description)
            assertThat(result.sqlText).isEqualTo(sqlText)
            assertThat(result.dialect).isEqualTo(dialect)

            verify(exactly = 1) { sqlFolderService.getFolderByIdOrThrow(projectId, folderId) }
            verify(exactly = 1) {
                sqlSnippetRepositoryJpa.existsByNameAndFolderIdAndDeletedAtIsNull(snippetName, folderId)
            }
            verify(exactly = 1) { sqlSnippetRepositoryJpa.save(any()) }
        }

        @Test
        @DisplayName("should create snippet without description")
        fun `should create snippet without description`() {
            // Given
            val snippetName = "snippet-no-desc"
            val sqlText = "SELECT 1"

            every { sqlFolderService.getFolderByIdOrThrow(projectId, folderId) } returns testFolder
            every {
                sqlSnippetRepositoryJpa.existsByNameAndFolderIdAndDeletedAtIsNull(snippetName, folderId)
            } returns false

            val savedSnippetSlot = slot<SqlSnippetEntity>()
            every { sqlSnippetRepositoryJpa.save(capture(savedSnippetSlot)) } answers {
                savedSnippetSlot.captured.apply {
                    val idField = this::class.java.superclass.getDeclaredField("id")
                    idField.isAccessible = true
                    idField.set(this, 1002L)
                }
            }

            // When
            val result =
                sqlSnippetService.createSnippet(
                    projectId = projectId,
                    folderId = folderId,
                    name = snippetName,
                    sqlText = sqlText,
                )

            // Then
            assertThat(result.name).isEqualTo(snippetName)
            assertThat(result.description).isNull()
            assertThat(result.dialect).isEqualTo(SqlDialect.BIGQUERY) // default value
        }

        @Test
        @DisplayName("should throw SqlFolderNotFoundException when folder does not exist")
        fun `should throw SqlFolderNotFoundException when folder does not exist`() {
            // Given
            val nonExistentFolderId = 999L

            every { sqlFolderService.getFolderByIdOrThrow(projectId, nonExistentFolderId) } throws
                SqlFolderNotFoundException(nonExistentFolderId, projectId)

            // When & Then
            val exception =
                assertThrows<SqlFolderNotFoundException> {
                    sqlSnippetService.createSnippet(
                        projectId = projectId,
                        folderId = nonExistentFolderId,
                        name = "new-snippet",
                        sqlText = "SELECT 1",
                    )
                }

            assertThat(exception.folderId).isEqualTo(nonExistentFolderId)
            assertThat(exception.projectId).isEqualTo(projectId)

            verify(exactly = 1) { sqlFolderService.getFolderByIdOrThrow(projectId, nonExistentFolderId) }
            verify(exactly = 0) {
                sqlSnippetRepositoryJpa.existsByNameAndFolderIdAndDeletedAtIsNull(any(), any())
            }
            verify(exactly = 0) { sqlSnippetRepositoryJpa.save(any()) }
        }

        @Test
        @DisplayName("should throw SqlSnippetAlreadyExistsException when snippet name already exists")
        fun `should throw SqlSnippetAlreadyExistsException when snippet name already exists`() {
            // Given
            val existingName = "existing-snippet"

            every { sqlFolderService.getFolderByIdOrThrow(projectId, folderId) } returns testFolder
            every {
                sqlSnippetRepositoryJpa.existsByNameAndFolderIdAndDeletedAtIsNull(existingName, folderId)
            } returns true

            // When & Then
            val exception =
                assertThrows<SqlSnippetAlreadyExistsException> {
                    sqlSnippetService.createSnippet(
                        projectId = projectId,
                        folderId = folderId,
                        name = existingName,
                        sqlText = "SELECT 1",
                    )
                }

            assertThat(exception.snippetName).isEqualTo(existingName)
            assertThat(exception.folderId).isEqualTo(folderId)

            verify(exactly = 1) { sqlFolderService.getFolderByIdOrThrow(projectId, folderId) }
            verify(exactly = 1) {
                sqlSnippetRepositoryJpa.existsByNameAndFolderIdAndDeletedAtIsNull(existingName, folderId)
            }
            verify(exactly = 0) { sqlSnippetRepositoryJpa.save(any()) }
        }
    }

    @Nested
    @DisplayName("updateSnippet")
    inner class UpdateSnippet {
        @Test
        @DisplayName("should update snippet successfully")
        fun `should update snippet successfully`() {
            // Given
            val newName = "updated-snippet"
            val newDescription = "Updated description"
            val newSqlText = "SELECT id, name, email FROM users"
            val newDialect = SqlDialect.TRINO

            every { sqlFolderService.getFolderByIdOrThrow(projectId, folderId) } returns testFolder
            every {
                sqlSnippetRepositoryJpa.findByIdAndFolderIdAndDeletedAtIsNull(snippetId, folderId)
            } returns testSnippet
            every {
                sqlSnippetRepositoryJpa.existsByNameAndFolderIdAndDeletedAtIsNull(newName, folderId)
            } returns false
            every { sqlSnippetRepositoryJpa.save(any()) } answers { firstArg() }

            // When
            val result =
                sqlSnippetService.updateSnippet(
                    projectId = projectId,
                    folderId = folderId,
                    snippetId = snippetId,
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

            verify(exactly = 1) { sqlFolderService.getFolderByIdOrThrow(projectId, folderId) }
            verify(exactly = 1) {
                sqlSnippetRepositoryJpa.findByIdAndFolderIdAndDeletedAtIsNull(snippetId, folderId)
            }
            verify(exactly = 1) { sqlSnippetRepositoryJpa.save(any()) }
        }

        @Test
        @DisplayName("should update snippet with partial fields")
        fun `should update snippet with partial fields`() {
            // Given
            val originalName = testSnippet.name
            val originalSqlText = testSnippet.sqlText
            val newDescription = "Only description updated"

            every { sqlFolderService.getFolderByIdOrThrow(projectId, folderId) } returns testFolder
            every {
                sqlSnippetRepositoryJpa.findByIdAndFolderIdAndDeletedAtIsNull(snippetId, folderId)
            } returns testSnippet
            every { sqlSnippetRepositoryJpa.save(any()) } answers { firstArg() }

            // When
            val result =
                sqlSnippetService.updateSnippet(
                    projectId = projectId,
                    folderId = folderId,
                    snippetId = snippetId,
                    description = newDescription,
                )

            // Then
            assertThat(result.name).isEqualTo(originalName)
            assertThat(result.description).isEqualTo(newDescription)
            assertThat(result.sqlText).isEqualTo(originalSqlText)

            verify(exactly = 1) { sqlSnippetRepositoryJpa.save(any()) }
        }

        @Test
        @DisplayName("should throw SqlSnippetNotFoundException when snippet does not exist")
        fun `should throw SqlSnippetNotFoundException when snippet does not exist`() {
            // Given
            val nonExistentSnippetId = 9999L

            every { sqlFolderService.getFolderByIdOrThrow(projectId, folderId) } returns testFolder
            every {
                sqlSnippetRepositoryJpa.findByIdAndFolderIdAndDeletedAtIsNull(nonExistentSnippetId, folderId)
            } returns null

            // When & Then
            val exception =
                assertThrows<SqlSnippetNotFoundException> {
                    sqlSnippetService.updateSnippet(
                        projectId = projectId,
                        folderId = folderId,
                        snippetId = nonExistentSnippetId,
                        name = "new-name",
                    )
                }

            assertThat(exception.snippetId).isEqualTo(nonExistentSnippetId)
            assertThat(exception.folderId).isEqualTo(folderId)

            verify(exactly = 1) { sqlFolderService.getFolderByIdOrThrow(projectId, folderId) }
            verify(exactly = 1) {
                sqlSnippetRepositoryJpa.findByIdAndFolderIdAndDeletedAtIsNull(nonExistentSnippetId, folderId)
            }
            verify(exactly = 0) { sqlSnippetRepositoryJpa.save(any()) }
        }

        @Test
        @DisplayName("should throw SqlSnippetAlreadyExistsException when new name already exists")
        fun `should throw SqlSnippetAlreadyExistsException when new name already exists`() {
            // Given
            val existingName = "existing-name"

            every { sqlFolderService.getFolderByIdOrThrow(projectId, folderId) } returns testFolder
            every {
                sqlSnippetRepositoryJpa.findByIdAndFolderIdAndDeletedAtIsNull(snippetId, folderId)
            } returns testSnippet
            every {
                sqlSnippetRepositoryJpa.existsByNameAndFolderIdAndDeletedAtIsNull(existingName, folderId)
            } returns true

            // When & Then
            val exception =
                assertThrows<SqlSnippetAlreadyExistsException> {
                    sqlSnippetService.updateSnippet(
                        projectId = projectId,
                        folderId = folderId,
                        snippetId = snippetId,
                        name = existingName,
                    )
                }

            assertThat(exception.snippetName).isEqualTo(existingName)
            assertThat(exception.folderId).isEqualTo(folderId)

            verify(exactly = 0) { sqlSnippetRepositoryJpa.save(any()) }
        }

        @Test
        @DisplayName("should not check for duplicate name when name unchanged")
        fun `should not check for duplicate name when name unchanged`() {
            // Given
            val currentName = testSnippet.name
            val newSqlText = "SELECT 1"

            every { sqlFolderService.getFolderByIdOrThrow(projectId, folderId) } returns testFolder
            every {
                sqlSnippetRepositoryJpa.findByIdAndFolderIdAndDeletedAtIsNull(snippetId, folderId)
            } returns testSnippet
            every { sqlSnippetRepositoryJpa.save(any()) } answers { firstArg() }

            // When
            val result =
                sqlSnippetService.updateSnippet(
                    projectId = projectId,
                    folderId = folderId,
                    snippetId = snippetId,
                    name = currentName, // Same name
                    sqlText = newSqlText,
                )

            // Then
            assertThat(result.sqlText).isEqualTo(newSqlText)

            // Should not check for duplicate since name is unchanged
            verify(exactly = 0) {
                sqlSnippetRepositoryJpa.existsByNameAndFolderIdAndDeletedAtIsNull(any(), any())
            }
        }
    }

    @Nested
    @DisplayName("deleteSnippet")
    inner class DeleteSnippet {
        @Test
        @DisplayName("should soft delete snippet successfully")
        fun `should soft delete snippet successfully`() {
            // Given
            every { sqlFolderService.getFolderByIdOrThrow(projectId, folderId) } returns testFolder
            every {
                sqlSnippetRepositoryJpa.findByIdAndFolderIdAndDeletedAtIsNull(snippetId, folderId)
            } returns testSnippet
            every { sqlSnippetRepositoryJpa.save(any()) } answers { firstArg() }

            // When
            sqlSnippetService.deleteSnippet(projectId, folderId, snippetId)

            // Then
            assertThat(testSnippet.deletedAt).isNotNull()
            verify(exactly = 1) { sqlFolderService.getFolderByIdOrThrow(projectId, folderId) }
            verify(exactly = 1) {
                sqlSnippetRepositoryJpa.findByIdAndFolderIdAndDeletedAtIsNull(snippetId, folderId)
            }
            verify(exactly = 1) { sqlSnippetRepositoryJpa.save(any()) }
        }

        @Test
        @DisplayName("should throw SqlSnippetNotFoundException when snippet does not exist")
        fun `should throw SqlSnippetNotFoundException when snippet does not exist`() {
            // Given
            val nonExistentSnippetId = 9999L

            every { sqlFolderService.getFolderByIdOrThrow(projectId, folderId) } returns testFolder
            every {
                sqlSnippetRepositoryJpa.findByIdAndFolderIdAndDeletedAtIsNull(nonExistentSnippetId, folderId)
            } returns null

            // When & Then
            val exception =
                assertThrows<SqlSnippetNotFoundException> {
                    sqlSnippetService.deleteSnippet(projectId, folderId, nonExistentSnippetId)
                }

            assertThat(exception.snippetId).isEqualTo(nonExistentSnippetId)
            assertThat(exception.folderId).isEqualTo(folderId)

            verify(exactly = 0) { sqlSnippetRepositoryJpa.save(any()) }
        }

        @Test
        @DisplayName("should throw SqlFolderNotFoundException when folder does not exist")
        fun `should throw SqlFolderNotFoundException when folder does not exist`() {
            // Given
            val nonExistentFolderId = 999L

            every { sqlFolderService.getFolderByIdOrThrow(projectId, nonExistentFolderId) } throws
                SqlFolderNotFoundException(nonExistentFolderId, projectId)

            // When & Then
            val exception =
                assertThrows<SqlFolderNotFoundException> {
                    sqlSnippetService.deleteSnippet(projectId, nonExistentFolderId, snippetId)
                }

            assertThat(exception.folderId).isEqualTo(nonExistentFolderId)

            verify(exactly = 0) {
                sqlSnippetRepositoryJpa.findByIdAndFolderIdAndDeletedAtIsNull(any(), any())
            }
        }

        @Test
        @DisplayName("should set deletedAt timestamp when soft deleting")
        fun `should set deletedAt timestamp when soft deleting`() {
            // Given
            val beforeDelete = LocalDateTime.now()

            every { sqlFolderService.getFolderByIdOrThrow(projectId, folderId) } returns testFolder
            every {
                sqlSnippetRepositoryJpa.findByIdAndFolderIdAndDeletedAtIsNull(snippetId, folderId)
            } returns testSnippet
            every { sqlSnippetRepositoryJpa.save(any()) } answers { firstArg() }

            // When
            sqlSnippetService.deleteSnippet(projectId, folderId, snippetId)

            // Then
            assertThat(testSnippet.deletedAt).isNotNull()
            assertThat(testSnippet.deletedAt).isAfterOrEqualTo(beforeDelete)
        }
    }

    @Nested
    @DisplayName("getSnippetById")
    inner class GetSnippetById {
        @Test
        @DisplayName("should return snippet when found by id and folderId")
        fun `should return snippet when found by id and folderId`() {
            // Given
            every {
                sqlSnippetRepositoryJpa.findByIdAndFolderIdAndDeletedAtIsNull(snippetId, folderId)
            } returns testSnippet

            // When
            val result = sqlSnippetService.getSnippetById(folderId, snippetId)

            // Then
            assertThat(result).isNotNull
            assertThat(result?.id).isEqualTo(snippetId)
            assertThat(result?.folderId).isEqualTo(folderId)
            assertThat(result?.name).isEqualTo(testSnippet.name)

            verify(exactly = 1) {
                sqlSnippetRepositoryJpa.findByIdAndFolderIdAndDeletedAtIsNull(snippetId, folderId)
            }
        }

        @Test
        @DisplayName("should return null when snippet not found")
        fun `should return null when snippet not found`() {
            // Given
            val nonExistentSnippetId = 9999L

            every {
                sqlSnippetRepositoryJpa.findByIdAndFolderIdAndDeletedAtIsNull(nonExistentSnippetId, folderId)
            } returns null

            // When
            val result = sqlSnippetService.getSnippetById(folderId, nonExistentSnippetId)

            // Then
            assertThat(result).isNull()
        }

        @Test
        @DisplayName("should not return soft deleted snippet")
        fun `should not return soft deleted snippet`() {
            // Given
            // findByIdAndFolderIdAndDeletedAtIsNull returns null for soft deleted snippets
            every {
                sqlSnippetRepositoryJpa.findByIdAndFolderIdAndDeletedAtIsNull(snippetId, folderId)
            } returns null

            // When
            val result = sqlSnippetService.getSnippetById(folderId, snippetId)

            // Then
            assertThat(result).isNull()
        }
    }

    @Nested
    @DisplayName("getSnippetByIdOrThrow")
    inner class GetSnippetByIdOrThrow {
        @Test
        @DisplayName("should return snippet when found")
        fun `should return snippet when found`() {
            // Given
            every {
                sqlSnippetRepositoryJpa.findByIdAndFolderIdAndDeletedAtIsNull(snippetId, folderId)
            } returns testSnippet

            // When
            val result = sqlSnippetService.getSnippetByIdOrThrow(folderId, snippetId)

            // Then
            assertThat(result.id).isEqualTo(snippetId)
            assertThat(result.name).isEqualTo(testSnippet.name)
        }

        @Test
        @DisplayName("should throw SqlSnippetNotFoundException when not found")
        fun `should throw SqlSnippetNotFoundException when not found`() {
            // Given
            val nonExistentSnippetId = 9999L

            every {
                sqlSnippetRepositoryJpa.findByIdAndFolderIdAndDeletedAtIsNull(nonExistentSnippetId, folderId)
            } returns null

            // When & Then
            val exception =
                assertThrows<SqlSnippetNotFoundException> {
                    sqlSnippetService.getSnippetByIdOrThrow(folderId, nonExistentSnippetId)
                }

            assertThat(exception.snippetId).isEqualTo(nonExistentSnippetId)
            assertThat(exception.folderId).isEqualTo(folderId)
        }
    }

    @Nested
    @DisplayName("listSnippets")
    inner class ListSnippets {
        @Test
        @DisplayName("should return all snippets for project")
        fun `should return all snippets for project`() {
            // Given
            val snippet1 = createTestSnippet(1001L, folderId, "snippet-1")
            val snippet2 = createTestSnippet(1002L, folderId, "snippet-2")
            val snippets = listOf(snippet1, snippet2)
            val pageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "updatedAt"))
            val page = PageImpl(snippets, pageable, 2)

            every {
                sqlSnippetRepositoryDsl.findByConditions(
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
                sqlSnippetService.listSnippets(
                    projectId = projectId,
                    folderId = null,
                    searchText = null,
                    starred = null,
                    dialect = null,
                    pageable = pageable,
                )

            // Then
            assertThat(result.content).hasSize(2)
            assertThat(result.content[0].name).isEqualTo("snippet-1")
            assertThat(result.content[1].name).isEqualTo("snippet-2")

            verify(exactly = 1) {
                sqlSnippetRepositoryDsl.findByConditions(
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
        @DisplayName("should filter snippets by folderId")
        fun `should filter snippets by folderId`() {
            // Given
            val snippet1 = createTestSnippet(1001L, folderId, "snippet-in-folder")
            val snippets = listOf(snippet1)
            val pageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "updatedAt"))
            val page = PageImpl(snippets, pageable, 1)

            every {
                sqlSnippetRepositoryDsl.findByConditions(
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
                sqlSnippetService.listSnippets(
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
        @DisplayName("should filter snippets by searchText")
        fun `should filter snippets by searchText`() {
            // Given
            val searchText = "user"
            val snippet1 = createTestSnippet(1001L, folderId, "user-query")
            val snippets = listOf(snippet1)
            val pageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "updatedAt"))
            val page = PageImpl(snippets, pageable, 1)

            every {
                sqlSnippetRepositoryDsl.findByConditions(
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
                sqlSnippetService.listSnippets(
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
        @DisplayName("should filter snippets by starred")
        fun `should filter snippets by starred`() {
            // Given
            val snippet1 =
                createTestSnippet(1001L, folderId, "starred-snippet").apply {
                    this.isStarred = true
                }
            val snippets = listOf(snippet1)
            val pageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "updatedAt"))
            val page = PageImpl(snippets, pageable, 1)

            every {
                sqlSnippetRepositoryDsl.findByConditions(
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
                sqlSnippetService.listSnippets(
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
        @DisplayName("should filter snippets by dialect")
        fun `should filter snippets by dialect`() {
            // Given
            val snippet1 =
                createTestSnippet(1001L, folderId, "trino-snippet").apply {
                    this.dialect = SqlDialect.TRINO
                }
            val snippets = listOf(snippet1)
            val pageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "updatedAt"))
            val page = PageImpl(snippets, pageable, 1)

            every {
                sqlSnippetRepositoryDsl.findByConditions(
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
                sqlSnippetService.listSnippets(
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
        @DisplayName("should return empty page when no snippets match")
        fun `should return empty page when no snippets match`() {
            // Given
            val pageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "updatedAt"))
            val page = PageImpl<SqlSnippetEntity>(emptyList(), pageable, 0)

            every {
                sqlSnippetRepositoryDsl.findByConditions(
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
                sqlSnippetService.listSnippets(
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
            val snippet1 = createTestSnippet(1003L, folderId, "snippet-page2")
            val snippets = listOf(snippet1)
            val pageable = PageRequest.of(1, 2, Sort.by(Sort.Direction.DESC, "updatedAt"))
            val page = PageImpl(snippets, pageable, 5) // Total 5 items, page 2 has 1

            every {
                sqlSnippetRepositoryDsl.findByConditions(
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
                sqlSnippetService.listSnippets(
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
    @DisplayName("countSnippets")
    inner class CountSnippets {
        @Test
        @DisplayName("should return count of non-deleted snippets in folder")
        fun `should return count of non-deleted snippets in folder`() {
            // Given
            every {
                sqlSnippetRepositoryJpa.countByFolderIdAndDeletedAtIsNull(folderId)
            } returns 5L

            // When
            val result = sqlSnippetService.countSnippets(folderId)

            // Then
            assertThat(result).isEqualTo(5L)
            verify(exactly = 1) {
                sqlSnippetRepositoryJpa.countByFolderIdAndDeletedAtIsNull(folderId)
            }
        }

        @Test
        @DisplayName("should return 0 when no snippets exist")
        fun `should return 0 when no snippets exist`() {
            // Given
            every {
                sqlSnippetRepositoryJpa.countByFolderIdAndDeletedAtIsNull(folderId)
            } returns 0L

            // When
            val result = sqlSnippetService.countSnippets(folderId)

            // Then
            assertThat(result).isEqualTo(0L)
        }
    }

    @Nested
    @DisplayName("countSnippetsByFolderIds")
    inner class CountSnippetsByFolderIds {
        @Test
        @DisplayName("should return count map for multiple folders")
        fun `should return count map for multiple folders`() {
            // Given
            val folderIds = listOf(100L, 101L, 102L)
            val countMap = mapOf(100L to 5L, 101L to 3L, 102L to 0L)

            every {
                sqlSnippetRepositoryDsl.countByFolderIds(folderIds)
            } returns countMap

            // When
            val result = sqlSnippetService.countSnippetsByFolderIds(folderIds)

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
                sqlSnippetRepositoryDsl.countByFolderIds(emptyList())
            } returns emptyMap()

            // When
            val result = sqlSnippetService.countSnippetsByFolderIds(emptyList())

            // Then
            assertThat(result).isEmpty()
        }
    }

    // Helper function to create test snippets
    private fun createTestSnippet(
        id: Long,
        folderId: Long,
        name: String,
    ): SqlSnippetEntity =
        SqlSnippetEntity(
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
