package com.github.lambda.domain.entity.audit

import com.github.lambda.common.enums.AuditResourceAction
import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.LocalDateTime

/**
 * 리소스 감사 엔티티
 *
 * 리소스 변경 이력을 추적합니다.
 */
@Entity(name = "audit_resource_history")
@Table(name = "audit_resource_history")
class AuditResourceEntity(
    @Column(name = "user_id", nullable = false, updatable = false)
    var userId: Long = 0,
    @Column(name = "resource_type", nullable = false)
    var resourceType: String = "",
    @Column(name = "resource_id", nullable = false)
    var resourceId: Long = 0,
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "resource_content", columnDefinition = "json")
    var resourceContent: String? = null,
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
        if (other !is AuditResourceEntity) return false
        return id != null && id == other.id
    }

    override fun hashCode(): Int = id?.hashCode() ?: 0
}
