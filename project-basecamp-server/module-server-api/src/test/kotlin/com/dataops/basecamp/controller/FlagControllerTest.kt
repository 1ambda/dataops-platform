package com.dataops.basecamp.controller

import com.dataops.basecamp.common.enums.FlagStatus
import com.dataops.basecamp.common.enums.SubjectType
import com.dataops.basecamp.common.enums.TargetingType
import com.dataops.basecamp.common.exception.FlagAlreadyExistsException
import com.dataops.basecamp.common.exception.FlagNotFoundException
import com.dataops.basecamp.common.exception.FlagTargetNotFoundException
import com.dataops.basecamp.domain.entity.flag.FlagEntity
import com.dataops.basecamp.domain.entity.flag.FlagTargetEntity
import com.dataops.basecamp.domain.service.FlagEvaluationResult
import com.dataops.basecamp.domain.service.FlagService
import com.dataops.basecamp.dto.flag.CreateFlagRequest
import com.dataops.basecamp.dto.flag.SetTargetRequest
import com.dataops.basecamp.dto.flag.UpdateFlagRequest
import com.dataops.basecamp.dto.flag.UpdateTargetPermissionRequest
import com.dataops.basecamp.util.SecurityContext
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import tools.jackson.databind.json.JsonMapper
import java.time.LocalDateTime

/**
 * FlagController REST API Tests
 *
 * Spring Boot 4.x patterns:
 * - @SpringBootTest: Full integration test with Spring context
 * - @MockkBean: springmockk 5.0.1 (Spring Boot 4 compatible)
 * - MockMvc: HTTP endpoint testing
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Execution(ExecutionMode.SAME_THREAD)
class FlagControllerTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var jsonMapper: JsonMapper

    @MockkBean(relaxed = true)
    private lateinit var flagService: FlagService

    private lateinit var testFlagEntity: FlagEntity
    private lateinit var testTargetEntity: FlagTargetEntity
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

    @BeforeEach
    fun setUp() {
        // Mock SecurityContext for authentication
        mockkObject(SecurityContext)
        every { SecurityContext.getCurrentUserIdOrThrow() } returns testUserId
        every { SecurityContext.getCurrentUserId() } returns testUserId

        testFlagEntity =
            FlagEntity(
                flagKey = testFlagKey,
                name = "Test Feature Flag",
                description = "Test description",
                status = FlagStatus.ENABLED,
                targetingType = TargetingType.GLOBAL,
            ).setId(1L)

        testTargetEntity =
            FlagTargetEntity(
                flagId = 1L,
                subjectType = SubjectType.USER,
                subjectId = testUserId,
                enabled = true,
                permissions = """{"execute": true, "write": false}""",
            ).setId(1L)
    }

    @AfterEach
    fun tearDown() {
        unmockkObject(SecurityContext)
    }

    // ============= 클라이언트 API 테스트 =============

    @Nested
    @DisplayName("GET /api/v1/flags/evaluate - 전체 Flag 평가")
    inner class EvaluateAllFlags {
        @Test
        @DisplayName("현재 사용자의 모든 Flag 상태를 반환한다")
        fun `evaluateAllFlags_success`() {
            // Given
            val evaluationResult =
                FlagEvaluationResult(
                    flags = mapOf("feature.a" to true, "feature.b" to false),
                    permissions =
                        mapOf(
                            "feature.a" to mapOf("execute" to true, "write" to false),
                        ),
                    evaluatedAt = LocalDateTime.now(),
                )

            every { flagService.evaluateAllFlags(testUserId) } returns evaluationResult

            // When & Then
            mockMvc
                .perform(get("/api/v1/flags/evaluate"))
                .andExpect(status().isOk)
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.flags['feature.a']").value(true))
                .andExpect(jsonPath("$.flags['feature.b']").value(false))
                .andExpect(jsonPath("$.permissions['feature.a'].execute").value(true))
                .andExpect(jsonPath("$.evaluatedAt").exists())

            verify(exactly = 1) { flagService.evaluateAllFlags(testUserId) }
        }
    }

    @Nested
    @DisplayName("GET /api/v1/flags/evaluate/{key} - 단일 Flag 평가")
    inner class EvaluateSingleFlag {
        @Test
        @DisplayName("특정 Flag의 활성화 상태를 반환한다")
        fun `evaluateSingleFlag_success`() {
            // Given
            every { flagService.isEnabled(testFlagKey, testUserId) } returns true

            // When & Then
            mockMvc
                .perform(get("/api/v1/flags/evaluate/$testFlagKey"))
                .andExpect(status().isOk)
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.flagKey").value(testFlagKey))
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.evaluatedAt").exists())

            verify(exactly = 1) { flagService.isEnabled(testFlagKey, testUserId) }
        }

        @Test
        @DisplayName("비활성화된 Flag는 false를 반환한다")
        fun `evaluateSingleFlag_disabled_returnsFalse`() {
            // Given
            every { flagService.isEnabled(testFlagKey, testUserId) } returns false

            // When & Then
            mockMvc
                .perform(get("/api/v1/flags/evaluate/$testFlagKey"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.flagKey").value(testFlagKey))
                .andExpect(jsonPath("$.enabled").value(false))
        }
    }

    // ============= Flag CRUD 테스트 =============

    @Nested
    @DisplayName("GET /api/v1/flags - 전체 Flag 목록")
    inner class GetAllFlags {
        @Test
        @DisplayName("모든 Flag를 반환한다")
        fun `getAllFlags_success`() {
            // Given
            val flags = listOf(testFlagEntity)
            every { flagService.getAllFlags() } returns flags

            // When & Then
            mockMvc
                .perform(get("/api/v1/flags"))
                .andExpect(status().isOk)
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].flagKey").value(testFlagKey))
                .andExpect(jsonPath("$[0].name").value("Test Feature Flag"))
                .andExpect(jsonPath("$[0].status").value("ENABLED"))

            verify(exactly = 1) { flagService.getAllFlags() }
        }

        @Test
        @DisplayName("Flag가 없으면 빈 배열을 반환한다")
        fun `getAllFlags_empty_returnsEmptyArray`() {
            // Given
            every { flagService.getAllFlags() } returns emptyList()

            // When & Then
            mockMvc
                .perform(get("/api/v1/flags"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0))
        }
    }

    @Nested
    @DisplayName("GET /api/v1/flags/{key} - Flag 상세 조회")
    inner class GetFlag {
        @Test
        @DisplayName("Flag를 정상적으로 반환한다")
        fun `getFlag_success`() {
            // Given
            every { flagService.getFlagOrThrow(testFlagKey) } returns testFlagEntity

            // When & Then
            mockMvc
                .perform(get("/api/v1/flags/$testFlagKey"))
                .andExpect(status().isOk)
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.flagKey").value(testFlagKey))
                .andExpect(jsonPath("$.name").value("Test Feature Flag"))
                .andExpect(jsonPath("$.description").value("Test description"))
                .andExpect(jsonPath("$.status").value("ENABLED"))
                .andExpect(jsonPath("$.targetingType").value("GLOBAL"))

            verify(exactly = 1) { flagService.getFlagOrThrow(testFlagKey) }
        }

        @Test
        @DisplayName("존재하지 않는 Flag 조회시 404를 반환한다")
        fun `getFlag_notFound_returns404`() {
            // Given
            every {
                flagService.getFlagOrThrow("nonexistent")
            } throws FlagNotFoundException("nonexistent")

            // When & Then
            mockMvc
                .perform(get("/api/v1/flags/nonexistent"))
                .andExpect(status().isNotFound)
        }
    }

    @Nested
    @DisplayName("POST /api/v1/flags - Flag 생성")
    inner class CreateFlag {
        @Test
        @DisplayName("Flag를 성공적으로 생성한다")
        fun `createFlag_success`() {
            // Given
            val request =
                CreateFlagRequest(
                    flagKey = "new.flag",
                    name = "New Flag",
                    description = "New description",
                    status = FlagStatus.ENABLED,
                    targetingType = TargetingType.GLOBAL,
                )

            every { flagService.createFlag(any()) } returns testFlagEntity

            // When & Then
            mockMvc
                .perform(
                    post("/api/v1/flags")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)),
                ).andExpect(status().isCreated)
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.flagKey").value(testFlagKey))

            verify(exactly = 1) { flagService.createFlag(any()) }
        }

        @Test
        @DisplayName("필수 필드 누락시 400을 반환한다")
        fun `createFlag_invalid_returns400`() {
            // Given - flagKey와 name 누락
            val invalidRequest = mapOf("description" to "Only description")

            // When & Then
            mockMvc
                .perform(
                    post("/api/v1/flags")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(invalidRequest)),
                ).andExpect(status().isBadRequest)
        }

        @Test
        @DisplayName("잘못된 flagKey 형식이면 400을 반환한다")
        fun `createFlag_invalidFlagKey_returns400`() {
            // Given - flagKey에 대문자 포함
            val invalidRequest =
                mapOf(
                    "flagKey" to "Invalid.FLAG.Key",
                    "name" to "Test",
                )

            // When & Then
            mockMvc
                .perform(
                    post("/api/v1/flags")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(invalidRequest)),
                ).andExpect(status().isBadRequest)
        }

        @Test
        @DisplayName("중복 flagKey면 409를 반환한다")
        fun `createFlag_duplicate_returns409`() {
            // Given
            val request =
                CreateFlagRequest(
                    flagKey = testFlagKey,
                    name = "Duplicate Flag",
                )

            every { flagService.createFlag(any()) } throws FlagAlreadyExistsException(testFlagKey)

            // When & Then
            mockMvc
                .perform(
                    post("/api/v1/flags")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)),
                ).andExpect(status().isConflict)
        }
    }

    @Nested
    @DisplayName("PUT /api/v1/flags/{key} - Flag 수정")
    inner class UpdateFlag {
        @Test
        @DisplayName("Flag를 성공적으로 수정한다")
        fun `updateFlag_success`() {
            // Given
            val request =
                UpdateFlagRequest(
                    name = "Updated Name",
                    status = FlagStatus.DISABLED,
                )

            every { flagService.updateFlag(testFlagKey, any()) } returns testFlagEntity

            // When & Then
            mockMvc
                .perform(
                    put("/api/v1/flags/$testFlagKey")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)),
                ).andExpect(status().isOk)
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.flagKey").value(testFlagKey))

            verify(exactly = 1) { flagService.updateFlag(testFlagKey, any()) }
        }

        @Test
        @DisplayName("존재하지 않는 Flag 수정시 404를 반환한다")
        fun `updateFlag_notFound_returns404`() {
            // Given
            val request = UpdateFlagRequest(name = "Updated Name")

            every {
                flagService.updateFlag("nonexistent", any())
            } throws FlagNotFoundException("nonexistent")

            // When & Then
            mockMvc
                .perform(
                    put("/api/v1/flags/nonexistent")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)),
                ).andExpect(status().isNotFound)
        }
    }

    @Nested
    @DisplayName("DELETE /api/v1/flags/{key} - Flag 삭제")
    inner class DeleteFlag {
        @Test
        @DisplayName("Flag를 성공적으로 삭제한다")
        fun `deleteFlag_success`() {
            // Given - relaxed mock handles void method

            // When & Then
            mockMvc
                .perform(
                    delete("/api/v1/flags/$testFlagKey")
                        .with(csrf()),
                ).andExpect(status().isNoContent)

            verify(exactly = 1) { flagService.deleteFlag(testFlagKey) }
        }

        @Test
        @DisplayName("존재하지 않는 Flag 삭제시 404를 반환한다")
        fun `deleteFlag_notFound_returns404`() {
            // Given
            every {
                flagService.deleteFlag("nonexistent")
            } throws FlagNotFoundException("nonexistent")

            // When & Then
            mockMvc
                .perform(
                    delete("/api/v1/flags/nonexistent")
                        .with(csrf()),
                ).andExpect(status().isNotFound)
        }
    }

    // ============= Target API 테스트 =============

    @Nested
    @DisplayName("GET /api/v1/flags/{key}/targets - Target 목록 조회")
    inner class GetTargets {
        @Test
        @DisplayName("Target 목록을 정상적으로 반환한다")
        fun `getTargets_success`() {
            // Given
            every { flagService.getTargets(testFlagKey) } returns listOf(testTargetEntity)

            // When & Then
            mockMvc
                .perform(get("/api/v1/flags/$testFlagKey/targets"))
                .andExpect(status().isOk)
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].flagKey").value(testFlagKey))
                .andExpect(jsonPath("$[0].subjectType").value("USER"))
                .andExpect(jsonPath("$[0].subjectId").value(testUserId))
                .andExpect(jsonPath("$[0].enabled").value(true))

            verify(exactly = 1) { flagService.getTargets(testFlagKey) }
        }

        @Test
        @DisplayName("Target이 없으면 빈 배열을 반환한다")
        fun `getTargets_empty_returnsEmptyArray`() {
            // Given
            every { flagService.getTargets(testFlagKey) } returns emptyList()

            // When & Then
            mockMvc
                .perform(get("/api/v1/flags/$testFlagKey/targets"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0))
        }

        @Test
        @DisplayName("존재하지 않는 Flag의 Target 조회시 404를 반환한다")
        fun `getTargets_flagNotFound_returns404`() {
            // Given
            every {
                flagService.getTargets("nonexistent")
            } throws FlagNotFoundException("nonexistent")

            // When & Then
            mockMvc
                .perform(get("/api/v1/flags/nonexistent/targets"))
                .andExpect(status().isNotFound)
        }
    }

    @Nested
    @DisplayName("POST /api/v1/flags/{key}/targets - Target 설정")
    inner class SetTarget {
        @Test
        @DisplayName("Target을 성공적으로 설정한다")
        fun `setTarget_success`() {
            // Given
            val request =
                SetTargetRequest(
                    subjectType = SubjectType.USER,
                    subjectId = testUserId,
                    enabled = true,
                    permissions = mapOf("execute" to true),
                )

            every { flagService.setTarget(testFlagKey, any()) } returns testTargetEntity

            // When & Then
            mockMvc
                .perform(
                    post("/api/v1/flags/$testFlagKey/targets")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)),
                ).andExpect(status().isCreated)
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.flagKey").value(testFlagKey))
                .andExpect(jsonPath("$.subjectType").value("USER"))
                .andExpect(jsonPath("$.enabled").value(true))

            verify(exactly = 1) { flagService.setTarget(testFlagKey, any()) }
        }

        @Test
        @DisplayName("존재하지 않는 Flag에 Target 설정시 404를 반환한다")
        fun `setTarget_flagNotFound_returns404`() {
            // Given
            val request =
                SetTargetRequest(
                    subjectType = SubjectType.USER,
                    subjectId = testUserId,
                    enabled = true,
                )

            every {
                flagService.setTarget("nonexistent", any())
            } throws FlagNotFoundException("nonexistent")

            // When & Then
            mockMvc
                .perform(
                    post("/api/v1/flags/nonexistent/targets")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)),
                ).andExpect(status().isNotFound)
        }
    }

    @Nested
    @DisplayName("PUT /api/v1/flags/{key}/targets/permissions - Target Permission 수정")
    inner class UpdateTargetPermission {
        @Test
        @DisplayName("Target Permission을 성공적으로 수정한다")
        fun `updateTargetPermission_success`() {
            // Given
            val request =
                UpdateTargetPermissionRequest(
                    subjectType = SubjectType.USER,
                    subjectId = testUserId,
                    permissionKey = "admin",
                    granted = true,
                )

            every { flagService.updateTargetPermission(testFlagKey, any()) } returns testTargetEntity

            // When & Then
            mockMvc
                .perform(
                    put("/api/v1/flags/$testFlagKey/targets/permissions")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)),
                ).andExpect(status().isOk)
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.flagKey").value(testFlagKey))

            verify(exactly = 1) { flagService.updateTargetPermission(testFlagKey, any()) }
        }

        @Test
        @DisplayName("Target이 없으면 404를 반환한다")
        fun `updateTargetPermission_targetNotFound_returns404`() {
            // Given
            val request =
                UpdateTargetPermissionRequest(
                    subjectType = SubjectType.USER,
                    subjectId = 999L,
                    permissionKey = "admin",
                    granted = true,
                )

            every {
                flagService.updateTargetPermission(testFlagKey, any())
            } throws FlagTargetNotFoundException(testFlagKey, "USER", 999L)

            // When & Then
            mockMvc
                .perform(
                    put("/api/v1/flags/$testFlagKey/targets/permissions")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)),
                ).andExpect(status().isNotFound)
        }

        @Test
        @DisplayName("permissionKey가 비어있으면 400을 반환한다")
        fun `updateTargetPermission_emptyPermissionKey_returns400`() {
            // Given
            val invalidRequest =
                mapOf(
                    "subjectType" to "USER",
                    "subjectId" to testUserId,
                    "permissionKey" to "",
                    "granted" to true,
                )

            // When & Then
            mockMvc
                .perform(
                    put("/api/v1/flags/$testFlagKey/targets/permissions")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(invalidRequest)),
                ).andExpect(status().isBadRequest)
        }
    }

    @Nested
    @DisplayName("DELETE /api/v1/flags/{key}/targets/{subjectType}/{subjectId} - Target 삭제")
    inner class RemoveTarget {
        @Test
        @DisplayName("Target을 성공적으로 삭제한다")
        fun `removeTarget_success`() {
            // Given - relaxed mock handles void method

            // When & Then
            mockMvc
                .perform(
                    delete("/api/v1/flags/$testFlagKey/targets/USER/$testUserId")
                        .with(csrf()),
                ).andExpect(status().isNoContent)

            verify(exactly = 1) { flagService.removeTarget(testFlagKey, SubjectType.USER, testUserId) }
        }

        @Test
        @DisplayName("존재하지 않는 Target 삭제시 404를 반환한다")
        fun `removeTarget_notFound_returns404`() {
            // Given
            every {
                flagService.removeTarget(testFlagKey, SubjectType.USER, 999L)
            } throws FlagTargetNotFoundException(testFlagKey, "USER", 999L)

            // When & Then
            mockMvc
                .perform(
                    delete("/api/v1/flags/$testFlagKey/targets/USER/999")
                        .with(csrf()),
                ).andExpect(status().isNotFound)
        }

        @Test
        @DisplayName("존재하지 않는 Flag의 Target 삭제시 404를 반환한다")
        fun `removeTarget_flagNotFound_returns404`() {
            // Given
            every {
                flagService.removeTarget("nonexistent", SubjectType.USER, testUserId)
            } throws FlagNotFoundException("nonexistent")

            // When & Then
            mockMvc
                .perform(
                    delete("/api/v1/flags/nonexistent/targets/USER/$testUserId")
                        .with(csrf()),
                ).andExpect(status().isNotFound)
        }
    }
}
