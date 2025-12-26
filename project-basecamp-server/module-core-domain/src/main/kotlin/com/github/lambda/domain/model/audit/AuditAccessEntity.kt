package com.github.lambda.domain.model.audit

import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.LocalDateTime

/**
 * 접근 감사 엔티티
 *
 * 사용자 접근 이력을 추적합니다.
 */
@Entity
@Table(name = "audit_access_history")
class AuditAccessEntity(
    @Column(name = "user_id", nullable = false, updatable = false)
    var userId: Long = 0,
    @Column(name = "access_type", nullable = false, updatable = false)
    var accessType: String = "",
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "access_content", updatable = false, columnDefinition = "json")
    var accessContent: String? = null,
    action: AuditResourceAction = AuditResourceAction.CREATE,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    val id: Long? = null

    @Column(name = "action", nullable = false, updatable = false)
    var action: String = action.value

    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: LocalDateTime = LocalDateTime.now()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AuditAccessEntity) return false
        return id != null && id == other.id
    }

    override fun hashCode(): Int = id?.hashCode() ?: 0
}
