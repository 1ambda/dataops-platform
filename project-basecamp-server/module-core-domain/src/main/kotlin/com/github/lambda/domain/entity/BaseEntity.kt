package com.github.lambda.domain.entity

import jakarta.persistence.Column
import jakarta.persistence.EntityListeners
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.MappedSuperclass
import org.springframework.data.annotation.CreatedBy
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.LastModifiedBy
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.time.LocalDateTime

/**
 * 모든 엔티티의 기본 클래스
 *
 * 생성/수정/삭제 감사(audit) 정보와 소프트 삭제 기능을 제공합니다.
 */
@MappedSuperclass
@EntityListeners(AuditingEntityListener::class)
abstract class BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null

    @CreatedBy
    @Column(name = "created_by")
    var createdBy: Long? = null

    @LastModifiedBy
    @Column(name = "updated_by")
    var updatedBy: Long? = null

    @Column(name = "deleted_by")
    var deletedBy: Long? = null

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: LocalDateTime? = null

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime? = null

    @Column(name = "deleted_at")
    var deletedAt: LocalDateTime? = null

    /**
     * 소프트 삭제 여부를 확인합니다.
     */
    val isDeleted: Boolean
        get() = deletedAt != null

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is BaseEntity) return false
        return id != null && id == other.id
    }

    override fun hashCode(): Int = id?.hashCode() ?: 0
}
