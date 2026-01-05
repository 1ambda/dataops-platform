package com.dataops.basecamp.domain.entity.user

import com.dataops.basecamp.domain.entity.BaseEntity
import jakarta.persistence.*

/**
 * 사용자 권한 엔티티
 *
 * 사용자가 기본 역할 외에 추가로 가진 권한을 나타냅니다.
 */
@Entity
@Table(name = "user_authority", schema = "public")
class UserAuthorityEntity(
    @Column(name = "user_id", nullable = false, updatable = false)
    var userId: Long = 0,
    @Column(name = "authority", nullable = false)
    var authority: String = "",
) : BaseEntity() {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is UserAuthorityEntity) return false
        return id != null && id == other.id
    }

    override fun hashCode(): Int = id?.hashCode() ?: 0
}
