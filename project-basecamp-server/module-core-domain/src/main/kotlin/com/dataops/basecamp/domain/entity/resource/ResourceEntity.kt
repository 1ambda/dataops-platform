package com.dataops.basecamp.domain.entity.resource

import com.dataops.basecamp.domain.entity.BaseEntity
import jakarta.persistence.*

/**
 * 리소스 엔티티
 *
 * 사용자가 소유한 리소스를 나타냅니다.
 */
@Entity
@Table(name = "resource")
class ResourceEntity(
    @Column(name = "user_id", nullable = false)
    var userId: Long = 0,
    @Column(name = "name", nullable = false)
    var resource: String = "",
) : BaseEntity()
