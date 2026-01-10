package com.dataops.basecamp.domain.service

import com.dataops.basecamp.common.enums.ResourcePermission
import com.dataops.basecamp.common.enums.ShareableResourceType
import com.dataops.basecamp.common.exception.GrantPermissionExceedsShareException
import com.dataops.basecamp.common.exception.ResourceShareNotFoundException
import com.dataops.basecamp.common.exception.UserGrantAlreadyExistsException
import com.dataops.basecamp.common.exception.UserGrantNotFoundException
import com.dataops.basecamp.domain.command.resource.CreateUserGrantCommand
import com.dataops.basecamp.domain.command.resource.RevokeUserGrantCommand
import com.dataops.basecamp.domain.command.resource.UpdateUserGrantCommand
import com.dataops.basecamp.domain.entity.resource.TeamResourceShareEntity
import com.dataops.basecamp.domain.entity.resource.UserResourceGrantEntity
import com.dataops.basecamp.domain.repository.resource.TeamResourceShareRepositoryJpa
import com.dataops.basecamp.domain.repository.resource.UserResourceGrantRepositoryDsl
import com.dataops.basecamp.domain.repository.resource.UserResourceGrantRepositoryJpa
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDateTime

/**
 * UserGrantService unit tests
 *
 * Tests user grant business logic with MockK.
 * Validates permission hierarchy enforcement.
 */
@DisplayName("UserGrantService tests")
class UserGrantServiceTest {
    private val userResourceGrantRepositoryJpa: UserResourceGrantRepositoryJpa = mockk()
    private val userResourceGrantRepositoryDsl: UserResourceGrantRepositoryDsl = mockk()
    private val teamResourceShareRepositoryJpa: TeamResourceShareRepositoryJpa = mockk()

    private val userGrantService =
        UserGrantService(
            userResourceGrantRepositoryJpa,
            userResourceGrantRepositoryDsl,
            teamResourceShareRepositoryJpa,
        )

    @Nested
    @DisplayName("createGrant")
    inner class CreateGrantTest {
        @Test
        @DisplayName("should create grant successfully with VIEWER permission")
        fun `should create grant successfully with VIEWER permission`() {
            // Given
            val share = createShareEntity(1L, ResourcePermission.VIEWER)
            val command =
                CreateUserGrantCommand(
                    shareId = 1L,
                    userId = 10L,
                    permission = ResourcePermission.VIEWER,
                    grantedBy = 20L,
                )
            val savedGrant = createGrantEntity(1L, command)

            every { teamResourceShareRepositoryJpa.findByIdAndDeletedAtIsNull(1L) } returns share
            every { userResourceGrantRepositoryJpa.existsByShareIdAndUserIdAndDeletedAtIsNull(1L, 10L) } returns false
            every { userResourceGrantRepositoryJpa.save(any()) } returns savedGrant

            // When
            val result = userGrantService.createGrant(command)

            // Then
            assertThat(result.shareId).isEqualTo(1L)
            assertThat(result.userId).isEqualTo(10L)
            assertThat(result.permission).isEqualTo(ResourcePermission.VIEWER)
            verify(exactly = 1) { userResourceGrantRepositoryJpa.save(any()) }
        }

        @Test
        @DisplayName("should create grant with EDITOR permission when share is EDITOR")
        fun `should create grant with EDITOR permission when share is EDITOR`() {
            // Given
            val share = createShareEntity(1L, ResourcePermission.EDITOR)
            val command =
                CreateUserGrantCommand(
                    shareId = 1L,
                    userId = 10L,
                    permission = ResourcePermission.EDITOR,
                    grantedBy = 20L,
                )
            val savedGrant = createGrantEntity(1L, command)

            every { teamResourceShareRepositoryJpa.findByIdAndDeletedAtIsNull(1L) } returns share
            every { userResourceGrantRepositoryJpa.existsByShareIdAndUserIdAndDeletedAtIsNull(1L, 10L) } returns false
            every { userResourceGrantRepositoryJpa.save(any()) } returns savedGrant

            // When
            val result = userGrantService.createGrant(command)

            // Then
            assertThat(result.permission).isEqualTo(ResourcePermission.EDITOR)
        }

        @Test
        @DisplayName("should create grant with VIEWER permission when share is EDITOR")
        fun `should create grant with VIEWER permission when share is EDITOR`() {
            // Given
            val share = createShareEntity(1L, ResourcePermission.EDITOR)
            val command =
                CreateUserGrantCommand(
                    shareId = 1L,
                    userId = 10L,
                    permission = ResourcePermission.VIEWER,
                    grantedBy = 20L,
                )
            val savedGrant = createGrantEntity(1L, command)

            every { teamResourceShareRepositoryJpa.findByIdAndDeletedAtIsNull(1L) } returns share
            every { userResourceGrantRepositoryJpa.existsByShareIdAndUserIdAndDeletedAtIsNull(1L, 10L) } returns false
            every { userResourceGrantRepositoryJpa.save(any()) } returns savedGrant

            // When
            val result = userGrantService.createGrant(command)

            // Then
            assertThat(result.permission).isEqualTo(ResourcePermission.VIEWER)
        }

        @Test
        @DisplayName("should throw ResourceShareNotFoundException when share not found")
        fun `should throw ResourceShareNotFoundException when share not found`() {
            // Given
            val command =
                CreateUserGrantCommand(
                    shareId = 999L,
                    userId = 10L,
                    permission = ResourcePermission.VIEWER,
                    grantedBy = 20L,
                )

            every { teamResourceShareRepositoryJpa.findByIdAndDeletedAtIsNull(999L) } returns null

            // When & Then
            assertThrows<ResourceShareNotFoundException> {
                userGrantService.createGrant(command)
            }

            verify(exactly = 0) { userResourceGrantRepositoryJpa.save(any()) }
        }

        @Test
        @DisplayName("should throw UserGrantAlreadyExistsException when grant already exists")
        fun `should throw UserGrantAlreadyExistsException when grant already exists`() {
            // Given
            val share = createShareEntity(1L, ResourcePermission.VIEWER)
            val command =
                CreateUserGrantCommand(
                    shareId = 1L,
                    userId = 10L,
                    permission = ResourcePermission.VIEWER,
                    grantedBy = 20L,
                )

            every { teamResourceShareRepositoryJpa.findByIdAndDeletedAtIsNull(1L) } returns share
            every { userResourceGrantRepositoryJpa.existsByShareIdAndUserIdAndDeletedAtIsNull(1L, 10L) } returns true

            // When & Then
            val exception =
                assertThrows<UserGrantAlreadyExistsException> {
                    userGrantService.createGrant(command)
                }

            assertThat(exception.shareId).isEqualTo(1L)
            assertThat(exception.userId).isEqualTo(10L)
            verify(exactly = 0) { userResourceGrantRepositoryJpa.save(any()) }
        }

        @Test
        @DisplayName("should throw GrantPermissionExceedsShareException when grant EDITOR exceeds share VIEWER")
        fun `should throw GrantPermissionExceedsShareException when grant EDITOR exceeds share VIEWER`() {
            // Given
            val share = createShareEntity(1L, ResourcePermission.VIEWER)
            val command =
                CreateUserGrantCommand(
                    shareId = 1L,
                    userId = 10L,
                    permission = ResourcePermission.EDITOR,
                    grantedBy = 20L,
                )

            every { teamResourceShareRepositoryJpa.findByIdAndDeletedAtIsNull(1L) } returns share
            every { userResourceGrantRepositoryJpa.existsByShareIdAndUserIdAndDeletedAtIsNull(1L, 10L) } returns false

            // When & Then
            val exception =
                assertThrows<GrantPermissionExceedsShareException> {
                    userGrantService.createGrant(command)
                }

            assertThat(exception.grantPermission).isEqualTo("EDITOR")
            assertThat(exception.sharePermission).isEqualTo("VIEWER")
            verify(exactly = 0) { userResourceGrantRepositoryJpa.save(any()) }
        }
    }

    @Nested
    @DisplayName("updateGrant")
    inner class UpdateGrantTest {
        @Test
        @DisplayName("should update grant successfully")
        fun `should update grant successfully`() {
            // Given
            val existingGrant =
                createGrantEntity(
                    id = 1L,
                    shareId = 1L,
                    userId = 10L,
                    permission = ResourcePermission.VIEWER,
                )
            val share = createShareEntity(1L, ResourcePermission.EDITOR)
            val command =
                UpdateUserGrantCommand(
                    grantId = 1L,
                    permission = ResourcePermission.EDITOR,
                    updatedBy = 20L,
                )

            every { userResourceGrantRepositoryJpa.findByIdAndDeletedAtIsNull(1L) } returns existingGrant
            every { teamResourceShareRepositoryJpa.findByIdAndDeletedAtIsNull(1L) } returns share
            every { userResourceGrantRepositoryJpa.save(any()) } returns existingGrant

            // When
            val result = userGrantService.updateGrant(command)

            // Then
            assertThat(result.permission).isEqualTo(ResourcePermission.EDITOR)
            verify(exactly = 1) { userResourceGrantRepositoryJpa.save(any()) }
        }

        @Test
        @DisplayName("should throw UserGrantNotFoundException when grant not found")
        fun `should throw UserGrantNotFoundException when grant not found`() {
            // Given
            val command =
                UpdateUserGrantCommand(
                    grantId = 999L,
                    permission = ResourcePermission.EDITOR,
                    updatedBy = 20L,
                )

            every { userResourceGrantRepositoryJpa.findByIdAndDeletedAtIsNull(999L) } returns null

            // When & Then
            val exception =
                assertThrows<UserGrantNotFoundException> {
                    userGrantService.updateGrant(command)
                }

            assertThat(exception.grantId).isEqualTo(999L)
            verify(exactly = 0) { userResourceGrantRepositoryJpa.save(any()) }
        }

        @Test
        @DisplayName("should throw GrantPermissionExceedsShareException when updating to EDITOR exceeds share VIEWER")
        fun `should throw GrantPermissionExceedsShareException when updating to EDITOR exceeds share VIEWER`() {
            // Given
            val existingGrant =
                createGrantEntity(
                    id = 1L,
                    shareId = 1L,
                    userId = 10L,
                    permission = ResourcePermission.VIEWER,
                )
            val share = createShareEntity(1L, ResourcePermission.VIEWER)
            val command =
                UpdateUserGrantCommand(
                    grantId = 1L,
                    permission = ResourcePermission.EDITOR,
                    updatedBy = 20L,
                )

            every { userResourceGrantRepositoryJpa.findByIdAndDeletedAtIsNull(1L) } returns existingGrant
            every { teamResourceShareRepositoryJpa.findByIdAndDeletedAtIsNull(1L) } returns share

            // When & Then
            assertThrows<GrantPermissionExceedsShareException> {
                userGrantService.updateGrant(command)
            }

            verify(exactly = 0) { userResourceGrantRepositoryJpa.save(any()) }
        }

        @Test
        @DisplayName("should throw ResourceShareNotFoundException when share not found during update")
        fun `should throw ResourceShareNotFoundException when share not found during update`() {
            // Given
            val existingGrant =
                createGrantEntity(
                    id = 1L,
                    shareId = 1L,
                    userId = 10L,
                    permission = ResourcePermission.VIEWER,
                )
            val command =
                UpdateUserGrantCommand(
                    grantId = 1L,
                    permission = ResourcePermission.EDITOR,
                    updatedBy = 20L,
                )

            every { userResourceGrantRepositoryJpa.findByIdAndDeletedAtIsNull(1L) } returns existingGrant
            every { teamResourceShareRepositoryJpa.findByIdAndDeletedAtIsNull(1L) } returns null

            // When & Then
            assertThrows<ResourceShareNotFoundException> {
                userGrantService.updateGrant(command)
            }

            verify(exactly = 0) { userResourceGrantRepositoryJpa.save(any()) }
        }
    }

    @Nested
    @DisplayName("revokeGrant")
    inner class RevokeGrantTest {
        @Test
        @DisplayName("should revoke grant successfully")
        fun `should revoke grant successfully`() {
            // Given
            val existingGrant =
                createGrantEntity(
                    id = 1L,
                    shareId = 1L,
                    userId = 10L,
                    permission = ResourcePermission.VIEWER,
                )
            val command =
                RevokeUserGrantCommand(
                    grantId = 1L,
                    revokedBy = 20L,
                )

            every { userResourceGrantRepositoryJpa.findByIdAndDeletedAtIsNull(1L) } returns existingGrant
            every { userResourceGrantRepositoryJpa.save(any()) } returns existingGrant

            // When
            userGrantService.revokeGrant(command)

            // Then
            verify(exactly = 1) { userResourceGrantRepositoryJpa.save(any()) }
        }

        @Test
        @DisplayName("should throw UserGrantNotFoundException when grant not found")
        fun `should throw UserGrantNotFoundException when grant not found`() {
            // Given
            val command =
                RevokeUserGrantCommand(
                    grantId = 999L,
                    revokedBy = 20L,
                )

            every { userResourceGrantRepositoryJpa.findByIdAndDeletedAtIsNull(999L) } returns null

            // When & Then
            assertThrows<UserGrantNotFoundException> {
                userGrantService.revokeGrant(command)
            }

            verify(exactly = 0) { userResourceGrantRepositoryJpa.save(any()) }
        }
    }

    @Nested
    @DisplayName("listGrants")
    inner class ListGrantsTest {
        @Test
        @DisplayName("should list grants by shareId")
        fun `should list grants by shareId`() {
            // Given
            val grants =
                listOf(
                    createGrantEntity(1L, 1L, 10L, ResourcePermission.VIEWER),
                    createGrantEntity(2L, 1L, 20L, ResourcePermission.EDITOR),
                )

            every { userResourceGrantRepositoryJpa.findByShareIdAndDeletedAtIsNull(1L) } returns grants

            // When
            val result = userGrantService.listGrantsByShare(1L)

            // Then
            assertThat(result).hasSize(2)
            assertThat(result.map { it.userId }).containsExactly(10L, 20L)
        }

        @Test
        @DisplayName("should list grants by userId")
        fun `should list grants by userId`() {
            // Given
            val grants =
                listOf(
                    createGrantEntity(1L, 1L, 10L, ResourcePermission.VIEWER),
                    createGrantEntity(2L, 2L, 10L, ResourcePermission.EDITOR),
                )

            every { userResourceGrantRepositoryJpa.findByUserIdAndDeletedAtIsNull(10L) } returns grants

            // When
            val result = userGrantService.listGrantsByUser(10L)

            // Then
            assertThat(result).hasSize(2)
            assertThat(result.map { it.shareId }).containsExactly(1L, 2L)
        }
    }

    @Nested
    @DisplayName("getGrant")
    inner class GetGrantTest {
        @Test
        @DisplayName("should return grant when found")
        fun `should return grant when found`() {
            // Given
            val grant = createGrantEntity(1L, 1L, 10L, ResourcePermission.VIEWER)

            every { userResourceGrantRepositoryJpa.findByIdAndDeletedAtIsNull(1L) } returns grant

            // When
            val result = userGrantService.getGrant(1L)

            // Then
            assertThat(result).isNotNull
            assertThat(result?.id).isEqualTo(1L)
        }

        @Test
        @DisplayName("should return null when grant not found")
        fun `should return null when grant not found`() {
            // Given
            every { userResourceGrantRepositoryJpa.findByIdAndDeletedAtIsNull(999L) } returns null

            // When
            val result = userGrantService.getGrant(999L)

            // Then
            assertThat(result).isNull()
        }

        @Test
        @DisplayName("should throw UserGrantNotFoundException when using getGrantOrThrow")
        fun `should throw UserGrantNotFoundException when using getGrantOrThrow`() {
            // Given
            every { userResourceGrantRepositoryJpa.findByIdAndDeletedAtIsNull(999L) } returns null

            // When & Then
            val exception =
                assertThrows<UserGrantNotFoundException> {
                    userGrantService.getGrantOrThrow(999L)
                }

            assertThat(exception.grantId).isEqualTo(999L)
        }

        @Test
        @DisplayName("should get grant by share and user")
        fun `should get grant by share and user`() {
            // Given
            val grant = createGrantEntity(1L, 1L, 10L, ResourcePermission.VIEWER)

            every {
                userResourceGrantRepositoryJpa.findByShareIdAndUserIdAndDeletedAtIsNull(1L, 10L)
            } returns grant

            // When
            val result = userGrantService.getGrantByShareAndUser(1L, 10L)

            // Then
            assertThat(result).isNotNull
            assertThat(result?.shareId).isEqualTo(1L)
            assertThat(result?.userId).isEqualTo(10L)
        }
    }

    // ==================== Helper Methods ====================

    private fun createShareEntity(
        id: Long,
        permission: ResourcePermission,
    ): TeamResourceShareEntity =
        TeamResourceShareEntity(
            ownerTeamId = 1L,
            sharedWithTeamId = 2L,
            resourceType = ShareableResourceType.METRIC,
            resourceId = 100L,
            permission = permission,
            visibleToTeam = true,
            grantedBy = 10L,
            grantedAt = LocalDateTime.now(),
        ).apply {
            val idField = TeamResourceShareEntity::class.java.superclass.getDeclaredField("id")
            idField.isAccessible = true
            idField.set(this, id)
        }

    private fun createGrantEntity(
        id: Long,
        shareId: Long,
        userId: Long,
        permission: ResourcePermission,
    ): UserResourceGrantEntity =
        UserResourceGrantEntity(
            shareId = shareId,
            userId = userId,
            permission = permission,
            grantedBy = 20L,
            grantedAt = LocalDateTime.now(),
        ).apply {
            val idField = UserResourceGrantEntity::class.java.superclass.getDeclaredField("id")
            idField.isAccessible = true
            idField.set(this, id)
        }

    private fun createGrantEntity(
        id: Long,
        command: CreateUserGrantCommand,
    ): UserResourceGrantEntity =
        createGrantEntity(
            id = id,
            shareId = command.shareId,
            userId = command.userId,
            permission = command.permission,
        )
}
