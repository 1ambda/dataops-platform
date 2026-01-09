package com.dataops.basecamp.service

import com.dataops.basecamp.common.enums.FlagStatus
import com.dataops.basecamp.common.enums.SubjectType
import com.dataops.basecamp.common.enums.TargetingType
import com.dataops.basecamp.common.exception.FlagAlreadyExistsException
import com.dataops.basecamp.common.exception.FlagNotFoundException
import com.dataops.basecamp.common.exception.FlagTargetNotFoundException
import com.dataops.basecamp.domain.entity.flag.FlagEntity
import com.dataops.basecamp.domain.entity.flag.FlagTargetEntity
import com.dataops.basecamp.domain.external.flag.FlagCachePort
import com.dataops.basecamp.domain.projection.flag.FlagTargetWithKeyProjection
import com.dataops.basecamp.domain.repository.flag.FlagRepositoryJpa
import com.dataops.basecamp.domain.repository.flag.FlagTargetRepositoryDsl
import com.dataops.basecamp.domain.repository.flag.FlagTargetRepositoryJpa
import com.dataops.basecamp.domain.service.CreateFlagCommand
import com.dataops.basecamp.domain.service.FlagService
import com.dataops.basecamp.domain.service.SetTargetCommand
import com.dataops.basecamp.domain.service.UpdateFlagCommand
import com.dataops.basecamp.domain.service.UpdatePermissionCommand
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertThrows

/**
 * FlagService Unit Tests
 *
 * MockK를 사용한 순수 단위 테스트입니다.
 * Spring Context를 로드하지 않고 빠르게 실행됩니다.
 *
 * @TestInstance(PER_CLASS) 사용으로 nested class에서 mock 공유
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FlagServiceTest {
    private val testUserId = 1L
    private val testFlagKey = "test.feature.flag"

    /**
     * Helper function to set entity ID via reflection
     */
    private fun <T : Any> T.setId(id: Long): T {
        val idField = this::class.java.superclass.getDeclaredField("id")
        idField.isAccessible = true
        idField.set(this, id)
        return this
    }

    /**
     * Factory function to create a fresh FlagEntity for testing
     */
    private fun createTestFlag(
        flagKey: String = testFlagKey,
        name: String = "Test Feature Flag",
        description: String? = "Test description",
        status: FlagStatus = FlagStatus.ENABLED,
        targetingType: TargetingType = TargetingType.GLOBAL,
        id: Long = 1L,
    ): FlagEntity =
        FlagEntity(
            flagKey = flagKey,
            name = name,
            description = description,
            status = status,
            targetingType = targetingType,
        ).setId(id)

    /**
     * Factory function to create a fresh FlagTargetEntity for testing
     */
    private fun createTestTarget(
        flagId: Long = 1L,
        subjectType: SubjectType = SubjectType.USER,
        subjectId: Long = testUserId,
        enabled: Boolean = true,
        permissions: String? = """{"execute": true, "write": false}""",
        id: Long = 1L,
    ): FlagTargetEntity =
        FlagTargetEntity(
            flagId = flagId,
            subjectType = subjectType,
            subjectId = subjectId,
            enabled = enabled,
            permissions = permissions,
        ).setId(id)

    /**
     * Create fresh mocks and service for each test
     */
    private fun createTestService(): TestFixture {
        val flagRepositoryJpa = mockk<FlagRepositoryJpa>()
        val flagTargetRepositoryJpa = mockk<FlagTargetRepositoryJpa>()
        val flagTargetRepositoryDsl = mockk<FlagTargetRepositoryDsl>()
        val flagCachePort = mockk<FlagCachePort>()

        val flagService =
            FlagService(
                flagRepositoryJpa = flagRepositoryJpa,
                flagTargetRepositoryJpa = flagTargetRepositoryJpa,
                flagTargetRepositoryDsl = flagTargetRepositoryDsl,
                flagCachePort = flagCachePort,
            )

        return TestFixture(
            flagRepositoryJpa = flagRepositoryJpa,
            flagTargetRepositoryJpa = flagTargetRepositoryJpa,
            flagTargetRepositoryDsl = flagTargetRepositoryDsl,
            flagCachePort = flagCachePort,
            flagService = flagService,
        )
    }

    private data class TestFixture(
        val flagRepositoryJpa: FlagRepositoryJpa,
        val flagTargetRepositoryJpa: FlagTargetRepositoryJpa,
        val flagTargetRepositoryDsl: FlagTargetRepositoryDsl,
        val flagCachePort: FlagCachePort,
        val flagService: FlagService,
    )

    // ============= Flag 평가 테스트 =============

    @Nested
    @DisplayName("isEnabled - Flag 활성화 여부 확인")
    inner class IsEnabledTests {
        @Test
        @DisplayName("Flag가 없으면 false를 반환한다")
        fun `isEnabled_FlagNotFound_returnsFalse`() {
            // Given
            val fixture = createTestService()
            every { fixture.flagRepositoryJpa.findByFlagKey(testFlagKey) } returns null

            // When
            val result = fixture.flagService.isEnabled(testFlagKey, testUserId)

            // Then
            assertThat(result).isFalse()
            verify(exactly = 1) { fixture.flagRepositoryJpa.findByFlagKey(testFlagKey) }
        }

        @Test
        @DisplayName("Flag가 DISABLED면 false를 반환한다")
        fun `isEnabled_FlagDisabled_returnsFalse`() {
            // Given
            val fixture = createTestService()
            val disabledFlag = createTestFlag(status = FlagStatus.DISABLED)
            every { fixture.flagRepositoryJpa.findByFlagKey(testFlagKey) } returns disabledFlag

            // When
            val result = fixture.flagService.isEnabled(testFlagKey, testUserId)

            // Then
            assertThat(result).isFalse()
        }

        @Test
        @DisplayName("GLOBAL + ENABLED면 true를 반환한다")
        fun `isEnabled_GlobalFlagEnabled_returnsTrue`() {
            // Given
            val fixture = createTestService()
            val globalFlag = createTestFlag(targetingType = TargetingType.GLOBAL)
            every { fixture.flagRepositoryJpa.findByFlagKey(testFlagKey) } returns globalFlag
            every {
                fixture.flagTargetRepositoryDsl.findByFlagIdAndSubject(1L, SubjectType.USER, testUserId)
            } returns null

            // When
            val result = fixture.flagService.isEnabled(testFlagKey, testUserId)

            // Then
            assertThat(result).isTrue()
        }

        @Test
        @DisplayName("USER + override 없으면 false를 반환한다")
        fun `isEnabled_UserFlagWithoutOverride_returnsFalse`() {
            // Given
            val fixture = createTestService()
            val userFlag = createTestFlag(targetingType = TargetingType.USER)
            every { fixture.flagRepositoryJpa.findByFlagKey(testFlagKey) } returns userFlag
            every {
                fixture.flagTargetRepositoryDsl.findByFlagIdAndSubject(1L, SubjectType.USER, testUserId)
            } returns null

            // When
            val result = fixture.flagService.isEnabled(testFlagKey, testUserId)

            // Then
            assertThat(result).isFalse()
        }

        @Test
        @DisplayName("USER + override enabled면 true를 반환한다")
        fun `isEnabled_UserFlagWithOverrideEnabled_returnsTrue`() {
            // Given
            val fixture = createTestService()
            val userFlag = createTestFlag(targetingType = TargetingType.USER)
            val enabledTarget = createTestTarget(enabled = true)
            every { fixture.flagRepositoryJpa.findByFlagKey(testFlagKey) } returns userFlag
            every {
                fixture.flagTargetRepositoryDsl.findByFlagIdAndSubject(1L, SubjectType.USER, testUserId)
            } returns enabledTarget

            // When
            val result = fixture.flagService.isEnabled(testFlagKey, testUserId)

            // Then
            assertThat(result).isTrue()
        }

        @Test
        @DisplayName("USER + override disabled면 false를 반환한다")
        fun `isEnabled_UserFlagWithOverrideDisabled_returnsFalse`() {
            // Given
            val fixture = createTestService()
            val userFlag = createTestFlag(targetingType = TargetingType.USER)
            val disabledTarget = createTestTarget(enabled = false)
            every { fixture.flagRepositoryJpa.findByFlagKey(testFlagKey) } returns userFlag
            every {
                fixture.flagTargetRepositoryDsl.findByFlagIdAndSubject(1L, SubjectType.USER, testUserId)
            } returns disabledTarget

            // When
            val result = fixture.flagService.isEnabled(testFlagKey, testUserId)

            // Then
            assertThat(result).isFalse()
        }
    }

    // ============= Permission 평가 테스트 =============

    @Nested
    @DisplayName("hasPermission - Permission 여부 확인")
    inner class HasPermissionTests {
        @Test
        @DisplayName("Flag 비활성화시 false를 반환한다")
        fun `hasPermission_FlagDisabled_returnsFalse`() {
            // Given
            val fixture = createTestService()
            val disabledFlag = createTestFlag(status = FlagStatus.DISABLED)
            every { fixture.flagRepositoryJpa.findByFlagKey(testFlagKey) } returns disabledFlag

            // When
            val result = fixture.flagService.hasPermission(testFlagKey, testUserId, "execute")

            // Then
            assertThat(result).isFalse()
        }

        @Test
        @DisplayName("Target이 없으면 기본값 true를 반환한다")
        fun `hasPermission_NoTarget_returnsTrue`() {
            // Given
            val fixture = createTestService()
            val flag = createTestFlag()
            every { fixture.flagRepositoryJpa.findByFlagKey(testFlagKey) } returns flag
            every {
                fixture.flagTargetRepositoryDsl.findByFlagIdAndSubject(1L, SubjectType.USER, testUserId)
            } returns null

            // When
            val result = fixture.flagService.hasPermission(testFlagKey, testUserId, "execute")

            // Then
            assertThat(result).isTrue()
        }

        @Test
        @DisplayName("Permission granted면 true를 반환한다")
        fun `hasPermission_PermissionGranted_returnsTrue`() {
            // Given
            val fixture = createTestService()
            val flag = createTestFlag()
            val target = createTestTarget(permissions = """{"execute": true}""")
            every { fixture.flagRepositoryJpa.findByFlagKey(testFlagKey) } returns flag
            every {
                fixture.flagTargetRepositoryDsl.findByFlagIdAndSubject(1L, SubjectType.USER, testUserId)
            } returns target

            // When
            val result = fixture.flagService.hasPermission(testFlagKey, testUserId, "execute")

            // Then
            assertThat(result).isTrue()
        }

        @Test
        @DisplayName("Permission denied면 false를 반환한다")
        fun `hasPermission_PermissionDenied_returnsFalse`() {
            // Given
            val fixture = createTestService()
            val flag = createTestFlag()
            val target = createTestTarget(permissions = """{"execute": false}""")
            every { fixture.flagRepositoryJpa.findByFlagKey(testFlagKey) } returns flag
            every {
                fixture.flagTargetRepositoryDsl.findByFlagIdAndSubject(1L, SubjectType.USER, testUserId)
            } returns target

            // When
            val result = fixture.flagService.hasPermission(testFlagKey, testUserId, "execute")

            // Then
            assertThat(result).isFalse()
        }

        @Test
        @DisplayName("Permission key가 없으면 기본값 true를 반환한다")
        fun `hasPermission_PermissionKeyNotFound_returnsTrue`() {
            // Given
            val fixture = createTestService()
            val flag = createTestFlag()
            val target = createTestTarget(permissions = """{"execute": true}""")
            every { fixture.flagRepositoryJpa.findByFlagKey(testFlagKey) } returns flag
            every {
                fixture.flagTargetRepositoryDsl.findByFlagIdAndSubject(1L, SubjectType.USER, testUserId)
            } returns target

            // When
            val result = fixture.flagService.hasPermission(testFlagKey, testUserId, "nonexistent_permission")

            // Then
            assertThat(result).isTrue()
        }
    }

    // ============= evaluateAllFlags 테스트 =============

    @Nested
    @DisplayName("evaluateAllFlags - 전체 Flag 평가")
    inner class EvaluateAllFlagsTests {
        @Test
        @DisplayName("모든 Flag 상태를 올바르게 평가한다")
        fun `evaluateAllFlags_returnsCorrectFlagStates`() {
            // Given
            val fixture = createTestService()
            val enabledGlobalFlag = createTestFlag(flagKey = "enabled.global", id = 1L)
            val disabledFlag =
                createTestFlag(
                    flagKey = "disabled.flag",
                    status = FlagStatus.DISABLED,
                    id = 2L,
                )
            val userFlag =
                createTestFlag(
                    flagKey = "user.flag",
                    targetingType = TargetingType.USER,
                    id = 3L,
                )

            every { fixture.flagRepositoryJpa.findAll() } returns listOf(enabledGlobalFlag, disabledFlag, userFlag)
            every {
                fixture.flagTargetRepositoryDsl.findBySubjectWithFlagKey(SubjectType.USER, testUserId)
            } returns emptyList()

            // When
            val result = fixture.flagService.evaluateAllFlags(testUserId)

            // Then
            assertThat(result.flags).hasSize(3)
            assertThat(result.flags["enabled.global"]).isTrue()
            assertThat(result.flags["disabled.flag"]).isFalse()
            assertThat(result.flags["user.flag"]).isFalse() // USER flag without target
        }

        @Test
        @DisplayName("Permission 정보를 포함하여 반환한다")
        fun `evaluateAllFlags_includesPermissions`() {
            // Given
            val fixture = createTestService()
            val flag = createTestFlag()
            every { fixture.flagRepositoryJpa.findAll() } returns listOf(flag)

            val targetProjection =
                FlagTargetWithKeyProjection(
                    flagKey = testFlagKey,
                    flagId = 1L,
                    subjectType = SubjectType.USER,
                    subjectId = testUserId,
                    enabled = true,
                    permissions = """{"execute": true, "write": false}""",
                )

            every {
                fixture.flagTargetRepositoryDsl.findBySubjectWithFlagKey(SubjectType.USER, testUserId)
            } returns listOf(targetProjection)

            // When
            val result = fixture.flagService.evaluateAllFlags(testUserId)

            // Then
            assertThat(result.flags[testFlagKey]).isTrue()
            assertThat(result.permissions[testFlagKey]).isNotNull
            assertThat(result.permissions[testFlagKey]?.get("execute")).isTrue()
            assertThat(result.permissions[testFlagKey]?.get("write")).isFalse()
            assertThat(result.evaluatedAt).isNotNull()
        }
    }

    // ============= Flag CRUD 테스트 =============

    @Nested
    @DisplayName("Flag CRUD 작업")
    inner class FlagCrudTests {
        @Test
        @DisplayName("Flag 생성 성공")
        fun `createFlag_success`() {
            // Given
            val fixture = createTestService()
            val command =
                CreateFlagCommand(
                    flagKey = "new.flag",
                    name = "New Flag",
                    description = "New description",
                    status = FlagStatus.ENABLED,
                    targetingType = TargetingType.GLOBAL,
                )
            val savedFlag = createTestFlag(flagKey = "new.flag")

            every { fixture.flagRepositoryJpa.existsByFlagKey("new.flag") } returns false
            every { fixture.flagRepositoryJpa.save(any()) } returns savedFlag
            every { fixture.flagCachePort.evictFlag("new.flag") } just Runs

            // When
            val result = fixture.flagService.createFlag(command)

            // Then
            assertThat(result).isNotNull
            assertThat(result.flagKey).isEqualTo("new.flag")
            verify(exactly = 1) { fixture.flagRepositoryJpa.save(any()) }
            verify(exactly = 1) { fixture.flagCachePort.evictFlag("new.flag") }
        }

        @Test
        @DisplayName("중복 Flag key 생성 시 예외 발생")
        fun `createFlag_duplicateKey_throwsException`() {
            // Given
            val fixture = createTestService()
            val command =
                CreateFlagCommand(
                    flagKey = testFlagKey,
                    name = "Duplicate Flag",
                    status = FlagStatus.ENABLED,
                    targetingType = TargetingType.GLOBAL,
                )

            every { fixture.flagRepositoryJpa.existsByFlagKey(testFlagKey) } returns true

            // When & Then
            assertThrows<FlagAlreadyExistsException> {
                fixture.flagService.createFlag(command)
            }

            verify(exactly = 0) { fixture.flagRepositoryJpa.save(any()) }
        }

        @Test
        @DisplayName("Flag 수정 성공")
        fun `updateFlag_success`() {
            // Given
            val fixture = createTestService()
            val command =
                UpdateFlagCommand(
                    name = "Updated Name",
                    description = "Updated description",
                    status = FlagStatus.DISABLED,
                )
            val existingFlag = createTestFlag()

            every { fixture.flagRepositoryJpa.findByFlagKey(testFlagKey) } returns existingFlag
            every { fixture.flagRepositoryJpa.save(any()) } returns existingFlag
            every { fixture.flagCachePort.evictFlag(testFlagKey) } just Runs

            // When
            val result = fixture.flagService.updateFlag(testFlagKey, command)

            // Then
            assertThat(result).isNotNull
            verify(exactly = 1) { fixture.flagRepositoryJpa.save(any()) }
            verify(exactly = 1) { fixture.flagCachePort.evictFlag(testFlagKey) }
        }

        @Test
        @DisplayName("존재하지 않는 Flag 수정 시 예외 발생")
        fun `updateFlag_notFound_throwsException`() {
            // Given
            val fixture = createTestService()
            val command = UpdateFlagCommand(name = "Updated Name")
            every { fixture.flagRepositoryJpa.findByFlagKey("nonexistent") } returns null

            // When & Then
            assertThrows<FlagNotFoundException> {
                fixture.flagService.updateFlag("nonexistent", command)
            }
        }

        @Test
        @DisplayName("Flag 삭제 성공")
        fun `deleteFlag_success`() {
            // Given
            val fixture = createTestService()
            val flag = createTestFlag()
            every { fixture.flagRepositoryJpa.findByFlagKey(testFlagKey) } returns flag
            every { fixture.flagRepositoryJpa.delete(any()) } just Runs
            every { fixture.flagCachePort.evictFlag(testFlagKey) } just Runs

            // When
            fixture.flagService.deleteFlag(testFlagKey)

            // Then
            verify(exactly = 1) { fixture.flagRepositoryJpa.delete(any()) }
            verify(exactly = 1) { fixture.flagCachePort.evictFlag(testFlagKey) }
        }

        @Test
        @DisplayName("존재하지 않는 Flag 삭제 시 예외 발생")
        fun `deleteFlag_notFound_throwsException`() {
            // Given
            val fixture = createTestService()
            every { fixture.flagRepositoryJpa.findByFlagKey("nonexistent") } returns null

            // When & Then
            assertThrows<FlagNotFoundException> {
                fixture.flagService.deleteFlag("nonexistent")
            }
        }

        @Test
        @DisplayName("Flag 조회 성공")
        fun `getFlag_success`() {
            // Given
            val fixture = createTestService()
            val flag = createTestFlag()
            every { fixture.flagRepositoryJpa.findByFlagKey(testFlagKey) } returns flag

            // When
            val result = fixture.flagService.getFlag(testFlagKey)

            // Then
            assertThat(result).isNotNull
            assertThat(result?.flagKey).isEqualTo(testFlagKey)
        }

        @Test
        @DisplayName("전체 Flag 조회 성공")
        fun `getAllFlags_success`() {
            // Given
            val fixture = createTestService()
            val flag1 = createTestFlag()
            val flag2 = createTestFlag(flagKey = "another.flag", id = 2L)
            every { fixture.flagRepositoryJpa.findAll() } returns listOf(flag1, flag2)

            // When
            val result = fixture.flagService.getAllFlags()

            // Then
            assertThat(result).hasSize(2)
        }
    }

    // ============= Target CRUD 테스트 =============

    @Nested
    @DisplayName("Target CRUD 작업")
    inner class TargetCrudTests {
        @Test
        @DisplayName("새 Target 설정 성공")
        fun `setTarget_createNew_success`() {
            // Given
            val fixture = createTestService()
            val command =
                SetTargetCommand(
                    subjectType = SubjectType.USER,
                    subjectId = 2L,
                    enabled = true,
                )
            val flag = createTestFlag()
            val savedTarget = createTestTarget(subjectId = 2L)

            every { fixture.flagRepositoryJpa.findByFlagKey(testFlagKey) } returns flag
            every {
                fixture.flagTargetRepositoryDsl.findByFlagIdAndSubject(1L, SubjectType.USER, 2L)
            } returns null
            every { fixture.flagTargetRepositoryJpa.save(any()) } returns savedTarget
            every { fixture.flagCachePort.evictTargets(SubjectType.USER, 2L) } just Runs

            // When
            val result = fixture.flagService.setTarget(testFlagKey, command)

            // Then
            assertThat(result).isNotNull
            verify(exactly = 1) { fixture.flagTargetRepositoryJpa.save(any()) }
            verify(exactly = 1) { fixture.flagCachePort.evictTargets(SubjectType.USER, 2L) }
        }

        @Test
        @DisplayName("기존 Target 업데이트 성공")
        fun `setTarget_updateExisting_success`() {
            // Given
            val fixture = createTestService()
            val command =
                SetTargetCommand(
                    subjectType = SubjectType.USER,
                    subjectId = testUserId,
                    enabled = false,
                )
            val flag = createTestFlag()
            val existingTarget = createTestTarget()

            every { fixture.flagRepositoryJpa.findByFlagKey(testFlagKey) } returns flag
            every {
                fixture.flagTargetRepositoryDsl.findByFlagIdAndSubject(1L, SubjectType.USER, testUserId)
            } returns existingTarget
            every { fixture.flagTargetRepositoryJpa.save(any()) } returns existingTarget
            every { fixture.flagCachePort.evictTargets(SubjectType.USER, testUserId) } just Runs

            // When
            val result = fixture.flagService.setTarget(testFlagKey, command)

            // Then
            assertThat(result).isNotNull
            verify(exactly = 1) { fixture.flagTargetRepositoryJpa.save(any()) }
        }

        @Test
        @DisplayName("Target 설정 시 Permission 포함 성공")
        fun `setTarget_withPermissions_success`() {
            // Given
            val fixture = createTestService()
            val command =
                SetTargetCommand(
                    subjectType = SubjectType.USER,
                    subjectId = testUserId,
                    enabled = true,
                    permissions = mapOf("execute" to true, "admin" to false),
                )
            val flag = createTestFlag()
            val savedTarget = createTestTarget()

            every { fixture.flagRepositoryJpa.findByFlagKey(testFlagKey) } returns flag
            every {
                fixture.flagTargetRepositoryDsl.findByFlagIdAndSubject(1L, SubjectType.USER, testUserId)
            } returns null
            every { fixture.flagTargetRepositoryJpa.save(any()) } returns savedTarget
            every { fixture.flagCachePort.evictTargets(SubjectType.USER, testUserId) } just Runs

            // When
            val result = fixture.flagService.setTarget(testFlagKey, command)

            // Then
            assertThat(result).isNotNull
            verify(exactly = 1) { fixture.flagTargetRepositoryJpa.save(any()) }
        }

        @Test
        @DisplayName("존재하지 않는 Flag에 Target 설정 시 예외 발생")
        fun `setTarget_flagNotFound_throwsException`() {
            // Given
            val fixture = createTestService()
            val command =
                SetTargetCommand(
                    subjectType = SubjectType.USER,
                    subjectId = testUserId,
                    enabled = true,
                )

            every { fixture.flagRepositoryJpa.findByFlagKey("nonexistent") } returns null

            // When & Then
            assertThrows<FlagNotFoundException> {
                fixture.flagService.setTarget("nonexistent", command)
            }
        }

        @Test
        @DisplayName("Target 삭제 성공")
        fun `removeTarget_success`() {
            // Given
            val fixture = createTestService()
            val flag = createTestFlag()
            val target = createTestTarget()

            every { fixture.flagRepositoryJpa.findByFlagKey(testFlagKey) } returns flag
            every {
                fixture.flagTargetRepositoryDsl.findByFlagIdAndSubject(1L, SubjectType.USER, testUserId)
            } returns target
            every { fixture.flagTargetRepositoryJpa.delete(any()) } just Runs
            every { fixture.flagCachePort.evictTargets(SubjectType.USER, testUserId) } just Runs

            // When
            fixture.flagService.removeTarget(testFlagKey, SubjectType.USER, testUserId)

            // Then
            verify(exactly = 1) { fixture.flagTargetRepositoryJpa.delete(any()) }
            verify(exactly = 1) { fixture.flagCachePort.evictTargets(SubjectType.USER, testUserId) }
        }

        @Test
        @DisplayName("존재하지 않는 Target 삭제 시 예외 발생")
        fun `removeTarget_notFound_throwsException`() {
            // Given
            val fixture = createTestService()
            val flag = createTestFlag()
            every { fixture.flagRepositoryJpa.findByFlagKey(testFlagKey) } returns flag
            every {
                fixture.flagTargetRepositoryDsl.findByFlagIdAndSubject(1L, SubjectType.USER, testUserId)
            } returns null

            // When & Then
            assertThrows<FlagTargetNotFoundException> {
                fixture.flagService.removeTarget(testFlagKey, SubjectType.USER, testUserId)
            }
        }

        @Test
        @DisplayName("Target 목록 조회 성공")
        fun `getTargets_success`() {
            // Given
            val fixture = createTestService()
            val flag = createTestFlag()
            val target = createTestTarget()

            every { fixture.flagRepositoryJpa.findByFlagKey(testFlagKey) } returns flag
            every { fixture.flagTargetRepositoryJpa.findByFlagId(1L) } returns listOf(target)

            // When
            val result = fixture.flagService.getTargets(testFlagKey)

            // Then
            assertThat(result).hasSize(1)
            assertThat(result[0].subjectId).isEqualTo(testUserId)
        }
    }

    // ============= Permission 업데이트 테스트 =============

    @Nested
    @DisplayName("Target Permission 업데이트")
    inner class UpdateTargetPermissionTests {
        @Test
        @DisplayName("새 Permission 추가 성공")
        fun `updateTargetPermission_addNew_success`() {
            // Given
            val fixture = createTestService()
            val command =
                UpdatePermissionCommand(
                    subjectType = SubjectType.USER,
                    subjectId = testUserId,
                    permissionKey = "admin",
                    granted = true,
                )
            val flag = createTestFlag()
            val target = createTestTarget()

            every { fixture.flagRepositoryJpa.findByFlagKey(testFlagKey) } returns flag
            every {
                fixture.flagTargetRepositoryDsl.findByFlagIdAndSubject(1L, SubjectType.USER, testUserId)
            } returns target
            every { fixture.flagTargetRepositoryJpa.save(any()) } returns target
            every { fixture.flagCachePort.evictTargets(SubjectType.USER, testUserId) } just Runs

            // When
            val result = fixture.flagService.updateTargetPermission(testFlagKey, command)

            // Then
            assertThat(result).isNotNull
            verify(exactly = 1) { fixture.flagTargetRepositoryJpa.save(any()) }
            verify(exactly = 1) { fixture.flagCachePort.evictTargets(SubjectType.USER, testUserId) }
        }

        @Test
        @DisplayName("기존 Permission 업데이트 성공")
        fun `updateTargetPermission_updateExisting_success`() {
            // Given
            val fixture = createTestService()
            val command =
                UpdatePermissionCommand(
                    subjectType = SubjectType.USER,
                    subjectId = testUserId,
                    permissionKey = "execute",
                    granted = false,
                )
            val flag = createTestFlag()
            val target = createTestTarget()

            every { fixture.flagRepositoryJpa.findByFlagKey(testFlagKey) } returns flag
            every {
                fixture.flagTargetRepositoryDsl.findByFlagIdAndSubject(1L, SubjectType.USER, testUserId)
            } returns target
            every { fixture.flagTargetRepositoryJpa.save(any()) } returns target
            every { fixture.flagCachePort.evictTargets(SubjectType.USER, testUserId) } just Runs

            // When
            val result = fixture.flagService.updateTargetPermission(testFlagKey, command)

            // Then
            assertThat(result).isNotNull
            verify(exactly = 1) { fixture.flagTargetRepositoryJpa.save(any()) }
        }

        @Test
        @DisplayName("Target이 없으면 예외 발생")
        fun `updateTargetPermission_targetNotFound_throwsException`() {
            // Given
            val fixture = createTestService()
            val command =
                UpdatePermissionCommand(
                    subjectType = SubjectType.USER,
                    subjectId = 999L,
                    permissionKey = "execute",
                    granted = true,
                )
            val flag = createTestFlag()

            every { fixture.flagRepositoryJpa.findByFlagKey(testFlagKey) } returns flag
            every {
                fixture.flagTargetRepositoryDsl.findByFlagIdAndSubject(1L, SubjectType.USER, 999L)
            } returns null

            // When & Then
            assertThrows<FlagTargetNotFoundException> {
                fixture.flagService.updateTargetPermission(testFlagKey, command)
            }
        }

        @Test
        @DisplayName("Flag가 없으면 예외 발생")
        fun `updateTargetPermission_flagNotFound_throwsException`() {
            // Given
            val fixture = createTestService()
            val command =
                UpdatePermissionCommand(
                    subjectType = SubjectType.USER,
                    subjectId = testUserId,
                    permissionKey = "execute",
                    granted = true,
                )

            every { fixture.flagRepositoryJpa.findByFlagKey("nonexistent") } returns null

            // When & Then
            assertThrows<FlagNotFoundException> {
                fixture.flagService.updateTargetPermission("nonexistent", command)
            }
        }
    }
}
