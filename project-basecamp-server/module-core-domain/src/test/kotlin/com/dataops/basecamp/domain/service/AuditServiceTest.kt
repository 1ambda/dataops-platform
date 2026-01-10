package com.dataops.basecamp.domain.service

import com.dataops.basecamp.common.enums.AuditAction
import com.dataops.basecamp.common.enums.AuditResource
import com.dataops.basecamp.domain.entity.audit.AuditLogEntity
import com.dataops.basecamp.domain.repository.audit.AuditLogRepositoryDsl
import com.dataops.basecamp.domain.repository.audit.AuditLogRepositoryJpa
import com.dataops.basecamp.domain.repository.audit.AuditStats
import com.dataops.basecamp.domain.repository.audit.UserAuditCount
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import java.time.LocalDateTime

/**
 * AuditService 비즈니스 로직 단위 테스트
 *
 * MockK를 사용하여 의존성을 격리하고 순수한 비즈니스 로직을 테스트합니다.
 */
@DisplayName("AuditService 비즈니스 로직 테스트")
class AuditServiceTest {
    private val auditLogRepositoryJpa: AuditLogRepositoryJpa = mockk()
    private val auditLogRepositoryDsl: AuditLogRepositoryDsl = mockk()

    private val auditService =
        AuditService(
            auditLogRepositoryJpa,
            auditLogRepositoryDsl,
        )

    private val testUserId = "user-123"
    private val testUserEmail = "test@example.com"
    private val testTraceId = "trace-abc-123"
    private val now = LocalDateTime.now()

    private fun createTestAuditLogEntity(
        id: Long = 1L,
        userId: String = testUserId,
        action: AuditAction = AuditAction.READ,
        resource: AuditResource = AuditResource.METRIC,
    ): AuditLogEntity =
        AuditLogEntity(
            id = id,
            userId = userId,
            userEmail = testUserEmail,
            traceId = testTraceId,
            action = action,
            resource = resource,
            httpMethod = "GET",
            requestUrl = "http://localhost:8080/api/v1/metrics",
            pathVariables = """{"name":"test-metric"}""",
            queryParameters = null,
            requestBody = null,
            responseStatus = 200,
            responseMessage = null,
            durationMs = 150L,
            clientType = "WEB",
            clientIp = "192.168.1.1",
            userAgent = "Mozilla/5.0",
            clientMetadata = null,
            resourceId = "test-metric",
            teamId = 1L,
            createdAt = now,
        )

    @Nested
    @DisplayName("saveLog 테스트")
    inner class SaveLogTest {
        @Test
        @DisplayName("모든 필드가 있는 audit log 저장 성공")
        fun `saveLog_success - should save audit log with all fields`() {
            // Given
            val entitySlot = slot<AuditLogEntity>()
            val savedEntity = createTestAuditLogEntity()
            every { auditLogRepositoryJpa.save(capture(entitySlot)) } returns savedEntity

            // When
            val result =
                auditService.saveLog(
                    userId = testUserId,
                    userEmail = testUserEmail,
                    traceId = testTraceId,
                    action = AuditAction.READ,
                    resource = AuditResource.METRIC,
                    httpMethod = "GET",
                    requestUrl = "http://localhost:8080/api/v1/metrics",
                    pathVariables = """{"name":"test-metric"}""",
                    queryParameters = null,
                    requestBody = null,
                    responseStatus = 200,
                    responseMessage = null,
                    durationMs = 150L,
                    clientType = "WEB",
                    clientIp = "192.168.1.1",
                    userAgent = "Mozilla/5.0",
                    clientMetadata = null,
                    resourceId = "test-metric",
                    teamId = 1L,
                )

            // Then
            assertThat(result.userId).isEqualTo(testUserId)
            assertThat(result.action).isEqualTo(AuditAction.READ)
            assertThat(result.resource).isEqualTo(AuditResource.METRIC)

            verify(exactly = 1) { auditLogRepositoryJpa.save(any()) }

            // Verify captured entity
            assertThat(entitySlot.captured.userId).isEqualTo(testUserId)
            assertThat(entitySlot.captured.userEmail).isEqualTo(testUserEmail)
            assertThat(entitySlot.captured.traceId).isEqualTo(testTraceId)
        }

        @Test
        @DisplayName("request body가 있는 audit log 저장 성공")
        fun `saveLog_withRequestBody - should save audit log with request body`() {
            // Given
            val requestBody = """{"name":"new-metric","description":"Test metric"}"""
            val entitySlot = slot<AuditLogEntity>()
            val savedEntity =
                AuditLogEntity(
                    id = 1L,
                    userId = testUserId,
                    userEmail = testUserEmail,
                    traceId = testTraceId,
                    action = AuditAction.CREATE,
                    resource = AuditResource.METRIC,
                    httpMethod = "POST",
                    requestUrl = "http://localhost:8080/api/v1/metrics",
                    pathVariables = null,
                    queryParameters = null,
                    requestBody = requestBody,
                    responseStatus = 201,
                    responseMessage = null,
                    durationMs = 200L,
                    clientType = "WEB",
                    clientIp = "192.168.1.1",
                    userAgent = "Mozilla/5.0",
                    clientMetadata = null,
                    resourceId = null,
                    teamId = 1L,
                    createdAt = now,
                )

            every { auditLogRepositoryJpa.save(capture(entitySlot)) } returns savedEntity

            // When
            val result =
                auditService.saveLog(
                    userId = testUserId,
                    userEmail = testUserEmail,
                    traceId = testTraceId,
                    action = AuditAction.CREATE,
                    resource = AuditResource.METRIC,
                    httpMethod = "POST",
                    requestUrl = "http://localhost:8080/api/v1/metrics",
                    pathVariables = null,
                    queryParameters = null,
                    requestBody = requestBody,
                    responseStatus = 201,
                    responseMessage = null,
                    durationMs = 200L,
                    clientType = "WEB",
                    clientIp = "192.168.1.1",
                    userAgent = "Mozilla/5.0",
                    clientMetadata = null,
                    resourceId = null,
                    teamId = 1L,
                )

            // Then
            assertThat(result.action).isEqualTo(AuditAction.CREATE)

            verify(exactly = 1) { auditLogRepositoryJpa.save(any()) }

            // Verify captured entity has request body
            assertThat(entitySlot.captured.requestBody).isEqualTo(requestBody)
            assertThat(entitySlot.captured.httpMethod).isEqualTo("POST")
        }
    }

    @Nested
    @DisplayName("findById 테스트")
    inner class FindByIdTest {
        @Test
        @DisplayName("존재하는 ID로 조회 시 audit log 반환")
        fun `findById_existing - should return audit log when found`() {
            // Given
            val logId = 1L
            val expectedLog = createTestAuditLogEntity(id = logId)
            every { auditLogRepositoryJpa.findById(logId) } returns expectedLog

            // When
            val result = auditService.findById(logId)

            // Then
            assertThat(result).isNotNull
            assertThat(result?.id).isEqualTo(logId)
            assertThat(result?.userId).isEqualTo(testUserId)
            assertThat(result?.action).isEqualTo(AuditAction.READ)

            verify(exactly = 1) { auditLogRepositoryJpa.findById(logId) }
        }

        @Test
        @DisplayName("존재하지 않는 ID로 조회 시 null 반환")
        fun `findById_notFound - should return null when not found`() {
            // Given
            val nonExistentId = 999L
            every { auditLogRepositoryJpa.findById(nonExistentId) } returns null

            // When
            val result = auditService.findById(nonExistentId)

            // Then
            assertThat(result).isNull()

            verify(exactly = 1) { auditLogRepositoryJpa.findById(nonExistentId) }
        }
    }

    @Nested
    @DisplayName("searchLogs 테스트")
    inner class SearchLogsTest {
        private val pageable: Pageable = PageRequest.of(0, 20)

        @Test
        @DisplayName("userId로 audit log 검색")
        fun `searchLogs_byUserId - should filter by user ID`() {
            // Given
            val logs = listOf(createTestAuditLogEntity())
            val page = PageImpl(logs, pageable, 1)
            every {
                auditLogRepositoryDsl.search(
                    userId = testUserId,
                    action = null,
                    resource = null,
                    startDate = null,
                    endDate = null,
                    pageable = pageable,
                )
            } returns page

            // When
            val result =
                auditService.searchLogs(
                    userId = testUserId,
                    action = null,
                    resource = null,
                    startDate = null,
                    endDate = null,
                    pageable = pageable,
                )

            // Then
            assertThat(result.totalElements).isEqualTo(1)
            assertThat(result.content.first().userId).isEqualTo(testUserId)

            verify(exactly = 1) {
                auditLogRepositoryDsl.search(
                    userId = testUserId,
                    action = null,
                    resource = null,
                    startDate = null,
                    endDate = null,
                    pageable = pageable,
                )
            }
        }

        @Test
        @DisplayName("action으로 audit log 검색")
        fun `searchLogs_byAction - should filter by action`() {
            // Given
            val logs = listOf(createTestAuditLogEntity(action = AuditAction.CREATE))
            val page = PageImpl(logs, pageable, 1)
            every {
                auditLogRepositoryDsl.search(
                    userId = null,
                    action = AuditAction.CREATE,
                    resource = null,
                    startDate = null,
                    endDate = null,
                    pageable = pageable,
                )
            } returns page

            // When
            val result =
                auditService.searchLogs(
                    userId = null,
                    action = AuditAction.CREATE,
                    resource = null,
                    startDate = null,
                    endDate = null,
                    pageable = pageable,
                )

            // Then
            assertThat(result.totalElements).isEqualTo(1)
            assertThat(result.content.first().action).isEqualTo(AuditAction.CREATE)

            verify(exactly = 1) {
                auditLogRepositoryDsl.search(
                    userId = null,
                    action = AuditAction.CREATE,
                    resource = null,
                    startDate = null,
                    endDate = null,
                    pageable = pageable,
                )
            }
        }

        @Test
        @DisplayName("resource로 audit log 검색")
        fun `searchLogs_byResource - should filter by resource`() {
            // Given
            val logs = listOf(createTestAuditLogEntity(resource = AuditResource.DATASET))
            val page = PageImpl(logs, pageable, 1)
            every {
                auditLogRepositoryDsl.search(
                    userId = null,
                    action = null,
                    resource = AuditResource.DATASET,
                    startDate = null,
                    endDate = null,
                    pageable = pageable,
                )
            } returns page

            // When
            val result =
                auditService.searchLogs(
                    userId = null,
                    action = null,
                    resource = AuditResource.DATASET,
                    startDate = null,
                    endDate = null,
                    pageable = pageable,
                )

            // Then
            assertThat(result.totalElements).isEqualTo(1)
            assertThat(result.content.first().resource).isEqualTo(AuditResource.DATASET)

            verify(exactly = 1) {
                auditLogRepositoryDsl.search(
                    userId = null,
                    action = null,
                    resource = AuditResource.DATASET,
                    startDate = null,
                    endDate = null,
                    pageable = pageable,
                )
            }
        }

        @Test
        @DisplayName("날짜 범위로 audit log 검색")
        fun `searchLogs_byDateRange - should filter by date range`() {
            // Given
            val startDate = now.minusDays(7)
            val endDate = now
            val logs = listOf(createTestAuditLogEntity())
            val page = PageImpl(logs, pageable, 1)
            every {
                auditLogRepositoryDsl.search(
                    userId = null,
                    action = null,
                    resource = null,
                    startDate = startDate,
                    endDate = endDate,
                    pageable = pageable,
                )
            } returns page

            // When
            val result =
                auditService.searchLogs(
                    userId = null,
                    action = null,
                    resource = null,
                    startDate = startDate,
                    endDate = endDate,
                    pageable = pageable,
                )

            // Then
            assertThat(result.totalElements).isEqualTo(1)

            verify(exactly = 1) {
                auditLogRepositoryDsl.search(
                    userId = null,
                    action = null,
                    resource = null,
                    startDate = startDate,
                    endDate = endDate,
                    pageable = pageable,
                )
            }
        }

        @Test
        @DisplayName("복합 필터로 audit log 검색")
        fun `searchLogs_multipleFilters - should filter with multiple criteria`() {
            // Given
            val startDate = now.minusDays(7)
            val endDate = now
            val logs =
                listOf(
                    createTestAuditLogEntity(
                        userId = testUserId,
                        action = AuditAction.CREATE,
                        resource = AuditResource.METRIC,
                    ),
                )
            val page = PageImpl(logs, pageable, 1)
            every {
                auditLogRepositoryDsl.search(
                    userId = testUserId,
                    action = AuditAction.CREATE,
                    resource = AuditResource.METRIC,
                    startDate = startDate,
                    endDate = endDate,
                    pageable = pageable,
                )
            } returns page

            // When
            val result =
                auditService.searchLogs(
                    userId = testUserId,
                    action = AuditAction.CREATE,
                    resource = AuditResource.METRIC,
                    startDate = startDate,
                    endDate = endDate,
                    pageable = pageable,
                )

            // Then
            assertThat(result.totalElements).isEqualTo(1)
            assertThat(result.content.first().userId).isEqualTo(testUserId)
            assertThat(result.content.first().action).isEqualTo(AuditAction.CREATE)
            assertThat(result.content.first().resource).isEqualTo(AuditResource.METRIC)

            verify(exactly = 1) {
                auditLogRepositoryDsl.search(
                    userId = testUserId,
                    action = AuditAction.CREATE,
                    resource = AuditResource.METRIC,
                    startDate = startDate,
                    endDate = endDate,
                    pageable = pageable,
                )
            }
        }
    }

    @Nested
    @DisplayName("getStats 테스트")
    inner class GetStatsTest {
        @Test
        @DisplayName("기본 audit 통계 조회")
        fun `getStats_basic - should return audit statistics`() {
            // Given
            val expectedStats =
                AuditStats(
                    totalCount = 100,
                    actionCounts =
                        mapOf(
                            AuditAction.READ to 50L,
                            AuditAction.CREATE to 30L,
                            AuditAction.UPDATE to 15L,
                            AuditAction.DELETE to 5L,
                        ),
                    resourceCounts =
                        mapOf(
                            AuditResource.METRIC to 40L,
                            AuditResource.DATASET to 35L,
                            AuditResource.WORKFLOW to 25L,
                        ),
                    topUsers =
                        listOf(
                            UserAuditCount(userId = "user-1", count = 30L),
                            UserAuditCount(userId = "user-2", count = 25L),
                            UserAuditCount(userId = "user-3", count = 20L),
                        ),
                    avgDurationMs = 150.5,
                )

            every {
                auditLogRepositoryDsl.getStats(
                    startDate = null,
                    endDate = null,
                )
            } returns expectedStats

            // When
            val result =
                auditService.getStats(
                    startDate = null,
                    endDate = null,
                )

            // Then
            assertThat(result.totalCount).isEqualTo(100)
            assertThat(result.actionCounts).hasSize(4)
            assertThat(result.actionCounts[AuditAction.READ]).isEqualTo(50L)
            assertThat(result.resourceCounts).hasSize(3)
            assertThat(result.resourceCounts[AuditResource.METRIC]).isEqualTo(40L)
            assertThat(result.topUsers).hasSize(3)
            assertThat(result.topUsers.first().userId).isEqualTo("user-1")
            assertThat(result.avgDurationMs).isEqualTo(150.5)

            verify(exactly = 1) {
                auditLogRepositoryDsl.getStats(
                    startDate = null,
                    endDate = null,
                )
            }
        }

        @Test
        @DisplayName("날짜 범위 지정 audit 통계 조회")
        fun `getStats_withDateRange - should return statistics for date range`() {
            // Given
            val startDate = now.minusDays(7)
            val endDate = now
            val expectedStats =
                AuditStats(
                    totalCount = 50,
                    actionCounts =
                        mapOf(
                            AuditAction.READ to 25L,
                            AuditAction.CREATE to 15L,
                        ),
                    resourceCounts =
                        mapOf(
                            AuditResource.METRIC to 20L,
                            AuditResource.DATASET to 15L,
                        ),
                    topUsers =
                        listOf(
                            UserAuditCount(userId = "user-1", count = 15L),
                        ),
                    avgDurationMs = 120.0,
                )

            every {
                auditLogRepositoryDsl.getStats(
                    startDate = startDate,
                    endDate = endDate,
                )
            } returns expectedStats

            // When
            val result =
                auditService.getStats(
                    startDate = startDate,
                    endDate = endDate,
                )

            // Then
            assertThat(result.totalCount).isEqualTo(50)
            assertThat(result.avgDurationMs).isEqualTo(120.0)

            verify(exactly = 1) {
                auditLogRepositoryDsl.getStats(
                    startDate = startDate,
                    endDate = endDate,
                )
            }
        }
    }

    @Nested
    @DisplayName("findByUserId 테스트")
    inner class FindByUserIdTest {
        @Test
        @DisplayName("userId로 audit log 목록 조회")
        fun `findByUserId - should return paginated logs for user`() {
            // Given
            val pageable = PageRequest.of(0, 20)
            val logs =
                listOf(
                    createTestAuditLogEntity(id = 1L),
                    createTestAuditLogEntity(id = 2L),
                )
            val page = PageImpl(logs, pageable, 2)

            every {
                auditLogRepositoryDsl.findByUserId(testUserId, pageable)
            } returns page

            // When
            val result = auditService.findByUserId(testUserId, pageable)

            // Then
            assertThat(result.totalElements).isEqualTo(2)
            assertThat(result.content).hasSize(2)
            assertThat(result.content.all { it.userId == testUserId }).isTrue()

            verify(exactly = 1) {
                auditLogRepositoryDsl.findByUserId(testUserId, pageable)
            }
        }
    }

    @Nested
    @DisplayName("findByAction 테스트")
    inner class FindByActionTest {
        @Test
        @DisplayName("action으로 audit log 목록 조회")
        fun `findByAction - should return paginated logs for action`() {
            // Given
            val pageable = PageRequest.of(0, 20)
            val action = AuditAction.CREATE
            val logs =
                listOf(
                    createTestAuditLogEntity(id = 1L, action = action),
                    createTestAuditLogEntity(id = 2L, action = action),
                )
            val page = PageImpl(logs, pageable, 2)

            every {
                auditLogRepositoryDsl.findByAction(action, pageable)
            } returns page

            // When
            val result = auditService.findByAction(action, pageable)

            // Then
            assertThat(result.totalElements).isEqualTo(2)
            assertThat(result.content).hasSize(2)
            assertThat(result.content.all { it.action == action }).isTrue()

            verify(exactly = 1) {
                auditLogRepositoryDsl.findByAction(action, pageable)
            }
        }
    }

    @Nested
    @DisplayName("findByResource 테스트")
    inner class FindByResourceTest {
        @Test
        @DisplayName("resource로 audit log 목록 조회")
        fun `findByResource - should return paginated logs for resource`() {
            // Given
            val pageable = PageRequest.of(0, 20)
            val resource = AuditResource.WORKFLOW
            val logs =
                listOf(
                    createTestAuditLogEntity(id = 1L, resource = resource),
                    createTestAuditLogEntity(id = 2L, resource = resource),
                )
            val page = PageImpl(logs, pageable, 2)

            every {
                auditLogRepositoryDsl.findByResource(resource, pageable)
            } returns page

            // When
            val result = auditService.findByResource(resource, pageable)

            // Then
            assertThat(result.totalElements).isEqualTo(2)
            assertThat(result.content).hasSize(2)
            assertThat(result.content.all { it.resource == resource }).isTrue()

            verify(exactly = 1) {
                auditLogRepositoryDsl.findByResource(resource, pageable)
            }
        }
    }
}
