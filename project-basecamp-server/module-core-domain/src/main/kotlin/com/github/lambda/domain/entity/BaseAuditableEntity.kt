package com.github.lambda.domain.entity

import jakarta.persistence.Column
import jakarta.persistence.EntityListeners
import jakarta.persistence.MappedSuperclass
import org.springframework.data.annotation.CreatedBy
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.LastModifiedBy
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.time.LocalDateTime

/**
 * 감사(Audit) 필드만 제공하는 기본 클래스
 *
 * ID 없이 생성/수정/삭제 감사 정보만 제공합니다.
 * 자체 ID(natural key)를 사용하는 엔티티에서 사용합니다.
 */
@MappedSuperclass
@EntityListeners(AuditingEntityListener::class)
abstract class BaseAuditableEntity {
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
    val createdAt: LocalDateTime? = null

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    val updatedAt: LocalDateTime? = null

    @Column(name = "deleted_at")
    var deletedAt: LocalDateTime? = null

    /**
     * 소프트 삭제 여부를 확인합니다.
     */
    val isDeleted: Boolean
        get() = deletedAt != null
}
