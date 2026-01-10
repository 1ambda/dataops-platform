package com.dataops.basecamp.dto.audit

import com.dataops.basecamp.common.enums.AuditAction
import com.dataops.basecamp.common.enums.AuditResource
import com.dataops.basecamp.domain.entity.audit.AuditLogEntity
import com.dataops.basecamp.domain.repository.audit.AuditStats
import com.fasterxml.jackson.annotation.JsonProperty
import java.time.LocalDateTime

/**
 * Audit Log DTO
 *
 * Response DTO for audit log entries.
 */
data class AuditLogDto(
    val id: Long,
    @JsonProperty("user_id")
    val userId: String,
    @JsonProperty("user_email")
    val userEmail: String?,
    @JsonProperty("trace_id")
    val traceId: String?,
    val action: AuditAction,
    val resource: AuditResource,
    @JsonProperty("http_method")
    val httpMethod: String,
    @JsonProperty("request_url")
    val requestUrl: String,
    @JsonProperty("path_variables")
    val pathVariables: String?,
    @JsonProperty("query_parameters")
    val queryParameters: String?,
    @JsonProperty("request_body")
    val requestBody: String?,
    @JsonProperty("response_status")
    val responseStatus: Int,
    @JsonProperty("response_message")
    val responseMessage: String?,
    @JsonProperty("duration_ms")
    val durationMs: Long,
    @JsonProperty("client_type")
    val clientType: String?,
    @JsonProperty("client_ip")
    val clientIp: String?,
    @JsonProperty("user_agent")
    val userAgent: String?,
    @JsonProperty("client_metadata")
    val clientMetadata: String?,
    @JsonProperty("resource_id")
    val resourceId: String?,
    @JsonProperty("team_id")
    val teamId: Long?,
    @JsonProperty("created_at")
    val createdAt: LocalDateTime,
)

/**
 * Extension function to convert AuditLogEntity to AuditLogDto
 */
fun AuditLogEntity.toDto(): AuditLogDto =
    AuditLogDto(
        id = this.id!!,
        userId = this.userId,
        userEmail = this.userEmail,
        traceId = this.traceId,
        action = this.action,
        resource = this.resource,
        httpMethod = this.httpMethod,
        requestUrl = this.requestUrl,
        pathVariables = this.pathVariables,
        queryParameters = this.queryParameters,
        requestBody = this.requestBody,
        responseStatus = this.responseStatus,
        responseMessage = this.responseMessage,
        durationMs = this.durationMs,
        clientType = this.clientType,
        clientIp = this.clientIp,
        userAgent = this.userAgent,
        clientMetadata = this.clientMetadata,
        resourceId = this.resourceId,
        teamId = this.teamId,
        createdAt = this.createdAt,
    )

/**
 * Audit Statistics DTO
 *
 * Response DTO for audit statistics.
 */
data class AuditStatsDto(
    @JsonProperty("total_count")
    val totalCount: Long,
    @JsonProperty("action_counts")
    val actionCounts: Map<String, Long>,
    @JsonProperty("resource_counts")
    val resourceCounts: Map<String, Long>,
    @JsonProperty("top_users")
    val topUsers: List<UserAuditCountDto>,
    @JsonProperty("avg_duration_ms")
    val avgDurationMs: Double,
)

/**
 * User Audit Count DTO
 */
data class UserAuditCountDto(
    @JsonProperty("user_id")
    val userId: String,
    val count: Long,
)

/**
 * Extension function to convert AuditStats to AuditStatsDto
 */
fun AuditStats.toDto(): AuditStatsDto =
    AuditStatsDto(
        totalCount = this.totalCount,
        actionCounts = this.actionCounts.mapKeys { it.key.name },
        resourceCounts = this.resourceCounts.mapKeys { it.key.name },
        topUsers =
            this.topUsers.map { user ->
                UserAuditCountDto(
                    userId = user.userId,
                    count = user.count,
                )
            },
        avgDurationMs = this.avgDurationMs,
    )
