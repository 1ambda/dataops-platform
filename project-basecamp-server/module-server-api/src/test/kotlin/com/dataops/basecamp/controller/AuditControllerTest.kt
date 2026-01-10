package com.dataops.basecamp.controller

import com.dataops.basecamp.common.enums.AuditAction
import com.dataops.basecamp.common.enums.AuditResource
import com.dataops.basecamp.domain.entity.audit.AuditLogEntity
import com.dataops.basecamp.domain.repository.audit.AuditStats
import com.dataops.basecamp.domain.repository.audit.UserAuditCount
import com.dataops.basecamp.domain.service.AuditService
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * AuditController REST API Tests
 *
 * Spring Boot 4.x patterns with @SpringBootTest and @AutoConfigureMockMvc
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Execution(ExecutionMode.SAME_THREAD)
class AuditControllerTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockkBean
    private lateinit var auditService: AuditService

    private lateinit var testAuditLog: AuditLogEntity
    private lateinit var testStats: AuditStats
    private val now = LocalDateTime.now()
    private val testUserId = "user-123"
    private val testUserEmail = "test@example.com"

    @BeforeEach
    fun setUp() {
        testAuditLog =
            AuditLogEntity(
                id = 1L,
                userId = testUserId,
                userEmail = testUserEmail,
                traceId = "trace-abc-123",
                action = AuditAction.READ,
                resource = AuditResource.METRIC,
                httpMethod = "GET",
                requestUrl = "http://localhost:8080/api/v1/metrics/test-metric",
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

        testStats =
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
                    ),
                avgDurationMs = 150.5,
            )
    }

    @Nested
    @DisplayName("GET /api/v1/management/audit/logs")
    inner class ListLogs {
        @Test
        @DisplayName("should return paginated audit log list")
        @WithMockUser(roles = ["ADMIN"])
        fun `listLogs_success - should return paginated audit logs`() {
            // Given
            val pageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt"))
            val logs = listOf(testAuditLog)
            val page = PageImpl(logs, pageable, 1)

            every {
                auditService.searchLogs(
                    userId = null,
                    action = null,
                    resource = null,
                    startDate = null,
                    endDate = null,
                    pageable = any(),
                )
            } returns page

            // When & Then
            mockMvc
                .perform(get("/api/v1/management/audit/logs"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.content[0].id").value(1))
                .andExpect(jsonPath("$.content[0].user_id").value(testUserId))
                .andExpect(jsonPath("$.content[0].action").value("READ"))
                .andExpect(jsonPath("$.content[0].resource").value("METRIC"))
                .andExpect(jsonPath("$.content[0].http_method").value("GET"))
                .andExpect(jsonPath("$.content[0].response_status").value(200))

            verify(exactly = 1) {
                auditService.searchLogs(
                    userId = null,
                    action = null,
                    resource = null,
                    startDate = null,
                    endDate = null,
                    pageable = any(),
                )
            }
        }

        @Test
        @DisplayName("should filter by userId, action, and resource")
        @WithMockUser(roles = ["ADMIN"])
        fun `listLogs_withFilters - should filter logs`() {
            // Given
            val pageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt"))
            val logs = listOf(testAuditLog)
            val page = PageImpl(logs, pageable, 1)

            every {
                auditService.searchLogs(
                    userId = testUserId,
                    action = AuditAction.READ,
                    resource = AuditResource.METRIC,
                    startDate = null,
                    endDate = null,
                    pageable = any(),
                )
            } returns page

            // When & Then
            mockMvc
                .perform(
                    get("/api/v1/management/audit/logs")
                        .param("userId", testUserId)
                        .param("action", "READ")
                        .param("resource", "METRIC"),
                ).andExpect(status().isOk)
                .andExpect(jsonPath("$.content[0].user_id").value(testUserId))
                .andExpect(jsonPath("$.content[0].action").value("READ"))
                .andExpect(jsonPath("$.content[0].resource").value("METRIC"))

            verify(exactly = 1) {
                auditService.searchLogs(
                    userId = testUserId,
                    action = AuditAction.READ,
                    resource = AuditResource.METRIC,
                    startDate = null,
                    endDate = null,
                    pageable = any(),
                )
            }
        }

        @Test
        @DisplayName("should filter by date range")
        @WithMockUser(roles = ["ADMIN"])
        fun `listLogs_withDateRange - should filter logs by date`() {
            // Given
            val startDate = now.minusDays(7)
            val endDate = now
            val formatter = DateTimeFormatter.ISO_DATE_TIME

            val pageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt"))
            val logs = listOf(testAuditLog)
            val page = PageImpl(logs, pageable, 1)

            every {
                auditService.searchLogs(
                    userId = null,
                    action = null,
                    resource = null,
                    startDate = any(),
                    endDate = any(),
                    pageable = any(),
                )
            } returns page

            // When & Then
            mockMvc
                .perform(
                    get("/api/v1/management/audit/logs")
                        .param("startDate", startDate.format(formatter))
                        .param("endDate", endDate.format(formatter)),
                ).andExpect(status().isOk)
                .andExpect(jsonPath("$.content").isArray)
                .andExpect(jsonPath("$.content[0].id").value(1))

            verify(exactly = 1) {
                auditService.searchLogs(
                    userId = null,
                    action = null,
                    resource = null,
                    startDate = any(),
                    endDate = any(),
                    pageable = any(),
                )
            }
        }
    }

    @Nested
    @DisplayName("GET /api/v1/management/audit/logs/{id}")
    inner class GetLog {
        @Test
        @DisplayName("should return audit log by id")
        @WithMockUser(roles = ["ADMIN"])
        fun `getLog_success - should return audit log details`() {
            // Given
            val logId = 1L
            every { auditService.findById(logId) } returns testAuditLog

            // When & Then
            mockMvc
                .perform(get("/api/v1/management/audit/logs/$logId"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.id").value(logId))
                .andExpect(jsonPath("$.user_id").value(testUserId))
                .andExpect(jsonPath("$.user_email").value(testUserEmail))
                .andExpect(jsonPath("$.trace_id").value("trace-abc-123"))
                .andExpect(jsonPath("$.action").value("READ"))
                .andExpect(jsonPath("$.resource").value("METRIC"))
                .andExpect(jsonPath("$.http_method").value("GET"))
                .andExpect(jsonPath("$.response_status").value(200))
                .andExpect(jsonPath("$.duration_ms").value(150))
                .andExpect(jsonPath("$.client_type").value("WEB"))
                .andExpect(jsonPath("$.client_ip").value("192.168.1.1"))
                .andExpect(jsonPath("$.resource_id").value("test-metric"))
                .andExpect(jsonPath("$.team_id").value(1))

            verify(exactly = 1) { auditService.findById(logId) }
        }

        @Test
        @DisplayName("should return 404 for non-existing id")
        @WithMockUser(roles = ["ADMIN"])
        fun `getLog_notFound - should return 404 when not found`() {
            // Given
            val nonExistentId = 999L
            every { auditService.findById(nonExistentId) } returns null

            // When & Then
            mockMvc
                .perform(get("/api/v1/management/audit/logs/$nonExistentId"))
                .andExpect(status().isNotFound)

            verify(exactly = 1) { auditService.findById(nonExistentId) }
        }
    }

    @Nested
    @DisplayName("GET /api/v1/management/audit/stats")
    inner class GetStats {
        @Test
        @DisplayName("should return audit statistics")
        @WithMockUser(roles = ["ADMIN"])
        fun `getStats_success - should return audit statistics`() {
            // Given
            every {
                auditService.getStats(
                    startDate = null,
                    endDate = null,
                )
            } returns testStats

            // When & Then
            mockMvc
                .perform(get("/api/v1/management/audit/stats"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.total_count").value(100))
                .andExpect(jsonPath("$.action_counts.READ").value(50))
                .andExpect(jsonPath("$.action_counts.CREATE").value(30))
                .andExpect(jsonPath("$.action_counts.UPDATE").value(15))
                .andExpect(jsonPath("$.action_counts.DELETE").value(5))
                .andExpect(jsonPath("$.resource_counts.METRIC").value(40))
                .andExpect(jsonPath("$.resource_counts.DATASET").value(35))
                .andExpect(jsonPath("$.resource_counts.WORKFLOW").value(25))
                .andExpect(jsonPath("$.top_users[0].user_id").value("user-1"))
                .andExpect(jsonPath("$.top_users[0].count").value(30))
                .andExpect(jsonPath("$.top_users[1].user_id").value("user-2"))
                .andExpect(jsonPath("$.top_users[1].count").value(25))
                .andExpect(jsonPath("$.avg_duration_ms").value(150.5))

            verify(exactly = 1) {
                auditService.getStats(
                    startDate = null,
                    endDate = null,
                )
            }
        }

        @Test
        @DisplayName("should return stats for date range")
        @WithMockUser(roles = ["ADMIN"])
        fun `getStats_withDateRange - should return stats for period`() {
            // Given
            val startDate = now.minusDays(7)
            val endDate = now
            val formatter = DateTimeFormatter.ISO_DATE_TIME

            val periodStats =
                AuditStats(
                    totalCount = 50,
                    actionCounts = mapOf(AuditAction.READ to 30L),
                    resourceCounts = mapOf(AuditResource.METRIC to 25L),
                    topUsers = listOf(UserAuditCount(userId = "user-1", count = 15L)),
                    avgDurationMs = 120.0,
                )

            every {
                auditService.getStats(
                    startDate = any(),
                    endDate = any(),
                )
            } returns periodStats

            // When & Then
            mockMvc
                .perform(
                    get("/api/v1/management/audit/stats")
                        .param("startDate", startDate.format(formatter))
                        .param("endDate", endDate.format(formatter)),
                ).andExpect(status().isOk)
                .andExpect(jsonPath("$.total_count").value(50))
                .andExpect(jsonPath("$.avg_duration_ms").value(120.0))

            verify(exactly = 1) {
                auditService.getStats(
                    startDate = any(),
                    endDate = any(),
                )
            }
        }
    }
}
