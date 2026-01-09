package com.dataops.basecamp.domain.service

import com.dataops.basecamp.common.exception.ProjectAlreadyExistsException
import com.dataops.basecamp.common.exception.ProjectNotFoundException
import com.dataops.basecamp.domain.entity.project.ProjectEntity
import com.dataops.basecamp.domain.repository.project.ProjectRepositoryDsl
import com.dataops.basecamp.domain.repository.project.ProjectRepositoryJpa
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
 * ProjectService Unit Tests
 *
 * Uses MockK to isolate dependencies and test pure business logic.
 */
@DisplayName("ProjectService Unit Tests")
class ProjectServiceTest {
    private val projectRepositoryJpa: ProjectRepositoryJpa = mockk()
    private val projectRepositoryDsl: ProjectRepositoryDsl = mockk()
    private val projectService = ProjectService(projectRepositoryJpa, projectRepositoryDsl)

    private lateinit var testProject: ProjectEntity

    @BeforeEach
    fun setUp() {
        testProject =
            ProjectEntity(
                name = "marketing-analytics",
                displayName = "Marketing Analytics",
                description = "Marketing analytics project",
            ).apply {
                // BaseEntity의 id 설정을 위한 reflection 사용
                val idField = this::class.java.superclass.getDeclaredField("id")
                idField.isAccessible = true
                idField.set(this, 1L)
            }
    }

    @Nested
    @DisplayName("createProject")
    inner class CreateProject {
        @Test
        @DisplayName("should create project successfully when name does not exist")
        fun `should create project successfully when name does not exist`() {
            // Given
            val project =
                ProjectEntity(
                    name = "new-project",
                    displayName = "New Project",
                    description = "A new project",
                )

            every { projectRepositoryJpa.existsByName(project.name) } returns false

            val savedProjectSlot = slot<ProjectEntity>()
            every { projectRepositoryJpa.save(capture(savedProjectSlot)) } answers {
                savedProjectSlot.captured.apply {
                    val idField = this::class.java.superclass.getDeclaredField("id")
                    idField.isAccessible = true
                    idField.set(this, 2L)
                }
            }

            // When
            val result = projectService.createProject(project)

            // Then
            assertThat(result.name).isEqualTo(project.name)
            assertThat(result.displayName).isEqualTo(project.displayName)
            assertThat(result.description).isEqualTo(project.description)

            verify(exactly = 1) { projectRepositoryJpa.existsByName(project.name) }
            verify(exactly = 1) { projectRepositoryJpa.save(any()) }
        }

        @Test
        @DisplayName("should throw ProjectAlreadyExistsException when project already exists")
        fun `should throw ProjectAlreadyExistsException when project already exists`() {
            // Given
            val name = "existing-project"
            val project =
                ProjectEntity(
                    name = name,
                    displayName = "Existing Project",
                )

            every { projectRepositoryJpa.existsByName(name) } returns true

            // When & Then
            val exception =
                assertThrows<ProjectAlreadyExistsException> {
                    projectService.createProject(project)
                }

            assertThat(exception.message).contains(name)
            verify(exactly = 1) { projectRepositoryJpa.existsByName(name) }
            verify(exactly = 0) { projectRepositoryJpa.save(any()) }
        }

        @Test
        @DisplayName("should check for duplicates including soft deleted projects")
        fun `should check for duplicates including soft deleted projects`() {
            // Given
            val name = "soft-deleted-project"
            val project =
                ProjectEntity(
                    name = name,
                    displayName = "Soft Deleted Project",
                )

            // existsByName returns true even for soft-deleted projects
            every { projectRepositoryJpa.existsByName(name) } returns true

            // When & Then
            val exception =
                assertThrows<ProjectAlreadyExistsException> {
                    projectService.createProject(project)
                }

            assertThat(exception.projectName).isEqualTo(name)
            verify(exactly = 1) { projectRepositoryJpa.existsByName(name) }
        }
    }

    @Nested
    @DisplayName("updateProject")
    inner class UpdateProject {
        @Test
        @DisplayName("should update project displayName successfully")
        fun `should update project displayName successfully`() {
            // Given
            val id = 1L
            val newDisplayName = "Updated Display Name"

            every { projectRepositoryJpa.findByIdAndDeletedAtIsNull(id) } returns testProject
            every { projectRepositoryJpa.save(any()) } answers { firstArg() }

            // When
            val result = projectService.updateProject(id, displayName = newDisplayName)

            // Then
            assertThat(result.displayName).isEqualTo(newDisplayName)
            assertThat(result.name).isEqualTo(testProject.name) // Name should not change
            verify(exactly = 1) { projectRepositoryJpa.findByIdAndDeletedAtIsNull(id) }
            verify(exactly = 1) { projectRepositoryJpa.save(any()) }
        }

        @Test
        @DisplayName("should update project description successfully")
        fun `should update project description successfully`() {
            // Given
            val id = 1L
            val newDescription = "Updated description"

            every { projectRepositoryJpa.findByIdAndDeletedAtIsNull(id) } returns testProject
            every { projectRepositoryJpa.save(any()) } answers { firstArg() }

            // When
            val result = projectService.updateProject(id, description = newDescription)

            // Then
            assertThat(result.description).isEqualTo(newDescription)
            verify(exactly = 1) { projectRepositoryJpa.save(any()) }
        }

        @Test
        @DisplayName("should update both displayName and description")
        fun `should update both displayName and description`() {
            // Given
            val id = 1L
            val newDisplayName = "New Display Name"
            val newDescription = "New description"

            every { projectRepositoryJpa.findByIdAndDeletedAtIsNull(id) } returns testProject
            every { projectRepositoryJpa.save(any()) } answers { firstArg() }

            // When
            val result =
                projectService.updateProject(
                    id = id,
                    displayName = newDisplayName,
                    description = newDescription,
                )

            // Then
            assertThat(result.displayName).isEqualTo(newDisplayName)
            assertThat(result.description).isEqualTo(newDescription)
        }

        @Test
        @DisplayName("should throw ProjectNotFoundException when updating non-existent project")
        fun `should throw ProjectNotFoundException when updating non-existent project`() {
            // Given
            val id = 999L

            every { projectRepositoryJpa.findByIdAndDeletedAtIsNull(id) } returns null

            // When & Then
            val exception =
                assertThrows<ProjectNotFoundException> {
                    projectService.updateProject(id, displayName = "Any")
                }

            assertThat(exception.projectId).isEqualTo(id)
            verify(exactly = 1) { projectRepositoryJpa.findByIdAndDeletedAtIsNull(id) }
            verify(exactly = 0) { projectRepositoryJpa.save(any()) }
        }

        @Test
        @DisplayName("should not modify project when both parameters are null")
        fun `should not modify project when both parameters are null`() {
            // Given
            val id = 1L
            val originalDisplayName = testProject.displayName
            val originalDescription = testProject.description

            every { projectRepositoryJpa.findByIdAndDeletedAtIsNull(id) } returns testProject
            every { projectRepositoryJpa.save(any()) } answers { firstArg() }

            // When
            val result = projectService.updateProject(id)

            // Then
            assertThat(result.displayName).isEqualTo(originalDisplayName)
            assertThat(result.description).isEqualTo(originalDescription)
        }
    }

    @Nested
    @DisplayName("deleteProject")
    inner class DeleteProject {
        @Test
        @DisplayName("should soft delete project successfully")
        fun `should soft delete project successfully`() {
            // Given
            val id = 1L

            every { projectRepositoryJpa.findByIdAndDeletedAtIsNull(id) } returns testProject
            every { projectRepositoryJpa.save(any()) } answers { firstArg() }

            // When
            projectService.deleteProject(id)

            // Then
            assertThat(testProject.deletedAt).isNotNull()
            verify(exactly = 1) { projectRepositoryJpa.findByIdAndDeletedAtIsNull(id) }
            verify(exactly = 1) { projectRepositoryJpa.save(any()) }
        }

        @Test
        @DisplayName("should throw ProjectNotFoundException when deleting non-existent project")
        fun `should throw ProjectNotFoundException when deleting non-existent project`() {
            // Given
            val id = 999L

            every { projectRepositoryJpa.findByIdAndDeletedAtIsNull(id) } returns null

            // When & Then
            val exception =
                assertThrows<ProjectNotFoundException> {
                    projectService.deleteProject(id)
                }

            assertThat(exception.projectId).isEqualTo(id)
            verify(exactly = 1) { projectRepositoryJpa.findByIdAndDeletedAtIsNull(id) }
            verify(exactly = 0) { projectRepositoryJpa.save(any()) }
        }

        @Test
        @DisplayName("should set deletedAt timestamp when soft deleting")
        fun `should set deletedAt timestamp when soft deleting`() {
            // Given
            val id = 1L
            val beforeDelete = LocalDateTime.now()

            every { projectRepositoryJpa.findByIdAndDeletedAtIsNull(id) } returns testProject
            every { projectRepositoryJpa.save(any()) } answers { firstArg() }

            // When
            projectService.deleteProject(id)

            // Then
            assertThat(testProject.deletedAt).isNotNull()
            assertThat(testProject.deletedAt).isAfterOrEqualTo(beforeDelete)
        }
    }

    @Nested
    @DisplayName("getProjectById")
    inner class GetProjectById {
        @Test
        @DisplayName("should return project when found by id")
        fun `should return project when found by id`() {
            // Given
            val id = 1L

            every { projectRepositoryJpa.findByIdAndDeletedAtIsNull(id) } returns testProject

            // When
            val result = projectService.getProjectById(id)

            // Then
            assertThat(result).isNotNull
            assertThat(result?.id).isEqualTo(id)
            assertThat(result?.name).isEqualTo(testProject.name)
            verify(exactly = 1) { projectRepositoryJpa.findByIdAndDeletedAtIsNull(id) }
        }

        @Test
        @DisplayName("should return null when project not found")
        fun `should return null when project not found`() {
            // Given
            val id = 999L

            every { projectRepositoryJpa.findByIdAndDeletedAtIsNull(id) } returns null

            // When
            val result = projectService.getProjectById(id)

            // Then
            assertThat(result).isNull()
            verify(exactly = 1) { projectRepositoryJpa.findByIdAndDeletedAtIsNull(id) }
        }

        @Test
        @DisplayName("should not return soft deleted project")
        fun `should not return soft deleted project`() {
            // Given
            val id = 1L

            // findByIdAndDeletedAtIsNull returns null for soft deleted projects
            every { projectRepositoryJpa.findByIdAndDeletedAtIsNull(id) } returns null

            // When
            val result = projectService.getProjectById(id)

            // Then
            assertThat(result).isNull()
        }
    }

    @Nested
    @DisplayName("getProjectByIdOrThrow")
    inner class GetProjectByIdOrThrow {
        @Test
        @DisplayName("should return project when found")
        fun `should return project when found`() {
            // Given
            val id = 1L

            every { projectRepositoryJpa.findByIdAndDeletedAtIsNull(id) } returns testProject

            // When
            val result = projectService.getProjectByIdOrThrow(id)

            // Then
            assertThat(result.id).isEqualTo(id)
            assertThat(result.name).isEqualTo(testProject.name)
        }

        @Test
        @DisplayName("should throw ProjectNotFoundException when not found")
        fun `should throw ProjectNotFoundException when not found`() {
            // Given
            val id = 999L

            every { projectRepositoryJpa.findByIdAndDeletedAtIsNull(id) } returns null

            // When & Then
            val exception =
                assertThrows<ProjectNotFoundException> {
                    projectService.getProjectByIdOrThrow(id)
                }

            assertThat(exception.projectId).isEqualTo(id)
        }
    }

    @Nested
    @DisplayName("getProjectByName")
    inner class GetProjectByName {
        @Test
        @DisplayName("should return project when found by name")
        fun `should return project when found by name`() {
            // Given
            val name = "marketing-analytics"

            every { projectRepositoryJpa.findByNameAndDeletedAtIsNull(name) } returns testProject

            // When
            val result = projectService.getProjectByName(name)

            // Then
            assertThat(result).isNotNull
            assertThat(result?.name).isEqualTo(name)
            verify(exactly = 1) { projectRepositoryJpa.findByNameAndDeletedAtIsNull(name) }
        }

        @Test
        @DisplayName("should return null when project not found by name")
        fun `should return null when project not found by name`() {
            // Given
            val name = "nonexistent-project"

            every { projectRepositoryJpa.findByNameAndDeletedAtIsNull(name) } returns null

            // When
            val result = projectService.getProjectByName(name)

            // Then
            assertThat(result).isNull()
            verify(exactly = 1) { projectRepositoryJpa.findByNameAndDeletedAtIsNull(name) }
        }
    }

    @Nested
    @DisplayName("listProjects")
    inner class ListProjects {
        @Test
        @DisplayName("should return all projects without filters")
        fun `should return all projects without filters`() {
            // Given
            val projects = listOf(testProject)
            val pageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "updatedAt"))

            every { projectRepositoryDsl.findByFilters(null, pageable) } returns PageImpl(projects)

            // When
            val result = projectService.listProjects(pageable = pageable)

            // Then
            assertThat(result.content).hasSize(1)
            assertThat(result.content[0].name).isEqualTo(testProject.name)
            verify(exactly = 1) { projectRepositoryDsl.findByFilters(null, pageable) }
        }

        @Test
        @DisplayName("should return filtered projects by search term")
        fun `should return filtered projects by search term`() {
            // Given
            val search = "marketing"
            val projects = listOf(testProject)
            val pageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "updatedAt"))

            every { projectRepositoryDsl.findByFilters(search, pageable) } returns PageImpl(projects)

            // When
            val result = projectService.listProjects(search = search, pageable = pageable)

            // Then
            assertThat(result.content).hasSize(1)
            verify(exactly = 1) { projectRepositoryDsl.findByFilters(search, pageable) }
        }

        @Test
        @DisplayName("should return empty list when no projects match filters")
        fun `should return empty list when no projects match filters`() {
            // Given
            val search = "nonexistent"
            val pageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "updatedAt"))

            every { projectRepositoryDsl.findByFilters(search, pageable) } returns PageImpl(emptyList())

            // When
            val result = projectService.listProjects(search = search, pageable = pageable)

            // Then
            assertThat(result.content).isEmpty()
        }

        @Test
        @DisplayName("should apply pagination correctly")
        fun `should apply pagination correctly`() {
            // Given
            val pageable = PageRequest.of(1, 10, Sort.by(Sort.Direction.DESC, "updatedAt"))

            every { projectRepositoryDsl.findByFilters(null, pageable) } returns
                PageImpl(emptyList(), pageable, 15)

            // When
            val result = projectService.listProjects(pageable = pageable)

            // Then
            assertThat(result.number).isEqualTo(1)
            assertThat(result.size).isEqualTo(10)
            assertThat(result.totalElements).isEqualTo(15)
        }
    }

    @Nested
    @DisplayName("countProjects")
    inner class CountProjects {
        @Test
        @DisplayName("should return count of non-deleted projects")
        fun `should return count of non-deleted projects`() {
            // Given
            every { projectRepositoryJpa.countByDeletedAtIsNull() } returns 5L

            // When
            val result = projectService.countProjects()

            // Then
            assertThat(result).isEqualTo(5L)
            verify(exactly = 1) { projectRepositoryJpa.countByDeletedAtIsNull() }
        }

        @Test
        @DisplayName("should return 0 when no projects exist")
        fun `should return 0 when no projects exist`() {
            // Given
            every { projectRepositoryJpa.countByDeletedAtIsNull() } returns 0L

            // When
            val result = projectService.countProjects()

            // Then
            assertThat(result).isEqualTo(0L)
        }
    }

    @Nested
    @DisplayName("existsProject")
    inner class ExistsProject {
        @Test
        @DisplayName("should return true when project exists")
        fun `should return true when project exists`() {
            // Given
            val name = "marketing-analytics"

            every { projectRepositoryJpa.existsByNameAndDeletedAtIsNull(name) } returns true

            // When
            val result = projectService.existsProject(name)

            // Then
            assertThat(result).isTrue()
            verify(exactly = 1) { projectRepositoryJpa.existsByNameAndDeletedAtIsNull(name) }
        }

        @Test
        @DisplayName("should return false when project does not exist")
        fun `should return false when project does not exist`() {
            // Given
            val name = "nonexistent-project"

            every { projectRepositoryJpa.existsByNameAndDeletedAtIsNull(name) } returns false

            // When
            val result = projectService.existsProject(name)

            // Then
            assertThat(result).isFalse()
        }

        @Test
        @DisplayName("should return false for soft deleted project")
        fun `should return false for soft deleted project`() {
            // Given
            val name = "soft-deleted-project"

            // existsByNameAndDeletedAtIsNull returns false for soft deleted
            every { projectRepositoryJpa.existsByNameAndDeletedAtIsNull(name) } returns false

            // When
            val result = projectService.existsProject(name)

            // Then
            assertThat(result).isFalse()
        }
    }
}
