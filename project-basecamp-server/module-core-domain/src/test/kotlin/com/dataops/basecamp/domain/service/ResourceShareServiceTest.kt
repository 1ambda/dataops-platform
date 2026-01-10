package com.dataops.basecamp.domain.service

import com.dataops.basecamp.common.enums.ResourcePermission
import com.dataops.basecamp.common.enums.ShareableResourceType
import com.dataops.basecamp.common.exception.ResourceShareAlreadyExistsException
import com.dataops.basecamp.common.exception.ResourceShareNotFoundException
import com.dataops.basecamp.common.exception.TeamNotFoundException
import com.dataops.basecamp.domain.command.resource.CreateResourceShareCommand
import com.dataops.basecamp.domain.command.resource.RevokeResourceShareCommand
import com.dataops.basecamp.domain.command.resource.UpdateResourceShareCommand
import com.dataops.basecamp.domain.entity.resource.TeamResourceShareEntity
import com.dataops.basecamp.domain.entity.team.TeamEntity
import com.dataops.basecamp.domain.repository.resource.TeamResourceShareRepositoryDsl
import com.dataops.basecamp.domain.repository.resource.TeamResourceShareRepositoryJpa
import com.dataops.basecamp.domain.repository.resource.UserResourceGrantRepositoryJpa
import com.dataops.basecamp.domain.repository.team.TeamRepositoryJpa
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDateTime

/**
 * ResourceShareService unit tests
 *
 * Tests resource sharing business logic with MockK.
 */
@DisplayName("ResourceShareService tests")
class ResourceShareServiceTest {
    private val teamResourceShareRepositoryJpa: TeamResourceShareRepositoryJpa = mockk()
    private val teamResourceShareRepositoryDsl: TeamResourceShareRepositoryDsl = mockk()
    private val userResourceGrantRepositoryJpa: UserResourceGrantRepositoryJpa = mockk()
    private val teamRepositoryJpa: TeamRepositoryJpa = mockk()

    private val resourceShareService =
        ResourceShareService(
            teamResourceShareRepositoryJpa,
            teamResourceShareRepositoryDsl,
            userResourceGrantRepositoryJpa,
            teamRepositoryJpa,
        )

    @Nested
    @DisplayName("createShare")
    inner class CreateShareTest {
        @Test
        @DisplayName("should create share successfully")
        fun `should create share successfully`() {
            // Given
            val ownerTeam = createTeamEntity(1L, "owner-team")
            val sharedWithTeam = createTeamEntity(2L, "shared-team")
            val command =
                CreateResourceShareCommand(
                    ownerTeamId = 1L,
                    sharedWithTeamId = 2L,
                    resourceType = ShareableResourceType.METRIC,
                    resourceId = 100L,
                    permission = ResourcePermission.VIEWER,
                    visibleToTeam = true,
                    grantedBy = 10L,
                )

            val savedShare = createShareEntity(1L, command)

            every { teamRepositoryJpa.findByIdAndDeletedAtIsNull(1L) } returns ownerTeam
            every { teamRepositoryJpa.findByIdAndDeletedAtIsNull(2L) } returns sharedWithTeam
            every {
                teamResourceShareRepositoryJpa
                    .existsByOwnerTeamIdAndSharedWithTeamIdAndResourceTypeAndResourceIdAndDeletedAtIsNull(
                        1L,
                        2L,
                        ShareableResourceType.METRIC,
                        100L,
                    )
            } returns false
            every { teamResourceShareRepositoryJpa.save(any()) } returns savedShare

            // When
            val result = resourceShareService.createShare(command)

            // Then
            assertThat(result.ownerTeamId).isEqualTo(1L)
            assertThat(result.sharedWithTeamId).isEqualTo(2L)
            assertThat(result.resourceType).isEqualTo(ShareableResourceType.METRIC)
            assertThat(result.permission).isEqualTo(ResourcePermission.VIEWER)
            verify(exactly = 1) { teamResourceShareRepositoryJpa.save(any()) }
        }

        @Test
        @DisplayName("should throw TeamNotFoundException when owner team not found")
        fun `should throw TeamNotFoundException when owner team not found`() {
            // Given
            val command =
                CreateResourceShareCommand(
                    ownerTeamId = 999L,
                    sharedWithTeamId = 2L,
                    resourceType = ShareableResourceType.METRIC,
                    resourceId = 100L,
                    grantedBy = 10L,
                )

            every { teamRepositoryJpa.findByIdAndDeletedAtIsNull(999L) } returns null

            // When & Then
            val exception =
                assertThrows<TeamNotFoundException> {
                    resourceShareService.createShare(command)
                }

            assertThat(exception.message).contains("999")
            verify(exactly = 0) { teamResourceShareRepositoryJpa.save(any()) }
        }

        @Test
        @DisplayName("should throw TeamNotFoundException when shared-with team not found")
        fun `should throw TeamNotFoundException when shared-with team not found`() {
            // Given
            val ownerTeam = createTeamEntity(1L, "owner-team")
            val command =
                CreateResourceShareCommand(
                    ownerTeamId = 1L,
                    sharedWithTeamId = 999L,
                    resourceType = ShareableResourceType.METRIC,
                    resourceId = 100L,
                    grantedBy = 10L,
                )

            every { teamRepositoryJpa.findByIdAndDeletedAtIsNull(1L) } returns ownerTeam
            every { teamRepositoryJpa.findByIdAndDeletedAtIsNull(999L) } returns null

            // When & Then
            val exception =
                assertThrows<TeamNotFoundException> {
                    resourceShareService.createShare(command)
                }

            assertThat(exception.message).contains("999")
            verify(exactly = 0) { teamResourceShareRepositoryJpa.save(any()) }
        }

        @Test
        @DisplayName("should throw ResourceShareAlreadyExistsException when share already exists")
        fun `should throw ResourceShareAlreadyExistsException when share already exists`() {
            // Given
            val ownerTeam = createTeamEntity(1L, "owner-team")
            val sharedWithTeam = createTeamEntity(2L, "shared-team")
            val command =
                CreateResourceShareCommand(
                    ownerTeamId = 1L,
                    sharedWithTeamId = 2L,
                    resourceType = ShareableResourceType.METRIC,
                    resourceId = 100L,
                    grantedBy = 10L,
                )

            every { teamRepositoryJpa.findByIdAndDeletedAtIsNull(1L) } returns ownerTeam
            every { teamRepositoryJpa.findByIdAndDeletedAtIsNull(2L) } returns sharedWithTeam
            every {
                teamResourceShareRepositoryJpa
                    .existsByOwnerTeamIdAndSharedWithTeamIdAndResourceTypeAndResourceIdAndDeletedAtIsNull(
                        1L,
                        2L,
                        ShareableResourceType.METRIC,
                        100L,
                    )
            } returns true

            // When & Then
            assertThrows<ResourceShareAlreadyExistsException> {
                resourceShareService.createShare(command)
            }

            verify(exactly = 0) { teamResourceShareRepositoryJpa.save(any()) }
        }
    }

    @Nested
    @DisplayName("updateShare")
    inner class UpdateShareTest {
        @Test
        @DisplayName("should update share successfully")
        fun `should update share successfully`() {
            // Given
            val existingShare =
                createShareEntity(
                    id = 1L,
                    ownerTeamId = 1L,
                    sharedWithTeamId = 2L,
                    permission = ResourcePermission.VIEWER,
                )
            val command =
                UpdateResourceShareCommand(
                    shareId = 1L,
                    permission = ResourcePermission.EDITOR,
                    visibleToTeam = false,
                    updatedBy = 10L,
                )

            every { teamResourceShareRepositoryJpa.findByIdAndDeletedAtIsNull(1L) } returns existingShare
            every { teamResourceShareRepositoryJpa.save(any()) } returns existingShare

            // When
            val result = resourceShareService.updateShare(command)

            // Then
            assertThat(result.permission).isEqualTo(ResourcePermission.EDITOR)
            assertThat(result.visibleToTeam).isEqualTo(false)
            verify(exactly = 1) { teamResourceShareRepositoryJpa.save(any()) }
        }

        @Test
        @DisplayName("should throw ResourceShareNotFoundException when share not found")
        fun `should throw ResourceShareNotFoundException when share not found`() {
            // Given
            val command =
                UpdateResourceShareCommand(
                    shareId = 999L,
                    permission = ResourcePermission.EDITOR,
                    updatedBy = 10L,
                )

            every { teamResourceShareRepositoryJpa.findByIdAndDeletedAtIsNull(999L) } returns null

            // When & Then
            val exception =
                assertThrows<ResourceShareNotFoundException> {
                    resourceShareService.updateShare(command)
                }

            assertThat(exception.shareId).isEqualTo(999L)
            verify(exactly = 0) { teamResourceShareRepositoryJpa.save(any()) }
        }
    }

    @Nested
    @DisplayName("revokeShare")
    inner class RevokeShareTest {
        @Test
        @DisplayName("should revoke share successfully with cascade delete grants")
        fun `should revoke share successfully with cascade delete grants`() {
            // Given
            val existingShare =
                createShareEntity(
                    id = 1L,
                    ownerTeamId = 1L,
                    sharedWithTeamId = 2L,
                )
            val command =
                RevokeResourceShareCommand(
                    shareId = 1L,
                    revokedBy = 10L,
                )

            every { teamResourceShareRepositoryJpa.findByIdAndDeletedAtIsNull(1L) } returns existingShare
            every { userResourceGrantRepositoryJpa.deleteByShareId(1L) } just Runs
            every { teamResourceShareRepositoryJpa.save(any()) } returns existingShare

            // When
            resourceShareService.revokeShare(command)

            // Then
            verify(exactly = 1) { userResourceGrantRepositoryJpa.deleteByShareId(1L) }
            verify(exactly = 1) { teamResourceShareRepositoryJpa.save(any()) }
        }

        @Test
        @DisplayName("should throw ResourceShareNotFoundException when share not found")
        fun `should throw ResourceShareNotFoundException when share not found`() {
            // Given
            val command =
                RevokeResourceShareCommand(
                    shareId = 999L,
                    revokedBy = 10L,
                )

            every { teamResourceShareRepositoryJpa.findByIdAndDeletedAtIsNull(999L) } returns null

            // When & Then
            assertThrows<ResourceShareNotFoundException> {
                resourceShareService.revokeShare(command)
            }

            verify(exactly = 0) { userResourceGrantRepositoryJpa.deleteByShareId(any()) }
            verify(exactly = 0) { teamResourceShareRepositoryJpa.save(any()) }
        }
    }

    @Nested
    @DisplayName("listShares")
    inner class ListSharesTest {
        @Test
        @DisplayName("should list shares by owner team")
        fun `should list shares by owner team`() {
            // Given
            val shares =
                listOf(
                    createShareEntity(1L, 1L, 2L),
                    createShareEntity(2L, 1L, 3L),
                )

            every { teamResourceShareRepositoryJpa.findByOwnerTeamIdAndDeletedAtIsNull(1L) } returns shares

            // When
            val result = resourceShareService.listSharesByOwnerTeam(1L)

            // Then
            assertThat(result).hasSize(2)
            assertThat(result.map { it.id }).containsExactly(1L, 2L)
        }

        @Test
        @DisplayName("should list shares by shared-with team")
        fun `should list shares by shared-with team`() {
            // Given
            val shares =
                listOf(
                    createShareEntity(1L, 1L, 2L),
                )

            every { teamResourceShareRepositoryJpa.findBySharedWithTeamIdAndDeletedAtIsNull(2L) } returns shares

            // When
            val result = resourceShareService.listSharesBySharedWithTeam(2L)

            // Then
            assertThat(result).hasSize(1)
            assertThat(result[0].sharedWithTeamId).isEqualTo(2L)
        }

        @Test
        @DisplayName("should filter shares by owner team and resource type")
        fun `should filter shares by owner team and resource type`() {
            // Given
            val shares =
                listOf(
                    createShareEntity(1L, 1L, 2L, resourceType = ShareableResourceType.METRIC),
                )

            every {
                teamResourceShareRepositoryDsl.findByOwnerTeamIdAndResourceType(1L, ShareableResourceType.METRIC)
            } returns shares

            // When
            val result =
                resourceShareService.listSharesByOwnerTeamAndResourceType(
                    1L,
                    ShareableResourceType.METRIC,
                )

            // Then
            assertThat(result).hasSize(1)
            assertThat(result[0].resourceType).isEqualTo(ShareableResourceType.METRIC)
        }

        @Test
        @DisplayName("should filter shares by shared-with team and resource type")
        fun `should filter shares by shared-with team and resource type`() {
            // Given
            val shares =
                listOf(
                    createShareEntity(1L, 1L, 2L, resourceType = ShareableResourceType.DATASET),
                )

            every {
                teamResourceShareRepositoryDsl.findBySharedWithTeamIdAndResourceType(2L, ShareableResourceType.DATASET)
            } returns shares

            // When
            val result =
                resourceShareService.listSharesBySharedWithTeamAndResourceType(
                    2L,
                    ShareableResourceType.DATASET,
                )

            // Then
            assertThat(result).hasSize(1)
            assertThat(result[0].resourceType).isEqualTo(ShareableResourceType.DATASET)
        }
    }

    @Nested
    @DisplayName("getShare")
    inner class GetShareTest {
        @Test
        @DisplayName("should return share when found")
        fun `should return share when found`() {
            // Given
            val share = createShareEntity(1L, 1L, 2L)

            every { teamResourceShareRepositoryJpa.findByIdAndDeletedAtIsNull(1L) } returns share

            // When
            val result = resourceShareService.getShare(1L)

            // Then
            assertThat(result).isNotNull
            assertThat(result?.id).isEqualTo(1L)
        }

        @Test
        @DisplayName("should return null when share not found")
        fun `should return null when share not found`() {
            // Given
            every { teamResourceShareRepositoryJpa.findByIdAndDeletedAtIsNull(999L) } returns null

            // When
            val result = resourceShareService.getShare(999L)

            // Then
            assertThat(result).isNull()
        }

        @Test
        @DisplayName("should throw ResourceShareNotFoundException when using getShareOrThrow")
        fun `should throw ResourceShareNotFoundException when using getShareOrThrow`() {
            // Given
            every { teamResourceShareRepositoryJpa.findByIdAndDeletedAtIsNull(999L) } returns null

            // When & Then
            val exception =
                assertThrows<ResourceShareNotFoundException> {
                    resourceShareService.getShareOrThrow(999L)
                }

            assertThat(exception.shareId).isEqualTo(999L)
        }
    }

    // ==================== Helper Methods ====================

    private fun createTeamEntity(
        id: Long,
        name: String,
    ): TeamEntity =
        TeamEntity(
            name = name,
            displayName = name,
        ).apply {
            val idField = TeamEntity::class.java.superclass.getDeclaredField("id")
            idField.isAccessible = true
            idField.set(this, id)
        }

    private fun createShareEntity(
        id: Long,
        ownerTeamId: Long,
        sharedWithTeamId: Long,
        resourceType: ShareableResourceType = ShareableResourceType.METRIC,
        resourceId: Long = 100L,
        permission: ResourcePermission = ResourcePermission.VIEWER,
    ): TeamResourceShareEntity =
        TeamResourceShareEntity(
            ownerTeamId = ownerTeamId,
            sharedWithTeamId = sharedWithTeamId,
            resourceType = resourceType,
            resourceId = resourceId,
            permission = permission,
            visibleToTeam = true,
            grantedBy = 10L,
            grantedAt = LocalDateTime.now(),
        ).apply {
            val idField = TeamResourceShareEntity::class.java.superclass.getDeclaredField("id")
            idField.isAccessible = true
            idField.set(this, id)
        }

    private fun createShareEntity(
        id: Long,
        command: CreateResourceShareCommand,
    ): TeamResourceShareEntity =
        createShareEntity(
            id = id,
            ownerTeamId = command.ownerTeamId,
            sharedWithTeamId = command.sharedWithTeamId,
            resourceType = command.resourceType,
            resourceId = command.resourceId,
            permission = command.permission,
        )
}
