package com.dataops.basecamp.domain.entity.audit

import com.dataops.basecamp.common.enums.AuditAction
import com.dataops.basecamp.common.enums.AuditResource
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.LocalDateTime

/**
 * Audit Log Entity
 *
 * Unified audit log entity that records all API calls.
 * Does NOT extend BaseEntity as audit logs are immutable.
 */
@Entity
@Table(
    name = "audit_log",
    indexes = [
        Index(name = "idx_audit_log_user_id", columnList = "user_id"),
        Index(name = "idx_audit_log_action", columnList = "action"),
        Index(name = "idx_audit_log_resource", columnList = "resource"),
        Index(name = "idx_audit_log_created_at", columnList = "created_at"),
        Index(name = "idx_audit_log_user_action", columnList = "user_id,action"),
        Index(name = "idx_audit_log_resource_action", columnList = "resource,action"),
        Index(name = "idx_audit_log_trace_id", columnList = "trace_id"),
    ],
)
class AuditLogEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    val id: Long? = null,
    // User Information
    @Column(name = "user_id", nullable = false, updatable = false, length = 100)
    val userId: String,
    @Column(name = "user_email", nullable = true, updatable = false, length = 255)
    val userEmail: String? = null,
    // Trace ID for distributed system request tracking
    @Column(name = "trace_id", nullable = true, updatable = false, length = 36)
    val traceId: String? = null,
    // Action & Resource Type
    @Column(name = "action", nullable = false, updatable = false, length = 50)
    @Enumerated(EnumType.STRING)
    val action: AuditAction,
    @Column(name = "resource", nullable = false, updatable = false, length = 50)
    @Enumerated(EnumType.STRING)
    val resource: AuditResource,
    // HTTP Request Information
    @Column(name = "http_method", nullable = false, updatable = false, length = 10)
    val httpMethod: String,
    @Column(name = "request_url", nullable = false, updatable = false, length = 2000)
    val requestUrl: String,
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "path_variables", nullable = true, updatable = false, columnDefinition = "json")
    val pathVariables: String? = null,
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "query_parameters", nullable = true, updatable = false, columnDefinition = "json")
    val queryParameters: String? = null,
    // Request Body (with sensitive keys filtered)
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "request_body", nullable = true, updatable = false, columnDefinition = "json")
    val requestBody: String? = null,
    // Response Information
    @Column(name = "response_status", nullable = false, updatable = false)
    val responseStatus: Int,
    @Column(name = "response_message", nullable = true, updatable = false, length = 500)
    val responseMessage: String? = null,
    // Performance Measurement
    @Column(name = "duration_ms", nullable = false, updatable = false)
    val durationMs: Long,
    // Client Information
    @Column(name = "client_type", nullable = true, updatable = false, length = 20)
    val clientType: String? = null,
    @Column(name = "client_ip", nullable = true, updatable = false, length = 45)
    val clientIp: String? = null,
    @Column(name = "user_agent", nullable = true, updatable = false, length = 500)
    val userAgent: String? = null,
    // Client Metadata - User-Agent parsing result
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "client_metadata", nullable = true, updatable = false, columnDefinition = "json")
    val clientMetadata: String? = null,
    // Additional Context
    @Column(name = "resource_id", nullable = true, updatable = false, length = 255)
    val resourceId: String? = null,
    @Column(name = "team_id", nullable = true, updatable = false)
    val teamId: Long? = null,
    // Timestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AuditLogEntity) return false
        return id != null && id == other.id
    }

    override fun hashCode(): Int = id?.hashCode() ?: 0
}
