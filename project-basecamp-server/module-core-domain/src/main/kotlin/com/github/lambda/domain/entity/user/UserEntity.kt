package com.github.lambda.domain.entity.user

import com.github.lambda.common.enums.UserRole
import com.github.lambda.domain.entity.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import java.time.LocalDateTime

/**
 * 사용자 엔티티
 */
@Entity
@Table(name = "user")
class UserEntity(
    @Column(name = "username", nullable = false, unique = true)
    var username: String = "",
    @Column(name = "email", nullable = false, unique = true)
    var email: String = "",
    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)
    var role: UserRole = UserRole.PUBLIC,
    @Column(name = "password")
    var password: String? = null,
    @Column(name = "enabled")
    var enabled: Boolean = true,
    @Column(name = "last_active_at")
    var lastActiveAt: LocalDateTime? = null,
) : BaseEntity() {
    /**
     * 이메일 기반으로 사용자 정보를 동기화합니다.
     */
    fun sync(email: String) {
        this.username = email.split("@")[0]
    }
}
